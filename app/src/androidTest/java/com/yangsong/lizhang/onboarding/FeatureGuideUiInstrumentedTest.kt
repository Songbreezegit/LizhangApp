package com.yangsong.lizhang.onboarding

import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.test.core.app.ApplicationProvider
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.R
import com.yangsong.lizhang.data.preferences.SharedPreferencesOnboardingRepository
import com.yangsong.lizhang.domain.onboarding.*
import com.yangsong.lizhang.ui.navigation.LiZhangNavGraph
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.OnboardingViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** 使用真实导航图、控件和隔离偏好；只在测试模拟器上运行，不读取通讯录。 */
class FeatureGuideUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<LiZhangApplication>()
    private val storage = app.createDeviceProtectedStorageContext()
    private val preferences = storage.getSharedPreferences(SharedPreferencesOnboardingRepository.FILE_NAME, Context.MODE_PRIVATE)
    private lateinit var repository: SharedPreferencesOnboardingRepository
    private lateinit var vm: OnboardingViewModel
    private var guideColor = Color.Unspecified
    private var guideDensity = 1f
    private val english = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.ENGLISH) })

    @Before fun 创建待开始的功能引导() {
        preferences.edit().clear().commit()
        repository = SharedPreferencesOnboardingRepository(storage, ExistingInstallationEvidence())
        repository.complete()
        vm = OnboardingViewModel(repository)
    }

    @After fun 清除隔离偏好() { preferences.edit().clear().commit() }

    private fun show() = compose.setContent {
        val activityRegistryOwner = requireNotNull(LocalActivityResultRegistryOwner.current)
        CompositionLocalProvider(LocalContext provides english, LocalConfiguration provides english.resources.configuration,
            LocalActivityResultRegistryOwner provides activityRegistryOwner) {
            LiZhangTheme {
                guideColor = MaterialTheme.colorScheme.primary
                guideDensity = LocalDensity.current.density
                LiZhangNavGraph(app.appContainer, onboardingViewModel = vm)
            }
        }
    }

    private fun waitFor(tag: String) = compose.waitUntil(8000) {
        runCatching { compose.onNodeWithTag(tag).assertIsDisplayed(); true }.getOrDefault(false)
    }

    private fun assertStep(step: FeatureGuideStep) {
        waitFor("功能引导气泡")
        assertEquals(step, vm.state.value.featureGuideStep)
        compose.onNodeWithTag("功能引导进度").assertTextEquals("${step.number} / 4")
    }

    private fun next() { compose.onNodeWithTag("功能引导下一步").performClick(); compose.waitForIdle() }
    private fun home() { compose.onNodeWithText(english.getString(R.string.nav_home)).performClick() }
    private fun back() { compose.onNodeWithContentDescription(english.getString(R.string.action_back)).performClick() }

    @Test fun 首页连续查看四步完成后气泡消失且没有导航() {
        show()
        listOf(FeatureGuideStep.ADD_RECORD, FeatureGuideStep.CONTACTS, FeatureGuideStep.REMINDERS, FeatureGuideStep.SETTINGS).forEach {
            assertStep(it)
            compose.onNodeWithTag("首页列表").assertIsDisplayed()
            next()
        }
        assertEquals(FeatureGuideStep.COMPLETED, vm.state.value.featureGuideStep)
        compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
        compose.onNodeWithTag("首页列表").assertIsDisplayed()
    }

    @Test fun 真实记一笔入口可穿透高亮并正常导航且返回后续接联系人() {
        show(); assertStep(FeatureGuideStep.ADD_RECORD)
        compose.onNodeWithTag("功能引导目标ADD_RECORD").performClick()
        waitFor("礼金保存栏")
        assertEquals(FeatureGuideStep.CONTACTS, vm.state.value.featureGuideStep)
        compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
        compose.onNodeWithTag("首页列表").assertDoesNotExist()
        back()
        assertStep(FeatureGuideStep.CONTACTS)
    }

    @Test fun 导航高亮贴合视觉入口并由真实绘制的箭头连接气泡() {
        show(); assertStep(FeatureGuideStep.ADD_RECORD)
        val visual = compose.onNodeWithTag("功能引导视觉锚点ADD_RECORD", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val click = compose.onNodeWithTag("功能引导目标ADD_RECORD").fetchSemanticsNode().boundsInRoot
        val bubble = compose.onNodeWithTag("功能引导气泡").fetchSemanticsNode().boundsInRoot
        assertTrue("高亮排除导航浮钮预留的空白", visual.top >= click.top + 16f * guideDensity)
        assertTrue("点击区域继续保留完整高度", click.height > visual.height + 16f * guideDensity)
        assertEquals("视觉锚点与可点击入口保持同一水平中心", click.center.x, visual.center.x, guideDensity)
        assertTrue("宽气泡也不能盖住其他导航浮钮", bubble.bottom < click.top)

        val overlay = compose.onNodeWithTag("功能引导高亮")
        val origin = overlay.fetchSemanticsNode().boundsInRoot.topLeft
        val frame = overlay.captureToImage()
        val pixels = frame.toPixelMap()
        val highlightTop = visual.top - 4f * guideDensity
        fun assertPainted(point: Offset, description: String) {
            val local = point - origin
            val radius = (2f * guideDensity).roundToInt().coerceAtLeast(2)
            var found = false
            for (y in (local.y.roundToInt() - radius)..(local.y.roundToInt() + radius)) {
                for (x in (local.x.roundToInt() - radius)..(local.x.roundToInt() + radius)) {
                    if (x !in 0 until frame.width || y !in 0 until frame.height) continue
                    val pixel = pixels[x, y]
                    val distance = abs(pixel.red - guideColor.red) + abs(pixel.green - guideColor.green) +
                        abs(pixel.blue - guideColor.blue)
                    if (distance < .4f) found = true
                }
            }
            assertTrue(description, found)
        }
        assertPainted(Offset(visual.center.x, highlightTop + guideDensity), "箭头尖端必须落在真实视觉高亮框上")
        assertPainted(Offset(visual.center.x - 3f * guideDensity, (bubble.bottom + highlightTop) / 2f),
            "气泡与高亮框之间必须真正绘制指向箭头，不能只有状态或坐标")
        val directory = File(app.getExternalFilesDir(null), "guide-v096").apply { mkdirs() }
        File(directory, "导航引导指向.png").outputStream().use {
            frame.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun 真实联系人入口正常导航并暂停直到用户自己回首页() {
        show(); assertStep(FeatureGuideStep.ADD_RECORD); next(); assertStep(FeatureGuideStep.CONTACTS)
        compose.onNodeWithTag("功能引导目标CONTACTS").performClick()
        waitFor("联系人列表")
        assertEquals(FeatureGuideStep.REMINDERS, vm.state.value.featureGuideStep)
        compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
        compose.onNodeWithTag("首页列表").assertDoesNotExist()
        compose.onNodeWithText(english.getString(R.string.contacts_first_hint)).assertDoesNotExist()
        home(); assertStep(FeatureGuideStep.REMINDERS)
    }

    @Test fun 提醒只打开现有页面并推进且我的入口完成整个引导() {
        show(); assertStep(FeatureGuideStep.ADD_RECORD); next(); next(); assertStep(FeatureGuideStep.REMINDERS)
        val permissionBefore = vm.state.value.notificationsPermission
        val enabledBefore = app.appContainer.reminderRepository.settings.value.enabled
        compose.onNodeWithTag("功能引导目标REMINDERS").performClick()
        waitFor("提醒顶部插画")
        assertEquals(FeatureGuideStep.SETTINGS, vm.state.value.featureGuideStep)
        assertEquals(permissionBefore, vm.state.value.notificationsPermission)
        assertEquals(enabledBefore, app.appContainer.reminderRepository.settings.value.enabled)
        compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        back(); assertStep(FeatureGuideStep.SETTINGS)
        compose.onNodeWithTag("功能引导目标SETTINGS").performClick()
        waitFor("设置列表")
        assertEquals(FeatureGuideStep.COMPLETED, vm.state.value.featureGuideStep)
        home(); waitFor("首页列表")
        compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
    }

    @Test fun 跳过后返回首页和重新创建仓库都不再显示() {
        show(); assertStep(FeatureGuideStep.ADD_RECORD)
        compose.onNodeWithTag("功能引导跳过").performClick()
        compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
        assertEquals(FeatureGuideStep.COMPLETED, SharedPreferencesOnboardingRepository(storage, ExistingInstallationEvidence()).state.value.featureGuideStep)
        compose.onNodeWithTag("功能引导目标CONTACTS").performClick(); waitFor("联系人列表")
        home(); waitFor("首页列表")
        compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
    }

    @Test fun 其他控件照常点击且不会推进当前步骤() {
        show(); assertStep(FeatureGuideStep.ADD_RECORD)
        compose.onNodeWithContentDescription(english.getString(R.string.action_search)).performClick()
        compose.waitForIdle()
        assertEquals(FeatureGuideStep.ADD_RECORD, vm.state.value.featureGuideStep)
        compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
        back(); assertStep(FeatureGuideStep.ADD_RECORD)
    }

    @Test fun 提醒目标滚出首页后气泡暂停且滚回后恢复() {
        show(); assertStep(FeatureGuideStep.ADD_RECORD); next(); next(); assertStep(FeatureGuideStep.REMINDERS)
        compose.onNodeWithTag("首页列表").performScrollToIndex(3)
        compose.waitForIdle()
        compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
        assertEquals(FeatureGuideStep.REMINDERS, vm.state.value.featureGuideStep)
        compose.onNodeWithTag("首页列表").performScrollToIndex(0)
        assertStep(FeatureGuideStep.REMINDERS)
    }

    @Test fun 法语窄屏大字体提醒气泡首次测量仍可见并保留操作区() {
        val localized = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocale(Locale.FRENCH)
        })
        compose.setContent {
            val density = LocalDensity.current
            val owner = requireNotNull(LocalActivityResultRegistryOwner.current)
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration,
                LocalDensity provides Density(density.density, 1.5f), LocalActivityResultRegistryOwner provides owner) {
                LiZhangTheme(darkTheme = true) {
                    Box(Modifier.requiredSize(320.dp, 640.dp).testTag("窄屏范围")) {
                        LiZhangNavGraph(app.appContainer, onboardingViewModel = vm)
                    }
                }
            }
        }
        assertStep(FeatureGuideStep.ADD_RECORD)
        next(); next(); assertStep(FeatureGuideStep.REMINDERS)
        val bubble = compose.onNodeWithTag("功能引导气泡").fetchSemanticsNode().boundsInRoot
        val screen = compose.onNodeWithTag("窄屏范围").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("功能引导目标REMINDERS").fetchSemanticsNode().boundsInRoot
        val nav = compose.onNodeWithTag("功能引导目标SETTINGS").fetchSemanticsNode().boundsInRoot
        assertFalse("气泡不能挡住顶部提醒入口", bubble.overlaps(target))
        assertTrue("气泡留在窄屏与底栏之间", bubble.left >= screen.left && bubble.right <= screen.right &&
            bubble.top >= screen.top && bubble.bottom <= nav.top)
        compose.onNodeWithTag("功能引导下一步").assertIsDisplayed()
        compose.onNodeWithTag("功能引导跳过").assertIsDisplayed()
        val directory = File(app.getExternalFilesDir(null), "guide-v096").apply { mkdirs() }
        File(directory, "法语窄屏大字体提醒.png").outputStream().use {
            compose.onNodeWithTag("窄屏范围").captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        next(); assertStep(FeatureGuideStep.SETTINGS)
    }

    @Test fun 七语言深浅色大字体气泡和操作均在安全区域且不覆盖目标() {
        val variant = mutableStateOf(Triple("zh", false, LayoutDirection.Ltr))
        compose.setContent {
            val localized = remember(variant.value.first) { app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(variant.value.first))
            }) }
            val density = LocalDensity.current
            val activityRegistryOwner = requireNotNull(LocalActivityResultRegistryOwner.current)
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration,
                LocalDensity provides Density(density.density, 1.5f), LocalLayoutDirection provides variant.value.third,
                LocalActivityResultRegistryOwner provides activityRegistryOwner) {
                LiZhangTheme(darkTheme = variant.value.second) { LiZhangNavGraph(app.appContainer, onboardingViewModel = vm) }
            }
        }
        assertStep(FeatureGuideStep.ADD_RECORD)
        for (step in listOf(FeatureGuideStep.ADD_RECORD, FeatureGuideStep.CONTACTS, FeatureGuideStep.REMINDERS, FeatureGuideStep.SETTINGS)) {
            for (tag in listOf("zh", "zh-Hant", "en", "ja", "ko", "es", "fr")) for (dark in listOf(false, true))
                for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                compose.runOnIdle { variant.value = Triple(tag, dark, direction) }
                assertStep(step)
                val bubble = compose.onNodeWithTag("功能引导气泡").fetchSemanticsNode().boundsInRoot
                val target = compose.onNodeWithTag("功能引导目标${step.name}").fetchSemanticsNode().boundsInRoot
                assertFalse("气泡不能覆盖真实目标", bubble.overlaps(target))
                assertTrue("气泡不能越过屏幕左边缘", bubble.left >= 0f)
                compose.onNodeWithTag("功能引导下一步").assertIsDisplayed()
                compose.onNodeWithTag("功能引导跳过").assertIsDisplayed()
                val nav = compose.onNodeWithTag("功能引导目标SETTINGS").fetchSemanticsNode().boundsInRoot
                assertTrue("气泡必须位于底栏上方", bubble.bottom <= nav.top)
            }
            next()
        }
    }
}
