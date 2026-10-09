package com.yangsong.lizhang.onboarding

import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.R
import com.yangsong.lizhang.data.preferences.SharedPreferencesOnboardingRepository
import com.yangsong.lizhang.domain.onboarding.*
import com.yangsong.lizhang.ui.navigation.LiZhangNavGraph
import com.yangsong.lizhang.ui.onboarding.*
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.OnboardingViewModel
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 真实导航和控件使用已同意业务前置；功能引导状态使用隔离偏好，不读取设备通讯录。 */
class FeatureGuideUiInstrumentedTest {
    @get:org.junit.Rule(order = 0) val acceptedPrivacy = com.yangsong.lizhang.fixtures.AcceptedPrivacyRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<LiZhangApplication>()
    private val storage = app.createDeviceProtectedStorageContext()
    private val preferences = storage.getSharedPreferences(SharedPreferencesOnboardingRepository.FILE_NAME, Context.MODE_PRIVATE)
    private val english = localized("en")
    private lateinit var displayContext: Context
    private lateinit var repository: SharedPreferencesOnboardingRepository
    private lateinit var vm: OnboardingViewModel
    private var guideDensity = 1f

    private fun localized(tag: String) = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
        setLocale(Locale.forLanguageTag(tag))
    })

    @Before fun 创建待开始的功能引导() {
        preferences.edit().clear().commit()
        repository = SharedPreferencesOnboardingRepository(storage, ExistingInstallationEvidence())
        repository.complete()
        vm = OnboardingViewModel(repository)
        displayContext = english
    }

    @After fun 清除隔离偏好() { preferences.edit().clear().commit() }

    private fun show() = compose.setContent {
        val owner = requireNotNull(LocalActivityResultRegistryOwner.current)
        CompositionLocalProvider(LocalContext provides english, LocalConfiguration provides english.resources.configuration,
            LocalActivityResultRegistryOwner provides owner) {
            LiZhangTheme {
                val density = LocalDensity.current
                SideEffect { guideDensity = density.density; displayContext = english }
                LiZhangNavGraph(app.appContainer, onboardingViewModel = vm)
            }
        }
    }

    private fun waitFor(tag: String) = compose.waitUntil(8000) {
        runCatching { compose.onNodeWithTag(tag).assertIsDisplayed(); true }.getOrDefault(false)
    }

    private fun assertProgress(step: FeatureGuideStep) {
        waitFor("功能引导气泡")
        compose.onNodeWithTag("功能引导气泡").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, displayContext.getString(R.string.feature_guide_progress, step.number, step.total)))
        compose.onNodeWithTag("功能引导下一步").assertIsDisplayed()
        compose.onNodeWithTag("功能引导跳过").assertIsDisplayed()
    }

    private fun assertStep(savedStep: FeatureGuideStep, presentedStep: FeatureGuideStep = savedStep) {
        assertProgress(presentedStep)
        assertEquals(savedStep, vm.state.value.featureGuideStep)
    }

    private fun assertPage(page: FeatureGuidePage, savedStep: FeatureGuideStep = FeatureGuideStep.ADD_RECORD) {
        assertProgress(page.step)
        assertEquals(savedStep, vm.state.value.featureGuideStep)
        assertFalse(vm.state.value.seenPageGuides.contains(page))
    }

    private fun next() { compose.onNodeWithTag("功能引导下一步").performClick(); compose.waitForIdle() }
    private fun home() {
        compose.onNodeWithContentDescription(displayContext.getString(R.string.nav_home)).performClick()
        waitFor("首页列表")
    }
    private fun back() { compose.onNodeWithContentDescription(displayContext.getString(R.string.action_back)).performClick() }
    private fun contacts() { compose.onNodeWithTag("功能引导目标CONTACTS").performClick(); waitFor("联系人列表") }
    private fun reminders() {
        compose.onNodeWithTag("功能引导目标REMINDERS").performClick()
        compose.waitUntil(8000) {
            runCatching { compose.onNodeWithText(displayContext.getString(R.string.nav_notifications)).assertIsDisplayed(); true }.getOrDefault(false)
        }
    }
    private fun settings() { compose.onNodeWithTag("功能引导目标SETTINGS").performClick(); waitFor("设置列表") }

    private fun targetMatcher(target: FeatureGuideTarget): SemanticsMatcher {
        val feature = hasTestTag("功能引导目标${target.name}")
        return when (target) {
            FeatureGuideTarget.RECORD_SAVE -> feature or hasTestTag("礼金保存栏")
            FeatureGuideTarget.REMINDERS_PAGE -> feature or hasTestTag("新增独立提醒")
            else -> feature
        }
    }

    private fun targetBounds(target: FeatureGuideTarget): Rect =
        compose.onAllNodes(targetMatcher(target), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot

    /** 保留目标可点击、云体不覆盖目标、屏幕边界和按钮可用的共同安全契约。 */
    private fun assertSafe(target: FeatureGuideTarget, screenTag: String = "功能引导高亮") {
        compose.waitForIdle()
        val bubble = compose.onNodeWithTag("功能引导气泡").fetchSemanticsNode().boundsInRoot
        val anchor = targetBounds(target)
        val screen = compose.onNodeWithTag(screenTag).fetchSemanticsNode().boundsInRoot
        assertFalse("云体不能覆盖真实目标", bubble.overlaps(anchor))
        assertTrue("云体不能越过屏幕边界", bubble.left >= screen.left && bubble.right <= screen.right &&
            bubble.top >= screen.top && bubble.bottom <= screen.bottom)
        if (target == FeatureGuideTarget.ADD_RECORD) {
            val nav = targetBounds(FeatureGuideTarget.SETTINGS)
            assertTrue("首页云体保留底栏完整操作区域", bubble.bottom <= nav.top)
        } else if (target in setOf(FeatureGuideTarget.RECORD_CONTACT, FeatureGuideTarget.RECORD_AMOUNT,
                FeatureGuideTarget.RECORD_DIRECTION, FeatureGuideTarget.RECORD_SAVE)) {
            assertTrue("记账云体保留固定保存栏", bubble.bottom <= targetBounds(FeatureGuideTarget.RECORD_SAVE).top)
        }
        for (tag in listOf("功能引导下一步", "功能引导跳过")) {
            val action = compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().boundsInRoot
            assertTrue("引导按钮仍在云体安全范围内", action.left >= bubble.left && action.right <= bubble.right &&
                action.top >= bubble.top && action.bottom <= bubble.bottom)
        }
    }

    @Test fun 五步只在真实记账页面推进且完成引导不自动保存或介绍其他页面() {
        val recordsBefore = runBlocking { app.appContainer.giftRecordRepository.observeAll().first() }
        show()
        FeatureGuideStep.recordSteps.forEach { step ->
            assertStep(step)
            if (step == FeatureGuideStep.ADD_RECORD) compose.onNodeWithTag("首页列表").assertIsDisplayed()
            else compose.onNodeWithTag("礼金保存栏").assertIsDisplayed()
            next()
        }
        assertEquals(FeatureGuideStep.COMPLETED, vm.state.value.featureGuideStep)
        assertTrue(vm.state.value.seenPageGuides.isEmpty())
        compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
        compose.onNodeWithTag("礼金保存栏").assertIsDisplayed()
        assertEquals("引导不会替用户保存空表单", recordsBefore,
            runBlocking { app.appContainer.giftRecordRepository.observeAll().first() })
        back()
        compose.onNodeWithTag("首页列表").assertIsDisplayed()
        compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
    }

    @Test fun 真实记一笔入口穿透高亮返回首页暂停并能继续联系人步骤() {
        show(); assertStep(FeatureGuideStep.ADD_RECORD)
        compose.onNodeWithTag("功能引导目标ADD_RECORD").performClick()
        waitFor("礼金保存栏")
        assertStep(FeatureGuideStep.RECORD_CONTACT)
        compose.onNodeWithTag("首页列表").assertDoesNotExist()
        back()
        assertStep(FeatureGuideStep.RECORD_CONTACT, FeatureGuideStep.ADD_RECORD)
        next()
        waitFor("礼金保存栏")
        assertStep(FeatureGuideStep.RECORD_CONTACT)
    }

    @Test fun 导航高亮贴合真实入口且圆云泡实际绘制并与目标保持间隔() {
        show(); assertStep(FeatureGuideStep.ADD_RECORD)
        val visual = compose.onNodeWithTag("功能引导视觉锚点ADD_RECORD", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val click = targetBounds(FeatureGuideTarget.ADD_RECORD)
        val bubble = compose.onNodeWithTag("功能引导气泡").fetchSemanticsNode().boundsInRoot
        assertTrue("高亮排除导航浮钮预留的空白", visual.top >= click.top + 16f * guideDensity)
        assertTrue("点击区域继续保留完整高度", click.height > visual.height + 16f * guideDensity)
        assertEquals("视觉锚点与可点击入口保持同一水平中心", click.center.x, visual.center.x, guideDensity)
        assertSafe(FeatureGuideTarget.ADD_RECORD)
        val overlay = compose.onNodeWithTag("功能引导高亮")
        val screen = overlay.fetchSemanticsNode().boundsInRoot
        val cat = compose.onNodeWithTag("功能引导猫咪装饰", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val cloudBody = Rect(bubble.left, bubble.top + cat.height * 40f / 64f, bubble.right, bubble.bottom)
        val cloud = guideCloudGeometry(cloudBody, guideDensity)
        val anchor = visual.inflate(6f * guideDensity)
        val dots = guideThoughtBubbles(anchor, cloud, cat, screen, cloudAbove = true, unit = guideDensity)
        assertEquals("当前设计以两个独立圆泡指向真实入口", 2, dots.size)
        assertFalse("圆泡之间保留空隙", dots[0].bounds.overlaps(dots[1].bounds))
        dots.forEach { assertFalse("圆泡不擦入目标", it.bounds.overlaps(anchor)) }
        val frame = overlay.captureToImage()
        val pixels = frame.toPixelMap()
        fun cloudDistance(point: Offset): Float {
            val local = point - screen.topLeft
            val pixel = pixels[local.x.roundToInt().coerceIn(0, frame.width - 1),
                local.y.roundToInt().coerceIn(0, frame.height - 1)]
            val color = GuideCloudPalette.surface
            return abs(pixel.red - color.red) + abs(pixel.green - color.green) + abs(pixel.blue - color.blue)
        }
        dots.forEach { dot ->
            assertTrue("每个圆泡中心必须有真实暖白绘制", cloudDistance(dot.center) < .08f)
            assertTrue("圆泡外侧不能沿用旧三角尾或连接线",
                cloudDistance(dot.center + Offset(dot.radius + 3f * guideDensity, 0f)) > .08f)
        }
        val gap = (dots[0].center + dots[1].center) / 2f
        assertTrue("圆泡间隙必须保留背景，不能重新连成箭头", cloudDistance(gap) > .08f)
        val directory = File(app.getExternalFilesDir(null), "guide-rc2").apply { mkdirs() }
        File(directory, "导航圆云泡实际绘制.png").outputStream().use {
            frame.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun 联系人页首次介绍可穿透真实菜单并独立消费且不推进记账() {
        show(); assertStep(FeatureGuideStep.ADD_RECORD)
        val permissionBefore = vm.state.value.contactsPermission
        contacts(); assertPage(FeatureGuidePage.CONTACTS)
        compose.onNodeWithText(english.getString(R.string.contacts_first_hint)).assertDoesNotExist()
        compose.onNodeWithTag("联系人添加菜单").performClick()
        compose.onNodeWithText(english.getString(R.string.contact_add_manual)).performClick()
        compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
        compose.onNodeWithTag("首页列表").assertDoesNotExist()
        assertEquals(FeatureGuideStep.ADD_RECORD, vm.state.value.featureGuideStep)
        assertEquals(permissionBefore, vm.state.value.contactsPermission)
        back(); waitFor("联系人列表"); assertPage(FeatureGuidePage.CONTACTS)
        next()
        assertEquals(setOf(FeatureGuidePage.CONTACTS), vm.state.value.seenPageGuides)
        home(); assertStep(FeatureGuideStep.ADD_RECORD)
    }

    @Test fun 提醒及我的只介绍当前页面不自动申请权限且各页状态独立() {
        show(); assertStep(FeatureGuideStep.ADD_RECORD)
        val permissionBefore = vm.state.value.notificationsPermission
        val reminderBefore = app.appContainer.reminderRepository.settings.value
        reminders(); assertPage(FeatureGuidePage.REMINDERS)
        assertEquals(permissionBefore, vm.state.value.notificationsPermission)
        assertEquals(reminderBefore, app.appContainer.reminderRepository.settings.value)
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        next()
        assertEquals(setOf(FeatureGuidePage.REMINDERS), vm.state.value.seenPageGuides)
        back(); waitFor("首页列表"); assertStep(FeatureGuideStep.ADD_RECORD)
        settings(); assertPage(FeatureGuidePage.SETTINGS)
        next()
        assertEquals(setOf(FeatureGuidePage.REMINDERS, FeatureGuidePage.SETTINGS), vm.state.value.seenPageGuides)
        assertEquals(FeatureGuideStep.ADD_RECORD, vm.state.value.featureGuideStep)
        assertEquals(permissionBefore, vm.state.value.notificationsPermission)
        assertEquals(reminderBefore, app.appContainer.reminderRepository.settings.value)
        home(); assertStep(FeatureGuideStep.ADD_RECORD)
    }

    @Test fun 跳过记账持久化且其他页面首次介绍仍独立不因返回重播() {
        show(); assertStep(FeatureGuideStep.ADD_RECORD)
        compose.onNodeWithTag("功能引导跳过").performClick()
        compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
        assertEquals(FeatureGuideStep.COMPLETED,
            SharedPreferencesOnboardingRepository(storage, ExistingInstallationEvidence()).state.value.featureGuideStep)
        contacts(); assertPage(FeatureGuidePage.CONTACTS, FeatureGuideStep.COMPLETED)
        compose.onNodeWithTag("功能引导跳过").performClick()
        compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
        val rebuilt = SharedPreferencesOnboardingRepository(storage, ExistingInstallationEvidence()).state.value
        assertEquals(FeatureGuideStep.COMPLETED, rebuilt.featureGuideStep)
        assertEquals(setOf(FeatureGuidePage.CONTACTS), rebuilt.seenPageGuides)
        home(); compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
    }

    @Test fun 搜索等其他控件照常点击按页介绍且不推进记账步骤() {
        show(); assertStep(FeatureGuideStep.ADD_RECORD)
        compose.onNodeWithContentDescription(english.getString(R.string.action_search)).performClick()
        assertPage(FeatureGuidePage.SEARCH)
        compose.onNodeWithTag("首页列表").assertDoesNotExist()
        next()
        assertEquals(FeatureGuideStep.ADD_RECORD, vm.state.value.featureGuideStep)
        assertEquals(setOf(FeatureGuidePage.SEARCH), vm.state.value.seenPageGuides)
        back(); assertStep(FeatureGuideStep.ADD_RECORD)
    }

    @Test fun 真实记账目标滚出视口时气泡暂停滚回后恢复且进度不变() {
        show(); assertStep(FeatureGuideStep.ADD_RECORD); next(); assertStep(FeatureGuideStep.RECORD_CONTACT)
        next(); assertStep(FeatureGuideStep.RECORD_AMOUNT)
        compose.onNodeWithTag("记账底部留白").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
        assertEquals(FeatureGuideStep.RECORD_AMOUNT, vm.state.value.featureGuideStep)
        compose.onNodeWithTag("功能引导目标RECORD_AMOUNT").performScrollTo()
        assertStep(FeatureGuideStep.RECORD_AMOUNT)
        assertSafe(FeatureGuideTarget.RECORD_AMOUNT)
    }

    @Test fun 法语窄屏大字体提醒页圆云泡首次测量仍可见并保留真实操作区() {
        val french = localized("fr")
        compose.setContent {
            val density = LocalDensity.current
            val owner = requireNotNull(LocalActivityResultRegistryOwner.current)
            SideEffect { displayContext = french; guideDensity = density.density }
            CompositionLocalProvider(LocalContext provides french, LocalConfiguration provides french.resources.configuration,
                LocalDensity provides Density(density.density, 1.5f), LocalActivityResultRegistryOwner provides owner) {
                LiZhangTheme(darkTheme = true) {
                    Box(Modifier.requiredSize(320.dp, 640.dp).testTag("窄屏范围")) {
                        LiZhangNavGraph(app.appContainer, onboardingViewModel = vm)
                    }
                }
            }
        }
        assertStep(FeatureGuideStep.ADD_RECORD)
        reminders(); assertPage(FeatureGuidePage.REMINDERS)
        assertSafe(FeatureGuideTarget.REMINDERS_PAGE, "窄屏范围")
        val directory = File(app.getExternalFilesDir(null), "guide-rc2").apply { mkdirs() }
        File(directory, "法语窄屏大字体提醒页.png").outputStream().use {
            compose.onNodeWithTag("窄屏范围").captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        next()
        assertEquals(setOf(FeatureGuidePage.REMINDERS), vm.state.value.seenPageGuides)
        assertEquals(FeatureGuideStep.ADD_RECORD, vm.state.value.featureGuideStep)
    }

    @Test fun 七语言深浅色单双向大字号覆盖五步和独立页气泡安全边界() {
        val variant = mutableStateOf(Triple("zh-CN", false, LayoutDirection.Ltr))
        compose.setContent {
            val context = remember(variant.value.first) { localized(variant.value.first) }
            val density = LocalDensity.current
            val owner = requireNotNull(LocalActivityResultRegistryOwner.current)
            SideEffect { displayContext = context; guideDensity = density.density }
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides context.resources.configuration,
                LocalDensity provides Density(density.density, 1.5f), LocalLayoutDirection provides variant.value.third,
                LocalActivityResultRegistryOwner provides owner) {
                LiZhangTheme(darkTheme = variant.value.second) { LiZhangNavGraph(app.appContainer, onboardingViewModel = vm) }
            }
        }
        fun variants(step: FeatureGuideStep, target: FeatureGuideTarget) {
            for (tag in listOf("zh-CN", "zh-Hant", "en", "ja", "ko", "es", "fr"))
                for (dark in listOf(false, true)) for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                    compose.runOnIdle { variant.value = Triple(tag, dark, direction) }
                    assertProgress(step)
                    assertSafe(target)
                }
        }
        for (step in FeatureGuideStep.recordSteps) {
            variants(step, FeatureGuideTarget.entries.first { it.step == step })
            assertEquals(step, vm.state.value.featureGuideStep)
            next()
        }
        assertEquals(FeatureGuideStep.COMPLETED, vm.state.value.featureGuideStep)
        back(); waitFor("首页列表")
        for (page in listOf(FeatureGuidePage.CONTACTS, FeatureGuidePage.REMINDERS, FeatureGuidePage.SETTINGS)) {
            when (page) {
                FeatureGuidePage.CONTACTS -> contacts()
                FeatureGuidePage.REMINDERS -> reminders()
                FeatureGuidePage.SETTINGS -> settings()
                else -> error("此安全矩阵仅覆盖联系人、提醒和我的真实入口")
            }
            assertPage(page, FeatureGuideStep.COMPLETED)
            val target = when (page) {
                FeatureGuidePage.CONTACTS -> FeatureGuideTarget.CONTACTS_PAGE
                FeatureGuidePage.REMINDERS -> FeatureGuideTarget.REMINDERS_PAGE
                else -> FeatureGuideTarget.SETTINGS_PAGE
            }
            variants(page.step, target)
            next()
            assertTrue(vm.state.value.seenPageGuides.contains(page))
            assertEquals(FeatureGuideStep.COMPLETED, vm.state.value.featureGuideStep)
            if (page == FeatureGuidePage.REMINDERS) { back(); waitFor("首页列表") } else home()
        }
        assertEquals(setOf(FeatureGuidePage.CONTACTS, FeatureGuidePage.REMINDERS, FeatureGuidePage.SETTINGS), vm.state.value.seenPageGuides)
    }
}
