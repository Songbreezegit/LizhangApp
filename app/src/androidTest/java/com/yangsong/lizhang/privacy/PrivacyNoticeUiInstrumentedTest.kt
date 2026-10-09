package com.yangsong.lizhang.privacy

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.legal.*
import com.yangsong.lizhang.domain.privacy.PrivacyConsentState
import com.yangsong.lizhang.domain.repository.LegalDocumentRepository
import com.yangsong.lizhang.domain.repository.PrivacyConsentRepository
import com.yangsong.lizhang.ui.privacy.PrivacyNoticeGate
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.PrivacyConsentViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 独立视图与合成文档不读通讯录或账本；设备执行状态由验收报告另列。 */
class PrivacyNoticeUiInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val documents = object : LegalDocumentRepository {
        override suspend fun load(type: LegalDocumentType) = LegalDocument(type,
            listOf(LegalDocumentSection("合成测试正文", "本地数据处理合成说明，仅用于测试。")))
    }

    @Test fun 没有默认勾选离线阅读与拒绝返回不会同意只有明确按钮放行() {
        val repository = UiConsentRepository()
        var accepted = 0
        val viewModel = PrivacyConsentViewModel(repository, documents)
        compose.setContent { LiZhangTheme {
            PrivacyNoticeGate(viewModel, { accepted++ }, {})
        } }
        compose.onNodeWithTag("首次隐私告知").assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertCountEquals(0)
        assertEquals(0, repository.accepts)
        compose.onNodeWithTag("隐私告知同意").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("告知隐私政策").performScrollTo().performClick()
        compose.onNodeWithTag("legal_document_privacy").assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_back)).performClick()
        compose.onNodeWithTag("隐私告知同意").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("隐私告知拒绝").performScrollTo().performClick()
        compose.onNodeWithTag("隐私拒绝说明").assertIsDisplayed()
        assertFalse(repository.state.value.canProcessPersonalData)
        compose.onNodeWithTag("返回隐私告知").performScrollTo().performClick()
        assertEquals(0, accepted)
        assertEquals(0, repository.accepts)
        readDocument("告知用户协议", viewModel, LegalDocumentType.TERMS)
        compose.onNodeWithTag("隐私告知同意").performScrollTo().performClick()
        assertEquals(1, accepted)
        assertTrue(repository.state.value.canProcessPersonalData)
    }

    @Test fun 拒绝后退出回调明确且不会默认同意() {
        val repository = UiConsentRepository()
        var exited = 0
        val viewModel = PrivacyConsentViewModel(repository, documents)
        compose.setContent { LiZhangTheme {
            PrivacyNoticeGate(viewModel, {}, { exited++ })
        } }
        compose.onNodeWithTag("隐私告知拒绝").performScrollTo().performClick()
        compose.onNodeWithTag("拒绝退出应用").performScrollTo().performClick()
        assertEquals(1, exited)
        assertEquals(0, repository.accepts)
        assertFalse(repository.state.value.canProcessPersonalData)
    }

    @Test fun 深浅色与两倍字号均能滚动阅读和作出选择() {
        val dark = mutableStateOf(false)
        val repository = UiConsentRepository()
        val viewModel = PrivacyConsentViewModel(repository, documents)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                LiZhangTheme(dark.value) { PrivacyNoticeGate(viewModel, {}, {}) }
            }
        }
        for (mode in listOf(false, true, false)) {
            compose.runOnIdle { dark.value = mode }
            compose.onNodeWithTag("告知用户协议").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag("legal_document_terms").assertIsDisplayed()
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_back)).performClick()
            compose.onNodeWithTag("隐私告知同意").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("隐私告知拒绝").performScrollTo().assertIsDisplayed()
        }
        assertEquals(0, repository.accepts)
        assertFalse(repository.state.value.canProcessPersonalData)
    }

    @Test fun 保存失败显示错误且不调用业务进入回调() {
        val repository = UiConsentRepository(writeSucceeds = false)
        var accepted = 0
        val viewModel = PrivacyConsentViewModel(repository, documents)
        compose.setContent { LiZhangTheme { PrivacyNoticeGate(viewModel, { accepted++ }, {}) } }
        readDocument("告知隐私政策", viewModel, LegalDocumentType.PRIVACY)
        readDocument("告知用户协议", viewModel, LegalDocumentType.TERMS)
        compose.onNodeWithTag("隐私告知同意").performScrollTo().performClick()
        compose.onNodeWithTag("隐私确认保存失败").performScrollTo().assertIsDisplayed()
        assertEquals(0, accepted)
        assertFalse(repository.state.value.canProcessPersonalData)
    }

    @Test fun 完整正文失败可重试返回与退出且不能保存同意() {
        var failing = true
        val repository = UiConsentRepository()
        val viewModel = PrivacyConsentViewModel(repository, LegalDocumentRepository { type ->
            if (type == LegalDocumentType.PRIVACY && failing) error("合成离线读取失败")
            documents.load(type)
        })
        var accepted = 0
        var exited = 0
        compose.setContent { LiZhangTheme { PrivacyNoticeGate(viewModel, { accepted++ }, { exited++ }) } }
        compose.onNodeWithTag("告知隐私政策").performScrollTo().performClick()
        compose.waitUntil(5_000) { viewModel.documents.value.privacy.failed }
        compose.onNodeWithTag("legal_read_failed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("legal_retry").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertFalse(viewModel.accept()) }
        assertEquals(0, repository.accepts)
        compose.runOnIdle { failing = false }
        compose.onNodeWithTag("legal_retry").performClick()
        compose.waitUntil(5_000) { viewModel.documents.value.privacy.document != null }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_back)).performClick()
        compose.onNodeWithTag("隐私告知同意").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("隐私告知拒绝").performScrollTo().performClick()
        compose.onNodeWithTag("拒绝退出应用").performScrollTo().performClick()
        assertEquals(1, exited)
        assertEquals(0, accepted)
        assertFalse(repository.state.value.canProcessPersonalData)
    }

    @Test fun 协议加载中返回告知仍禁止同意加载完成后不自动确认() {
        val pending = CompletableDeferred<LegalDocument>()
        val repository = UiConsentRepository()
        val viewModel = PrivacyConsentViewModel(repository, LegalDocumentRepository { type ->
            if (type == LegalDocumentType.TERMS) pending.await() else documents.load(type)
        })
        var accepted = 0
        compose.setContent { LiZhangTheme { PrivacyNoticeGate(viewModel, { accepted++ }, {}) } }
        readDocument("告知隐私政策", viewModel, LegalDocumentType.PRIVACY)
        compose.onNodeWithTag("告知用户协议").performScrollTo().performClick()
        compose.onNodeWithTag("legal_loading").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_back)).performClick()
        compose.onNodeWithTag("隐私告知同意").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertFalse(viewModel.accept()) }
        compose.runOnIdle {
            pending.complete(LegalDocument(LegalDocumentType.TERMS,
                listOf(LegalDocumentSection("合成协议全文", "测试完整说明"))))
        }
        compose.waitUntil(5_000) { viewModel.documents.value.canAgree }
        compose.onNodeWithTag("隐私告知同意").performScrollTo().assertIsEnabled()
        assertEquals(0, repository.accepts)
        assertEquals(0, accepted)
    }

    private fun readDocument(tag: String, viewModel: PrivacyConsentViewModel, type: LegalDocumentType) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitUntil(5_000) { viewModel.documents.value.forType(type).document != null }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_back)).performClick()
    }
}

private class UiConsentRepository(private val writeSucceeds: Boolean = true) : PrivacyConsentRepository {
    override val state = MutableStateFlow(PrivacyConsentState())
    var accepts = 0
    override fun acceptCurrentPolicy(documents: LoadedConsentDocuments): Boolean {
        if (!documents.matchesPolicy(LegalPolicy.CURRENT_VERSION)) return false
        accepts++
        if (writeSucceeds) state.value = PrivacyConsentState(acceptedVersion = LegalPolicy.CURRENT_VERSION)
        return writeSucceeds
    }
    override fun declineCurrentPolicy(): Boolean {
        state.value = state.value.copy(declinedVersion = LegalPolicy.CURRENT_VERSION)
        return true
    }
}
