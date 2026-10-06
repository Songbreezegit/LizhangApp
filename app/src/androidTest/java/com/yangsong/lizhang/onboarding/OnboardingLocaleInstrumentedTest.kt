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
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 全新安装时单独执行，验证引导的语言重建与 Pager 保存状态。 */
class OnboardingLocaleInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun 引导第二页切换四语言后保留页码且资源已经更新() {
        val app = ApplicationProvider.getApplicationContext<LiZhangApplication>()
        assertFalse(app.appContainer.onboardingRepository.state.value.completed)
        val original = AppCompatDelegate.getApplicationLocales()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var originalResourceLanguage = ""
            scenario.onActivity { originalResourceLanguage = it.resources.configuration.locales[0].language }
            try {
                compose.onNodeWithTag("引导下一步").performClick()
                compose.waitUntil(5000) {
                    runCatching {
                        compose.onNodeWithTag("引导页面2").assertIsDisplayed()
                        compose.onNodeWithTag("引导下一步").assertIsEnabled()
                        true
                    }.getOrDefault(false)
                }
                for (tag in listOf("zh-CN", "en", "ja", "ko")) {
                    var previous: MainActivity? = null
                    scenario.onActivity {
                        if (it.resources.configuration.locales[0].language != tag.substringBefore('-')) previous = it
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    }
                    try {
                        compose.waitUntil(8000) {
                            var ready = false
                            runCatching { scenario.onActivity {
                                ready = it !== previous && it.resources.configuration.locales[0].language == tag.substringBefore('-') &&
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
                        runCatching { compose.onNodeWithTag("引导页面2").assertIsDisplayed(); true }.getOrDefault(false)
                    }
                    compose.onNodeWithText(app.createConfigurationContext(android.content.res.Configuration(app.resources.configuration).apply {
                        setLocale(java.util.Locale.forLanguageTag(tag))
                    }).getString(R.string.onboarding_features_title)).assertIsDisplayed()
                    assertFalse(app.appContainer.onboardingRepository.state.value.completed)
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
