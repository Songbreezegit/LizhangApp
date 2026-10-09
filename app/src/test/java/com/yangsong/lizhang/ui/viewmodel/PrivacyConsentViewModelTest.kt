package com.yangsong.lizhang.ui.viewmodel

import com.yangsong.lizhang.domain.legal.*
import com.yangsong.lizhang.domain.privacy.PrivacyConsentState
import com.yangsong.lizhang.domain.repository.LegalDocumentRepository
import com.yangsong.lizhang.domain.repository.PrivacyConsentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyConsentViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val documents = LegalDocumentRepository { type -> document(type) }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `创建和重建不会默认同意`() {
        val repository = ConsentRepository()
        repeat(2) {
            val viewModel = PrivacyConsentViewModel(repository, documents)
            assertFalse(viewModel.state.value.canProcessPersonalData)
            assertFalse(viewModel.documents.value.canAgree)
            assertFalse(viewModel.accept())
            assertFalse(viewModel.saveFailed.value)
        }
        assertEquals(0, repository.accepts)
    }

    @Test fun `两份全文加载成功后明确点击同意并保存成功才放行`() = runTest(dispatcher) {
        val repository = ConsentRepository()
        val viewModel = PrivacyConsentViewModel(repository, documents)
        viewModel.loadDocument(LegalDocumentType.PRIVACY)
        viewModel.loadDocument(LegalDocumentType.TERMS)
        assertFalse(viewModel.accept())
        advanceUntilIdle()
        assertTrue(viewModel.documents.value.canAgree)
        assertEquals(0, repository.accepts)
        assertTrue(viewModel.accept())
        assertTrue(viewModel.state.value.canProcessPersonalData)
        assertEquals(LegalPolicy.CURRENT_VERSION, viewModel.state.value.acceptedVersion)
        assertTrue(PrivacyConsentViewModel(repository, documents).state.value.canProcessPersonalData)
    }

    @Test fun `只加载一份全文不能直接调用同意`() = runTest(dispatcher) {
        for (type in listOf(LegalDocumentType.PRIVACY, LegalDocumentType.TERMS)) {
            val repository = ConsentRepository()
            val viewModel = PrivacyConsentViewModel(repository, documents)
            viewModel.loadDocument(type)
            advanceUntilIdle()
            assertFalse(viewModel.accept())
            assertFalse(viewModel.documents.value.canAgree)
            assertEquals(0, repository.accepts)
        }
    }

    @Test fun `任一全文失败禁止确认且重试成功不会自动同意`() = runTest(dispatcher) {
        for (failedType in listOf(LegalDocumentType.PRIVACY, LegalDocumentType.TERMS)) {
            var failing = true
            val repository = ConsentRepository()
            val viewModel = PrivacyConsentViewModel(repository, LegalDocumentRepository { type ->
                if (type == failedType && failing) error("合成读取失败") else document(type)
            })
            viewModel.loadDocument(LegalDocumentType.PRIVACY)
            viewModel.loadDocument(LegalDocumentType.TERMS)
            advanceUntilIdle()
            assertTrue(viewModel.documents.value.forType(failedType).failed)
            assertFalse(viewModel.accept())
            assertEquals(0, repository.accepts)
            failing = false
            viewModel.loadDocument(failedType, retry = true)
            assertFalse(viewModel.documents.value.canAgree)
            assertFalse(viewModel.accept())
            advanceUntilIdle()
            assertTrue(viewModel.documents.value.canAgree)
            assertEquals(0, repository.accepts)
            assertTrue(viewModel.accept())
        }
    }

    @Test fun `重新读取已成功文档期间旧成功状态不能继续确认`() = runTest(dispatcher) {
        val repository = ConsentRepository()
        val viewModel = PrivacyConsentViewModel(repository, documents)
        viewModel.loadDocument(LegalDocumentType.PRIVACY)
        viewModel.loadDocument(LegalDocumentType.TERMS)
        advanceUntilIdle()
        assertTrue(viewModel.documents.value.canAgree)
        viewModel.loadDocument(LegalDocumentType.TERMS, retry = true)
        assertFalse(viewModel.accept())
        assertFalse(viewModel.documents.value.canAgree)
        assertEquals(0, repository.accepts)
        advanceUntilIdle()
        assertTrue(viewModel.documents.value.canAgree)
    }

    @Test fun `空正文与错误文档类型不能替代完整协议`() = runTest(dispatcher) {
        for (invalid in listOf(LegalDocument(LegalDocumentType.TERMS, emptyList()),
            LegalDocument(LegalDocumentType.TERMS, listOf(LegalDocumentSection(null, " "))),
            document(LegalDocumentType.HELP))) {
            val repository = ConsentRepository()
            val viewModel = PrivacyConsentViewModel(repository, LegalDocumentRepository { type ->
                if (type == LegalDocumentType.TERMS) invalid else document(type)
            })
            viewModel.loadDocument(LegalDocumentType.PRIVACY)
            viewModel.loadDocument(LegalDocumentType.TERMS)
            advanceUntilIdle()
            assertTrue(viewModel.documents.value.terms.failed)
            assertFalse(viewModel.accept())
            assertEquals(0, repository.accepts)
        }
    }

    @Test fun `加载中重复点击与返回阅读不会重复加载已就绪全文`() = runTest(dispatcher) {
        var reads = 0
        val viewModel = PrivacyConsentViewModel(ConsentRepository(), LegalDocumentRepository {
            reads++
            document(it)
        })
        repeat(3) { viewModel.loadDocument(LegalDocumentType.PRIVACY) }
        advanceUntilIdle()
        viewModel.loadDocument(LegalDocumentType.PRIVACY)
        advanceUntilIdle()
        assertEquals(1, reads)
    }

    @Test fun `拒绝保留未同意状态且重建不放行`() {
        val repository = ConsentRepository()
        PrivacyConsentViewModel(repository, documents).decline()
        assertEquals(1, repository.declines)
        assertEquals(LegalPolicy.CURRENT_VERSION, repository.state.value.declinedVersion)
        assertFalse(PrivacyConsentViewModel(repository, documents).state.value.canProcessPersonalData)
        assertEquals(0, repository.accepts)
    }

    @Test fun `写盘失败显示错误并禁止进入`() = runTest(dispatcher) {
        val repository = ConsentRepository(writeSucceeds = false)
        val viewModel = PrivacyConsentViewModel(repository, documents)
        viewModel.loadDocument(LegalDocumentType.PRIVACY)
        viewModel.loadDocument(LegalDocumentType.TERMS)
        advanceUntilIdle()
        assertFalse(viewModel.accept())
        assertTrue(viewModel.saveFailed.value)
        assertFalse(viewModel.state.value.canProcessPersonalData)
        repository.writeSucceeds = true
        assertTrue(viewModel.accept())
        assertFalse(viewModel.saveFailed.value)
    }
}

private fun document(type: LegalDocumentType) = LegalDocument(type,
    listOf(LegalDocumentSection("合成全文", "测试专用完整说明，不访问业务数据。")))

private class ConsentRepository(var writeSucceeds: Boolean = true) : PrivacyConsentRepository {
    override val state = MutableStateFlow(PrivacyConsentState())
    var accepts = 0
    var declines = 0
    override fun acceptCurrentPolicy(documents: LoadedConsentDocuments): Boolean {
        if (!documents.matchesPolicy(LegalPolicy.CURRENT_VERSION)) return false
        accepts++
        if (writeSucceeds) state.value = PrivacyConsentState(acceptedVersion = LegalPolicy.CURRENT_VERSION)
        return writeSucceeds
    }
    override fun declineCurrentPolicy(): Boolean {
        declines++
        if (writeSucceeds) state.value = state.value.copy(declinedVersion = LegalPolicy.CURRENT_VERSION)
        return writeSucceeds
    }
}
