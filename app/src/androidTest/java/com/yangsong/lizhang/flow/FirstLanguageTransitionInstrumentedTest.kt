package com.yangsong.lizhang.flow

import android.animation.ValueAnimator
import android.app.LocaleManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
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
import com.yangsong.lizhang.core.common.ThemeOperationDiagnostics
import com.yangsong.lizhang.domain.model.AppLanguage
import com.yangsong.lizhang.domain.model.AppThemeMode
import com.yangsong.lizhang.ui.component.currentAppLanguage
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 单独清空专项模拟器的应用数据后，以独立冷进程运行本类。
 * 不在启动前设置任何应用语言，保留用户从「跟随系统」第一次选择语言的完整路径。
 * 使用系统实际合成截图；没有 Compose 测试时钟，PNG 编码在交接完成后执行。
 */
class FirstLanguageTransitionInstrumentedTest {
    @get:org.junit.Rule(order = 0)
    val acceptedPrivacy = com.yangsong.lizhang.fixtures.AcceptedPrivacyRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private val app get() = ApplicationProvider.getApplicationContext<LiZhangApplication>()
    private val verifyCoverCommits get() = InstrumentationRegistry.getArguments()
        .getString("verifyLanguageCoverCommits", "false").also {
            require(it == "true" || it == "false") { "verifyLanguageCoverCommits只能为true或false" }
        }.toBoolean()
    private val expectedRecreation get() = booleanArgument("expectedLanguageRecreation", default = true)
    private val explicitRecreationCheck get() = booleanArgument("explicitRecreationCheck", default = false)
    private data class Frame(val startedMs: Long, val endedMs: Long, val bitmap: Bitmap,
        val content: Boolean, val black: Boolean)
    private data class Result(val title: String, val samples: Int, val blank: Int, val black: Int)

