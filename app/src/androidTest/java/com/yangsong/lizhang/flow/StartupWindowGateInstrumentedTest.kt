package com.yangsong.lizhang.flow

import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.MainActivity
import com.yangsong.lizhang.ui.component.StartupWindowCallbacks
import com.yangsong.lizhang.ui.component.StartupWindowGate
import com.yangsong.lizhang.ui.viewmodel.StartupAnimationViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 真实 Activity 根视图 + 独立非敏感启动状态；故障注入不能替代真实系统画面冷启动验收。 */
class StartupWindowGateInstrumentedTest {
    @get:org.junit.Rule(order = 0) val acceptedPrivacy = com.yangsong.lizhang.fixtures.AcceptedPrivacyRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private class CapturedCallbacks : StartupWindowCallbacks {
        // API 26 也注入等待退出的路径，覆盖缺失回调与旧宿主保护，不按系统版本跳过。
        override val supportsSystemSplash = true
        val pendingCommits = mutableListOf<Runnable>()
        var exit: (((() -> Unit) -> Unit))? = null
        var clearCount = 0
        var removeCount = 0
        override fun afterContentDraw(root: View, committed: Runnable) { pendingCommits.add(committed) }
        override fun setSplashExitListener(listener: (() -> Unit) -> Unit) { exit = listener }
        override fun clearSplashExitListener() { clearCount++; exit = null }
        fun emitExit(callback: ((() -> Unit) -> Unit)? = exit) { requireNotNull(callback).invoke { removeCount++ } }
    }

    private fun waitFor(message: String, timeoutMillis: Long = 4000, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        var satisfied = false
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync { satisfied = predicate() }
            if (satisfied) break
            // 仅轮询已经发生的回调，不用固定等待替代真实窗口交接。
            SystemClock.sleep(10)
        }
        assertTrue(message, satisfied)
    }

    @Test fun 系统退出回调缺失时降级播放并在帧缺失后释放启动覆盖状态() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var root: View
            lateinit var state: StartupAnimationViewModel
            lateinit var gate: StartupWindowGate
            var capturedExit: ((() -> Unit) -> Unit)? = null
            val callbacks = CapturedCallbacks()
            scenario.onActivity {
                root = it.findViewById(android.R.id.content)
                state = StartupAnimationViewModel()
                state.initialize(eligible = true, animationsEnabled = true)
                gate = StartupWindowGate(it, state, waitForSystemSplash = true, windowCallbacks = callbacks)
                gate.attach()
                capturedExit = callbacks.exit
                root.postInvalidateOnAnimation()
            }
            try {
                waitFor("真实根视图已绘制并请求内容提交回调") { callbacks.pendingCommits.size == 1 }
                instrumentation.runOnMainSync {
                    callbacks.pendingCommits.single().run()
                    assertTrue(state.visible)
                    // 已绘制后的短宽限允许缺失退出回调时降级播放，不能仍要求旧无限等待语义。
                    assertFalse(state.hasStarted)
                }
                waitFor("既有退出回调超时必须释放，不能永久遮挡") { !state.visible }
                instrumentation.runOnMainSync {
                    assertFalse(state.ready)
                    assertEquals(1f, state.progress, 0f)
                    assertTrue(state.diagnostics().any { "降级播放" in it.event })
                    assertTrue(state.diagnostics().any { it.event == "启动动画帧回调超时" })
                    assertTrue("降级与超时结束都清理退出监听", callbacks.clearCount > 0)
                    callbacks.emitExit(capturedExit)
                    assertFalse("超时后的迟到退出不能复活启动层", state.visible)
                }
            } finally {
                instrumentation.runOnMainSync { gate.detach() }
            }
        }
    }

    @Test fun 旧宿主迟到提交退出与重复清理不能影响新宿主() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val callbacks = CapturedCallbacks()
            lateinit var state: StartupAnimationViewModel
            lateinit var oldGate: StartupWindowGate
            lateinit var newGate: StartupWindowGate
            lateinit var root: View
            var oldExit: ((() -> Unit) -> Unit)? = null
            scenario.onActivity {
                root = it.findViewById(android.R.id.content)
                state = StartupAnimationViewModel()
                state.initialize(eligible = true, animationsEnabled = true)
                oldGate = StartupWindowGate(it, state, true, callbacks)
                oldGate.attach()
                oldExit = callbacks.exit
                root.postInvalidateOnAnimation()
            }
            waitFor("旧宿主真实绘制已登记提交回调") { callbacks.pendingCommits.size == 1 }
            scenario.onActivity {
                oldGate.detach()
                newGate = StartupWindowGate(it, state, true, callbacks)
                newGate.attach()
                root.postInvalidateOnAnimation()
            }
            try {
                waitFor("新宿主真实绘制已登记自己的提交回调") { callbacks.pendingCommits.size == 2 }
                instrumentation.runOnMainSync {
                    val clearsAfterNewAttach = callbacks.clearCount
                    val newExit = callbacks.exit
                    assertNotNull(newExit)
                    oldGate.detach()
                    oldGate.detach()
                    assertEquals("旧宿主重复清理不能注销新退出回调", clearsAfterNewAttach, callbacks.clearCount)
                    callbacks.pendingCommits[0].run()
                    callbacks.pendingCommits[0].run()
                    callbacks.emitExit(oldExit)
                    assertFalse("旧回调不能使新窗口提前就绪", state.ready)
                    assertTrue("旧回调不能释放新的有效启动请求", state.visible)
                    callbacks.pendingCommits[1].run()
                    assertFalse("新窗口内容提交仍须等自己的退出回调", state.ready)
                    callbacks.emitExit(newExit)
                    assertTrue("新窗口两项条件都完成后可播放", state.ready)
                    state.onFrame(0L)
                    state.onFrame(900_000_000L)
                    assertFalse(state.visible)
                    assertEquals(1f, state.progress, 0f)
                }
            } finally {
                instrumentation.runOnMainSync { oldGate.detach(); newGate.detach() }
            }
        }
    }

    @Test fun 启动过程主动中断后迟到提交和退出不复活覆盖层() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val callbacks = CapturedCallbacks()
            lateinit var state: StartupAnimationViewModel
            lateinit var gate: StartupWindowGate
            var capturedExit: ((() -> Unit) -> Unit)? = null
            scenario.onActivity {
                state = StartupAnimationViewModel()
                state.initialize(eligible = true, animationsEnabled = true)
                gate = StartupWindowGate(it, state, true, callbacks)
                gate.attach()
                capturedExit = callbacks.exit
                it.findViewById<View>(android.R.id.content).postInvalidateOnAnimation()
            }
            try {
                waitFor("真实根视图已登记可迟到的提交回调") { callbacks.pendingCommits.size == 1 }
                instrumentation.runOnMainSync {
                    state.finish()
                    callbacks.pendingCommits.single().run()
                    callbacks.emitExit(capturedExit)
                    state.onFrame(500_000_000L)
                    assertFalse(state.visible)
                    assertFalse(state.ready)
                    assertFalse(state.hasStarted)
                    assertEquals(1f, state.progress, 0f)
                }
            } finally {
                instrumentation.runOnMainSync { gate.detach() }
            }
        }
    }
}
