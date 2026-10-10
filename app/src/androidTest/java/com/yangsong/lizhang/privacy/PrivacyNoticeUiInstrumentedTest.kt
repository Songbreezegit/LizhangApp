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
import com.yangsong.lizhang.ui.privacy.PrivacyNoticeScreen
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.LegalDocumentUiState
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

    @Test fun 无默认勾选且完整两份正文在同一卡片滚动只有明确同意放行() {
        val repository = UiConsentRepository()
        val viewModel = PrivacyConsentViewModel(repository)
        var accepted = 0
        compose.setContent { LiZhangTheme {
            PrivacyNoticeGate(viewModel, documents, { accepted++ }, {})
        } }
        waitForDocuments()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertCountEquals(0)
        compose.onNodeWithText(compose.activity.getString(R.string.privacy_notice_intro)).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.privacy_notice_candidate)).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.privacy_notice_version, LegalPolicy.CURRENT_VERSION))
            .assertDoesNotExist()
        compose.onNodeWithTag("告知隐私政策").assertDoesNotExist()
        compose.onNodeWithTag("告知用户协议").assertDoesNotExist()
        val agreeBefore = compose.onNodeWithTag("隐私告知同意").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val declineBefore = compose.onNodeWithTag("隐私告知拒绝").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        for (type in listOf(LegalDocumentType.PRIVACY, LegalDocumentType.TERMS)) {
            for (index in 1..6) {
                compose.onNodeWithText("${type.name}合成完整正文$index。仅用于测试。").performScrollTo().assertIsDisplayed()
            }
        }
        assertEquals(agreeBefore, compose.onNodeWithTag("隐私告知同意").fetchSemanticsNode().boundsInRoot)
        assertEquals(declineBefore, compose.onNodeWithTag("隐私告知拒绝").fetchSemanticsNode().boundsInRoot)
        assertEquals(0, repository.accepts)
        assertEquals(0, accepted)
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
            compose.onNodeWithText("TERMS合成完整正文6。仅用于测试。").performScrollTo().assertIsDisplayed()
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

    @Test fun 任一正文加载失败只能重试完整正文成功后才可同意() {
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
        compose.waitUntil(5000) { compose.onAllNodesWithTag("告知重新读取_terms").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("隐私告知同意").assertIsNotEnabled()
        assertEquals(0, repository.accepts)
        compose.runOnIdle { failTerms = false }
        compose.onNodeWithTag("告知重新读取_terms").performScrollTo().performClick()
        waitForDocuments()
        compose.onNodeWithText("TERMS完整合成正文").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("隐私告知同意").performClick()
        assertEquals(1, accepted)
        assertEquals(1, repository.accepts)
    }

    @Test fun 加载中空正文及类型错误均不能使用摘要同意() {
        val state = mutableStateOf(LegalDocumentUiState())
        val terms = LegalDocumentUiState(isLoading = false,
            document = LegalDocument(LegalDocumentType.TERMS, listOf(LegalDocumentSection(null, "合成协议全文"))))
        compose.setContent { LiZhangTheme {
            PrivacyNoticeScreen(privacyState = state.value, termsState = terms, onAgree = {}, onDecline = {})
        } }
        for (invalid in listOf(
            LegalDocumentUiState(),
            LegalDocumentUiState(isLoading = false, document = LegalDocument(LegalDocumentType.PRIVACY, emptyList())),
            LegalDocumentUiState(isLoading = false, document = LegalDocument(LegalDocumentType.PRIVACY,
                listOf(LegalDocumentSection(null, " ")))),
            terms,
        )) {
            compose.runOnIdle { state.value = invalid }
            compose.onNodeWithTag("隐私告知同意").assertIsNotEnabled()
            compose.onNodeWithTag("隐私告知拒绝").assertIsEnabled()
        }
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
