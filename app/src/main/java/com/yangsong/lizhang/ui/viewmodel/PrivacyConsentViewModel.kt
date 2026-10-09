package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.domain.legal.LegalDocumentType
import com.yangsong.lizhang.domain.legal.LoadedConsentDocuments
import com.yangsong.lizhang.domain.repository.LegalDocumentRepository
import com.yangsong.lizhang.domain.repository.PrivacyConsentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PrivacyNoticeDocumentState(
    val privacy: LegalDocumentUiState = LegalDocumentUiState(isLoading = false),
    val terms: LegalDocumentUiState = LegalDocumentUiState(isLoading = false),
) {
    fun forType(type: LegalDocumentType): LegalDocumentUiState = when (type) {
        LegalDocumentType.PRIVACY -> privacy
        LegalDocumentType.TERMS -> terms
        LegalDocumentType.HELP -> error("首次告知仅需隐私政策与用户协议")
    }

    val loadedDocuments: LoadedConsentDocuments?
        get() = if (privacy.isLoading || terms.isLoading || privacy.failed || terms.failed) null
            else LoadedConsentDocuments.verify(privacy.document, terms.document)
    val canAgree: Boolean get() = loadedDocuments != null
}

/** 文档加载与确认由同一个状态源管理；任何直接调用同意都必须先通过全文加载校验。 */
class PrivacyConsentViewModel(
    private val repository: PrivacyConsentRepository,
    private val legalDocuments: LegalDocumentRepository,
) : ViewModel() {
    val state = repository.state
    private val mutableSaveFailed = MutableStateFlow(false)
    val saveFailed = mutableSaveFailed.asStateFlow()
    private val mutableDocuments = MutableStateFlow(PrivacyNoticeDocumentState())
    val documents = mutableDocuments.asStateFlow()
    private val loads = mutableMapOf<LegalDocumentType, Job>()

    fun loadDocument(type: LegalDocumentType, retry: Boolean = false) {
        val previous = mutableDocuments.value.forType(type)
        if (loads[type]?.isActive == true || (!retry && previous.document != null && !previous.failed)) return
        updateDocument(type, LegalDocumentUiState())
        mutableSaveFailed.value = false
        loads[type] = viewModelScope.launch {
            try {
                val document = legalDocuments.load(type)
                require(document.type == type && document.sections.isNotEmpty() &&
                    document.sections.all { it.body.isNotBlank() }) { "离线文档类型或正文无效" }
                updateDocument(type, LegalDocumentUiState(isLoading = false, document = document))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateDocument(type, LegalDocumentUiState(isLoading = false, failed = true))
            }
        }
    }

    fun accept(): Boolean {
        val loaded = mutableDocuments.value.loadedDocuments ?: return false
        return repository.acceptCurrentPolicy(loaded).also { mutableSaveFailed.value = !it }
    }

    fun decline() { repository.declineCurrentPolicy() }

    private fun updateDocument(type: LegalDocumentType, document: LegalDocumentUiState) {
        mutableDocuments.value = when (type) {
            LegalDocumentType.PRIVACY -> mutableDocuments.value.copy(privacy = document)
            LegalDocumentType.TERMS -> mutableDocuments.value.copy(terms = document)
            LegalDocumentType.HELP -> error("首次告知仅需隐私政策与用户协议")
        }
    }

    companion object {
        fun factory(repository: PrivacyConsentRepository, legalDocuments: LegalDocumentRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(PrivacyConsentViewModel::class.java))
                    return PrivacyConsentViewModel(repository, legalDocuments) as T
                }
            }
    }
}
