package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.domain.model.ContactLedgerSummary
import com.yangsong.lizhang.domain.repository.ContactRepository
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ContactSort { RECENT, NAME }

data class ContactsUiState(
    val query: String = "",
    val sort: ContactSort = ContactSort.RECENT,
    val contacts: List<ContactLedgerSummary> = emptyList(),
    val isLoading: Boolean = true,
    val error: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModel(
    private val repository: ContactRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(ContactSort.RECENT)
    private val retrySignal = RetrySignal()
    private val chineseCollator = Collator.getInstance(Locale.CHINA)
    private val recentComparator = Comparator<ContactLedgerSummary> { first, second ->
        val timeResult = second.lastInteractionTime.compareTo(first.lastInteractionTime)
        if (timeResult != 0) timeResult
        else chineseCollator.compare(first.contact.name, second.contact.name)
    }

    val uiState: StateFlow<ContactsUiState> = retrySignal.flow(
        source = {
            combine(query, sort) { currentQuery, currentSort -> currentQuery to currentSort }
                .flatMapLatest { (currentQuery, currentSort) ->
                    repository.observeContactSummaries(currentQuery).map { list ->
                        ContactsUiState(
                            query = currentQuery,
                            sort = currentSort,
                            contacts = when (currentSort) {
                                ContactSort.NAME -> list.sortedWith { first, second ->
                                    chineseCollator.compare(first.contact.name, second.contact.name)
                                }
                                ContactSort.RECENT -> list.sortedWith(recentComparator)
                            },
                            isLoading = false,
                        )
                    }
                }
        },
        onError = {
            ContactsUiState(
                query = query.value,
                sort = sort.value,
                isLoading = false,
                error = true,
            )
        },
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    fun updateQuery(value: String) {
        query.value = value
    }

    fun updateSort(value: ContactSort) {
        sort.value = value
    }

    fun retry() = retrySignal.retry()

    companion object {
        fun factory(repository: ContactRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ContactsViewModel(repository) as T
        }
    }
}
