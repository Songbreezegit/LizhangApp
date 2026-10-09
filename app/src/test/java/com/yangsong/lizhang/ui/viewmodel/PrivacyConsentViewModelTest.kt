package com.yangsong.lizhang.ui.viewmodel

import com.yangsong.lizhang.domain.legal.LegalPolicy
import com.yangsong.lizhang.domain.privacy.PrivacyConsentState
import com.yangsong.lizhang.domain.repository.PrivacyConsentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class PrivacyConsentViewModelTest {
    @Test fun `创建和重建不会默认同意`() {
        val repository = ConsentRepository()
        repeat(2) { assertFalse(PrivacyConsentViewModel(repository).state.value.canProcessPersonalData) }
        assertEquals(0, repository.accepts)
    }

    @Test fun `明确点击同意并保存成功才放行`() {
        val repository = ConsentRepository()
        val viewModel = PrivacyConsentViewModel(repository)
        assertTrue(viewModel.accept())
        assertTrue(viewModel.state.value.canProcessPersonalData)
        assertEquals(LegalPolicy.CURRENT_VERSION, viewModel.state.value.acceptedVersion)
        assertTrue(PrivacyConsentViewModel(repository).state.value.canProcessPersonalData)
    }

    @Test fun `拒绝保留未同意状态且重建不放行`() {
        val repository = ConsentRepository()
        PrivacyConsentViewModel(repository).decline()
        assertEquals(1, repository.declines)
        assertEquals(LegalPolicy.CURRENT_VERSION, repository.state.value.declinedVersion)
        assertFalse(PrivacyConsentViewModel(repository).state.value.canProcessPersonalData)
        assertEquals(0, repository.accepts)
    }

    @Test fun `写盘失败显示错误并禁止进入`() {
        val repository = ConsentRepository(writeSucceeds = false)
        val viewModel = PrivacyConsentViewModel(repository)
        assertFalse(viewModel.accept())
        assertTrue(viewModel.saveFailed.value)
        assertFalse(viewModel.state.value.canProcessPersonalData)
        repository.writeSucceeds = true
        assertTrue(viewModel.accept())
        assertFalse(viewModel.saveFailed.value)
    }
}

private class ConsentRepository(var writeSucceeds: Boolean = true) : PrivacyConsentRepository {
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
        if (writeSucceeds) state.value = state.value.copy(declinedVersion = LegalPolicy.CURRENT_VERSION)
        return writeSucceeds
    }
}
