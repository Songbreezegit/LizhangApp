package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.domain.model.ContactLedgerSummary
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.model.YearlyGiftSummary
import com.yangsong.lizhang.domain.repository.ContactRepository
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
class HomeViewModel(
    @Suppress("UNUSED_PARAMETER") contactRepository: ContactRepository,
    giftRepository: GiftRecordRepository,
) : ViewModel() {
    private val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    private val selectedYear = MutableStateFlow(currentYear)
    private val retrySignal = RetrySignal()

    val uiState = retrySignal.flow(
        source = {
            combine(
                giftRepository.observeYearlySummaries(),
                giftRepository.observeRecent(HOME_RECENT_RECORD_LIMIT),
                selectedYear,
            ) { yearlySummaries, recentRecords, year ->
                buildHomeState(yearlySummaries, recentRecords, year, currentYear)
            }
        },
        onError = { HomeUiState(isLoading = false, error = true) },
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectYear(year: Int) {
        if (year in uiState.value.availableYears) selectedYear.value = year
    }

    fun retry() = retrySignal.retry()

    companion object {
        private const val HOME_RECENT_RECORD_LIMIT = 8

        fun factory(c: ContactRepository, g: GiftRecordRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) = HomeViewModel(c, g) as T
        }
    }
}

internal fun buildHomeState(
    yearlySummaries: List<YearlyGiftSummary>,
    recentRecords: List<GiftRecordWithContact>,
    selectedYear: Int,
    currentYear: Int,
): HomeUiState {
    // 年份范围随真实账本数据动态扩展，但绝不提供未来年份。
    val earliestYear = yearlySummaries
        .asSequence()
        .map(YearlyGiftSummary::year)
        .filter { it in 1..currentYear }
        .minOrNull() ?: currentYear
    val availableYears = (currentYear downTo earliestYear).toList()
    val effectiveYear = selectedYear.takeIf { it in availableYears } ?: availableYears.first()
    val selectedSummary = yearlySummaries.firstOrNull { it.year == effectiveYear }
    return HomeUiState(
        isLoading = false,
        year = effectiveYear,
        availableYears = availableYears,
        recentRecords = recentRecords,
        received = selectedSummary?.receivedInCents ?: 0,
        given = selectedSummary?.givenInCents ?: 0,
    )
}
