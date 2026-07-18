package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.repository.ContactRepository
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContactDetailUiState(
    val contact: Contact? = null,
    val records: List<GiftRecord> = emptyList(),
)

class ContactDetailViewModel(
    contactId: Long,
    contactRepository: ContactRepository,
    private val giftRecordRepository: GiftRecordRepository,
) : ViewModel() {
    val uiState: StateFlow<ContactDetailUiState> = combine(
        contactRepository.observeContact(contactId),
        giftRecordRepository.observeByContact(contactId),
    ) { contact, records -> ContactDetailUiState(contact, records) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactDetailUiState())

    fun deleteRecord(record: GiftRecord) = viewModelScope.launch { giftRecordRepository.delete(record) }

    companion object {
        fun factory(
            contactId: Long,
            contactRepository: ContactRepository,
            giftRecordRepository: GiftRecordRepository,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ContactDetailViewModel(contactId, contactRepository, giftRecordRepository) as T
        }
    }
}
