package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.domain.contact.ContactImportRules
import com.yangsong.lizhang.domain.model.ContactImportSelection
import com.yangsong.lizhang.domain.model.ContactImportStatus
import com.yangsong.lizhang.domain.model.ContactImportResult
import com.yangsong.lizhang.domain.model.DeviceContact
import com.yangsong.lizhang.domain.repository.ContactRepository
import com.yangsong.lizhang.domain.repository.DeviceContactRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ContactPermissionState { UNKNOWN, GRANTED, DENIED, BLOCKED }
enum class ContactImportError { READ, WRITE }

data class ContactImportCandidate(val contact: DeviceContact, val status: ContactImportStatus) {
    val exists: Boolean get() = status == ContactImportStatus.EXISTING
}

data class ContactImportUiState(
    val isLoading: Boolean = false,
    val permissionState: ContactPermissionState = ContactPermissionState.UNKNOWN,
    val contacts: List<ContactImportCandidate> = emptyList(),
    val visibleContacts: List<ContactImportCandidate> = emptyList(),
    val selectedKeys: Set<String> = emptySet(),
    val query: String = "",
    val isImporting: Boolean = false,
    val importResult: ContactImportResult? = null,
    val error: ContactImportError? = null,
    val loaded: Boolean = false,
)

class ContactImportViewModel(
    private val deviceRepository: DeviceContactRepository,
    private val contactRepository: ContactRepository,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val state = MutableStateFlow(ContactImportUiState())
    val uiState = state.asStateFlow()
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    fun permissionChanged(permission: ContactPermissionState) {
        if (permission != ContactPermissionState.GRANTED) {
            loadJob?.cancel()
            searchJob?.cancel()
            state.update { ContactImportUiState(permissionState = permission, isImporting = it.isImporting) }
            return
        }
        state.update { it.copy(permissionState = permission) }
        if (!state.value.loaded && !state.value.isLoading) load()
    }

    fun load() {
        if (state.value.permissionState != ContactPermissionState.GRANTED || state.value.isLoading) return
        state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val raw = deviceRepository.readContacts()
                val existing = contactRepository.observeContacts().first()
                val candidates = withContext(computationDispatcher) {
                    val index = ContactImportRules.DuplicateIndex(existing)
                    ContactImportRules.candidates(raw).map { ContactImportCandidate(it, index.status(it)) }
                }
                state.update { it.copy(
                    isLoading = false, loaded = true, contacts = candidates,
                    selectedKeys = candidates.filter { it.status == ContactImportStatus.NEW }.map { it.contact.phone }.toSet(),
                ) }
                updateQuery(state.value.query)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                permissionChanged(ContactPermissionState.DENIED)
            } catch (_: Exception) {
                state.update { it.copy(isLoading = false, error = ContactImportError.READ) }
            }
        }
    }

    fun updateQuery(query: String) {
        state.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val contacts = state.value.contacts
            val visible = withContext(computationDispatcher) {
                val term = query.trim()
                val phoneTerm = ContactImportRules.normalizePhone(term)
                contacts.filter {
                    it.contact.name.contains(term, ignoreCase = true) || it.contact.phone.contains(term) ||
                        (phoneTerm != null && it.contact.phone.contains(phoneTerm))
                }
            }
            state.update { it.copy(visibleContacts = visible) }
        }
    }

    fun toggle(phone: String) = state.update { current ->
        if (current.isImporting || current.contacts.none { it.contact.phone == phone && !it.exists }) current
        else current.copy(selectedKeys = if (phone in current.selectedKeys) current.selectedKeys - phone else current.selectedKeys + phone)
    }

    /** 全选始终作用于整个候选集合，搜索不会丢失其他选择。 */
    fun selectAll(selected: Boolean) = state.update {
        if (it.isImporting) it else it.copy(selectedKeys = if (selected) {
            it.contacts.filterNot { row -> row.exists }.map { row -> row.contact.phone }.toSet()
        } else emptySet())
    }

    fun importSelected() {
        val current = state.value
        if (current.isImporting || current.selectedKeys.isEmpty() || current.importResult != null ||
            current.permissionState != ContactPermissionState.GRANTED) return
        state.update { it.copy(isImporting = true, error = null) }
        viewModelScope.launch {
            try {
                val selections = withContext(computationDispatcher) {
                    current.contacts.map { row ->
                        ContactImportSelection(row.contact,
                            selected = row.contact.phone in current.selectedKeys && !row.exists,
                            allowPossibleDuplicate = row.status == ContactImportStatus.POSSIBLE_DUPLICATE,
                        )
                    }
                }
                val result = contactRepository.importDeviceContacts(selections)
                state.update { it.copy(isImporting = false, importResult = result) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.update { it.copy(isImporting = false, error = ContactImportError.WRITE) }
            }
        }
    }

    companion object {
        fun factory(deviceRepository: DeviceContactRepository, contactRepository: ContactRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ContactImportViewModel(deviceRepository, contactRepository) as T
            }
    }
}
