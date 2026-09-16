package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class SearchUiState(
    val query: String = "",
    val records: List<GiftRecordWithContact> = emptyList(),
    val error: Boolean = false,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class SearchViewModel(repository: GiftRecordRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val retrySignal = RetrySignal()

    val uiState: StateFlow<SearchUiState> = retrySignal.flow(
        source = {
            query
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .distinctUntilChanged()
                .flatMapLatest { currentQuery ->
                repository.observeSearch(currentQuery).map { SearchUiState(currentQuery, it) }
            }
        },
        onError = { SearchUiState(query = query.value, error = true) },
    )
        .onStart { emit(SearchUiState()) }
        .onStart { emit(SearchUiState()) }
        .combine(query) { result, input -> result.copy(query = input) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun updateQuery(value: String) {
        if (query.value != value) query.value = value
    }

    fun retry() = retrySignal.retry()

    companion object {
        private const val SEARCH_DEBOUNCE_MILLIS = 250L

        fun factory(repository: GiftRecordRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchViewModel(repository) as T
        }
    }
}
