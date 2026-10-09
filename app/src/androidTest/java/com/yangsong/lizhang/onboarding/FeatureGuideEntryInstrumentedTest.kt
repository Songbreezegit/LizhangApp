package com.yangsong.lizhang.onboarding

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.MainActivity
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.model.AppLanguage
import com.yangsong.lizhang.domain.onboarding.FeatureGuidePage
import com.yangsong.lizhang.domain.onboarding.FeatureGuideStep
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/** 全新测试安装后单独执行，覆盖明确隐私确认、真实记账第三步、七语言重建和文档返回。 */
class FeatureGuideEntryInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun waitFor(tag: String) = compose.waitUntil(8000) {
        runCatching { compose.onNodeWithTag(tag).assertIsDisplayed(); true }.getOrDefault(false)
    }

    @Test fun 明确隐私确认后记账第三步经重建和七语言切换恢复且文档返回不重置() {
        val app = ApplicationProvider.getApplicationContext<LiZhangApplication>()
        val repository = app.appContainer.onboardingRepository
        assertFalse("需从全新测试安装开始", repository.state.value.completed)
        assertFalse(app.appContainer.canProcessPersonalData)
        val original = AppCompatDelegate.getApplicationLocales()
        var originalLanguage = ""
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor("首次隐私告知")
            compose.onNodeWithTag("隐私告知同意").performScrollTo().performClick()
            waitFor("功能引导气泡")
            assertTrue(app.appContainer.canProcessPersonalData)
            assertEquals(FeatureGuideStep.ADD_RECORD, repository.state.value.featureGuideStep)
            compose.onNodeWithTag("功能引导下一步").performClick()
            waitFor("礼金保存栏")
            waitFor("功能引导气泡")
            assertEquals(FeatureGuideStep.RECORD_CONTACT, repository.state.value.featureGuideStep)
            compose.onNodeWithTag("功能引导下一步").performClick()
            waitFor("功能引导气泡")
            assertEquals(FeatureGuideStep.RECORD_AMOUNT, repository.state.value.featureGuideStep)
            compose.onNodeWithTag("功能引导气泡").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 / 5"))
            scenario.recreate()
            waitFor("功能引导气泡")
            compose.onNodeWithTag("功能引导气泡").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 / 5"))
            scenario.onActivity { originalLanguage = it.resources.configuration.locales[0].toLanguageTag() }
            try {
                for (tag in listOf("zh-CN", "zh-Hant", "en", "ja", "ko", "es", "fr")) {
                    var previous: MainActivity? = null
                    scenario.onActivity {
                        if (AppLanguage.fromLanguageTag(it.resources.configuration.locales[0].toLanguageTag()) != AppLanguage.fromLanguageTag(tag)) previous = it
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    }
                    compose.waitUntil(8000) {
                        var ready = false
                        runCatching { scenario.onActivity {
                            ready = it !== previous &&
                                AppLanguage.fromLanguageTag(it.resources.configuration.locales[0].toLanguageTag()) == AppLanguage.fromLanguageTag(tag) &&
                                it.appearanceHost.isNavigationReady && it.hasWindowFocus()
                        } }
                        ready
                    }
                    waitFor("功能引导气泡")
                    val localized = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                        setLocale(Locale.forLanguageTag(tag))
                    })
                    compose.onNodeWithText(localized.getString(R.string.feature_guide_record_amount_title)).assertIsDisplayed()
                    compose.onNodeWithTag("功能引导气泡").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                        localized.getString(R.string.feature_guide_progress, 3, 5)))
                    assertEquals(FeatureGuideStep.RECORD_AMOUNT, repository.state.value.featureGuideStep)
                    assertTrue(app.appContainer.canProcessPersonalData)
                }
            } finally {
                scenario.onActivity { AppCompatDelegate.setApplicationLocales(original) }
                compose.waitUntil(8000) {
                    var ready = false
                    runCatching { scenario.onActivity {
                        ready = it.resources.configuration.locales[0].toLanguageTag() == originalLanguage &&
                            it.appearanceHost.isNavigationReady && it.hasWindowFocus()
                    } }
                    ready
                }
            }
            var backLabel = ""
            var settingsLabel = ""
            var homeLabel = ""
            var termsLabel = ""
            scenario.onActivity {
                backLabel = it.getString(R.string.action_back)
                settingsLabel = it.getString(R.string.nav_settings)
                homeLabel = it.getString(R.string.nav_home)
                termsLabel = it.getString(R.string.legal_terms_title)
            }
            compose.onNodeWithContentDescription(backLabel).performClick()
            waitFor("首页列表")
            assertEquals(FeatureGuideStep.RECORD_AMOUNT, repository.state.value.featureGuideStep)
            compose.onNodeWithContentDescription(settingsLabel).performClick()
            waitFor("设置列表")
            waitFor("功能引导气泡")
            compose.onNodeWithTag("功能引导下一步").performClick()
            assertTrue(repository.state.value.seenPageGuides.contains(FeatureGuidePage.SETTINGS))
            assertEquals(FeatureGuideStep.RECORD_AMOUNT, repository.state.value.featureGuideStep)
            compose.onNodeWithTag("设置列表").performScrollToIndex(3)
            compose.onNodeWithText(termsLabel).performClick()
            waitFor("legal_document_terms")
            compose.onNodeWithContentDescription(backLabel).performClick()
            waitFor("设置列表")
            assertEquals(FeatureGuideStep.RECORD_AMOUNT, repository.state.value.featureGuideStep)
            compose.onNodeWithContentDescription(homeLabel).performClick()
            waitFor("功能引导气泡")
            compose.onNodeWithTag("功能引导下一步").performClick()
            waitFor("礼金保存栏")
            waitFor("功能引导气泡")
            compose.onNodeWithTag("功能引导气泡").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 / 5"))
            assertEquals(FeatureGuideStep.RECORD_AMOUNT, repository.state.value.featureGuideStep)
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("功能引导气泡")
            compose.onNodeWithTag("首次隐私告知").assertDoesNotExist()
            assertEquals(FeatureGuideStep.RECORD_AMOUNT, repository.state.value.featureGuideStep)
            compose.onNodeWithTag("功能引导下一步").performClick()
            waitFor("礼金保存栏")
            waitFor("功能引导气泡")
            compose.onNodeWithTag("功能引导气泡").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 / 5"))
        }
    }
}
