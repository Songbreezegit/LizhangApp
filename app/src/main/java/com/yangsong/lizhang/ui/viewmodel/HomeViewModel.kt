package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.domain.model.ContactLedgerSummary
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.repository.ContactRepository
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val contactCount: Int = 0,
    val contactSummaries: List<ContactLedgerSummary> = emptyList(),
    val recentRecords: List<GiftRecordWithContact> = emptyList(),
)

class HomeViewModel(
    contactRepository: ContactRepository,
    giftRecordRepository: GiftRecordRepository,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        contactRepository.observeContactSummaries(),
        giftRecordRepository.observeRecent(),
    ) { summaries, records ->
        HomeUiState(summaries.size, summaries, records)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        fun factory(contactRepository: ContactRepository, giftRecordRepository: GiftRecordRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(contactRepository, giftRecordRepository) as T
            }
    }
}
