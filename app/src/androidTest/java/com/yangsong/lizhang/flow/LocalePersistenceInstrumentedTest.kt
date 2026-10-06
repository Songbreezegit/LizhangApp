package com.yangsong.lizhang.flow

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.MainActivity
import com.yangsong.lizhang.R
import com.yangsong.lizhang.data.preferences.SharedPreferencesOnboardingRepository
import com.yangsong.lizhang.domain.model.AppLanguage
import com.yangsong.lizhang.ui.component.currentAppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 两个方法分别执行，中间由 adb 强制停止进程；localePersistenceTag 默认 ja，兼容原有调用。 */
class LocalePersistenceInstrumentedTest {
    private val localeTag get() = InstrumentationRegistry.getArguments().getString("localePersistenceTag") ?: "ja"
    private val targetLanguage get() = AppLanguage.fromLanguageTag(localeTag)

    @Test
    fun setJapaneseForRestart() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("localePersistencePhase") == "setup")
        require(targetLanguage != AppLanguage.SYSTEM) { "localePersistenceTag 必须是已支持的显式语言" }
        prepareOnboarding()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var previous: MainActivity? = null
            scenario.onActivity {
                if (resourceLanguage(it) != targetLanguage) previous = it
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(localeTag))
            }
            awaitLanguage(scenario, previous)
            scenario.onActivity { assertEquals(expectedLanguageTitle(), it.getString(R.string.language)) }
        }
    }

    @Test
    fun verifyJapaneseAfterRestart() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("localePersistencePhase") == "check")
        require(targetLanguage != AppLanguage.SYSTEM) { "localePersistenceTag 必须是已支持的显式语言" }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitLanguage(scenario)
            scenario.onActivity {
                assertEquals(LocaleListCompat.forLanguageTags(localeTag).toLanguageTags(),
                    AppCompatDelegate.getApplicationLocales().toLanguageTags())
                assertEquals(expectedLanguageTitle(), it.getString(R.string.language))
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            }
        }
    }

    private fun prepareOnboarding() {
        val app = ApplicationProvider.getApplicationContext<LiZhangApplication>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            app.appContainer.onboardingRepository.complete()
            app.appContainer.onboardingRepository.completeFeatureGuide()
        }
        // setup 与 check 之间会强制停止进程，显式等待 apply 的偏好写盘，避免引导状态丢失。
        assertTrue("测试引导偏好已写盘", app.getSharedPreferences(
            SharedPreferencesOnboardingRepository.FILE_NAME, android.content.Context.MODE_PRIVATE,
        ).edit().commit())
    }

    private fun awaitLanguage(scenario: ActivityScenario<MainActivity>, previous: MainActivity? = null) {
        val deadline = android.os.SystemClock.uptimeMillis() + 8000
        var ready = false
        while (!ready && android.os.SystemClock.uptimeMillis() < deadline) {
            // 主线程空闲不代表系统配置已经派发，等待实际目标 Activity 的资源与导航。
            runCatching { scenario.onActivity {
                ready = it !== previous && resourceLanguage(it) == targetLanguage && currentAppLanguage() == targetLanguage &&
                    it.appearanceHost.isNavigationReady && it.hasWindowFocus() && it.appearanceState.snapshot == null
            } }
            if (!ready) android.os.SystemClock.sleep(30)
        }
        assertTrue("目标语言 $localeTag 的 Activity、资源、导航与快照已经就绪", ready)
    }

    private fun resourceLanguage(activity: MainActivity): AppLanguage =
        AppLanguage.fromLanguageTag(activity.resources.configuration.locales[0].toLanguageTag())

    private fun expectedLanguageTitle(): String = when (targetLanguage) {
        AppLanguage.ZH_CN -> "语言"
        AppLanguage.ZH_HANT -> "語言"
        AppLanguage.EN -> "Language"
        AppLanguage.JA -> "言語"
        AppLanguage.KO -> "언어"
        AppLanguage.ES -> "Idioma"
        AppLanguage.FR -> "Langue"
        AppLanguage.SYSTEM -> error("冷启动测试需要显式语言")
    }
}
