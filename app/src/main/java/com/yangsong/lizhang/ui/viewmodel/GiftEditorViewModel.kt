package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.core.util.toCentsOrNull
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

enum class GiftRecordValidationError {
    CONTACT_REQUIRED,
    AMOUNT_INVALID,
}

data class GiftEditorUiState(
    val recordId: Long = 0,
    val contactId: Long? = null,
    val amount: String = "",
    val eventType: EventType = EventType.WEDDING,
    val eventDate: Long = Calendar.getInstance().startOfDay(),
    val direction: GiftDirection = GiftDirection.RECEIVED,
    val notes: String = "",
    val validationError: GiftRecordValidationError? = null,
    val isSaved: Boolean = false,
)

class GiftEditorViewModel(private val repository: GiftRecordRepository) : ViewModel() {
    private val mutableUiState = MutableStateFlow(GiftEditorUiState())
    val uiState: StateFlow<GiftEditorUiState> = mutableUiState.asStateFlow()

    fun update(transform: (GiftEditorUiState) -> GiftEditorUiState) {
        mutableUiState.value = transform(mutableUiState.value).copy(validationError = null, isSaved = false)
    }

    fun edit(record: GiftRecord) {
        mutableUiState.value = GiftEditorUiState(
            recordId = record.id,
            contactId = record.contactId,
            amount = (record.amountInCents / 100.0).toString(),
            eventType = record.eventType,
            eventDate = record.eventDate,
            direction = record.direction,
            notes = record.notes.orEmpty(),
        )
    }

    fun save() {
        val state = mutableUiState.value
        val contactId = state.contactId
        val amountInCents = state.amount.toCentsOrNull()
        when {
            contactId == null -> mutableUiState.value = state.copy(validationError = GiftRecordValidationError.CONTACT_REQUIRED)
            amountInCents == null || amountInCents <= 0 -> mutableUiState.value = state.copy(validationError = GiftRecordValidationError.AMOUNT_INVALID)
            else -> viewModelScope.launch {
                val record = GiftRecord(
                    id = state.recordId,
                    contactId = contactId,
                    amountInCents = amountInCents,
                    eventType = state.eventType,
                    eventDate = state.eventDate,
                    direction = state.direction,
                    notes = state.notes,
                )
                if (record.id == 0L) repository.create(record) else repository.update(record)
                mutableUiState.value = mutableUiState.value.copy(isSaved = true)
            }
        }
    }

    fun delete() {
        val state = mutableUiState.value
        val contactId = state.contactId ?: return
        if (state.recordId == 0L) return
        viewModelScope.launch {
            repository.delete(
                GiftRecord(
                    id = state.recordId,
                    contactId = contactId,
                    amountInCents = state.amount.toCentsOrNull() ?: 0,
                    eventType = state.eventType,
                    eventDate = state.eventDate,
                    direction = state.direction,
                    notes = state.notes,
                ),
            )
            mutableUiState.value = GiftEditorUiState()
        }
    }

    companion object {
        fun factory(repository: GiftRecordRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = GiftEditorViewModel(repository) as T
        }
    }
}

private fun Calendar.startOfDay(): Long = apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
