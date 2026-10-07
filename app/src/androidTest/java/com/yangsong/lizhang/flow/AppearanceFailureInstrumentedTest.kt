package com.yangsong.lizhang.flow

import android.graphics.Bitmap
import android.view.PixelCopy
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.MainActivity
import com.yangsong.lizhang.domain.model.AppThemeMode
import com.yangsong.lizhang.ui.component.AppearanceTransitionHost
import com.yangsong.lizhang.ui.viewmodel.AppearanceTransitionViewModel
import com.yangsong.lizhang.ui.viewmodel.SettingsViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 专门控制异常回调顺序；原多语言后的真实触摸仍由 NativeTransitionRecordingTest 验收。 */
class AppearanceFailureInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as LiZhangApplication
    private class Captures {
        val pending = mutableListOf<Pair<Bitmap, (Int) -> Unit>>()
        fun request(bitmap: Bitmap, completed: (Int) -> Unit) { pending += bitmap to completed }
        fun complete(index: Int, result: Int) = pending[index].second(result)
    }
    private fun settings() = SettingsViewModel(app.appContainer.giftRecordRepository,
        app.appContainer.backupRepository, app.appContainer.themeRepository)

    @Test fun 旧宿主首次解除不能清除新快照或注销新监听() {
        compose.runOnIdle {
            val state = AppearanceTransitionViewModel()
            val root = compose.activity.findViewById<FrameLayout>(android.R.id.content)
            val old = AppearanceTransitionHost(compose.activity, state).also { it.attach() }
            val current = AppearanceTransitionHost(compose.activity, state).also { it.attach() }
            val currentOverlay = root.getChildAt(root.childCount - 1)
            val id = state.nextRequest()
            val shot = AppearanceTransitionViewModel.Snapshot(id,
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), root.width, root.height,
                0f, 0f, true, null, originHost = current.diagnosticHostId)
            state.install(shot)
            state.pendingDark = true
            try {
                old.detach(preserveLanguage = false)
                assertSame("首次旧清理不能取消已经由新宿主接管的请求", shot, state.snapshot)
                assertEquals(id, state.requestId)
                assertEquals(true, state.pendingDark)
                state.clear()
                assertEquals("新监听仍能隐藏新覆盖层", View.GONE, currentOverlay.visibility)
                old.detach(preserveLanguage = false)
                assertEquals(id, state.requestId)
            } finally {
                old.detach(preserveLanguage = false)
                current.detach(preserveLanguage = false)
                state.clear()
            }
        }
    }

    @Test fun 旧宿主迟到捕获与取消不能反转新宿主目标() {
        compose.runOnIdle {
            val state = AppearanceTransitionViewModel()
            val oldCaptures = Captures()
            val newCaptures = Captures()
            val viewModel = settings()
            viewModel.setThemeMode(AppThemeMode.LIGHT)
            val store = androidx.lifecycle.ViewModelStore().also { it.put("异常主题", viewModel) }
            val old = AppearanceTransitionHost(compose.activity, state, oldCaptures::request).also { it.attach() }
            val current = AppearanceTransitionHost(compose.activity, state, newCaptures::request)
            var oldSubmissions = 0
            var newSubmissions = 0
            try {
                old.circular(Offset.Zero, false) { oldSubmissions++; viewModel.setThemeMode(AppThemeMode.LIGHT) }
                current.attach()
                current.circular(Offset.Zero, true) { newSubmissions++; viewModel.setThemeMode(AppThemeMode.DARK) }
                val id = state.requestId
                old.cancel()
                old.detach(preserveLanguage = false)
                if (oldCaptures.pending.isNotEmpty()) oldCaptures.complete(0, PixelCopy.SUCCESS)
                if (newCaptures.pending.isNotEmpty()) newCaptures.complete(0, PixelCopy.ERROR_SOURCE_NO_DATA)
                assertEquals("旧清理与迟到回调不创建新请求", id, state.requestId)
                assertEquals("被更新请求替代的旧目标不提交", if (android.animation.ValueAnimator.areAnimatorsEnabled()) 0 else 1, oldSubmissions)
                assertEquals(1, newSubmissions)
                assertEquals(AppThemeMode.DARK, app.appContainer.themeRepository.themeMode.value)
                assertNull(state.pendingDark)
                assertNull(state.snapshot)
            } finally {
                old.detach(false); current.detach(false); state.clear(); store.clear()
            }
        }
    }

    @Test fun PixelCopy失败仍经ViewModel提交目标并释放状态() {
        compose.runOnIdle {
            val state = AppearanceTransitionViewModel()
            val captures = Captures()
            val viewModel = settings()
            viewModel.setThemeMode(AppThemeMode.LIGHT)
            val store = androidx.lifecycle.ViewModelStore().also { it.put("异常主题", viewModel) }
            val host = AppearanceTransitionHost(compose.activity, state, captures::request).also { it.attach() }
            var submissions = 0
            try {
                host.circular(Offset.Zero, true) { submissions++; viewModel.setThemeMode(AppThemeMode.DARK) }
                if (captures.pending.isNotEmpty()) captures.complete(0, PixelCopy.ERROR_SOURCE_NO_DATA)
                assertEquals(1, submissions)
                assertEquals(AppThemeMode.DARK, app.appContainer.themeRepository.themeMode.value)
                assertNull(state.pendingDark)
                assertNull(state.snapshot)
            } finally { host.detach(false); state.clear(); store.clear() }
        }
    }

    @Test fun 捕获超时提交有效目标且迟到成功不恢复覆盖层() {
        lateinit var state: AppearanceTransitionViewModel
        lateinit var host: AppearanceTransitionHost
        lateinit var store: androidx.lifecycle.ViewModelStore
        val captures = Captures()
        val submissions = java.util.concurrent.atomic.AtomicInteger()
        compose.runOnIdle {
            state = AppearanceTransitionViewModel()
            val viewModel = settings()
            viewModel.setThemeMode(AppThemeMode.DARK)
            store = androidx.lifecycle.ViewModelStore().also { it.put("异常主题", viewModel) }
            host = AppearanceTransitionHost(compose.activity, state, captures::request).also { it.attach() }
            host.circular(Offset.Zero, false) { submissions.incrementAndGet(); viewModel.setThemeMode(AppThemeMode.LIGHT) }
        }
        try {
            compose.waitUntil(2000) { submissions.get() == 1 }
            compose.runOnIdle {
                val id = state.requestId
                if (captures.pending.isNotEmpty()) captures.complete(0, PixelCopy.SUCCESS)
                assertEquals(id, state.requestId)
                assertEquals(1, submissions.get())
                assertEquals(AppThemeMode.LIGHT, app.appContainer.themeRepository.themeMode.value)
                assertNull(state.pendingDark)
                assertNull(state.snapshot)
            }
        } finally { compose.runOnIdle { host.detach(false); state.clear(); store.clear() } }
    }

    @Test fun 捕获期间中断仍提交未被替代的用户目标() {
        compose.runOnIdle {
            val state = AppearanceTransitionViewModel()
            val captures = Captures()
            val viewModel = settings()
            viewModel.setThemeMode(AppThemeMode.LIGHT)
            val store = androidx.lifecycle.ViewModelStore().also { it.put("异常主题", viewModel) }
            val host = AppearanceTransitionHost(compose.activity, state, captures::request).also { it.attach() }
            var submissions = 0
            try {
                host.circular(Offset.Zero, true) { submissions++; viewModel.setThemeMode(AppThemeMode.DARK) }
                host.cancel()
                if (captures.pending.isNotEmpty()) captures.complete(0, PixelCopy.SUCCESS)
                assertEquals(1, submissions)
                assertEquals(AppThemeMode.DARK, app.appContainer.themeRepository.themeMode.value)
                assertNull(state.pendingDark)
                assertNull(state.snapshot)
            } finally { host.detach(false); state.clear(); store.clear() }
        }
    }

    @Test fun 重建中旧宿主仍提交未被新请求替代的已接受目标() {
        compose.runOnIdle {
            val state = AppearanceTransitionViewModel()
            val captures = Captures()
            val viewModel = settings()
            viewModel.setThemeMode(AppThemeMode.LIGHT)
            val store = androidx.lifecycle.ViewModelStore().also { it.put("异常主题", viewModel) }
            val old = AppearanceTransitionHost(compose.activity, state, captures::request).also { it.attach() }
            val current = AppearanceTransitionHost(compose.activity, state)
            var submissions = 0
            try {
                old.circular(Offset.Zero, true) { submissions++; viewModel.setThemeMode(AppThemeMode.DARK) }
                val id = state.requestId
                current.attach()
                old.detach(preserveLanguage = false)
                if (captures.pending.isNotEmpty()) captures.complete(0, PixelCopy.SUCCESS)
                assertEquals("没有新请求时不静默丢弃已接受目标", 1, submissions)
                assertEquals(id, state.requestId)
                assertEquals(AppThemeMode.DARK, app.appContainer.themeRepository.themeMode.value)
                assertNull(state.pendingDark)
                assertNull(state.snapshot)
            } finally { old.detach(false); current.detach(false); state.clear(); store.clear() }
        }
    }
}
