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
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 全新安装时单独执行，验证未确认告知的七语言重建与离线文档位置保存。 */
class OnboardingLocaleInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun 未确认告知阅读隐私时切换七语言保留文档和未同意状态() {
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
                compose.onNodeWithTag("告知隐私政策").performScrollTo().performClick()
                compose.waitUntil(5000) {
                    runCatching {
                        compose.onNodeWithTag("legal_document_privacy").assertIsDisplayed()
                        true
                    }.getOrDefault(false)
                }
                for (tag in listOf("zh-CN", "zh-Hant", "en", "ja", "ko", "es", "fr")) {
                    var previous: MainActivity? = null
                    scenario.onActivity {
                        if (AppLanguage.fromLanguageTag(it.resources.configuration.locales[0].toLanguageTag()) != AppLanguage.fromLanguageTag(tag)) previous = it
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    }
                    try {
                        compose.waitUntil(8000) {
                            var ready = false
                            runCatching { scenario.onActivity {
                                val locale = it.resources.configuration.locales[0]
                                ready = it !== previous && locale.language == tag.substringBefore('-') &&
                                    (tag != "zh-Hant" || locale.script == "Hant") &&
                                    it.appearanceHost.isNavigationReady && it.hasWindowFocus()
                            } }
                            ready
                        }
                    } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
                        var detail = "目标 $tag"
                        scenario.onActivity { detail += "，当前 ${it.resources.configuration.locales[0].language}，新 Activity=${it !== previous}，就绪=${it.appearanceHost.isNavigationReady}，焦点=${it.hasWindowFocus()}" }
                        throw AssertionError(detail, error)
                    }
                    compose.waitUntil(5000) {
                        runCatching { compose.onNodeWithTag("legal_document_privacy").assertIsDisplayed(); true }.getOrDefault(false)
                    }
                    compose.onNodeWithText(app.createConfigurationContext(android.content.res.Configuration(app.resources.configuration).apply {
                        setLocale(java.util.Locale.forLanguageTag(tag))
                    }).getString(R.string.legal_language_fallback)).assertIsDisplayed()
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
