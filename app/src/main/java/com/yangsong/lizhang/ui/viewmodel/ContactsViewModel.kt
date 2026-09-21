package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.domain.model.ContactLedgerSummary
import com.yangsong.lizhang.domain.model.ContactDeletePreview
import com.yangsong.lizhang.domain.model.ContactBulkDeleteOutcome
import com.yangsong.lizhang.domain.model.BulkDeleteResult
import com.yangsong.lizhang.domain.repository.ContactRepository
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class ContactSort { RECENT, NAME }

data class ContactsUiState(
    val query: String = "",
    val sort: ContactSort = ContactSort.RECENT,
    val contacts: List<ContactLedgerSummary> = emptyList(),
    val isLoading: Boolean = true,
    val error: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedContactIds: Set<Long> = emptySet(),
    val isPreparingDelete: Boolean = false,
    val isDeleting: Boolean = false,
    val deletePreview: ContactDeletePreview? = null,
    val cascadeConfirmed: Boolean = false,
    val deletePreviewChanged: Boolean = false,
    val deleteResult: BulkDeleteResult? = null,
    val deleteFailed: Boolean = false,
)

private data class ContactManagementState(
    val active: Boolean = false,
    val selected: Set<Long> = emptySet(),
    val preparing: Boolean = false,
    val deleting: Boolean = false,
    val preview: ContactDeletePreview? = null,
    val cascadeConfirmed: Boolean = false,
    val previewChanged: Boolean = false,
    val result: BulkDeleteResult? = null,
    val failed: Boolean = false,
) {
    val locked: Boolean get() = preparing || deleting || preview != null
}

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class ContactsViewModel(
    private val repository: ContactRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(ContactSort.RECENT)
    private val retrySignal = RetrySignal()
    private val management = MutableStateFlow(ContactManagementState())
    private val chineseCollator = Collator.getInstance(Locale.CHINA)
    private val recentComparator = Comparator<ContactLedgerSummary> { first, second ->
        val timeResult = second.lastInteractionTime.compareTo(first.lastInteractionTime)
        if (timeResult != 0) timeResult
        else chineseCollator.compare(first.contact.name, second.contact.name)
    }

    val uiState: StateFlow<ContactsUiState> = retrySignal.flow(
        source = {
            combine(query, sort) { currentQuery, currentSort -> currentQuery to currentSort }
                .debounce(CONTACT_QUERY_DEBOUNCE_MILLIS)
                .distinctUntilChanged()
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
    ).onStart { emit(ContactsUiState()) }
        .combine(query) { result, input -> result.copy(query = input) }
        .combine(management) { result, selection -> result.copy(
            isSelectionMode = selection.active,
            selectedContactIds = selection.selected,
            isPreparingDelete = selection.preparing,
            isDeleting = selection.deleting,
            deletePreview = selection.preview,
            cascadeConfirmed = selection.cascadeConfirmed,
            deletePreviewChanged = selection.previewChanged,
            deleteResult = selection.result,
            deleteFailed = selection.failed,
        ) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    fun updateQuery(value: String) {
        if (query.value != value) query.value = value
    }

    fun updateSort(value: ContactSort) {
        if (sort.value != value) sort.value = value
    }

    fun retry() = retrySignal.retry()

    fun enterSelectionMode() {
        if (!management.value.active) management.value = ContactManagementState(active = true)
    }

    fun exitSelectionMode() {
        if (!management.value.preparing && !management.value.deleting) management.value = ContactManagementState()
    }

    fun toggleContact(id: Long) {
        if (uiState.value.contacts.none { it.contact.id == id }) return
        management.update {
            if (!it.active || it.locked) it else it.copy(
                selected = if (id in it.selected) it.selected - id else it.selected + id, failed = false,
            )
        }
    }

    fun selectAllVisible() {
        val visible = uiState.value.contacts.map { it.contact.id }.toSet()
        management.update { if (!it.active || it.locked) it else it.copy(selected = it.selected + visible) }
    }

    fun clearSelection() {
        management.update { if (!it.active || it.locked) it else it.copy(selected = emptySet()) }
    }

    fun requestDelete() {
        val current = management.value
        if (!current.active || current.locked || current.selected.isEmpty()) return
        management.value = current.copy(preparing = true, failed = false, previewChanged = false)
        viewModelScope.launch {
            try {
                val preview = repository.previewDelete(current.selected)
                management.update { it.copy(preparing = false, preview = preview, cascadeConfirmed = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                management.update { it.copy(preparing = false, failed = true) }
            }
        }
    }

    fun dismissDelete() {
        management.update {
            if (it.deleting) it else it.copy(preview = null, cascadeConfirmed = false, previewChanged = false)
        }
    }

    fun confirmCascade(confirmed: Boolean) {
        management.update { if (it.deleting || it.preview == null) it else it.copy(cascadeConfirmed = confirmed) }
    }

    fun confirmDelete() {
        val current = management.value
        val preview = current.preview ?: return
        if (!current.active || current.deleting || current.preparing ||
            (preview.giftRecordCount > 0 && !current.cascadeConfirmed)) return
        management.value = current.copy(deleting = true, failed = false)
        viewModelScope.launch {
            try {
                when (val outcome = repository.deleteContacts(current.selected, preview)) {
                    is ContactBulkDeleteOutcome.Deleted -> management.value = ContactManagementState(result = outcome.result)
                    is ContactBulkDeleteOutcome.Changed -> management.update { it.copy(
                        deleting = false, preview = outcome.preview, cascadeConfirmed = false, previewChanged = true,
                    ) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                management.update { it.copy(deleting = false, preview = null, cascadeConfirmed = false, failed = true) }
            }
        }
    }

    fun consumeDeleteFeedback() {
        management.update { it.copy(result = null, failed = false) }
    }

    companion object {
        private const val CONTACT_QUERY_DEBOUNCE_MILLIS = 250L

        fun factory(repository: ContactRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ContactsViewModel(repository) as T
        }
    }
}
