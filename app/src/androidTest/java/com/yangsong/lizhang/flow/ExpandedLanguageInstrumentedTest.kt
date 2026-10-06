package com.yangsong.lizhang.flow

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.MainActivity
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.model.AppLanguage
import com.yangsong.lizhang.ui.component.currentAppLanguage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 只调整测试安装的引导与语言偏好；不创建联系人，也不清除应用数据。 */
class ExpandedLanguageInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun 新增语言通过真实弹窗切换且重建保留语言与设置页() {
        val app = ApplicationProvider.getApplicationContext<LiZhangApplication>()
        instrumentation.runOnMainSync {
            app.appContainer.onboardingRepository.complete()
            app.appContainer.onboardingRepository.completeFeatureGuide()
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // AppCompat 在首个 Activity 启动时读取持久化设置，启动后再保存原值。
            val original = AppCompatDelegate.getApplicationLocales()
            try {
                var previous: MainActivity? = null
                scenario.onActivity {
                    if (resourceLanguage(it) != AppLanguage.ZH_CN) previous = it
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN"))
                }
                awaitLanguage(scenario, AppLanguage.ZH_CN, previous)
                // 导航文字在顶部标题与底部页签中可能重复，只点击可点击的页签。
                compose.onNode(hasText("我的") and hasClickAction()).performClick()
                compose.onNodeWithTag("设置列表").assertIsDisplayed()

                val targets = listOf(
                    LanguageTarget(AppLanguage.ZH_HANT, "繁體中文", "語言"),
                    LanguageTarget(AppLanguage.ZH_CN, "简体中文", "语言"),
                    LanguageTarget(AppLanguage.ES, "Español", "Idioma"),
                    LanguageTarget(AppLanguage.FR, "Français", "Langue"),
                )
                for (target in targets) {
                    scenario.onActivity { previous = it }
                    openPicker(scenario)
                    languageOption(target.displayName).performScrollTo().performClick()
                    awaitLanguage(scenario, target.language, previous)
                    assertLanguageAndSettings(scenario, target)
                    assertSelectedOption(scenario, target)

                    scenario.onActivity { previous = it }
                    scenario.recreate()
                    awaitLanguage(scenario, target.language, previous)
                    assertLanguageAndSettings(scenario, target)
                    assertSelectedOption(scenario, target)
                }
            } finally {
                // 失败时弹窗也可能仍在；先关闭，避免其焦点影响语言恢复。
                runCatching {
                    if (compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()) dismissPicker(scenario)
                }
                scenario.onActivity { AppCompatDelegate.setApplicationLocales(original) }
            }
        }
    }

    private fun assertLanguageAndSettings(scenario: ActivityScenario<MainActivity>, target: LanguageTarget) {
        scenario.onActivity {
            assertEquals("实际资源使用目标语言", target.language, resourceLanguage(it))
            assertEquals("应用语言偏好使用目标语言", target.language, currentAppLanguage())
            assertEquals("实际资源具有对应翻译", target.title, it.getString(R.string.language))
        }
        compose.onNodeWithTag("设置列表").assertIsDisplayed()
        compose.onNodeWithText(target.title).performScrollTo().assertIsDisplayed()
    }

    private fun assertSelectedOption(scenario: ActivityScenario<MainActivity>, target: LanguageTarget) {
        openPicker(scenario)
        languageOption(target.displayName).performScrollTo().assertIsSelected()
        compose.onAllNodes(isSelectable() and hasAnyAncestor(isDialog()) and isSelected()).assertCountEquals(1)
        dismissPicker(scenario)
    }

    private fun openPicker(scenario: ActivityScenario<MainActivity>) {
        compose.onNodeWithText(localizedString(scenario, R.string.language)).performScrollTo().performClick()
        compose.onNode(isDialog()).assertExists()
    }

    private fun dismissPicker(scenario: ActivityScenario<MainActivity>) {
        compose.onNode(hasText(localizedString(scenario, R.string.action_cancel)) and hasAnyAncestor(isDialog()))
            .performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    private fun languageOption(label: String) =
        compose.onNode(isSelectable() and hasText(label) and hasAnyAncestor(isDialog()))

    private fun localizedString(scenario: ActivityScenario<MainActivity>, resourceId: Int): String {
        var value = ""
        scenario.onActivity { value = it.getString(resourceId) }
        return value
    }

    private fun awaitLanguage(
        scenario: ActivityScenario<MainActivity>,
        target: AppLanguage,
        previous: MainActivity? = null,
    ) {
        // 中文必须同时识别 script/region；只检查 language == zh 会误把简繁切换当作完成。
        var lastDiagnostic = "目标=$target，尚未取得 Activity"
        var lastActivityReadFailure: Throwable? = null
        try {
            compose.waitUntil(8000) {
                var ready = false
                runCatching {
                    scenario.onActivity {
                        val preference = currentAppLanguage()
                        val newActivity = it !== previous
                        val navigationReady = it.appearanceHost.isNavigationReady
                        val windowFocus = it.hasWindowFocus()
                        val snapshotReleased = it.appearanceState.snapshot == null
                        lastDiagnostic = "目标=$target，完整资源tag=${it.resources.configuration.locales.toLanguageTags()}，" +
                            "currentAppLanguage=$preference，Activity identity是否新=$newActivity，" +
                            "当前identity=${System.identityHashCode(it)}，旧identity=${previous?.let(System::identityHashCode)}，" +
                            "navigationReady=$navigationReady，windowFocus=$windowFocus，" +
                            "snapshot是否null=$snapshotReleased，languageRequest是否null=${it.appearanceState.languageRequest == null}"
                        ready = newActivity && resourceLanguage(it) == target && preference == target &&
                            navigationReady && windowFocus && snapshotReleased
                        lastActivityReadFailure = null
                    }
                }.onFailure { lastActivityReadFailure = it }
                ready
            }
        } catch (timeout: ComposeTimeoutException) {
            val diagnostic = lastDiagnostic +
                (lastActivityReadFailure?.let { "，最后读取Activity异常=${it.javaClass.simpleName}: ${it.message}" } ?: "")
            println("语言就绪超时诊断：$diagnostic")
            throw AssertionError("语言就绪超时诊断：$diagnostic", timeout)
        }
        compose.waitForIdle()
    }

    private fun resourceLanguage(activity: MainActivity): AppLanguage =
        AppLanguage.fromLanguageTag(activity.resources.configuration.locales[0].toLanguageTag())

    private data class LanguageTarget(val language: AppLanguage, val displayName: String, val title: String)
}
