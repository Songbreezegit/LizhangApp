package com.yangsong.lizhang.ui.viewmodel

import com.yangsong.lizhang.domain.legal.LegalDocumentParser
import com.yangsong.lizhang.domain.legal.LegalDocumentType
import com.yangsong.lizhang.domain.repository.LegalDocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class LegalDocumentViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `文档加载只调用离线仓库并保留全文`() = runTest(dispatcher) {
        val document = LegalDocumentParser.parse(LegalDocumentType.TERMS, "# 协议\n## 完整说明\n替换恢复与密码遗失说明")
        var requested: LegalDocumentType? = null
        val viewModel = LegalDocumentViewModel(LegalDocumentRepository { requested = it; document }, LegalDocumentType.TERMS)
        assertTrue(viewModel.state.value.isLoading)
        advanceUntilIdle()
        assertEquals(LegalDocumentType.TERMS, requested)
        assertEquals(document, viewModel.state.value.document)
        assertFalse(viewModel.state.value.failed)
    }

    @Test fun `正文读取失败不显示成功摘要重试后读取完整正文`() = runTest(dispatcher) {
        var fail = true
        val document = LegalDocumentParser.parse(LegalDocumentType.PRIVACY, "# 隐私\n完整正文")
        val viewModel = LegalDocumentViewModel(LegalDocumentRepository {
            if (fail) error("测试读取失败") else document
        }, LegalDocumentType.PRIVACY)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.failed)
        assertNull(viewModel.state.value.document)
        fail = false
        viewModel.reload()
        advanceUntilIdle()
        assertEquals(document, viewModel.state.value.document)
        assertFalse(viewModel.state.value.failed)
    }

    @Test fun `加载中重复点击不会发起重复读取`() = runTest(dispatcher) {
        var reads = 0
        val viewModel = LegalDocumentViewModel(LegalDocumentRepository {
            reads++
            LegalDocumentParser.parse(it, "# 文档\n完整正文")
        }, LegalDocumentType.HELP)
        repeat(3) { viewModel.reload() }
        advanceUntilIdle()
        assertEquals(1, reads)
    }
}
