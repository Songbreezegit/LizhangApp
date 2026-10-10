package com.yangsong.lizhang.flow

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Process
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.MainActivity
import com.yangsong.lizhang.R
import com.yangsong.lizhang.core.common.ReminderNavigationContract
import com.yangsong.lizhang.ui.viewmodel.StartupAnimationViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** 每次以独立 instrumentation 进程执行；startupScenario 可为 launcher/notification/interrupt/background/back。 */
class StartupAnimationInstrumentedTest {
    @get:org.junit.Rule(order = 0) val acceptedPrivacy = com.yangsong.lizhang.fixtures.AcceptedPrivacyRule()
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = ApplicationProvider.getApplicationContext<LiZhangApplication>()
    private val evidenceFolder by lazy {
        val args = InstrumentationRegistry.getArguments()
        val runId = args.getString("repairRunId") ?: args.getString("v096RunId")
            ?: "生命周期_${SystemClock.elapsedRealtime()}_${Process.myPid()}"
        require(runId.matches(Regex("[\\p{L}\\p{N}_.-]+"))) { "运行编号不能包含路径分隔符" }
        File(app.getExternalFilesDir(null), "startup-v096/$runId").also {
            check(!it.exists()) { "不覆盖已有启动生命周期证据：$runId" }
            check(it.mkdirs())
        }
    }

