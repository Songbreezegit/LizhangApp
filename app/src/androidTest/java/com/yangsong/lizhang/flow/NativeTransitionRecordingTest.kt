package com.yangsong.lizhang.flow

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.yangsong.lizhang.*
import com.yangsong.lizhang.domain.model.AppLanguage
import com.yangsong.lizhang.domain.model.AppThemeMode
import com.yangsong.lizhang.ui.component.currentAppLanguage
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test
import org.junit.Assert.*
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner

/** 无 Compose 测试时钟，使用正常应用渲染流程录制空白设备。 */
class NativeTransitionRecordingTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private val app get() = ApplicationProvider.getApplicationContext<LiZhangApplication>()
    private fun text(value: String) = device.wait(Until.findObject(By.text(value)), 6000) ?: run {
        frame("文字定位失败")
        device.dumpWindowHierarchy(File(app.getExternalFilesDir(null), "transition-evidence/文字定位失败.xml"))
        error("未显示：$value；测试提醒数量=${app.appContainer.reminderRepository.reminders.value.size}")
    }
    private fun chooseLanguage(value: String) {
        val choices = listOf("跟随系统", "简体中文", "繁體中文", "English", "日本語", "한국어", "Español", "Français")
        // 同语言文字也在下方设置行中；必须等弹窗选项出现后点击对应单选项。
        device.wait(Until.findObjects(By.clazz("android.widget.RadioButton")), 6000) ?: error("语言弹窗未显示")
        device.waitForIdle()
        val options = device.findObjects(By.clazz("android.widget.RadioButton"))
        assertEquals("语言弹窗具有完整选项", choices.size, options.size)
        val choiceIndex = choices.indexOf(value)
        assertTrue("目标语言属于现有选项：$value", choiceIndex >= 0)
        options[choiceIndex].click()
    }
    private fun frame(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("没有实际绘制帧")
        val folder = File(app.getExternalFilesDir(null), "transition-evidence").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun awaitThemeFrame(dark: Boolean, dimmed: Boolean = false) {
        awaitPage {
            val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return@awaitPage false
            // 卡片外的实际渐变像素，避免仅凭偏好提交或空闲事件保存错误端点。
            val color = bitmap.getPixel(bitmap.width / 100, bitmap.height / 3)
            bitmap.recycle()
            val light = android.graphics.Color.red(color) + android.graphics.Color.green(color) + android.graphics.Color.blue(color)
            if (dark) light < if (dimmed) 90 else 190 else light > if (dimmed) 220 else 500
        }
    }
    private fun activity(): MainActivity {
        var value: MainActivity? = null
        instrumentation.runOnMainSync { value = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull() }
        return value ?: error("应用没有已恢复 Activity")
    }
    private fun awaitPage(predicate: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 6000
        fun ready() = runCatching(predicate).getOrDefault(false)
        while (!ready() && SystemClock.uptimeMillis() < end) SystemClock.sleep(30)
        assertTrue("目标页面和覆盖层完成交接", ready())
    }
    private fun launchFreshActivity(intent: Intent = Intent(app, MainActivity::class.java)) {
        val previous = runCatching { activity() }.getOrNull()
        app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        // startActivity 异步提交；旧 Activity 的焦点不能作为新任务启动完成的依据。
        awaitPage {
            val current = activity()
            current !== previous && current.hasWindowFocus() && current.appearanceHost.isNavigationReady &&
                current.appearanceState.snapshot == null && current.appearanceState.pendingDark == null &&
                !current.startupState.visible
        }
    }
    private fun awaitChinese(previous: MainActivity, needsRecreation: Boolean) {
        awaitPage {
            val current = activity()
            (!needsRecreation || current !== previous) &&
                AppLanguage.fromLanguageTag(current.resources.configuration.locales[0].toLanguageTag()) == AppLanguage.ZH_CN &&
                currentAppLanguage() == AppLanguage.ZH_CN && current.appearanceHost.isNavigationReady &&
                current.hasWindowFocus() && current.appearanceState.snapshot == null
        }
    }
    private fun awaitThemeMode(mode: AppThemeMode, dark: Boolean) {
        awaitPage {
            val current = activity()
            app.appContainer.themeRepository.themeMode.value == mode && current.hasWindowFocus() &&
                current.appearanceHost.isNavigationReady && current.appearanceState.snapshot == null &&
                current.appearanceState.pendingDark == null
        }
        awaitThemeFrame(dark)
        device.waitForIdle()
    }
    private fun themeSwitch() = device.wait(Until.findObject(By.pkg(app.packageName).checkable(true)), 6000)
        ?: error("应用内深色模式开关未显示")
    @Test fun 正常渲染下圆形弹窗语言和提醒页面走查() {
        launchFreshActivity()
        val chinesePrevious = activity()
        val needsChineseRecreation = currentAppLanguage() != AppLanguage.ZH_CN
        instrumentation.runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN"))
            app.appContainer.themeRepository.setThemeMode(AppThemeMode.LIGHT)
        }
        awaitChinese(chinesePrevious, needsChineseRecreation)
        awaitThemeMode(AppThemeMode.LIGHT, dark = false)
        val expectedMotion = InstrumentationRegistry.getArguments().getString("motion", "enabled") == "enabled"
        assertEquals("本次检查使用指定的实际动画比例", expectedMotion, android.animation.ValueAnimator.areAnimatorsEnabled())
        text("我的").click()
        text("主题设置")
        awaitPage { activity().appearanceHost.isNavigationReady }
        text("深色模式")
        if (themeSwitch().visibleBounds.bottom > device.displayHeight * 3 / 4) {
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                device.displayWidth / 2, device.displayHeight / 2, 20)
        }
        device.waitForIdle()
        awaitThemeMode(AppThemeMode.LIGHT, dark = false)
        awaitPage { !themeSwitch().isChecked }
        frame("正常渲染_浅色设置")
        themeSwitch().click()
        awaitThemeMode(AppThemeMode.DARK, dark = true)
        awaitPage { themeSwitch().isChecked }
        frame("正常渲染_深色设置")
        themeSwitch().click()
        awaitThemeMode(AppThemeMode.LIGHT, dark = false)
        awaitPage { !themeSwitch().isChecked }
        text("主题设置").click()
        val options = device.wait(Until.findObjects(By.clazz("android.widget.RadioButton")), 6000)!!
        assertEquals(3, options.size)
        options[2].click()
        awaitPage { app.appContainer.themeRepository.themeMode.value == AppThemeMode.DARK }
        awaitThemeFrame(dark = true, dimmed = true)
        device.waitForIdle()
        frame("正常渲染_主题弹窗深色")
        device.findObjects(By.clazz("android.widget.RadioButton"))[1].click()
        awaitPage { app.appContainer.themeRepository.themeMode.value == AppThemeMode.LIGHT }
        awaitThemeFrame(dark = false, dimmed = true)
        device.waitForIdle()
        frame("正常渲染_主题弹窗浅色")
        text("完成").click()
        device.waitForIdle()
        // 同时核对列表项、偏移与实际画面，文字换行时不单用标签坐标判断。
        device.swipe(device.displayWidth / 2, text("语言").visibleBounds.centerY(),
            device.displayWidth / 2, device.displayHeight / 7, 35)
        device.waitForIdle()
        frame("正常渲染_语言切换前位置")
        var title = "语言"
        for ((choice, target) in listOf("English" to "Language", "日本語" to "言語", "한국어" to "언어", "简体中文" to "语言",
            "跟随系统" to "Language", "简体中文" to "语言")) {
            val previous = activity()
            val state = previous.appearanceState
            val submissions = state.languageSubmissions
            val handoffs = state.languageHandoffs
            val hosts = state.hostCount
            val top = text(title).visibleBounds.top
            val position = previous.appearanceHost.currentListPosition
            assertNotNull("取得原有列表状态", position)
            text(title).click()
            // 从点击之前持续读取系统实际合成画面，不能只检查重建完成后的终点。
            val sampling = AtomicBoolean(true)
            val samples = AtomicInteger()
            val blankFrames = AtomicInteger()
            val observer = Thread {
                while (sampling.get()) {
                    val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: continue
                    if (!hasRenderedContent(bitmap)) {
                        val folder = File(app.getExternalFilesDir(null), "transition-evidence").apply { mkdirs() }
                        File(folder, "语言空屏_${target}_${blankFrames.incrementAndGet()}.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }
                    val index = samples.incrementAndGet()
                    if (index % 8 == 1) {
                        val folder = File(app.getExternalFilesDir(null), "transition-evidence").apply { mkdirs() }
                        File(folder, "语言连续帧_${target}_$index.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }
                    bitmap.recycle()
                    SystemClock.sleep(30)
                }
            }.apply { start() }
            try {
                chooseLanguage(choice)
                text(target)
                awaitPage { activity() !== previous && activity().appearanceState.snapshot == null }
                device.waitForIdle()
            } finally {
                sampling.set(false)
                observer.join(3000)
            }
            assertTrue("实际重建期间采集了连续画面", samples.get() > 1)
            assertEquals("语言重建全过程没有实际空屏：$target", 0, blankFrames.get())
            println("语言连续画面检查：$target，采样 ${samples.get()} 帧，空屏 ${blankFrames.get()} 帧")
            assertSame(state, activity().appearanceState)
            // 旧 Activity 的重复清理不得解除新宿主的监听或清空共享交接状态。
            instrumentation.runOnMainSync { previous.appearanceHost.detach(preserveLanguage = false) }
            assertEquals("每次语言选择只实际重建一次", hosts + 1, state.hostCount)
            assertEquals(submissions + 1, state.languageSubmissions)
            if (android.animation.ValueAnimator.areAnimatorsEnabled()) assertEquals(handoffs + 1, state.languageHandoffs)
            frame("正常渲染_语言_$target")
            assertEquals("语言重建保持首个可见设置项及偏移：原坐标 $top，现坐标 ${text(target).visibleBounds.top}，记录 ${state.lastLanguagePosition}，恢复 ${state.lastRestoredPosition}，目标 ${state.lastLanguageTarget}",
                position, activity().appearanceHost.currentListPosition)
            title = target
        }
        val sameLanguageState = activity().appearanceState
        val sameSubmissions = sameLanguageState.languageSubmissions
        val sameHosts = sameLanguageState.hostCount
        text("语言").click()
        chooseLanguage("简体中文")
        device.waitForIdle()
        text("取消").click()
        device.waitForIdle()
        assertEquals("同语言不提交设置", sameSubmissions, sameLanguageState.languageSubmissions)
        assertEquals("同语言不重建", sameHosts, sameLanguageState.hostCount)
        // 连续六次语言重建后，再次验证新宿主仍能双向完成真实圆形展开。
        device.swipe(device.displayWidth / 2, device.displayHeight / 3,
            device.displayWidth / 2, device.displayHeight * 3 / 4, 25)
        text("深色模式")
        device.waitForIdle()
        awaitPage { activity().hasWindowFocus() && activity().appearanceHost.isNavigationReady }
        awaitThemeFrame(dark = false)
        val circularBefore = activity().appearanceState.circularHandoffs
        for (dark in listOf(true, false)) {
            var toggle = device.wait(Until.findObject(By.pkg(app.packageName).checkable(true)), 6000)!!
            if (toggle.visibleBounds.bottom > device.displayHeight * 3 / 4) {
                device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 2, 20)
                device.waitForIdle()
                toggle = device.findObject(By.pkg(app.packageName).checkable(true))!!
            }
            assertEquals("点击前实际开关状态与目标相反", !dark, toggle.isChecked)
            frame("连续重建后开关点击前_$dark")
            toggle.click()
            try {
                awaitPage { app.appContainer.themeRepository.themeMode.value ==
                    if (dark) AppThemeMode.DARK else AppThemeMode.LIGHT }
            } catch (failure: AssertionError) {
                frame("连续重建后开关点击失败_$dark")
                val folder = File(app.getExternalFilesDir(null), "transition-evidence")
                device.dumpWindowHierarchy(File(folder, "连续重建后开关点击失败.xml"))
                println("点击失败：目标深色 $dark，偏好 ${app.appContainer.themeRepository.themeMode.value}，控件 ${toggle.visibleBounds}")
                throw failure
            }
            awaitPage { activity().appearanceState.snapshot == null }
            awaitThemeFrame(dark)
            frame("连续重建后圆形_${if (dark) "深色" else "浅色"}")
        }
        assertEquals(circularBefore + if (expectedMotion) 2 else 0, activity().appearanceState.circularHandoffs)
        text("语言")
        device.waitForIdle()
        text("首页").click()
        device.wait(Until.findObject(By.desc("提醒")), 6000) ?: error("首页提醒入口未显示")
        awaitPage { activity().appearanceHost.isNavigationReady }
        device.findObject(By.desc("提醒")).click()
        text("提醒")
        awaitPage { activity().appearanceHost.isNavigationReady }
        device.waitForIdle()
        // 页面滑入期间先等待导航完成，再按可见内容滚动，避免点中移动中的旧坐标。
        repeat(5) {
            if (!device.hasObject(By.text("还没有独立提醒"))) {
                device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 2, 20)
                device.waitForIdle()
            }
        }
        text("还没有独立提醒")
        frame("正常渲染_提醒空状态")
        val previous = activity()
        val submissions = previous.appearanceState.languageSubmissions
        instrumentation.runOnMainSync { previous.recreate() }
        awaitPage { activity() !== previous && activity().appearanceHost.isNavigationReady }
        assertSame("重建窗口的返回键属于新 Activity", activity(),
            activity().window.decorView.findViewTreeOnBackPressedDispatcherOwner())
        val content = activity().findViewById<android.view.ViewGroup>(android.R.id.content)
        assertSame("新内容的返回键属于新 Activity", activity(), content.getChildAt(0).findViewTreeOnBackPressedDispatcherOwner())
        assertSame("新内容的生命周期属于新 Activity", activity(), content.getChildAt(0).findViewTreeLifecycleOwner())
        assertSame("新内容的保存状态属于新 Activity", activity(), content.getChildAt(0).findViewTreeSavedStateRegistryOwner())
        text("我的提醒")
        device.waitForIdle()
        assertEquals(submissions, activity().appearanceState.languageSubmissions)
        device.pressBack()
        // 首页自身滚动位置也会恢复，不能要求顶部提醒按钮仍在可见视口内。
        if (device.wait(Until.findObject(By.text("最近往来")), 6000) == null) {
            frame("返回失败诊断")
            val folder = File(app.getExternalFilesDir(null), "transition-evidence").apply { mkdirs() }
            device.dumpWindowHierarchy(File(folder, "返回失败诊断.xml"))
            error("返回首页失败")
        }
        // 新通知冷启动只消费一次，配置重建后返回不重新打开提醒列表。
        launchFreshActivity(com.yangsong.lizhang.core.common.ReminderNavigationContract.createOpenRemindersIntent(app))
        text("我的提醒")
        val notified = activity()
        instrumentation.runOnMainSync { notified.recreate() }
        awaitPage { activity() !== notified && activity().appearanceHost.isNavigationReady }
        text("我的提醒")
        device.waitForIdle()
        device.pressBack()
        device.wait(Until.findObject(By.text("最近往来")), 6000) ?: error("通知入口返回首页失败")
        val home = activity()
        instrumentation.runOnMainSync { home.recreate() }
        awaitPage { activity() !== home && activity().appearanceHost.isNavigationReady }
        device.wait(Until.findObject(By.text("最近往来")), 6000) ?: error("通知入口被重建重复消费")
    }

    @Test fun 空屏检测区分纯色渐变和中性文字内容() {
        val bitmap = Bitmap.createBitmap(240, 400, Bitmap.Config.ARGB_8888)
        try {
            for (color in listOf(android.graphics.Color.BLACK, android.graphics.Color.WHITE, 0xff252629.toInt())) {
                bitmap.eraseColor(color)
                assertFalse("纯色画面不能当作页面内容", hasRenderedContent(bitmap))
            }
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint().apply {
                shader = android.graphics.LinearGradient(0f, 0f, 0f, 400f, 0xfff1f4f5.toInt(), 0xfff7f4ef.toInt(), android.graphics.Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, 240f, 400f, paint)
            assertFalse("平滑背景渐变仍是空画面", hasRenderedContent(bitmap))
            paint.shader = null
            paint.isAntiAlias = true
            paint.color = 0xff292e35.toInt()
            paint.textSize = 24f
            canvas.drawText("Language", 20f, 180f, paint)
            assertTrue("中性背景的实际文字必须被识别", hasRenderedContent(bitmap))
        } finally { bitmap.recycle() }
    }

    @Test fun 跟随系统的深浅色开关按实际外观反向切换() {
        val uiModeManager = app.getSystemService(android.app.UiModeManager::class.java)
        val mode = uiModeManager.nightMode
        val originalNight = app.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        try {
            launchFreshActivity()
            val chinesePrevious = activity()
            val needsChineseRecreation = currentAppLanguage() != AppLanguage.ZH_CN
            instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN")) }
            awaitChinese(chinesePrevious, needsChineseRecreation)
            for ((night, target) in listOf("yes" to AppThemeMode.LIGHT, "no" to AppThemeMode.DARK)) {
                instrumentation.runOnMainSync { app.appContainer.themeRepository.setThemeMode(AppThemeMode.SYSTEM) }
                val previous = activity()
                val targetNight = if (night == "yes") android.content.res.Configuration.UI_MODE_NIGHT_YES
                    else android.content.res.Configuration.UI_MODE_NIGHT_NO
                val needsRecreation = previous.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK != targetNight
                device.executeShellCommand("cmd uimode night $night")
                // 资源配置会先于新 Activity 的输入窗口更新，不能用旧实例就绪状态提前开始点击。
                awaitPage {
                    val current = activity()
                    (!needsRecreation || current !== previous) &&
                        current.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == targetNight &&
                        current.hasWindowFocus() && current.appearanceHost.isNavigationReady && !current.startupState.visible
                }
                text("我的").click()
                text("主题设置")
                awaitPage { activity().appearanceHost.isNavigationReady }
                text("深色模式")
                val visibleToggle = themeSwitch()
                // 配置重建会恢复设置列表，不能再次盲目拖动已经可见的开关。
                if (visibleToggle.visibleBounds.bottom > device.displayHeight * 3 / 4) {
                    device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 2, 20)
                }
                device.waitForIdle()
                awaitThemeMode(AppThemeMode.SYSTEM, dark = night == "yes")
                val toggle = themeSwitch()
                assertEquals("SYSTEM 开关显示实际外观", night == "yes", toggle.isChecked)
                val beforeBounds = toggle.visibleBounds
                frame("系统${night}_点击前")
                toggle.click()
                try {
                    awaitThemeMode(target, dark = target == AppThemeMode.DARK)
                } catch (failure: AssertionError) {
                    frame("系统${night}_开关失败")
                    val current = activity()
                    println("开关失败：系统=$night，目标=$target，偏好=${app.appContainer.themeRepository.themeMode.value}，" +
                        "点击前范围=$beforeBounds，当前范围=${toggle.visibleBounds}，当前选中=${themeSwitch().isChecked}，焦点=${current.hasWindowFocus()}，" +
                        "导航就绪=${current.appearanceHost.isNavigationReady}，快照=${current.appearanceState.snapshot != null}，" +
                        "待切换=${current.appearanceState.pendingDark}，开屏=${current.startupState.visible}")
                    device.dumpWindowHierarchy(File(app.getExternalFilesDir(null), "transition-evidence/系统${night}_开关失败.xml"))
                    throw failure
                }
                assertEquals("点击后切换为实际外观的相反模式", night != "yes", themeSwitch().isChecked)
                frame("系统${night}_开关反向切换")
            }
        } finally {
            device.executeShellCommand("cmd uimode night ${if (mode == android.app.UiModeManager.MODE_NIGHT_YES) "yes" else if (mode == android.app.UiModeManager.MODE_NIGHT_NO) "no" else "auto"}")
            // 恢复系统配置也可能重建 Activity；待恢复窗口就绪后再初始化下一条测试的浅色偏好。
            awaitPage {
                val current = activity()
                uiModeManager.nightMode == mode &&
                    app.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == originalNight &&
                    current.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == originalNight &&
                    current.hasWindowFocus() && current.appearanceHost.isNavigationReady &&
                    current.appearanceState.snapshot == null && current.appearanceState.pendingDark == null
            }
            instrumentation.runOnMainSync { app.appContainer.themeRepository.setThemeMode(AppThemeMode.LIGHT) }
            awaitThemeMode(AppThemeMode.LIGHT, dark = false)
        }
    }
}

/** 检查正文区域的真实高对比笔画；排除系统栏和底部导航，平滑渐变不算内容。 */
private fun hasRenderedContent(bitmap: Bitmap): Boolean {
    val step = (bitmap.width / 240).coerceAtLeast(2)
    fun contrast(a: Int, b: Int) = maxOf(
        kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)),
        kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)),
        kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)),
    )
    var edges = 0
    for (y in bitmap.height * 15 / 100 until bitmap.height * 78 / 100 step step) {
        for (x in bitmap.width * 6 / 100 until bitmap.width * 94 / 100 step step) {
            val pixel = bitmap.getPixel(x, y)
            if (maxOf(contrast(pixel, bitmap.getPixel(x + step, y)), contrast(pixel, bitmap.getPixel(x, y + step))) >= 24) {
                if (++edges >= 40) return true
            }
        }
    }
    return false
}
