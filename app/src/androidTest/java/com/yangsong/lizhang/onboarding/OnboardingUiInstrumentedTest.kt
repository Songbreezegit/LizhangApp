package com.yangsong.lizhang.onboarding

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.uiautomator.UiDevice
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.R
import com.yangsong.lizhang.data.preferences.SharedPreferencesOnboardingRepository
import com.yangsong.lizhang.domain.onboarding.*
import com.yangsong.lizhang.ui.onboarding.*
import com.yangsong.lizhang.ui.screen.HomeContent
import com.yangsong.lizhang.ui.screen.ContactsContent
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class OnboardingUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val localizedContext = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
        setLocale(Locale.ENGLISH)
    })

    // ComponentActivity 不参与 AppCompat 的语言重建，组件测试显式提供资源环境。
    private fun setContent(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalContext provides localizedContext,
            LocalConfiguration provides localizedContext.resources.configuration, content = content)
    }

    private fun next() {
        compose.onNodeWithTag("引导下一步").performClick()
        compose.waitForIdle()
    }

    @Test fun 三页翻页返回及开始使用保存状态() {
        val preferences = context.createDeviceProtectedStorageContext().getSharedPreferences(SharedPreferencesOnboardingRepository.FILE_NAME, 0)
        preferences.edit().clear().commit()
        val repository = SharedPreferencesOnboardingRepository(context.createDeviceProtectedStorageContext(), ExistingInstallationEvidence())
        val viewModel = OnboardingViewModel(repository)
        setContent { LiZhangTheme { OnboardingScreen(OnboardingMode.FIRST_LAUNCH, { viewModel.finish(OnboardingMode.FIRST_LAUNCH) }) } }
        compose.onNodeWithTag("引导页面1").assertIsDisplayed()
        next()
        compose.onNodeWithTag("引导页面2").assertIsDisplayed()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.onNodeWithTag("引导页面1").assertIsDisplayed()
        assertFalse(repository.state.value.completed)
        next(); next()
        compose.onNodeWithTag("引导页面3").assertIsDisplayed()
        compose.onNodeWithTag("引导跳过").assertDoesNotExist()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.onNodeWithTag("引导页面2").assertIsDisplayed()
        next()
        compose.onNodeWithTag("引导完成").performClick()
        assertTrue(repository.state.value.completed)
        assertTrue(SharedPreferencesOnboardingRepository(context.createDeviceProtectedStorageContext(), ExistingInstallationEvidence()).state.value.completed)
        preferences.edit().clear().commit()
    }

    @Test fun 第一页跳过和第三页手动完成各自正确回调() {
        val mode = mutableStateOf(OnboardingMode.FIRST_LAUNCH)
        var skipped = false
        var reviewed = false
        setContent { key(mode.value) { LiZhangTheme { OnboardingScreen(mode.value, {
            if (mode.value == OnboardingMode.FIRST_LAUNCH) skipped = true else reviewed = true
        }) } } }
        compose.onNodeWithTag("引导跳过").performClick()
        assertTrue(skipped)
        compose.runOnIdle { mode.value = OnboardingMode.REVIEW }
        next(); next()
        compose.onNodeWithText(localizedContext.getString(R.string.onboarding_done)).assertIsDisplayed().performClick()
        assertTrue(reviewed)
    }

    @Test fun 四语言深浅色三页均正常加载且切换后不残留旧文案() {
        val variant = mutableStateOf("zh" to false)
        setContent {
            val localized = remember(variant.value.first) { context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(variant.value.first))
            }) }
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration) {
                key(variant.value) { LiZhangTheme(darkTheme = variant.value.second) { OnboardingScreen(OnboardingMode.REVIEW, {}) } }
            }
        }
        for (tag in listOf("zh", "en", "ja", "ko")) for (dark in listOf(false, true)) {
            compose.runOnIdle { variant.value = tag to dark }
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
            compose.onNodeWithText(resources.getString(R.string.onboarding_intro_title)).assertIsDisplayed()
            next()
            compose.onNodeWithText(resources.getString(R.string.onboarding_record_title)).assertIsDisplayed()
            next()
            compose.onNodeWithText(resources.getString(R.string.onboarding_privacy_title)).assertIsDisplayed()
            compose.onNodeWithText(resources.getString(R.string.onboarding_done)).assertIsDisplayed()
        }
    }

    @Test fun 空首页不再生成独立提示且原记账入口保持可点击() {
        var navigated = false
        setContent { LiZhangTheme {
            HomeContent(HomeUiState(isLoading = false), { navigated = true })
        } }
        compose.onNodeWithText(localizedContext.getString(R.string.home_record_hint)).assertDoesNotExist()
        compose.onNodeWithText(localizedContext.getString(R.string.action_add_gift)).performClick()
        assertTrue(navigated)
    }

    @Test fun 空联系人页没有第二条独立提示且原添加菜单保持可点击() {
        setContent { LiZhangTheme {
            ContactsContent(ContactsUiState(isLoading = false), {}, {}, {}, {})
        } }
        compose.onNodeWithText(localizedContext.getString(R.string.contacts_first_hint)).assertDoesNotExist()
        compose.onNodeWithTag("联系人添加菜单").performClick()
        compose.onNodeWithText(localizedContext.getString(R.string.contact_add_manual)).assertIsDisplayed()
    }
}
