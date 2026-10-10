package com.yangsong.lizhang.flow

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.ui.component.AndroidStartupWindowCallbacks
import com.yangsong.lizhang.ui.component.StartupSplashHost
import com.yangsong.lizhang.ui.component.StartupWindowGate
import com.yangsong.lizhang.ui.viewmodel.StartupAnimationViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 独立 Activity 与合成触摸视图；不读写联系人、账本或隐私同意状态。 */
class StartupSplashCleanupInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private class LateSplashHost(private val activity: ComponentActivity) : StartupSplashHost {
        override val supported = true
        var exit: (((() -> Unit) -> Unit))? = null
        var registrations = 0
        var maximumRegistrations = 0
        var overlay: View? = null
        var interceptedTouches = 0

        override fun setExitListener(listener: (() -> Unit) -> Unit) {
            registrations++
            maximumRegistrations = maxOf(maximumRegistrations, registrations)
            exit = listener
        }

        override fun clearExitListener() {
            registrations = (registrations - 1).coerceAtLeast(0)
            exit = null
        }

        fun attachLateView() {
            overlay = View(activity).also { view ->
                view.setOnTouchListener { _, _ -> interceptedTouches++; true }
                (activity.window.decorView as ViewGroup).addView(view,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }

        override fun removeAttachedViews() {
            overlay?.let { view -> (view.parent as? ViewGroup)?.removeView(view) }
        }

        fun deliverExit() {
            requireNotNull(exit).invoke { removeAttachedViews() }
        }
    }

    private fun waitFor(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 4000
        var satisfied = false
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync { satisfied = predicate() }
            if (satisfied) break
            SystemClock.sleep(10)
        }
        assertTrue(message, satisfied)
    }

    private fun click(activity: ComponentActivity, button: Button) {
        val position = IntArray(2)
        button.getLocationInWindow(position)
        val x = position[0] + button.width / 2f
        val y = position[1] + button.height / 2f
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 1, MotionEvent.ACTION_UP, x, y, 0)
        try {
            activity.dispatchTouchEvent(down)
            activity.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    @Test fun 超时后才转交的启动视图被移除且真实窗口触摸可达() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var activity: ComponentActivity
            lateinit var button: Button
            lateinit var host: LateSplashHost
            lateinit var state: StartupAnimationViewModel
            lateinit var gate: StartupWindowGate
            var clicks = 0
            scenario.onActivity {
                activity = it
                button = Button(it).apply { text = "合成触摸目标"; setOnClickListener { clicks++ } }
                it.setContentView(button)
                host = LateSplashHost(it)
                state = StartupAnimationViewModel()
                state.initialize(eligible = true, animationsEnabled = true)
                gate = StartupWindowGate(it, state, true, AndroidStartupWindowCallbacks(it, host))
                gate.attach()
            }
            try {
                waitFor("退出回调和动画帧均缺失时必须结束启动覆盖") { !state.visible }
                instrumentation.runOnMainSync {
                    assertTrue("真实超时路径已经执行", state.diagnostics().any { "超时" in it.event })
                    assertNotNull("超时后仍保留仅负责移除迟到视图的监听", host.exit)
                    host.attachLateView()
                }
                waitFor("迟到覆盖视图已经实际布局") { host.overlay?.width == activity.window.decorView.width }
                instrumentation.runOnMainSync {
                    click(activity, button)
                    assertTrue("移除前覆盖视图确实拦截窗口触摸", host.interceptedTouches > 0)
                    assertEquals(0, clicks)
                    host.deliverExit()
                    assertNull("迟到退出必须移除窗口子视图", host.overlay?.parent)
                    assertFalse("迟到退出不能复活启动状态", state.visible)
                    assertFalse(state.ready)
                    click(activity, button)
                }
                waitFor("移除迟到启动视图后窗口触摸必须到达目标") { clicks == 1 }
                assertEquals("替换监听始终只保留一个系统登记", 1, host.maximumRegistrations)
            } finally {
                instrumentation.runOnMainSync { gate.detach(); host.removeAttachedViews() }
            }
        }
    }

    @Test fun 同一窗口重新接管会替换迟到清理监听且主动取消移除现有覆盖() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity {
                val host = LateSplashHost(it)
                val callbacks = AndroidStartupWindowCallbacks(it, host)
                callbacks.setSplashExitListener { remove -> remove() }
                callbacks.clearSplashExitListener()
                val oldCleanup = requireNotNull(host.exit)
                var ready = false
                callbacks.setSplashExitListener { remove -> remove(); ready = true }
                val current = requireNotNull(host.exit)
                oldCleanup.invoke {}
                assertTrue("旧迟到回调不能清掉新监听", host.exit === current)
                assertFalse(ready)
                host.deliverExit()
                assertTrue("新的接管监听仍能接收退出", ready)
                val state = StartupAnimationViewModel()
                state.initialize(eligible = true, animationsEnabled = true)
                val gate = StartupWindowGate(it, state, true, callbacks)
                gate.attach()
                try {
                    host.attachLateView()
                    assertNotNull(host.overlay?.parent)
                    gate.cancel()
                    assertNull("主动取消立即移除已经挂载的系统覆盖", host.overlay?.parent)
                    assertFalse(state.visible)
                    gate.detach()
                    assertEquals(1, host.maximumRegistrations)
                } finally {
                    gate.detach()
                    host.removeAttachedViews()
                }
            }
        }
    }
}
