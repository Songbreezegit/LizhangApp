package com.yangsong.lizhang.privacy

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.legal.*
import com.yangsong.lizhang.domain.privacy.PrivacyConsentState
import com.yangsong.lizhang.domain.repository.LegalDocumentRepository
import com.yangsong.lizhang.domain.repository.PrivacyConsentRepository
import com.yangsong.lizhang.ui.privacy.PrivacyNoticeGate
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.PrivacyConsentViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 独立视图与合成文档不读通讯录或账本；设备执行状态由验收报告另列。 */
class PrivacyNoticeUiInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val documents = LegalDocumentRepository { type ->
        LegalDocument(type, (1..6).map { index ->
            LegalDocumentSection("${type.name}合成章节$index", "${type.name}合成完整正文$index。仅用于测试。")
        })
    }
    private fun waitForDocuments() = compose.waitUntil(5000) {
        runCatching { compose.onNodeWithTag("隐私告知同意").assertIsEnabled(); true }.getOrDefault(false)
    }

    @Test fun 摘要无默认同意且两个链接离线打开全文返回后主动同意才放行() {
        val repository = UiConsentRepository()
        val viewModel = PrivacyConsentViewModel(repository)
        var accepted = 0
        var exited = 0
        compose.setContent { LiZhangTheme {
            PrivacyNoticeGate(viewModel, documents, { accepted++ }, { exited++ })
        } }
        waitForDocuments()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertCountEquals(0)
        compose.onNodeWithText(compose.activity.getString(R.string.privacy_notice_intro)).assertExists()
        compose.onNodeWithText("PRIVACY合成完整正文1。仅用于测试。").assertDoesNotExist()
        for (type in listOf(LegalDocumentType.PRIVACY, LegalDocumentType.TERMS)) {
            val tag = if (type == LegalDocumentType.PRIVACY) "告知阅读隐私政策" else "告知阅读用户协议"
            compose.onNodeWithTag(tag).performScrollTo().performClick()
            compose.onNodeWithTag("legal_open_browser").assertDoesNotExist()
            for (index in 1..6) {
                compose.onNodeWithText("${type.name}合成完整正文$index。仅用于测试。").performScrollTo().assertIsDisplayed()
            }
            if (type == LegalDocumentType.PRIVACY) {
                compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_back)).performClick()
            } else {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    compose.activity.onBackPressedDispatcher.onBackPressed()
                }
            }
            compose.onNodeWithTag("隐私告知同意").assertIsEnabled()
        }
        assertEquals(0, repository.accepts)
        assertEquals(0, accepted)
        assertEquals(0, exited)
        compose.onNodeWithTag("隐私告知同意").performClick()
        assertEquals(1, accepted)
        assertTrue(repository.state.value.canProcessPersonalData)
    }

    @Test fun 拒绝按钮直接退出不出现拒绝说明页且不默认同意() {
        val repository = UiConsentRepository()
        val viewModel = PrivacyConsentViewModel(repository)
        var exited = 0
        compose.setContent { LiZhangTheme {
            PrivacyNoticeGate(viewModel, documents, {}, { exited++ })
        } }
        compose.onNodeWithTag("隐私告知拒绝").assertIsDisplayed().performClick()
        compose.onNodeWithTag("隐私拒绝说明").assertDoesNotExist()
        assertEquals(1, exited)
        assertEquals(1, repository.declines)
        assertEquals(0, repository.accepts)
        assertFalse(repository.state.value.canProcessPersonalData)
    }

    @Test fun 顶栏返回与系统返回均直接拒绝退出且不放行业务() {
        val repository = UiConsentRepository()
        val viewModel = PrivacyConsentViewModel(repository)
        var exited = 0
        compose.setContent { LiZhangTheme {
            PrivacyNoticeGate(viewModel, documents, {}, { exited++ })
        } }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_back)).performClick()
        assertEquals(1, exited)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
        assertEquals(2, exited)
        assertEquals(2, repository.declines)
        assertEquals(0, repository.accepts)
        assertFalse(repository.state.value.canProcessPersonalData)
        compose.onNodeWithTag("隐私拒绝说明").assertDoesNotExist()
    }

    @Test fun 小屏深浅色两倍字号与保存失败仍能在卡片读到末尾且选择始终可见() {
        val dark = mutableStateOf(false)
        val repository = UiConsentRepository(writeSucceeds = false)
        val viewModel = PrivacyConsentViewModel(repository)
        compose.setContent {
            val density = LocalDensity.current
            Box(Modifier.width(320.dp).height(640.dp)) {
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    LiZhangTheme(dark.value) { PrivacyNoticeGate(viewModel, documents, {}, {}) }
                }
            }
        }
        waitForDocuments()
        compose.onNodeWithTag("隐私告知同意").performClick()
        for (mode in listOf(false, true, false)) {
            compose.runOnIdle { dark.value = mode }
            val card = compose.onNodeWithTag("首次隐私告知阅读卡片").fetchSemanticsNode().boundsInRoot
            assertTrue("错误提示与两倍字号仍保留可阅读高度", card.height > 48f * compose.activity.resources.displayMetrics.density)
            compose.onNodeWithTag("告知阅读用户协议").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("隐私告知同意").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithTag("隐私告知拒绝").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithTag("隐私确认保存失败").performScrollTo().assertIsDisplayed()
                .assertTextEquals(compose.activity.getString(R.string.privacy_notice_save_failed))
            compose.onNodeWithTag("隐私告知同意").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithTag("隐私告知拒绝").assertIsDisplayed().assertIsEnabled()
        }
        assertEquals(1, repository.accepts)
        assertFalse(repository.state.value.canProcessPersonalData)
    }

    @Test fun 全文失败可重试且返回摘要后仍须主动同意() {
        val repository = UiConsentRepository()
        val viewModel = PrivacyConsentViewModel(repository)
        var accepted = 0
        var failTerms = true
        val retryDocuments = LegalDocumentRepository { type ->
            if (type == LegalDocumentType.TERMS && failTerms) error("合成读取失败")
            LegalDocument(type, listOf(LegalDocumentSection("合成章节", "${type.name}完整合成正文")))
        }
        compose.setContent { LiZhangTheme {
            PrivacyNoticeGate(viewModel, retryDocuments, { accepted++ }, {})
        } }
        compose.onNodeWithTag("隐私告知同意").assertIsEnabled()
        compose.onNodeWithTag("告知阅读用户协议").performScrollTo().performClick()
        val retry = compose.activity.getString(R.string.legal_retry)
        compose.waitUntil(5000) { compose.onAllNodesWithText(retry).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, repository.accepts)
        assertEquals(0, accepted)
        compose.runOnIdle { failTerms = false }
        compose.onNodeWithText(retry).performScrollTo().performClick()
        compose.onNodeWithText("TERMS完整合成正文").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_back)).performClick()
        compose.onNodeWithTag("隐私告知同意").assertIsEnabled().performClick()
        assertEquals(1, accepted)
        assertEquals(1, repository.accepts)
    }

    @Test fun 摘要不预读全文也不自动同意() {
        val repository = UiConsentRepository()
        val viewModel = PrivacyConsentViewModel(repository)
        var reads = 0
        val unavailable = LegalDocumentRepository {
            reads++
            error("合成读取失败")
        }
        compose.setContent { LiZhangTheme { PrivacyNoticeGate(viewModel, unavailable, {}, {}) } }
        compose.onNodeWithTag("隐私告知同意").assertIsEnabled()
        compose.onNodeWithTag("隐私告知拒绝").assertIsEnabled()
        compose.runOnIdle {
            assertEquals(0, reads)
            assertEquals(0, repository.accepts)
            assertFalse(repository.state.value.canProcessPersonalData)
        }
        compose.onNodeWithTag("隐私告知同意").performClick()
        assertEquals(1, repository.accepts)
    }

    @Test fun 保存失败显示错误且不调用业务进入回调() {
        val repository = UiConsentRepository(writeSucceeds = false)
        val viewModel = PrivacyConsentViewModel(repository)
        var accepted = 0
        compose.setContent { LiZhangTheme {
            PrivacyNoticeGate(viewModel, documents, { accepted++ }, {})
        } }
        waitForDocuments()
        compose.onNodeWithTag("隐私告知同意").performClick()
        compose.onNodeWithTag("隐私确认保存失败").assertIsDisplayed()
        assertEquals(0, accepted)
        assertFalse(repository.state.value.canProcessPersonalData)
    }
}

private class UiConsentRepository(private val writeSucceeds: Boolean = true) : PrivacyConsentRepository {
    override val state = MutableStateFlow(PrivacyConsentState())
    var accepts = 0
    var declines = 0
    override fun acceptCurrentPolicy(): Boolean {
        accepts++
        if (writeSucceeds) state.value = PrivacyConsentState(acceptedVersion = LegalPolicy.CURRENT_VERSION)
        return writeSucceeds
    }
    override fun declineCurrentPolicy(): Boolean {
        declines++
        state.value = state.value.copy(declinedVersion = LegalPolicy.CURRENT_VERSION)
        return true
    }
}
