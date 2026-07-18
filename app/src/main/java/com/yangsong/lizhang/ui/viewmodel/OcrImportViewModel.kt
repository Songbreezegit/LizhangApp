package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OcrStage { INTRO, UNAVAILABLE, PENDING, SUCCESS }

data class OcrPendingRecordUi(
    val id: Long,
    val name: String,
    val amount: String,
    val date: String,
    val lowConfidence: Boolean = false,
    val possibleDuplicate: Boolean = false,
)

data class OcrImportUiState(
    val stage: OcrStage = OcrStage.INTRO,
    val records: List<OcrPendingRecordUi> = emptyList(),
)

class OcrImportViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(OcrImportUiState())
    val uiState: StateFlow<OcrImportUiState> = mutableUiState.asStateFlow()

    fun notifyUnavailable() {
        mutableUiState.value = OcrImportUiState(OcrStage.UNAVAILABLE)
    }

    fun reset() {
        mutableUiState.value = OcrImportUiState()
    }

    fun remove(recordId: Long) {
        mutableUiState.value = mutableUiState.value.copy(
            records = mutableUiState.value.records.filterNot { it.id == recordId },
        )
    }

    fun update(record: OcrPendingRecordUi) {
        mutableUiState.value = mutableUiState.value.copy(
            records = mutableUiState.value.records.map { if (it.id == record.id) record else it },
        )
    }

    companion object {
        fun factory() = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = OcrImportViewModel() as T
        }
    }
}
