package com.yangsong.lizhang.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.MainActivity
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.legal.LegalPolicy
import com.yangsong.lizhang.domain.onboarding.FeatureGuideStep
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 必须在全新隔离测试安装中单独运行；不能预置同意来掩盖真实首次告知。 */
class OnboardingEntryInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun waitFor(tag: String) = compose.waitUntil(8000) {
        runCatching { compose.onNodeWithTag(tag).assertIsDisplayed(); true }.getOrDefault(false)
    }

    private fun assertNoOptionalPermissions(app: LiZhangApplication) {
        assertEquals(PackageManager.PERMISSION_DENIED, ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CONTACTS))
        if (Build.VERSION.SDK_INT >= 33) assertEquals(PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS))
        assertFalse(app.appContainer.onboardingRepository.state.value.contactsPermission.requested)
        assertFalse(app.appContainer.onboardingRepository.state.value.notificationsPermission.requested)
    }

    @Test fun 首次拒绝退出后仍告知离线阅读确认启动真实记账引导且重启不重复() {
        val app = ApplicationProvider.getApplicationContext<LiZhangApplication>()
        val container = app.appContainer
        assertFalse("需卸载后全新安装且不能预置隐私同意", container.canProcessPersonalData)
        assertFalse("此验收必须从全新安装开始", container.onboardingRepository.state.value.completed)
        assertFalse(container.isBusinessDatabaseInitialized)
        assertFalse(container.isReminderRepositoryInitialized)
        assertNoOptionalPermissions(app)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor("首次隐私告知")
            compose.onNodeWithTag("首页列表").assertDoesNotExist()
            compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
            compose.onNodeWithTag("隐私告知拒绝").performScrollTo().performClick()
            waitFor("隐私拒绝说明")
            assertFalse(container.canProcessPersonalData)
            assertFalse(container.onboardingRepository.state.value.completed)
            assertFalse(container.isBusinessDatabaseInitialized)
            assertFalse(container.isReminderRepositoryInitialized)
            assertNoOptionalPermissions(app)
            compose.onNodeWithTag("拒绝退出应用").performScrollTo().performClick()
            compose.waitUntil(5000) { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor("首次隐私告知")
            assertFalse(container.canProcessPersonalData)
            for ((entry, document) in listOf("告知隐私政策" to "legal_document_privacy", "告知用户协议" to "legal_document_terms")) {
                compose.onNodeWithTag(entry).performScrollTo().performClick()
                waitFor(document)
                assertFalse(container.canProcessPersonalData)
                assertFalse(container.isBusinessDatabaseInitialized)
                var back = ""
                scenario.onActivity { back = it.getString(R.string.action_back) }
                compose.onNodeWithContentDescription(back).performClick()
                waitFor("首次隐私告知")
            }
            scenario.recreate()
            waitFor("首次隐私告知")
            assertFalse(container.canProcessPersonalData)
            compose.onNodeWithTag("隐私告知同意").performScrollTo().performClick()
            waitFor("首页列表")
            waitFor("功能引导气泡")
            assertTrue(container.canProcessPersonalData)
            assertEquals(LegalPolicy.CURRENT_VERSION, container.privacyConsentRepository.state.value.acceptedVersion)
            assertTrue(container.onboardingRepository.state.value.completed)
            assertEquals(FeatureGuideStep.ADD_RECORD, container.onboardingRepository.state.value.featureGuideStep)
            assertTrue(container.onboardingRepository.state.value.seenPageGuides.isEmpty())
            assertNoOptionalPermissions(app)
            compose.onNodeWithTag("首次隐私告知").assertDoesNotExist()
            compose.onNodeWithTag("引导页面1").assertDoesNotExist()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("首页列表")
            compose.onNodeWithTag("首次隐私告知").assertDoesNotExist()
            assertTrue(container.canProcessPersonalData)
            assertEquals(FeatureGuideStep.ADD_RECORD, container.onboardingRepository.state.value.featureGuideStep)
            assertNoOptionalPermissions(app)
        }
    }
}
