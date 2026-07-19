package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.core.common.NavigationConstants
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.repository.ContactRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContactEditorUiState(
    val contactId: Long = NavigationConstants.NEW_CONTACT_ID,
    val name: String = "",
    val phone: String = "",
    val relationship: String = "",
    val notes: String = "",
    val createdTime: Long = System.currentTimeMillis(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val nameError: Boolean = false,
    val operationFailed: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
) {
    val isNewContact: Boolean get() = contactId == NavigationConstants.NEW_CONTACT_ID
}

class ContactEditorViewModel(
    private val contactId: Long,
    private val repository: ContactRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ContactEditorUiState(contactId = contactId, isLoading = contactId != NavigationConstants.NEW_CONTACT_ID),
    )
    val uiState: StateFlow<ContactEditorUiState> = _uiState.asStateFlow()

    init {
        if (contactId != NavigationConstants.NEW_CONTACT_ID) loadContact()
    }

    private fun loadContact() = viewModelScope.launch {
        runCatching { repository.observeContact(contactId).first() }
            .onSuccess { contact ->
                _uiState.update { state ->
                    if (contact == null) state.copy(isLoading = false, operationFailed = true)
                    else state.copy(
                        name = contact.name,
                        phone = contact.phone.orEmpty(),
                        relationship = contact.relationship.orEmpty(),
                        notes = contact.notes.orEmpty(),
                        createdTime = contact.createdTime,
                        isLoading = false,
                    )
                }
            }
            .onFailure { _uiState.update { it.copy(isLoading = false, operationFailed = true) } }
    }

    fun updateName(value: String) = _uiState.update { it.copy(name = value, nameError = false, operationFailed = false) }
    fun updatePhone(value: String) = _uiState.update { it.copy(phone = value, operationFailed = false) }
    fun updateRelationship(value: String) = _uiState.update { it.copy(relationship = value, operationFailed = false) }
    fun updateNotes(value: String) = _uiState.update { it.copy(notes = value, operationFailed = false) }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, operationFailed = false) }
            val contact = state.toContact()
            runCatching {
                if (state.isNewContact) repository.create(contact) else repository.update(contact)
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            }.onFailure {
                _uiState.update { it.copy(isSaving = false, operationFailed = true) }
            }
        }
    }

    fun delete() {
        val state = _uiState.value
        if (state.isNewContact || state.isDeleting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, operationFailed = false) }
            runCatching { repository.delete(state.toContact()) }
                .onSuccess { _uiState.update { it.copy(isDeleting = false, isDeleted = true) } }
                .onFailure { _uiState.update { it.copy(isDeleting = false, operationFailed = true) } }
        }
    }

    private fun ContactEditorUiState.toContact() = Contact(
        id = contactId,
        name = name.trim(),
        phone = phone.trim().ifBlank { null },
        relationship = relationship.trim().ifBlank { null },
        notes = notes.trim().ifBlank { null },
        createdTime = createdTime,
    )

    companion object {
        fun factory(contactId: Long, repository: ContactRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ContactEditorViewModel(contactId, repository) as T
        }
    }
}
