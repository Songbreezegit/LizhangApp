package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.core.common.NavigationConstants
import com.yangsong.lizhang.core.util.toCentsOrNull
import com.yangsong.lizhang.domain.model.*
import com.yangsong.lizhang.domain.repository.ContactRepository
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import java.math.BigDecimal
import java.util.Calendar
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class GiftRecordValidationError {
    CONTACT_REQUIRED,
    AMOUNT_INVALID,
}

data class GiftEditorUiState(
    val recordId: Long = NavigationConstants.NEW_RECORD_ID,
    val contactId: Long? = null,
    val amount: String = "",
    val eventType: EventType = EventType.WEDDING,
    val eventDate: Long = Calendar.getInstance().startOfDay(),
    val direction: GiftDirection = GiftDirection.RECEIVED,
    val notes: String = "",
    val createdTime: Long = System.currentTimeMillis(),
    val validationError: GiftRecordValidationError? = null,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val operationFailed: Boolean = false,
    val isSaved: Boolean = false,
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val contacts: List<Contact> = emptyList(),
    val isCreatingContact: Boolean = false,
) {
    val isEditing: Boolean get() = recordId != NavigationConstants.NEW_RECORD_ID
}

class GiftEditorViewModel(
    private val repository: GiftRecordRepository,
    private val contactRepository: ContactRepository,
    private val recordId: Long = NavigationConstants.NEW_RECORD_ID,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        GiftEditorUiState(recordId = recordId, isLoading = recordId != NavigationConstants.NEW_RECORD_ID),
    )
    val uiState: StateFlow<GiftEditorUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            contactRepository.observeContacts().collect { contacts ->
                mutableUiState.update { it.copy(contacts = contacts) }
            }
        }
        if (recordId != NavigationConstants.NEW_RECORD_ID) loadRecord()
    }

    private fun loadRecord() = viewModelScope.launch {
        runCatching { repository.observeRecord(recordId).first() }
            .onSuccess { record ->
                mutableUiState.update { state ->
                    if (record == null) state.copy(isLoading = false, loadFailed = true)
                    else state.fromRecord(record)
                }
            }
            .onFailure { mutableUiState.update { it.copy(isLoading = false, loadFailed = true) } }
    }

    fun update(transform: (GiftEditorUiState) -> GiftEditorUiState) {
        mutableUiState.update {
            transform(it).copy(
                validationError = null,
                isSaved = false,
                operationFailed = false,
                hasUnsavedChanges = true,
            )
        }
    }

    fun createContact(name: String, phone: String, relationship: String) {
        if (name.isBlank() || mutableUiState.value.isCreatingContact) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isCreatingContact = true, operationFailed = false) }
            runCatching {
                contactRepository.create(
                    Contact(
                        name = name.trim(),
                        phone = phone.trim().ifBlank { null },
                        relationship = relationship.trim().ifBlank { null },
                    ),
                )
            }.onSuccess { contactId ->
                mutableUiState.update {
                    it.copy(
                        contactId = contactId,
                        isCreatingContact = false,
                        validationError = null,
                        hasUnsavedChanges = true,
                    )
                }
            }.onFailure {
                mutableUiState.update { it.copy(isCreatingContact = false, operationFailed = true) }
            }
        }
    }

    fun save() {
        val state = mutableUiState.value
        val contactId = state.contactId
        val amountInCents = state.amount.toCentsOrNull()
        when {
            contactId == null -> mutableUiState.update { it.copy(validationError = GiftRecordValidationError.CONTACT_REQUIRED) }
            amountInCents == null || amountInCents <= 0 -> mutableUiState.update { it.copy(validationError = GiftRecordValidationError.AMOUNT_INVALID) }
            state.isSaving -> Unit
            else -> viewModelScope.launch {
                mutableUiState.update { it.copy(isSaving = true, operationFailed = false) }
                val record = GiftRecord(
                    id = state.recordId,
                    contactId = contactId,
                    amountInCents = amountInCents,
                    eventType = state.eventType,
                    eventDate = state.eventDate,
                    direction = state.direction,
                    notes = state.notes.trim().ifBlank { null },
                    createdTime = state.createdTime,
                )
                runCatching {
                    if (record.id == NavigationConstants.NEW_RECORD_ID) repository.create(record)
                    else repository.update(record)
                }.onSuccess {
                    mutableUiState.update {
                        it.copy(
                            isSaved = true,
                            isSaving = false,
                            hasUnsavedChanges = false,
                        )
                    }
                }.onFailure {
                    mutableUiState.update { it.copy(isSaving = false, operationFailed = true) }
                }
            }
        }
    }

    private fun GiftEditorUiState.fromRecord(record: GiftRecord) = copy(
        recordId = record.id,
        contactId = record.contactId,
        amount = BigDecimal.valueOf(record.amountInCents, 2).stripTrailingZeros().toPlainString(),
        eventType = record.eventType,
        eventDate = record.eventDate,
        direction = record.direction,
        notes = record.notes.orEmpty(),
        createdTime = record.createdTime,
        isLoading = false,
        loadFailed = false,
        hasUnsavedChanges = false,
    )

    companion object {
        fun factory(
            repository: GiftRecordRepository,
            contactRepository: ContactRepository,
            recordId: Long = NavigationConstants.NEW_RECORD_ID,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                GiftEditorViewModel(repository, contactRepository, recordId) as T
        }
    }
}

private fun Calendar.startOfDay(): Long = apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
