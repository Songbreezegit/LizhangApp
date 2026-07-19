package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.core.util.DateFormatter
import com.yangsong.lizhang.domain.export.GiftRecordCsvFormatter
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExportDocument(val fileName: String, val content: String)

enum class SettingsMessage {
    CSV_EMPTY,
    CSV_PREPARE_FAILED,
    CSV_SAVE_SUCCESS,
    CSV_SAVE_FAILED,
    CSV_SAVE_CANCELLED,
}

data class SettingsUiState(
    val isPreparingCsv: Boolean = false,
    val pendingCsv: ExportDocument? = null,
    val message: SettingsMessage? = null,
)

class SettingsViewModel(
    private val repository: GiftRecordRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    fun prepareCsvExport() {
        if (_uiState.value.isPreparingCsv) return
        viewModelScope.launch {
            _uiState.update { it.copy(isPreparingCsv = true, message = null) }
            runCatching { repository.observeAll().first() }
                .onSuccess { records ->
                    if (records.isEmpty()) {
                        _uiState.update { it.copy(isPreparingCsv = false, message = SettingsMessage.CSV_EMPTY) }
                    } else {
                        val fileName = "礼账_${DateFormatter.format(now(), "yyyyMMdd_HHmmss")}.csv"
                        _uiState.update {
                            it.copy(
                                isPreparingCsv = false,
                                pendingCsv = ExportDocument(fileName, GiftRecordCsvFormatter.format(records)),
                            )
                        }
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isPreparingCsv = false, message = SettingsMessage.CSV_PREPARE_FAILED) }
                }
        }
    }

    fun consumePendingCsv() = _uiState.update { it.copy(pendingCsv = null) }

    fun reportCsvSaveResult(success: Boolean) = _uiState.update {
        it.copy(message = if (success) SettingsMessage.CSV_SAVE_SUCCESS else SettingsMessage.CSV_SAVE_FAILED)
    }

    fun reportCsvSaveCancelled() = _uiState.update { it.copy(message = SettingsMessage.CSV_SAVE_CANCELLED) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(repository: GiftRecordRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(repository) as T
        }
    }
}
