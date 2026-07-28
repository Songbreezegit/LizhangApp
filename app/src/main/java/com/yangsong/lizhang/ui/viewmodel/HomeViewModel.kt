package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.*
import com.yangsong.lizhang.domain.model.*
import com.yangsong.lizhang.domain.repository.*
import kotlinx.coroutines.flow.*
import java.util.Calendar

data class HomeUiState(
    val isLoading: Boolean = true,
    val error: Boolean = false,
    val year: Int = Calendar.getInstance().get(Calendar.YEAR),
    val availableYears: List<Int> = listOf(year),
    val recentRecords: List<GiftRecordWithContact> = emptyList(),
    val received: Long = 0,
    val given: Long = 0,
) {
    val net get() = received - given
}
class HomeViewModel(contactRepository: ContactRepository, giftRepository: GiftRecordRepository) : ViewModel() {
    private val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    private val selectedYear = MutableStateFlow(currentYear)
    private val retrySignal = RetrySignal()

    val uiState = retrySignal.flow(
        source = {
            combine(
                contactRepository.observeContactSummaries(),
                giftRepository.observeRecent(Int.MAX_VALUE),
                selectedYear,
            ) { _, records, year ->
                val current = records.filter {
                    Calendar.getInstance().apply { timeInMillis = it.record.eventDate }
                        .get(Calendar.YEAR) == year
                }
                val recordYears = records.map {
                    Calendar.getInstance().apply { timeInMillis = it.record.eventDate }.get(Calendar.YEAR)
                }
                // 年份范围随真实账本数据动态扩展，但绝不提供未来年份。
                val earliestYear = recordYears
                    .filter { it in 1..currentYear }
                    .minOrNull()
                    ?: currentYear
                HomeUiState(
                    isLoading = false,
                    year = year,
                    availableYears = (currentYear downTo earliestYear).toList(),
                    recentRecords = records.take(8),
                    received = current.filter { it.record.direction == GiftDirection.RECEIVED }
                        .sumOf { it.record.amountInCents },
                    given = current.filter { it.record.direction == GiftDirection.GIVEN }
                        .sumOf { it.record.amountInCents },
                )
            }
        },
        onError = { HomeUiState(isLoading = false, error = true) },
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectYear(year: Int) {
        if (year in uiState.value.availableYears) selectedYear.value = year
    }

    fun retry() = retrySignal.retry()

    companion object { fun factory(c: ContactRepository, g: GiftRecordRepository) = object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>) = HomeViewModel(c,g) as T } }
}
