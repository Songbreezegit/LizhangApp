package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.core.util.DateFormatter
import com.yangsong.lizhang.domain.export.GiftRecordCsvFormatter
import com.yangsong.lizhang.domain.export.GiftRecordXlsxFormatter
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExportFormat { CSV, EXCEL }

data class ExportDocument(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val format: ExportFormat,
)

enum class SettingsMessage {
    EXPORT_EMPTY,
    CSV_PREPARE_FAILED,
    EXCEL_PREPARE_FAILED,
    CSV_SAVE_SUCCESS,
    EXCEL_SAVE_SUCCESS,
    CSV_SAVE_FAILED,
    EXCEL_SAVE_FAILED,
    CSV_SAVE_CANCELLED,
    EXCEL_SAVE_CANCELLED,
}

data class SettingsUiState(
    val isPreparingCsv: Boolean = false,
    val isPreparingExcel: Boolean = false,
    val pendingExport: ExportDocument? = null,
    val message: SettingsMessage? = null,
)

class SettingsViewModel(
    private val repository: GiftRecordRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    fun prepareCsvExport() = prepareExport(ExportFormat.CSV)

    fun prepareExcelExport() = prepareExport(ExportFormat.EXCEL)

    private fun prepareExport(format: ExportFormat) {
        if (_uiState.value.isPreparingCsv || _uiState.value.isPreparingExcel) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPreparingCsv = format == ExportFormat.CSV,
                    isPreparingExcel = format == ExportFormat.EXCEL,
                    message = null,
                )
            }
            runCatching { repository.observeAll().first() }
                .onSuccess { records ->
                    if (records.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isPreparingCsv = false,
                                isPreparingExcel = false,
                                message = SettingsMessage.EXPORT_EMPTY,
                            )
                        }
                    } else {
                        val timestamp = DateFormatter.format(now(), "yyyyMMdd_HHmmss")
                        val document = when (format) {
                            ExportFormat.CSV -> ExportDocument(
                                fileName = "礼账_$timestamp.csv",
                                mimeType = "text/csv",
                                bytes = GiftRecordCsvFormatter.format(records).toByteArray(Charsets.UTF_8),
                                format = format,
                            )
                            ExportFormat.EXCEL -> ExportDocument(
                                fileName = "礼账_$timestamp.xlsx",
                                mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                bytes = GiftRecordXlsxFormatter.format(records),
                                format = format,
                            )
                        }
                        _uiState.update {
                            it.copy(
                                isPreparingCsv = false,
                                isPreparingExcel = false,
                                pendingExport = document,
                            )
                        }
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isPreparingCsv = false,
                            isPreparingExcel = false,
                            message = if (format == ExportFormat.CSV) {
                                SettingsMessage.CSV_PREPARE_FAILED
                            } else {
                                SettingsMessage.EXCEL_PREPARE_FAILED
                            },
                        )
                    }
                }
        }
    }

    fun consumePendingExport() = _uiState.update { it.copy(pendingExport = null) }

    fun reportSaveResult(format: ExportFormat, success: Boolean) = _uiState.update {
        val message = when {
            format == ExportFormat.CSV && success -> SettingsMessage.CSV_SAVE_SUCCESS
            format == ExportFormat.CSV -> SettingsMessage.CSV_SAVE_FAILED
            success -> SettingsMessage.EXCEL_SAVE_SUCCESS
            else -> SettingsMessage.EXCEL_SAVE_FAILED
        }
        it.copy(message = message)
    }

    fun reportSaveCancelled(format: ExportFormat) = _uiState.update {
        it.copy(
            message = if (format == ExportFormat.CSV) {
                SettingsMessage.CSV_SAVE_CANCELLED
            } else {
                SettingsMessage.EXCEL_SAVE_CANCELLED
            },
        )
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(repository: GiftRecordRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(repository) as T
        }
    }
}
