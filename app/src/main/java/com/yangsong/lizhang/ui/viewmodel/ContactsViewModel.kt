package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.ContactLedgerSummary
import com.yangsong.lizhang.domain.repository.ContactRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContactsUiState(
    val query: String = "",
    val contacts: List<ContactLedgerSummary> = emptyList(),
)

class ContactsViewModel(private val repository: ContactRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    val uiState: StateFlow<ContactsUiState> = query
        .flatMapLatest { currentQuery ->
            repository.observeContactSummaries(currentQuery).map { ContactsUiState(currentQuery, it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    fun updateQuery(value: String) {
        query.value = value
    }

    fun save(contact: Contact) = viewModelScope.launch {
        if (contact.id == 0L) repository.create(contact) else repository.update(contact)
    }

    fun delete(contact: Contact) = viewModelScope.launch { repository.delete(contact) }

    companion object {
        fun factory(repository: ContactRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ContactsViewModel(repository) as T
        }
    }
}
