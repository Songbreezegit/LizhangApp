package com.yangsong.lizhang.onboarding

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.MainActivity
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.onboarding.FeatureGuideStep
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/** 全新测试安装后单独执行，覆盖真实 Activity 重建、语言切换与设置手动查看。 */
class FeatureGuideEntryInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun waitFor(tag: String) = compose.waitUntil(8000) {
        runCatching { compose.onNodeWithTag(tag).assertIsDisplayed(); true }.getOrDefault(false)
    }

    @Test fun 三页跳过后第三步经重建和四语言切换恢复且设置重看不重置() {
        val app = ApplicationProvider.getApplicationContext<LiZhangApplication>()
        val repository = app.appContainer.onboardingRepository
        assertFalse("需从全新测试安装开始", repository.state.value.completed)
        val original = AppCompatDelegate.getApplicationLocales()
        var originalLanguage = ""
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor("引导页面1")
            compose.onNodeWithTag("引导跳过").performClick()
            waitFor("功能引导气泡")
            assertEquals(FeatureGuideStep.ADD_RECORD, repository.state.value.featureGuideStep)
            repeat(2) { compose.onNodeWithTag("功能引导下一步").performClick() }
            assertEquals(FeatureGuideStep.REMINDERS, repository.state.value.featureGuideStep)
            scenario.recreate()
            waitFor("功能引导气泡")
            compose.onNodeWithTag("功能引导进度").assertTextEquals("3 / 4")
            scenario.onActivity { originalLanguage = it.resources.configuration.locales[0].language }
            try {
                for (tag in listOf("zh-CN", "en", "ja", "ko")) {
                    var previous: MainActivity? = null
                    scenario.onActivity {
                        if (it.resources.configuration.locales[0].language != tag.substringBefore('-')) previous = it
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    }
                    compose.waitUntil(8000) {
                        var ready = false
                        runCatching { scenario.onActivity {
                            ready = it !== previous && it.resources.configuration.locales[0].language == tag.substringBefore('-') &&
                                it.appearanceHost.isNavigationReady && it.hasWindowFocus()
                        } }
                        ready
                    }
                    waitFor("功能引导气泡")
                    val localized = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                        setLocale(Locale.forLanguageTag(tag))
                    })
                    compose.onNodeWithText(localized.getString(R.string.feature_guide_reminders_title)).assertIsDisplayed()
                    assertEquals(FeatureGuideStep.REMINDERS, repository.state.value.featureGuideStep)
                }
            } finally {
                scenario.onActivity { AppCompatDelegate.setApplicationLocales(original) }
                compose.waitUntil(8000) {
                    var ready = false
                    runCatching { scenario.onActivity {
                        ready = it.resources.configuration.locales[0].language == originalLanguage &&
                            it.appearanceHost.isNavigationReady && it.hasWindowFocus()
                    } }
                    ready
                }
            }
            waitFor("功能引导气泡")
            compose.onNodeWithTag("功能引导目标SETTINGS").performClick()
            waitFor("设置列表")
            compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
            assertEquals(FeatureGuideStep.REMINDERS, repository.state.value.featureGuideStep)
            compose.onNodeWithTag("设置列表").performScrollToIndex(3)
            var reviewLabel = ""
            scenario.onActivity { reviewLabel = it.getString(R.string.settings_onboarding) }
            compose.onNodeWithText(reviewLabel).performClick()
            waitFor("引导页面1")
            repeat(2) { compose.onNodeWithTag("引导下一步").performClick() }
            waitFor("引导页面3")
            compose.onNodeWithTag("引导完成").performClick()
            waitFor("设置列表")
            assertEquals(FeatureGuideStep.REMINDERS, repository.state.value.featureGuideStep)
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("功能引导气泡")
            compose.onNodeWithTag("功能引导进度").assertTextEquals("3 / 4")
        }
    }
}
