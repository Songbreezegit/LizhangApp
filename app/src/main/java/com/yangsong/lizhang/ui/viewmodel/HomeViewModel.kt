package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.*
import com.yangsong.lizhang.domain.model.*
import com.yangsong.lizhang.domain.repository.*
import kotlinx.coroutines.flow.*
import java.util.Calendar

data class HomeUiState(val isLoading: Boolean = true, val error: Boolean = false, val recentRecords: List<GiftRecordWithContact> = emptyList(), val received: Long = 0, val given: Long = 0) { val net get() = received - given }
class HomeViewModel(contactRepository: ContactRepository, giftRepository: GiftRecordRepository) : ViewModel() {
    val uiState = combine(contactRepository.observeContactSummaries(), giftRepository.observeRecent(Int.MAX_VALUE)) { _, records ->
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val current = records.filter { Calendar.getInstance().apply { timeInMillis = it.record.eventDate }.get(Calendar.YEAR) == year }
        HomeUiState(false, recentRecords = records.take(8), received = current.filter { it.record.direction == GiftDirection.RECEIVED }.sumOf { it.record.amountInCents }, given = current.filter { it.record.direction == GiftDirection.GIVEN }.sumOf { it.record.amountInCents })
    }.catch { emit(HomeUiState(isLoading = false, error = true)) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
    companion object { fun factory(c: ContactRepository, g: GiftRecordRepository) = object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>) = HomeViewModel(c,g) as T } }
}
