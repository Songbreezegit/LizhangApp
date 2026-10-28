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
    @get:org.junit.Rule(order = 0) val acceptedPrivacy = com.yangsong.lizhang.fixtures.AcceptedPrivacyRule()
    @get:Rule(order = 1) val completedGuides: org.junit.rules.TestRule = AppearanceGuidesRule()
    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as LiZhangApplication
    private class Captures {
        val pending = mutableListOf<Pair<Bitmap, (Int) -> Unit>>()
        fun request(bitmap: Bitmap, completed: (Int) -> Unit) { pending += bitmap to completed }
        fun complete(index: Int, result: Int) = pending[index].second(result)
    }
    private fun settings() = SettingsViewModel(app.appContainer.giftRecordRepository,
        app.appContainer.backupRepository, app.appContainer.themeRepository)

    @Test fun 捕获和等待绘制期间十次重复请求只接受首次目标() {
        org.junit.Assume.assumeTrue(android.animation.ValueAnimator.areAnimatorsEnabled())
        compose.runOnIdle {
            val state = AppearanceTransitionViewModel()
            val captures = Captures()
            val host = AppearanceTransitionHost(compose.activity, state, captures::request).also { it.attach() }
            var submissions = 0
            var rejectedSubmissions = 0
            try {
                host.circular(Offset.Zero, true) { submissions++ }
                val id = state.requestId
                repeat(10) {
                    host.circular(Offset.Zero, false) { rejectedSubmissions++ }
                    host.colors(false) { rejectedSubmissions++ }
                }
                assertTrue(state.isThemeTransitioning)
                assertEquals(true, state.pendingDark)
                assertEquals(id, state.requestId)
                assertEquals(1, captures.pending.size)
                assertEquals(0, submissions)
                captures.complete(0, PixelCopy.SUCCESS)
                assertEquals(1, submissions)
                assertNull(state.pendingDark)
                assertTrue("仓库提交及 pendingDark 清空都不解锁", state.isThemeTransitioning)
                assertFalse(state.snapshot!!.started)
                repeat(10) { host.circular(Offset.Zero, false) { rejectedSubmissions++ } }
                assertEquals(0, rejectedSubmissions)
                assertEquals(1, captures.pending.size)
                host.cancel()
                assertNull(state.snapshot)
                assertFalse(state.isThemeTransitioning)
                host.circular(Offset.Zero, false) { submissions++ }
                assertEquals(2, captures.pending.size)
                val nextId = state.requestId
                state.clear(id)
                assertEquals(nextId, state.themeRequest!!.id)
                host.cancel()
                host.cancel()
                assertEquals("取消和重复取消只提交一次尚未提交目标", 2, submissions)
                assertFalse(state.isThemeTransitioning)
            } finally { host.detach(false); state.clear() }
        }
    }

    @Test fun 颜色渐变与圆形共用锁且完成回调校验请求及目标() {
        org.junit.Assume.assumeTrue(android.animation.ValueAnimator.areAnimatorsEnabled())
        compose.runOnIdle {
            val state = AppearanceTransitionViewModel()
            val captures = Captures()
            val host = AppearanceTransitionHost(compose.activity, state, captures::request).also { it.attach() }
            var submissions = 0
            try {
                host.colors(true) { submissions++ }
                val id = state.requestId
                repeat(10) {
                    host.colors(false) { submissions++ }
                    host.circular(Offset.Zero, false) { submissions++ }
                }
                assertEquals(1, submissions)
                assertTrue(state.animateColors)
                assertTrue(state.isThemeTransitioning)
                assertTrue(captures.pending.isEmpty())
                val oldExecution = requireNotNull(host.colorsStarted(id, true))
                val execution = requireNotNull(host.colorsStarted(id, true))
                host.colorsFinished(id - 1, execution, true)
                host.colorsFinished(id, execution, false)
                host.colorsFinished(id, oldExecution, true)
                assertTrue(state.isThemeTransitioning)
                host.colorsFinished(id, execution, true)
                assertFalse(state.isThemeTransitioning)
                host.circular(Offset.Zero, false) { submissions++ }
                host.colorsFinished(id, execution, true)
                assertTrue("旧渐变回调不能解锁新的圆形请求", state.isThemeTransitioning)
                captures.complete(0, PixelCopy.ERROR_SOURCE_NO_DATA)
                assertEquals(2, submissions)
                assertFalse(state.isThemeTransitioning)
            } finally { host.detach(false); state.clear() }
        }
    }

    @Test fun 分配失败和捕获抛错仍提交一次并允许下一次切换() {
        org.junit.Assume.assumeTrue(android.animation.ValueAnimator.areAnimatorsEnabled())
        compose.runOnIdle {
            for (allocationFails in listOf(true, false)) {
                val state = AppearanceTransitionViewModel()
                val host = AppearanceTransitionHost(compose.activity, state,
                    captureWindowForTest = { _, _ -> throw IllegalStateException("测试捕获失败") },
                    bitmapForTest = if (allocationFails) { _, _ -> throw OutOfMemoryError("测试分配失败") } else null,
                ).also { it.attach() }
                var submissions = 0
                try {
                    repeat(2) {
                        host.circular(Offset.Zero, it == 0) { submissions++ }
                        assertEquals(it + 1, submissions)
                        assertNull(state.snapshot)
                        assertFalse(state.isThemeTransitioning)
                    }
                } finally { host.detach(false); state.clear() }
            }
        }
    }

    @Test fun 无效尺寸直接提交并释放主题锁() {
        compose.runOnIdle {
            val root = compose.activity.findViewById<FrameLayout>(android.R.id.content)
            val bounds = android.graphics.Rect(root.left, root.top, root.right, root.bottom)
            val state = AppearanceTransitionViewModel()
            val host = AppearanceTransitionHost(compose.activity, state).also { it.attach() }
            var submissions = 0
            try {
                root.layout(0, 0, 0, 0)
                host.circular(Offset.Zero, true) { submissions++ }
                assertEquals(1, submissions)
                assertNull(state.snapshot)
                assertFalse(state.isThemeTransitioning)
            } finally {
                root.layout(bounds.left, bounds.top, bounds.right, bounds.bottom)
                host.detach(false); state.clear()
            }
        }
    }

    @Test fun 语言中断收尾已接受主题且捕获失败仍保留列表恢复请求() {
        org.junit.Assume.assumeTrue(android.animation.ValueAnimator.areAnimatorsEnabled())
        compose.runOnIdle {
            val originalLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
            val target = if (com.yangsong.lizhang.ui.component.currentAppLanguage() ==
                com.yangsong.lizhang.domain.model.AppLanguage.EN) com.yangsong.lizhang.domain.model.AppLanguage.ZH_CN
                else com.yangsong.lizhang.domain.model.AppLanguage.EN
            val state = AppearanceTransitionViewModel()
            val captures = Captures()
            val host = AppearanceTransitionHost(compose.activity, state, captures::request).also { it.attach() }
            var submissions = 0
            try {
                host.circular(Offset.Zero, true) { submissions++ }
                host.language(target)
                assertEquals("语言中断时只提交一次已接受的主题", 1, submissions)
                assertFalse("语言操作不占用主题锁", state.isThemeTransitioning)
                assertEquals(2, captures.pending.size)
                captures.complete(0, PixelCopy.SUCCESS)
                assertNull(state.snapshot)
                captures.complete(1, PixelCopy.ERROR_SOURCE_NO_DATA)
                assertEquals(1, state.languageSubmissions)
                assertEquals(target, state.languageRequest!!.target)
                assertNull(state.snapshot)
                assertFalse(state.isThemeTransitioning)
            } finally {
                host.detach(false); state.clear()
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(originalLocales)
            }
        }
    }

    @Test fun 快照安全超时释放等待绘制的锁且旧清理不影响新请求() {
        org.junit.Assume.assumeTrue(android.animation.ValueAnimator.areAnimatorsEnabled())
        lateinit var state: AppearanceTransitionViewModel
        lateinit var host: AppearanceTransitionHost
        var oldId = 0L
        compose.runOnIdle {
            state = AppearanceTransitionViewModel()
            host = AppearanceTransitionHost(compose.activity, state,
                captureWindowForTest = { _, completed -> completed(PixelCopy.SUCCESS) }).also { it.attach() }
            host.circular(Offset.Zero, true) {}
            oldId = state.requestId
            assertTrue(state.isThemeTransitioning)
            assertNotNull(state.snapshot)
        }
        try {
            compose.waitUntil(2500) { !state.isThemeTransitioning }
            compose.runOnIdle {
                assertNull(state.snapshot)
                assertEquals(0, state.circularHandoffs)
                host.colors(false) {}
                state.clear(oldId)
                assertTrue(state.isThemeTransitioning)
                val execution = requireNotNull(host.colorsStarted(state.requestId, false))
                host.colorsFinished(state.requestId, execution, false)
                assertFalse(state.isThemeTransitioning)
            }
        } finally { compose.runOnIdle { host.detach(false); state.clear() } }
    }

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
                if (newCaptures.pending.isNotEmpty()) {
                    assertTrue("旧宿主取消、解除及迟到捕获均不能释放新锁", state.isThemeTransitioning)
                    assertEquals(id, state.themeRequest!!.id)
                }
                if (newCaptures.pending.isNotEmpty()) newCaptures.complete(0, PixelCopy.ERROR_SOURCE_NO_DATA)
                assertEquals("旧清理与迟到回调不创建新请求", id, state.requestId)
                assertEquals("被更新请求替代的旧目标不提交", if (android.animation.ValueAnimator.areAnimatorsEnabled()) 0 else 1, oldSubmissions)
                assertEquals(1, newSubmissions)
                assertEquals(AppThemeMode.DARK, app.appContainer.themeRepository.themeMode.value)
                assertNull(state.pendingDark)
                assertNull(state.snapshot)
                assertFalse(state.isThemeTransitioning)
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
                assertFalse(state.isThemeTransitioning)
            } finally { host.detach(false); state.clear(); store.clear() }
        }
    }

    @Test fun 捕获超时提交有效目标且迟到成功不恢复覆盖层() {
        lateinit var state: AppearanceTransitionViewModel
        lateinit var host: AppearanceTransitionHost
        lateinit var viewModel: SettingsViewModel
        lateinit var store: androidx.lifecycle.ViewModelStore
        val captures = Captures()
        val submissions = java.util.concurrent.atomic.AtomicInteger()
        compose.runOnIdle {
            state = AppearanceTransitionViewModel()
            viewModel = settings()
            viewModel.setThemeMode(AppThemeMode.DARK)
            store = androidx.lifecycle.ViewModelStore().also { it.put("异常主题", viewModel) }
            host = AppearanceTransitionHost(compose.activity, state, captures::request).also { it.attach() }
            host.circular(Offset.Zero, false) { submissions.incrementAndGet(); viewModel.setThemeMode(AppThemeMode.LIGHT) }
        }
        try {
            compose.waitUntil(2000) { submissions.get() == 1 }
            compose.runOnIdle {
                assertEquals(1, submissions.get())
                assertEquals(AppThemeMode.LIGHT, app.appContainer.themeRepository.themeMode.value)
                assertNull(state.pendingDark)
                assertNull(state.snapshot)
                assertFalse(state.isThemeTransitioning)
                if (captures.pending.isNotEmpty()) {
                    host.circular(Offset.Zero, true) { submissions.incrementAndGet(); viewModel.setThemeMode(AppThemeMode.DARK) }
                    val nextId = state.requestId
                    captures.complete(0, PixelCopy.SUCCESS)
                    assertEquals(nextId, state.requestId)
                    assertEquals(nextId, state.themeRequest!!.id)
                    assertTrue(state.isThemeTransitioning)
                    assertNull(state.snapshot)
                    captures.complete(1, PixelCopy.ERROR_SOURCE_NO_DATA)
                    assertEquals(2, submissions.get())
                    assertEquals(AppThemeMode.DARK, app.appContainer.themeRepository.themeMode.value)
                    assertFalse(state.isThemeTransitioning)
                }
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
                assertFalse(state.isThemeTransitioning)
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
                assertFalse(state.isThemeTransitioning)
            } finally { old.detach(false); current.detach(false); state.clear(); store.clear() }
        }
    }
}
