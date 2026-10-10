package com.yangsong.lizhang.onboarding

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.MainActivity
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.model.AppLanguage
import com.yangsong.lizhang.ui.component.currentAppLanguage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 全新安装时单独执行，验证未确认告知的七语言配置更新与同卡片完整文档。 */
class OnboardingLocaleInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun 未确认告知切换七语言仍可在同一卡片阅读完整文档且不自动同意() {
        val app = ApplicationProvider.getApplicationContext<LiZhangApplication>()
        assertFalse(app.appContainer.onboardingRepository.state.value.completed)
        assertFalse(app.appContainer.canProcessPersonalData)
        val original = AppCompatDelegate.getApplicationLocales()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var originalResourceLanguage = ""
            scenario.onActivity { originalResourceLanguage = it.resources.configuration.locales[0].language }
            try {
                compose.waitUntil(8000) {
                    runCatching { compose.onNodeWithTag("首次隐私告知").assertIsDisplayed(); true }.getOrDefault(false)
                }
                compose.waitUntil(5000) {
                    runCatching {
                        compose.onNodeWithTag("隐私告知同意").assertIsEnabled()
                        true
                    }.getOrDefault(false)
                }
                for (tag in listOf("zh-CN", "zh-Hant", "en", "ja", "ko", "es", "fr")) {
                    var previous: MainActivity? = null
                    scenario.onActivity {
                        previous = it
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    }
                    try {
                        compose.waitUntil(8000) {
                            var ready = false
                            runCatching { scenario.onActivity {
                                val locale = it.resources.configuration.locales[0]
                                ready = it === previous && AppLanguage.fromLanguageTag(locale.toLanguageTag()) == AppLanguage.fromLanguageTag(tag) &&
                                    currentAppLanguage() == AppLanguage.fromLanguageTag(tag) &&
                                    it.appearanceHost.isNavigationReady && it.hasWindowFocus()
                            } }
                            ready
                        }
                    } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
                        var detail = "目标 $tag"
                        scenario.onActivity { detail += "，当前 ${it.resources.configuration.locales[0].toLanguageTag()}，同一 Activity=${it === previous}，就绪=${it.appearanceHost.isNavigationReady}，焦点=${it.hasWindowFocus()}" }
                        throw AssertionError(detail, error)
                    }
                    compose.onNodeWithTag("首次隐私告知").assertIsDisplayed()
                    compose.onNodeWithTag("告知正文_privacy").assertExists()
                    compose.onNodeWithTag("告知正文_terms").assertExists()
                    compose.onNodeWithText(app.createConfigurationContext(android.content.res.Configuration(app.resources.configuration).apply {
                        setLocale(java.util.Locale.forLanguageTag(tag))
                    }).getString(R.string.legal_language_fallback)).performScrollTo().assertIsDisplayed()
                    compose.onNodeWithTag("隐私告知同意").assertIsDisplayed().assertIsEnabled()
                    compose.onNodeWithTag("隐私告知拒绝").assertIsDisplayed().assertIsEnabled()
                    assertFalse(app.appContainer.onboardingRepository.state.value.completed)
                    assertFalse(app.appContainer.canProcessPersonalData)
                    assertFalse(app.appContainer.isBusinessDatabaseInitialized)
                    assertFalse(app.appContainer.isReminderRepositoryInitialized)
                }
            } finally {
                // 等原语言恢复后才关闭 Activity，避免异步配置派发影响下一类测试。
                scenario.onActivity { AppCompatDelegate.setApplicationLocales(original) }
                compose.waitUntil(8000) {
                    var restored = false
                    runCatching { scenario.onActivity {
                        restored = it.resources.configuration.locales[0].language == originalResourceLanguage &&
                            it.appearanceHost.isNavigationReady && it.hasWindowFocus()
                    } }
                    restored
                }
            }
        }
    }
}
