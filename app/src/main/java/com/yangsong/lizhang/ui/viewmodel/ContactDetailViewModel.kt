package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.repository.ContactRepository
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ContactDetailUiState(
    val contact: Contact? = null,
    val records: List<GiftRecord> = emptyList(),
    val received: Long = 0,
    val given: Long = 0,
    val isLoading: Boolean = true,
    val error: Boolean = false,
    val notFound: Boolean = false,
) {
    val net get() = received - given
}

class ContactDetailViewModel(
    id: Long,
    contactRepository: ContactRepository,
    giftRepository: GiftRecordRepository,
) : ViewModel() {
    private val retrySignal = RetrySignal()

    val uiState: StateFlow<ContactDetailUiState> = retrySignal.flow(
        source = {
            combine(
                contactRepository.observeContact(id),
                giftRepository.observeByContact(id),
            ) { contact, records ->
                ContactDetailUiState(
                    contact = contact,
                    records = records,
                    received = records.filter { it.direction == GiftDirection.RECEIVED }
                        .sumOf { it.amountInCents },
                    given = records.filter { it.direction == GiftDirection.GIVEN }
                        .sumOf { it.amountInCents },
                    isLoading = false,
                    notFound = contact == null,
                )
            }
        },
        onError = { ContactDetailUiState(isLoading = false, error = true) },
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactDetailUiState())

    fun retry() = retrySignal.retry()

    companion object {
        fun factory(
            id: Long,
            contactRepository: ContactRepository,
            giftRepository: GiftRecordRepository,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ContactDetailViewModel(id, contactRepository, giftRepository) as T
        }
    }
}
