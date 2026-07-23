package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.domain.ocr.OcrImportDraft
import com.yangsong.lizhang.domain.ocr.OcrLedgerParser
import com.yangsong.lizhang.domain.ocr.OcrEngine
import com.yangsong.lizhang.domain.ocr.OcrFallbackReason
import com.yangsong.lizhang.domain.ocr.OcrProcessingStage
import com.yangsong.lizhang.domain.ocr.OcrRecognitionResult
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import com.yangsong.lizhang.domain.repository.OcrImportRepository
import com.yangsong.lizhang.domain.repository.OcrRecognitionRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OcrStage {
    INTRO,
    PREPROCESSING,
    UPLOADING,
    PRIMARY_RECOGNIZING,
    WAITING_RESULT,
    VL_RECOGNIZING,
    OFFLINE_RECOGNIZING,
    PENDING,
    EMPTY,
    ERROR,
    IMPORTING,
    SUCCESS,
}

data class OcrPendingRecordUi(
    val id: Long,
    val name: String,
    val amount: String,
    val date: String,
    val lowConfidence: Boolean = false,
    val possibleDuplicate: Boolean = false,
    val modelConflict: Boolean = false,
)

data class OcrImportUiState(
    val stage: OcrStage = OcrStage.INTRO,
    val records: List<OcrPendingRecordUi> = emptyList(),
    val validationFailed: Boolean = false,
    val importedCount: Int = 0,
    val engine: OcrEngine? = null,
    val fallbackReason: OcrFallbackReason? = null,
    val warnings: List<String> = emptyList(),
)

class OcrImportViewModel(
    private val recognitionRepository: OcrRecognitionRepository,
    private val importRepository: OcrImportRepository,
    private val giftRecordRepository: GiftRecordRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(OcrImportUiState())
    val uiState: StateFlow<OcrImportUiState> = mutableUiState.asStateFlow()

    fun recognize(imageUri: String, allowCloud: Boolean = true) {
        viewModelScope.launch {
            mutableUiState.value = OcrImportUiState(stage = OcrStage.PREPROCESSING)
            runCatching {
                val result = recognitionRepository.recognizeDetailed(imageUri, allowCloud, ::updateStage)
                val primaryDrafts = OcrLedgerParser.parse(result.lines)
                val alternativeDrafts = OcrLedgerParser.parse(result.alternativeLines)
                val drafts = mergeCandidates(primaryDrafts, alternativeDrafts)
                val existing = giftRecordRepository.observeAll().first()
                val records = drafts.mapIndexed { index, candidate ->
                    val draft = candidate.first
                    OcrPendingRecordUi(
                        id = index.toLong() + 1,
                        name = draft.name,
                        amount = BigDecimal.valueOf(draft.amountInCents, 2).stripTrailingZeros().toPlainString(),
                        date = draft.date.toString(),
                        lowConfidence = draft.lowConfidence || candidate.second,
                        modelConflict = candidate.second,
                        possibleDuplicate = existing.any { item ->
                            item.contactName.trim() == draft.name.trim() &&
                                item.record.amountInCents == draft.amountInCents &&
                                Instant.ofEpochMilli(item.record.eventDate).atZone(ZoneId.systemDefault()).toLocalDate() == draft.date
                        },
                    )
                }
                result to records
            }.onSuccess { (result, records) ->
                mutableUiState.value = OcrImportUiState(
                    stage = if (records.isEmpty()) OcrStage.EMPTY else OcrStage.PENDING,
                    records = records,
                    engine = result.engine,
                    fallbackReason = result.fallbackReason,
                    warnings = result.warnings,
                )
            }.onFailure {
                mutableUiState.value = OcrImportUiState(stage = OcrStage.ERROR)
            }
        }
    }

    private fun updateStage(stage: OcrProcessingStage) {
        val uiStage = when (stage) {
            OcrProcessingStage.PREPROCESSING -> OcrStage.PREPROCESSING
            OcrProcessingStage.UPLOADING -> OcrStage.UPLOADING
            OcrProcessingStage.PRIMARY_RECOGNIZING -> OcrStage.PRIMARY_RECOGNIZING
            OcrProcessingStage.WAITING_RESULT -> OcrStage.WAITING_RESULT
            OcrProcessingStage.VL_RECOGNIZING -> OcrStage.VL_RECOGNIZING
            OcrProcessingStage.OFFLINE_RECOGNIZING -> OcrStage.OFFLINE_RECOGNIZING
            OcrProcessingStage.COMPLETED -> return
        }
        mutableUiState.update { it.copy(stage = uiStage) }
    }

    private fun mergeCandidates(
        primary: List<OcrImportDraft>,
        alternatives: List<OcrImportDraft>,
    ): List<Pair<OcrImportDraft, Boolean>> {
        if (alternatives.isEmpty()) return primary.map { it to false }
        val alternativeKeys = alternatives.map { "${it.name}|${it.amountInCents}|${it.date}" }.toSet()
        val primaryKeys = primary.map { "${it.name}|${it.amountInCents}|${it.date}" }.toSet()
        return buildList {
            primary.forEach { draft ->
                val key = "${draft.name}|${draft.amountInCents}|${draft.date}"
                add(draft to (key !in alternativeKeys))
            }
            alternatives.filter { "${it.name}|${it.amountInCents}|${it.date}" !in primaryKeys }
                .forEach { add(it to true) }
        }
    }

    fun reset() {
        mutableUiState.value = OcrImportUiState()
    }

    fun remove(recordId: Long) {
        mutableUiState.update { state -> state.copy(records = state.records.filterNot { it.id == recordId }) }
    }

    fun update(record: OcrPendingRecordUi) {
        mutableUiState.update { state ->
            state.copy(
                records = state.records.map { if (it.id == record.id) record else it },
                validationFailed = false,
            )
        }
    }

    fun confirmImport() {
        val drafts = mutableUiState.value.records.mapNotNull(::toDraft)
        if (drafts.size != mutableUiState.value.records.size || drafts.isEmpty()) {
            mutableUiState.update { it.copy(validationFailed = true) }
            return
        }
        viewModelScope.launch {
            mutableUiState.update { it.copy(stage = OcrStage.IMPORTING, validationFailed = false) }
            runCatching { importRepository.import(drafts) }
                .onSuccess { count -> mutableUiState.value = OcrImportUiState(OcrStage.SUCCESS, importedCount = count) }
                .onFailure { mutableUiState.update { it.copy(stage = OcrStage.ERROR) } }
        }
    }

    private fun toDraft(record: OcrPendingRecordUi): OcrImportDraft? = runCatching {
        val amountInCents = BigDecimal(record.amount.trim()).movePointRight(2).longValueExact()
        OcrImportDraft(
            name = record.name.trim().takeIf { it.isNotEmpty() } ?: error("姓名为空"),
            amountInCents = amountInCents.takeIf { it > 0 } ?: error("金额无效"),
            date = LocalDate.parse(record.date.trim()),
            lowConfidence = record.lowConfidence,
        )
    }.getOrNull()

    companion object {
        fun factory(
            recognitionRepository: OcrRecognitionRepository,
            importRepository: OcrImportRepository,
            giftRecordRepository: GiftRecordRepository,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = OcrImportViewModel(
                recognitionRepository,
                importRepository,
                giftRecordRepository,
            ) as T
        }
    }
}
