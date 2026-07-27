package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.core.util.DateFormatter
import com.yangsong.lizhang.domain.backup.BackupArchiveCodec
import com.yangsong.lizhang.domain.backup.InvalidBackupPasswordException
import com.yangsong.lizhang.domain.export.GiftRecordCsvFormatter
import com.yangsong.lizhang.domain.export.GiftRecordXlsxFormatter
import com.yangsong.lizhang.domain.repository.BackupRepository
import com.yangsong.lizhang.domain.repository.BackupSummary
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import com.yangsong.lizhang.domain.repository.ThemeRepository
import com.yangsong.lizhang.domain.model.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExportFormat { CSV, EXCEL, BACKUP }

data class ExportDocument(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val format: ExportFormat,
)

data class PendingRestore(
    val bytes: ByteArray,
    val summary: BackupSummary,
    val password: String? = null,
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
    BACKUP_PREPARE_FAILED,
    BACKUP_SAVE_SUCCESS,
    BACKUP_SAVE_FAILED,
    BACKUP_SAVE_CANCELLED,
    BACKUP_READ_FAILED,
    BACKUP_RESTORE_SUCCESS,
    BACKUP_RESTORE_FAILED,
}

data class SettingsUiState(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val isPreparingCsv: Boolean = false,
    val isPreparingExcel: Boolean = false,
    val isPreparingBackup: Boolean = false,
    val isReadingBackup: Boolean = false,
    val isRestoringBackup: Boolean = false,
    val pendingExport: ExportDocument? = null,
    val pendingRestore: PendingRestore? = null,
    val encryptedBackupAwaitingPassword: ByteArray? = null,
    val isBackupPasswordInvalid: Boolean = false,
    val message: SettingsMessage? = null,
)

class SettingsViewModel(
    private val repository: GiftRecordRepository,
    private val backupRepository: BackupRepository,
    private val themeRepository: ThemeRepository? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        themeRepository?.let { themes ->
            viewModelScope.launch {
                themes.themeMode.collect { mode ->
                    _uiState.update { it.copy(themeMode = mode) }
                }
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        themeRepository?.setThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun prepareCsvExport() = prepareExport(ExportFormat.CSV)

    fun prepareExcelExport() = prepareExport(ExportFormat.EXCEL)

    fun prepareBackupExport(password: String? = null) {
        if (_uiState.value.isBusy()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isPreparingBackup = true, message = null) }
            runCatching { withContext(backgroundDispatcher) { backupRepository.createBackup(password) } }
                .onSuccess { backup ->
                    val timestamp = DateFormatter.format(now(), "yyyyMMdd_HHmmss")
                    val filePrefix = if (password.isNullOrEmpty()) "礼账备份" else "礼账加密备份"
                    _uiState.update {
                        it.copy(
                            isPreparingBackup = false,
                            pendingExport = ExportDocument(
                                fileName = "${filePrefix}_$timestamp.${BackupArchiveCodec.FILE_EXTENSION}",
                                mimeType = BackupArchiveCodec.MIME_TYPE,
                                bytes = backup.bytes,
                                format = ExportFormat.BACKUP,
                            ),
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isPreparingBackup = false, message = SettingsMessage.BACKUP_PREPARE_FAILED)
                    }
                }
        }
    }

    fun inspectBackup(bytes: ByteArray, password: String? = null) {
        if (_uiState.value.isBusy()) return
        if (backupRepository.requiresPassword(bytes) && password == null) {
            _uiState.update {
                it.copy(
                    encryptedBackupAwaitingPassword = bytes,
                    isBackupPasswordInvalid = false,
                    message = null,
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isReadingBackup = true,
                    isBackupPasswordInvalid = false,
                    message = null,
                )
            }
            runCatching {
                withContext(computeDispatcher) { backupRepository.inspectBackup(bytes, password) }
            }
                .onSuccess { summary ->
                    _uiState.update {
                        it.copy(
                            isReadingBackup = false,
                            pendingRestore = PendingRestore(bytes, summary, password),
                            encryptedBackupAwaitingPassword = null,
                            isBackupPasswordInvalid = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        if (error is InvalidBackupPasswordException) {
                            it.copy(isReadingBackup = false, isBackupPasswordInvalid = true)
                        } else {
                            it.copy(
                                isReadingBackup = false,
                                encryptedBackupAwaitingPassword = null,
                                isBackupPasswordInvalid = false,
                                message = SettingsMessage.BACKUP_READ_FAILED,
                            )
                        }
                    }
                }
        }
    }

    fun unlockEncryptedBackup(password: String) {
        val bytes = _uiState.value.encryptedBackupAwaitingPassword ?: return
        inspectBackup(bytes, password)
    }

    fun cancelBackupPassword() = _uiState.update {
        it.copy(encryptedBackupAwaitingPassword = null, isBackupPasswordInvalid = false)
    }

    fun reportBackupReadFailed() = _uiState.update {
        it.copy(
            isReadingBackup = false,
            encryptedBackupAwaitingPassword = null,
            isBackupPasswordInvalid = false,
            message = SettingsMessage.BACKUP_READ_FAILED,
        )
    }

    fun cancelRestore() = _uiState.update { it.copy(pendingRestore = null) }

    fun confirmRestore() {
        val pending = _uiState.value.pendingRestore ?: return
        if (_uiState.value.isBusy()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoringBackup = true, message = null) }
            runCatching {
                withContext(backgroundDispatcher) {
                    backupRepository.restoreBackup(pending.bytes, pending.password)
                }
            }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isRestoringBackup = false,
                            pendingRestore = null,
                            message = SettingsMessage.BACKUP_RESTORE_SUCCESS,
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isRestoringBackup = false, message = SettingsMessage.BACKUP_RESTORE_FAILED)
                    }
                }
        }
    }

    private fun prepareExport(format: ExportFormat) {
        require(format != ExportFormat.BACKUP)
        if (_uiState.value.isBusy()) return
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
                            ExportFormat.BACKUP -> error("备份导出使用独立流程")
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
            format == ExportFormat.EXCEL && success -> SettingsMessage.EXCEL_SAVE_SUCCESS
            format == ExportFormat.EXCEL -> SettingsMessage.EXCEL_SAVE_FAILED
            success -> SettingsMessage.BACKUP_SAVE_SUCCESS
            else -> SettingsMessage.BACKUP_SAVE_FAILED
        }
        it.copy(message = message)
    }

    fun reportSaveCancelled(format: ExportFormat) = _uiState.update {
        it.copy(
            message = when (format) {
                ExportFormat.CSV -> SettingsMessage.CSV_SAVE_CANCELLED
                ExportFormat.EXCEL -> SettingsMessage.EXCEL_SAVE_CANCELLED
                ExportFormat.BACKUP -> SettingsMessage.BACKUP_SAVE_CANCELLED
            },
        )
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun SettingsUiState.isBusy(): Boolean =
        isPreparingCsv || isPreparingExcel || isPreparingBackup || isReadingBackup || isRestoringBackup

    companion object {
        fun factory(
            repository: GiftRecordRepository,
            backupRepository: BackupRepository,
            themeRepository: ThemeRepository,
        ) =
            object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(repository, backupRepository, themeRepository) as T
        }
    }
}