    @Test fun 默认系统语言首次选择与随后选择均无实际黑帧() {
        val args = InstrumentationRegistry.getArguments()
        val dark = args.getString("firstLocaleDark", "false").also {
            require(it == "true" || it == "false") { "firstLocaleDark只能为true或false" }
        }.toBoolean()
        val runId = args.getString("firstLanguageRunId")
            ?: "冷进程_${SystemClock.elapsedRealtime()}_${Process.myPid()}"
        require(runId.matches(Regex("[\\p{L}\\p{N}_.-]+"))) { "运行编号不能包含路径分隔符" }
        val folder = File(app.getExternalFilesDir(null), "first-language-evidence/$runId")
        check(!folder.exists() && folder.mkdirs()) { "证据目录已存在或无法创建，不覆盖历史运行" }
        val notes = mutableListOf<String>()
        try {
            instrumentation.runOnMainSync {
                assertTrue("必须单独冷进程运行，不能先通过其他用例启动过 Activity", !app.startupSession.hasClaimed)
                assertTrue("首次路径不能预先设置 AppCompat 应用语言", AppCompatDelegate.getApplicationLocales().isEmpty)
                if (Build.VERSION.SDK_INT >= 33) {
                    assertTrue("首次路径不能预先设置 Android 应用语言",
                        app.getSystemService(LocaleManager::class.java).applicationLocales.isEmpty)
                }
                app.appContainer.onboardingRepository.complete()
                app.appContainer.onboardingRepository.completeFeatureGuide()
                app.appContainer.themeRepository.setThemeMode(if (dark) AppThemeMode.DARK else AppThemeMode.LIGHT)
            }
            notes += "PID=${Process.myPid()} API=${Build.VERSION.SDK_INT}；深色=$dark；" +
                "检查覆盖帧提交=$verifyCoverCommits；语言预期重建=$expectedRecreation；" +
                "额外显式重建检查=$explicitRecreationCheck；启动前未设置应用语言；系统语言=${systemLocaleTags()}"
            app.startActivity(Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            awaitPage {
                val current = activity()
                current.hasWindowFocus() && current.appearanceHost.isNavigationReady && !current.startupState.visible
            }
            val initial = activity()
            assertEquals(AppLanguage.SYSTEM, currentAppLanguage())
            assertEquals(AppLanguage.SYSTEM, initial.appearanceState.languagePreference)
            assertEquals("首次语言选择之前只有冷启动宿主", 1L, initial.appearanceState.hostCount)
            assertEquals("首次语言选择之前未提交语言", 0, initial.appearanceState.languageSubmissions)
            assertEquals("首次语言选择之前未消费语言快照", 0, initial.appearanceState.languageHandoffs)
            assertEquals(systemLocaleTags(), initial.resources.configuration.locales.toLanguageTags())
            val settingsTab = device.wait(Until.findObject(By.res("功能引导目标SETTINGS")), 6000)
                ?: error("首页未显示实际设置导航入口")
            assertTrue("实际设置导航入口可交互", settingsTab.isClickable && settingsTab.isEnabled)
            settingsTab.click()
            awaitPage { activity().appearanceHost.isNavigationReady }
            // 中文系统和英文系统都从默认偏好开始；目标资源必须实际改变，不能走同语言快捷分支。
            val initialResource = AppLanguage.fromLanguageTag(initial.resources.configuration.locales[0].toLanguageTag())
            val first = if (initialResource == AppLanguage.EN) AppLanguage.ZH_CN else AppLanguage.EN
            val second = if (first == AppLanguage.EN) AppLanguage.ZH_CN else AppLanguage.EN
            val results = listOf(
                transition(first, "首次", folder, notes),
                transition(second, "随后", folder, notes),
            )
            // 两轮都收集完成再判断画面，失败对照也能保留「首次」和「随后」的独立证据。
            results.forEach { result ->
                assertTrue("${result.title}实际语言交接期间必须采到连续画面", result.samples > 1)
                assertEquals("${result.title}语言交接采样中没有实际黑屏", 0, result.black)
                assertEquals("${result.title}语言交接采样中持续存在正文内容", 0, result.blank)
            }
            if (explicitRecreationCheck) verifyExplicitRecreation(second, notes)
        } catch (failure: Throwable) {
            notes += failure.stackTraceToString()
            runCatching { device.dumpWindowHierarchy(File(folder, "失败现场.xml")) }
            throw failure
        } finally {
            File(folder, "结果与采样说明.txt").writeText(notes.joinToString("\n") +
                "\n系统截图不是逐刷新帧录制；下列时序和最大采样间隔明确本轮采集覆盖范围。\n")
            File(folder, "外观时间线.txt").writeText(ThemeOperationDiagnostics.lines().joinToString("\n"))
        }
    }

    private fun transition(target: AppLanguage, label: String, folder: File, notes: MutableList<String>): Result {
        val previous = activity()
        val state = previous.appearanceState
        val submissions = state.languageSubmissions
        val handoffs = state.languageHandoffs
        val hosts = state.hostCount
        // 旧 APK 对照没有此字段；通过可选反射读取，避免新测试链接旧包时丢失兼容性。
        val coverCommitsBefore = languageCoverCommits(state)
        if (verifyCoverCommits) assertNotNull("修复包必须提供真实覆盖帧提交计数", coverCommitsBefore)
        val rowTitle = previous.getString(R.string.language)
        val row = languageRow(rowTitle)
        val originalPosition = previous.appearanceHost.currentListPosition
        assertNotNull("交接前已经获得实际设置列表位置", originalPosition)
        row.click()
        val options = device.wait(Until.findObjects(By.clazz("android.widget.RadioButton")), 6000)
            ?: error("语言选择弹窗未显示")
        assertEquals("语言选择弹窗保持现有完整选项", AppLanguage.entries.size, options.size)
        val frames = Collections.synchronizedList(mutableListOf<Frame>())
        val running = AtomicBoolean(true)
        val firstFrame = CountDownLatch(1)
        val samplingFailure = AtomicReference<Throwable?>()
        val origin = SystemClock.uptimeMillis()
        notes += "${label}采样起点绝对uptime=$origin；目标=${target.localeTag}；预期重建=$expectedRecreation"
        val observer = Thread {
            try {
                var bytes = 0L
                while (running.get()) {
                    val start = SystemClock.uptimeMillis()
                    val bitmap = instrumentation.uiAutomation.takeScreenshot()
                        ?: error("系统截图返回空，无法评估此采样窗口")
                    val end = SystemClock.uptimeMillis()
                    val content = hasRenderedContent(bitmap)
                    val black = isMostlyBlack(bitmap)
                    val width = minOf(240, bitmap.width)
                    val scaled = Bitmap.createScaledBitmap(bitmap, width,
                        (bitmap.height.toLong() * width / bitmap.width).toInt().coerceAtLeast(1), true)
                    if (scaled !== bitmap) bitmap.recycle()
                    frames += Frame(start - origin, end - origin, scaled, content, black)
                    firstFrame.countDown()
                    bytes += scaled.allocationByteCount
                    check(bytes < 64L * 1024 * 1024) { "连续截图达到 64 MiB 上限，不以提前停采伪造通过" }
                    SystemClock.sleep(12)
                }
            } catch (failure: Throwable) {
                samplingFailure.set(failure)
                firstFrame.countDown()
            }
        }.apply { name = "首次语言系统画面采样"; start() }
        var clickedAt = 0L
        try {
            assertTrue("真实选择语言之前开始采集系统画面", firstFrame.await(3, TimeUnit.SECONDS))
            samplingFailure.get()?.let { throw it }
            clickedAt = SystemClock.uptimeMillis() - origin
            options[AppLanguage.entries.indexOf(target)].click()
            awaitPage {
                val current = activity()
                (if (expectedRecreation) current !== previous else current === previous) &&
                    currentAppLanguage() == target &&
                    current.resources.configuration.locales.toLanguageTags() == desiredLocaleTags(target) &&
                    current.appearanceState.languagePreference == target &&
                    current.hasWindowFocus() && current.appearanceHost.isNavigationReady &&
                    current.appearanceState.snapshot == null && current.appearanceState.languageRequest == null &&
                    current.appearanceState.pendingDark == null && !current.startupState.visible
            }
            val current = activity()
            text(current.getString(R.string.language))
            assertSame("语言切换保留原 Activity 的外观交接状态", state, current.appearanceState)
            if (expectedRecreation) {
                assertNotSame("旧包对照的语言选择确有实际 Activity 重建", previous, current)
                assertEquals("每次真实语言选择只重建一次 Activity", hosts + 1, state.hostCount)
            } else {
                assertSame("修复包的语言选择必须持续使用同一 Activity 窗口", previous, current)
                assertEquals("修复包的语言选择不得创建新的窗口宿主", hosts, state.hostCount)
            }
            assertEquals("每次真实语言选择只提交一次", submissions + 1, state.languageSubmissions)
            val coverCommitsAfter = languageCoverCommits(state)
            notes += "${label}覆盖真实帧提交计数=$coverCommitsBefore→$coverCommitsAfter；" +
                "语言提交计数=$submissions→${state.languageSubmissions}；宿主数=$hosts→${state.hostCount}"
            if (verifyCoverCommits) {
                assertEquals("每次语言提交之前必须完成一次真实覆盖帧提交，不能依靠超时降级通过",
                    requireNotNull(coverCommitsBefore) + 1, coverCommitsAfter)
            }
            if (ValueAnimator.areAnimatorsEnabled()) {
                assertEquals("每次真实语言快照只交接一次", handoffs + 1, state.languageHandoffs)
            }
            assertEquals("首个可见设置项与偏移恢复到原实际位置", originalPosition,
                current.appearanceHost.currentListPosition)
            // 保留交接后的真实端点帧，覆盖释放之后仍须正常合成页面内容。
            device.waitForIdle()
        } finally {
            running.set(false)
            observer.join(5000)
            assertFalse("采样线程须完整结束后才读取、编码和释放帧", observer.isAlive)
            val captured = synchronized(frames) { frames.toList() }
            val records = captured.mapIndexed { index, frame ->
                File(folder, "$label-${target.name}-${index.toString().padStart(3, '0')}.png").outputStream().use {
                    frame.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                frame.bitmap.recycle()
                "frame=$index startMs=${frame.startedMs} endMs=${frame.endedMs} content=${frame.content} black=${frame.black}"
            }
            File(folder, "$label-${target.name}-帧时序.txt").writeText("clickMs=$clickedAt\n" + records.joinToString("\n"))
            val maximumGap = captured.zipWithNext().maxOfOrNull { (before, after) -> after.startedMs - before.endedMs } ?: 0
            val maximumInterval = captured.zipWithNext().maxOfOrNull { (before, after) -> after.startedMs - before.startedMs } ?: 0
            notes += "$label→${target.localeTag}；采样=${captured.size}；黑帧=${captured.count { it.black }}；" +
                "空帧=${captured.count { !it.content }}；最大帧间空隙=${maximumGap}ms；最大开始间隔=${maximumInterval}ms；" +
                "点击=${clickedAt}ms；最后截图返回=${captured.lastOrNull()?.endedMs}ms；" +
                "采样异常=${samplingFailure.get()?.javaClass?.simpleName}"
        }
        samplingFailure.get()?.let { throw it }
        val captured = synchronized(frames) { frames.toList() }
        assertTrue("必须覆盖选择前的实际画面", captured.any { it.endedMs <= clickedAt })
        assertTrue("必须覆盖实际选择后的交接过程", captured.any { it.startedMs >= clickedAt })
        return Result(label, captured.size, captured.count { !it.content }, captured.count { it.black })
    }

    private fun text(value: String) = device.wait(Until.findObject(By.text(value)), 6000)
        ?: error("未显示实际目标语言正文：$value")

    private fun languageRow(title: String): androidx.test.uiautomator.UiObject2 {
        repeat(6) {
            val rows = device.wait(Until.findObjects(By.res("语言设置行")), 6000)
                ?: error("实际设置页未显示语言行")
            assertEquals("实际设置页中语言行必须唯一", 1, rows.size)
            val row = rows.single()
            val bounds = row.visibleBounds
            if (bounds.top >= device.displayHeight / 4 && bounds.bottom <= device.displayHeight * 3 / 4) {
                assertTrue("实际语言行可交互", row.isEnabled && bounds.width() > 0)
                text(title)
                device.waitForIdle()
                return device.findObject(By.res("语言设置行")) ?: error("语言行就绪后丢失")
            }
            val from = device.displayHeight / 2
            val to = if (bounds.top < device.displayHeight / 4) from + device.displayHeight / 6
                else from - device.displayHeight / 6
            device.swipe(device.displayWidth / 2, from, device.displayWidth / 2, to, 20)
            device.waitForIdle()
        }
        error("实际语言行未进入可交互区域")
    }

    private fun activity(): MainActivity {
        var value: MainActivity? = null
        instrumentation.runOnMainSync {
            value = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().firstOrNull()
        }
        return value ?: error("当前没有已恢复的 MainActivity")
    }

    private fun awaitPage(predicate: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 8000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < end) {
            ready = runCatching(predicate).getOrDefault(false)
            if (!ready) SystemClock.sleep(20)
        }
        assertTrue("实际 Activity、完整资源、导航、焦点和覆盖层完成交接", ready)
    }

    private fun systemLocaleTags(): String = if (Build.VERSION.SDK_INT >= 33) {
        app.getSystemService(LocaleManager::class.java).systemLocales.toLanguageTags()
    } else android.content.res.Resources.getSystem().configuration.locales.toLanguageTags()

    private fun desiredLocaleTags(target: AppLanguage): String =
        (listOf(target.localeTag) + systemLocaleTags().split(',')).distinct().joinToString(",")

    /** 独立于两轮语言采帧；显式重建仍必须恢复当前设置页及新 Activity 的视图树所有者。 */
    private fun verifyExplicitRecreation(target: AppLanguage, notes: MutableList<String>) {
        val previous = activity()
        val state = previous.appearanceState
        val hosts = state.hostCount
        val submissions = state.languageSubmissions
        val handoffs = state.languageHandoffs
        val covers = languageCoverCommits(state)
        val position = previous.appearanceHost.currentListPosition
        assertNotNull("显式重建之前取得实际设置列表位置", position)
        val started = SystemClock.uptimeMillis()
        instrumentation.runOnMainSync { previous.recreate() }
        awaitPage {
            val current = activity()
            current !== previous && currentAppLanguage() == target &&
                current.resources.configuration.locales.toLanguageTags() == desiredLocaleTags(target) &&
                current.appearanceState.languagePreference == target &&
                current.hasWindowFocus() && current.appearanceHost.isNavigationReady &&
                current.appearanceState.snapshot == null && current.appearanceState.languageRequest == null &&
                current.appearanceState.pendingDark == null && !current.startupState.visible
        }
        val current = activity()
        text(current.getString(R.string.language))
        device.waitForIdle()
        assertNotSame("显式重建必须创建实际新的 Activity", previous, current)
        assertSame("显式重建仍保留原外观 ViewModel", state, current.appearanceState)
        assertEquals("显式重建只创建一个新宿主", hosts + 1, state.hostCount)
        assertEquals("显式重建保持设置列表位置", position, current.appearanceHost.currentListPosition)
        assertEquals("显式重建不重新提交语言", submissions, state.languageSubmissions)
        assertEquals("显式重建不再次播放语言淡出", handoffs, state.languageHandoffs)
        assertEquals("显式重建不重复捕获语言覆盖", covers, languageCoverCommits(state))
        instrumentation.runOnMainSync {
            assertSame("新窗口返回键属于新 Activity", current,
                current.window.decorView.findViewTreeOnBackPressedDispatcherOwner())
            val content = current.findViewById<android.view.ViewGroup>(android.R.id.content)
            val composeViews = (0 until content.childCount).map(content::getChildAt)
                .filterIsInstance<androidx.compose.ui.platform.ComposeView>()
            assertEquals("显式重建后只保留一个实际 Compose 内容树", 1, composeViews.size)
            val composeView = composeViews.single()
            assertSame("新内容返回键属于新 Activity", current, composeView.findViewTreeOnBackPressedDispatcherOwner())
            assertSame("新内容生命周期属于新 Activity", current, composeView.findViewTreeLifecycleOwner())
            assertSame("新内容保存状态属于新 Activity", current, composeView.findViewTreeSavedStateRegistryOwner())
        }
        device.pressBack()
        awaitPage {
            activity() === current && current.hasWindowFocus() && current.appearanceHost.isNavigationReady &&
                device.hasObject(By.text(current.getString(R.string.home_recent)))
        }
        assertEquals("系统返回首页不重新提交语言", submissions, state.languageSubmissions)
        notes += "额外显式重建检查：开始uptime=$started，完成uptime=${SystemClock.uptimeMillis()}；" +
            "宿主=$hosts→${state.hostCount}；新Activity=true；设置页/列表/视图树所有者恢复；系统返回真实首页成功；" +
            "此阶段不计入首次或随后语言切换的黑帧统计"
    }

    private fun booleanArgument(name: String, default: Boolean): Boolean =
        InstrumentationRegistry.getArguments().getString(name, default.toString()).also {
            require(it == "true" || it == "false") { "$name 只能为 true 或 false" }
        }.toBoolean()

    private fun languageCoverCommits(state: Any): Int? = runCatching {
        state.javaClass.getDeclaredField("languageCoverCommits").apply { isAccessible = true }.getInt(state)
    }.getOrNull()

    /** 排除系统栏与底栏；深色页面的文字不应被当成整块纯黑，正文检测另行判断。 */
    private fun isMostlyBlack(bitmap: Bitmap): Boolean {
        var black = 0
        var total = 0
        val step = (bitmap.width / 100).coerceAtLeast(2)
        for (y in bitmap.height * 15 / 100 until bitmap.height * 78 / 100 step step) {
            for (x in bitmap.width * 6 / 100 until bitmap.width * 94 / 100 step step) {
                val pixel = bitmap.getPixel(x, y)
                if (maxOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) <= 12) black++
                total++
            }
        }
        return total > 0 && black.toDouble() / total >= 0.98
    }
}
