package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GiftRecordDetailUiState(
    val item: GiftRecordWithContact? = null,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteFailed: Boolean = false,
    val isDeleted: Boolean = false,
)

class GiftRecordDetailViewModel(
    recordId: Long,
    private val repository: GiftRecordRepository,
) : ViewModel() {
    private val operationState = MutableStateFlow(GiftRecordDetailUiState())
    private val retrySignal = RetrySignal()

    val uiState: StateFlow<GiftRecordDetailUiState> = retrySignal.flow(
        source = {
            combine(
                repository.observeRecordWithContact(recordId),
                operationState,
            ) { item, operation ->
                operation.copy(
                    item = item,
                    isLoading = false,
                    loadFailed = item == null && !operation.isDeleted,
                )
            }
        },
        onError = { GiftRecordDetailUiState(isLoading = false, loadFailed = true) },
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GiftRecordDetailUiState())

    fun retry() = retrySignal.retry()

    fun delete() {
        val item = uiState.value.item ?: return
        if (uiState.value.isDeleting) return
        viewModelScope.launch {
            operationState.update { it.copy(isDeleting = true, deleteFailed = false) }
            runCatching { repository.delete(item.record) }
                .onSuccess { operationState.update { it.copy(isDeleting = false, isDeleted = true) } }
                .onFailure { operationState.update { it.copy(isDeleting = false, deleteFailed = true) } }
        }
    }

    companion object {
        fun factory(recordId: Long, repository: GiftRecordRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                GiftRecordDetailViewModel(recordId, repository) as T
        }
    }
}