    private fun frame(name: String): Bitmap {
        val bitmap = compose.onNodeWithTag("品牌开屏").captureToImage().asAndroidBitmap()
        File(evidenceFolder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private fun assertReminderPage(scenario: ActivityScenario<MainActivity>) {
        val device = UiDevice.getInstance(instrumentation)
        var title = ""
        scenario.onActivity { title = it.getString(R.string.nav_notifications) }
        compose.waitForIdle()
        assertNotNull("通知入口仍打开提醒页", device.wait(Until.findObject(By.text(title)), 6000))
    }

    @Test fun 冷启动真实帧和配置重建及通知入口验收() {
        val entry = InstrumentationRegistry.getArguments().getString("startupScenario", "launcher")
        var coldOpportunity = false
        instrumentation.runOnMainSync { coldOpportunity = !app.startupSession.hasClaimed }
        File(evidenceFolder, "运行前置.txt").writeText("场景=$entry\nPID=${Process.myPid()}\n进程启动机会尚未消费=$coldOpportunity\n")
        assertTrue("启动场景需要独立冷进程，不能复用已消费机会", coldOpportunity)
        if (entry == "notification" || entry == "interrupt") app.appContainer.onboardingRepository.complete()
        if (entry == "notification") {
            // 通知 action 会被业务消费并清除，ActivityScenario 的启动 Intent 匹配不适用于这个入口。
            instrumentation.runOnMainSync {
                app.startActivity(ReminderNavigationContract.createOpenRemindersIntent(app)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
            var activity: MainActivity? = null
            compose.waitUntil(8000) {
                instrumentation.runOnMainSync {
                    activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<MainActivity>().firstOrNull()
                }
                activity?.appearanceHost?.isNavigationReady == true
            }
            val resumed = requireNotNull(activity)
            try {
                assertFalse("通知冷启动不覆盖实际提醒页", resumed.startupState.visible)
                compose.onNodeWithTag("品牌开屏").assertDoesNotExist()
                assertNotNull("通知入口仍打开提醒页", UiDevice.getInstance(instrumentation)
                    .wait(Until.findObject(By.text(resumed.getString(R.string.nav_notifications))), 6000))
            } finally {
                instrumentation.runOnMainSync { resumed.finish() }
            }
            return
        }
        compose.mainClock.autoAdvance = false
        val intent = Intent(app, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            var state: StartupAnimationViewModel? = null
            scenario.onActivity { state = it.startupState }
            val startup = requireNotNull(state)
            if (!ValueAnimator.areAnimatorsEnabled()) {
                assertFalse("通知入口或关闭动画时不覆盖实际页面", startup.visible)
                compose.mainClock.autoAdvance = true
                compose.onNodeWithTag("品牌开屏").assertDoesNotExist()
                return@use
            }
            assertTrue("独立冷启动进程应显示品牌动画", startup.visible)
            compose.waitUntil(6000) {
                var focused = false
                scenario.onActivity { focused = it.hasWindowFocus() }
                startup.ready && focused
            }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("品牌开屏").assertIsDisplayed()
            compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
            val first = frame("${entry}_纯白起始")
            var density = 1f
            scenario.onActivity { density = it.resources.displayMetrics.density }
            val viewport = Rect(0, 0, first.width, first.height)
            assertTrue("应用内时间轨从纯白开始，允许小猫尚在屏幕顶部之外",
                StartupScenePixels.background(first, viewport, density).coloredFraction < .005f)
            compose.mainClock.advanceTimeBy(220)
            val falling = frame("${entry}_下落220ms")
            assertTrue("落地前小猫实际进入画面", StartupScenePixels.catPixels(falling, viewport, density) >= 12)
            assertTrue("260ms落地前背景仍是纯白",
                StartupScenePixels.background(falling, viewport, density).coloredFraction < .005f)
            compose.mainClock.advanceTimeBy(100)
            val landed = frame("${entry}_落地扩散320ms")
            val landingBackground = StartupScenePixels.background(landed, viewport, density)
            assertTrue("小猫落地后位于实际画面中心",
                StartupScenePixels.catPixels(landed, viewport, density, falling = false) >= 12)
            assertTrue("落地后渐变已经从小猫周围向外揭示", landingBackground.coloredFraction > .02f)
            assertTrue("落地阶段实际画面含白底间隔两侧的细涟漪",
                StartupScenePixels.rippleRays(landed, viewport, density) > 0)
            compose.mainClock.advanceTimeBy(180)
            val middle = frame("${entry}_渐变展开500ms")
            val middleBackground = StartupScenePixels.background(middle, viewport, density)
            assertTrue("渐变揭示继续向外扩大",
                middleBackground.coloredFraction > landingBackground.coloredFraction + .02f &&
                    middleBackground.maximumColoredRadius > landingBackground.maximumColoredRadius + 12f * density)
            var changed = 0
            for (y in 0 until first.height step 3) for (x in 0 until first.width step 3) {
                if (first.getPixel(x, y) != middle.getPixel(x, y)) changed++
            }
            assertTrue("实际小猫下落和渐变扩散画面发生变化", changed > 100)
            assertTrue("中途仍在有限动画时间轴内", startup.progress > 0f && startup.progress < 1f)
            File(evidenceFolder, "阶段采样.txt").writeText(
                "本组使用Compose测试时钟，不替代系统真实帧验收\n" +
                    "设计时长=900ms；落地=260ms；圆渐变揭示=260..620ms；退场=620..900ms\n" +
                    "320ms背景覆盖=${landingBackground.coloredFraction}；500ms背景覆盖=${middleBackground.coloredFraction}\n" +
                    "320ms实际扩散半径=${landingBackground.maximumColoredRadius / density}dp；" +
                    "500ms实际扩散半径=${middleBackground.maximumColoredRadius / density}dp\n",
            )

            when (entry) {
                "back" -> {
                    UiDevice.getInstance(instrumentation).pressBack()
                    var released = false
                    compose.waitUntil(3000) {
                        scenario.onActivity { released = !it.startupState.visible }
                        released
                    }
                    compose.mainClock.autoAdvance = true
                    compose.onNodeWithTag("品牌开屏").assertDoesNotExist()
                    compose.onNodeWithTag("首页列表").assertIsDisplayed()
                    assertTrue("隐私已确认后沿用当前首页功能引导", app.appContainer.onboardingRepository.state.value.completed)
                    scenario.moveToState(Lifecycle.State.CREATED)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    scenario.onActivity { assertFalse("返回键中断后热返回不补播", it.startupState.visible) }
                }
                "interrupt" -> {
                    scenario.onActivity { it.startActivity(ReminderNavigationContract.createOpenRemindersIntent(it)) }
                    compose.waitUntil(3000) { !startup.visible }
                    compose.mainClock.autoAdvance = true
                    assertReminderPage(scenario)
                    scenario.recreate()
                    assertReminderPage(scenario)
                    UiDevice.getInstance(instrumentation).pressBack()
                    compose.waitUntil(6000) {
                        runCatching { compose.onNodeWithTag("首页列表").assertIsDisplayed(); true }.getOrDefault(false)
                    }
                }
                "background" -> {
                    scenario.moveToState(Lifecycle.State.CREATED)
                    assertFalse("开屏中退后台立即结束", startup.visible)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    compose.mainClock.autoAdvance = true
                    compose.onNodeWithTag("品牌开屏").assertDoesNotExist()
                }
                else -> {
                    val beforeRotation = startup.progress
                    scenario.recreate()
                    scenario.onActivity { assertSame("重建保留同一启动状态", startup, it.startupState) }
                    assertTrue("重建不将动画回拨到开始", startup.progress >= beforeRotation)
                    compose.mainClock.advanceTimeBy(1000)
                    compose.mainClock.autoAdvance = true
                    compose.waitUntil(3000) { !startup.visible }
                    compose.onNodeWithTag("品牌开屏").assertDoesNotExist()
                    compose.onNodeWithTag("首页列表").assertIsDisplayed()
                    assertTrue("隐私已确认后沿用当前首页功能引导", app.appContainer.onboardingRepository.state.value.completed)
                    scenario.moveToState(Lifecycle.State.CREATED)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    assertFalse("热返回不重复开屏", startup.visible)
                    val original = AppCompatDelegate.getApplicationLocales()
                    var originalResourceLanguage = ""
                    scenario.onActivity { originalResourceLanguage = it.resources.configuration.locales[0].language }
                    try {
                        var previous: MainActivity? = null
                        var target = "en"
                        scenario.onActivity {
                            previous = it
                            target = if (it.resources.configuration.locales[0].language == "en") "zh-CN" else "en"
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(target))
                        }
                        compose.waitUntil(8000) {
                            var ready = false
                            runCatching { scenario.onActivity {
                                ready = it !== previous && it.resources.configuration.locales[0].language == target.substringBefore('-') &&
                                    it.appearanceHost.isNavigationReady && !it.startupState.visible
                            } }
                            ready
                        }
                        compose.onNodeWithTag("品牌开屏").assertDoesNotExist()
                    } finally {
                        scenario.onActivity { AppCompatDelegate.setApplicationLocales(original) }
                        compose.waitUntil(8000) {
                            var ready = false
                            runCatching { scenario.onActivity {
                                ready = it.resources.configuration.locales[0].language == originalResourceLanguage &&
                                    it.appearanceHost.isNavigationReady && !it.startupState.visible
                            } }
                            ready
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = true
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertFalse("同进程新 Activity 不补播开屏", it.startupState.visible) }
            compose.onNodeWithTag("品牌开屏").assertDoesNotExist()
        }
    }
}
