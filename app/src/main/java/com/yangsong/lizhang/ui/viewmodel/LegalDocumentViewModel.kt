package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.BuildConfig
import com.yangsong.lizhang.domain.legal.LegalDocument
import com.yangsong.lizhang.domain.legal.LegalDocumentType
import com.yangsong.lizhang.domain.repository.LegalDocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

data class LegalDocumentUiState(
    val isLoading: Boolean = true,
    val document: LegalDocument? = null,
    val failed: Boolean = false,
    val debugFailure: String? = null,
)

class LegalDocumentViewModel(
    private val repository: LegalDocumentRepository,
    val type: LegalDocumentType,
) : ViewModel() {
    private val _state = MutableStateFlow(LegalDocumentUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null

    init { reload() }

    fun reload() {
        if (loadJob?.isActive == true) return
        _state.value = LegalDocumentUiState()
        loadJob = viewModelScope.launch {
            try {
                _state.value = LegalDocumentUiState(isLoading = false, document = repository.load(type))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // 不显示异常 message、正文或元数据值；Release 不携带诊断详情。
                _state.value = LegalDocumentUiState(isLoading = false, failed = true,
                    debugFailure = if (BuildConfig.DEBUG) "${type.name}: ${error.javaClass.simpleName}" else null)
            }
        }
    }

    companion object {
        fun factory(repository: LegalDocumentRepository, type: LegalDocumentType) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(LegalDocumentViewModel::class.java))
                    return LegalDocumentViewModel(repository, type) as T
                }
            }
    }
}
