package com.yangsong.lizhang.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.yangsong.lizhang.domain.contact.ContactImportRules
import com.yangsong.lizhang.ui.component.CenteredSnackbarHost
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.AppTextField
import com.yangsong.lizhang.ui.component.AppTopBar
import com.yangsong.lizhang.ui.component.ContactListItem
import com.yangsong.lizhang.ui.component.EmptyState
import com.yangsong.lizhang.ui.component.ErrorState
import com.yangsong.lizhang.ui.component.LoadingState
import com.yangsong.lizhang.ui.component.PageIllustration
import com.yangsong.lizhang.ui.viewmodel.ContactSort
import com.yangsong.lizhang.ui.viewmodel.ContactsUiState
import com.yangsong.lizhang.ui.viewmodel.ContactsViewModel
import com.yangsong.lizhang.ui.theme.CoralPrimary

@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    onContactClick: (Long) -> Unit,
    onAddContact: () -> Unit,
    onImportContacts: () -> Unit = {},
    onSelectionModeChange: (Boolean) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.isSelectionMode) { onSelectionModeChange(state.isSelectionMode) }
    DisposableEffect(Unit) { onDispose { onSelectionModeChange(false) } }
    BackHandler(state.isSelectionMode) {
        if (state.deletePreview != null) viewModel.dismissDelete() else viewModel.exitSelectionMode()
    }
    LaunchedEffect(viewModel) {
        viewModel.uiState.collect { feedback ->
        val message = when {
            feedback.deleteFailed -> context.getString(R.string.contact_bulk_delete_failed)
            feedback.deleteResult != null -> feedback.deleteResult.let { result ->
                if (result.deletedGiftRecords > 0) context.getString(R.string.contact_bulk_deleted_records, result.deletedContacts, result.deletedGiftRecords)
                else context.getString(R.string.contact_bulk_deleted, result.deletedContacts)
            }
            else -> null
        }
        if (message != null) {
            viewModel.consumeDeleteFeedback()
            snackbar.showSnackbar(message)
        }
        }
    }
    Box {
    ContactsContent(
        state = state,
        onQueryChange = viewModel::updateQuery,
        onSortChange = viewModel::updateSort,
        onContactClick = onContactClick,
        onAddContact = onAddContact,
        onRetry = viewModel::retry,
        onImportContacts = onImportContacts,
        onEnterSelection = viewModel::enterSelectionMode,
        onExitSelection = viewModel::exitSelectionMode,
        onToggleContact = viewModel::toggleContact,
        onSelectAllVisible = viewModel::selectAllVisible,
        onClearSelection = viewModel::clearSelection,
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = viewModel::dismissDelete,
        onConfirmCascade = viewModel::confirmCascade,
    )
    CenteredSnackbarHost(snackbar)
    }
}

@Composable
fun ContactsContent(
    state: ContactsUiState,
    onQueryChange: (String) -> Unit,
    onSortChange: (ContactSort) -> Unit,
    onContactClick: (Long) -> Unit,
    onAddContact: () -> Unit,
    onRetry: () -> Unit = {},
    onImportContacts: () -> Unit = {},
    onEnterSelection: () -> Unit = {},
    onExitSelection: () -> Unit = {},
    onToggleContact: (Long) -> Unit = {},
    onSelectAllVisible: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onRequestDelete: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onDismissDelete: () -> Unit = {},
    onConfirmCascade: (Boolean) -> Unit = {},
) {
    val busy = state.isDeleting || state.isPreparingDelete
    Scaffold(
        topBar = {
            AppTopBar(
                if (state.isSelectionMode) stringResource(R.string.contact_bulk_selected, state.selectedContactIds.size)
                else stringResource(R.string.nav_contacts),
                action = {
                    if (state.isSelectionMode) {
                        TextButton(onExitSelection, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
                        TextButton(onSelectAllVisible, enabled = !busy && !state.isLoading && !state.error && state.contacts.isNotEmpty()) {
                            Text(stringResource(R.string.contact_bulk_select_all))
                        }
                    } else TextButton(onEnterSelection) { Text(stringResource(R.string.contact_bulk_manage)) }
                },
            )
        },
        bottomBar = {
            if (state.isSelectionMode) Surface(shadowElevation = 4.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    TextButton(onClearSelection, enabled = !busy && state.selectedContactIds.isNotEmpty()) {
                        Text(stringResource(R.string.contact_bulk_clear))
                    }
                    Button(onRequestDelete, enabled = !busy && state.selectedContactIds.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.contact_bulk_delete_action, state.selectedContactIds.size))
                    }
                }
            }
        },
        floatingActionButton = {
            if (!state.isSelectionMode) {
            Box(Modifier.padding(bottom = 96.dp)) {
                FloatingActionButton(
                    onClick = onAddContact,
                    containerColor = CoralPrimary,
                    contentColor = Color.White,
                    shape = CircleShape,
                ) {
                    Icon(
                        Icons.Outlined.PersonAdd,
                        stringResource(R.string.contact_create),
                        Modifier.size(28.dp),
                    )
                }
            }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 124.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { PageIllustration(R.drawable.page_contacts_cat, Modifier.fillMaxWidth().height(190.dp)) }
            if (!state.isSelectionMode) item {
                Card(onClick = onImportContacts, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.contact_import_entry), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.contact_import_entry_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                AppTextField(
                    state.query,
                    onQueryChange,
                    stringResource(R.string.contact_search_hint),
                    leadingIcon = Icons.Outlined.Search,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        state.sort == ContactSort.RECENT,
                        { onSortChange(ContactSort.RECENT) },
                        { Text(stringResource(R.string.contact_sort_recent)) },
                    )
                    FilterChip(
                        state.sort == ContactSort.NAME,
                        { onSortChange(ContactSort.NAME) },
                        { Text(stringResource(R.string.contact_sort_name)) },
                    )
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    when {
                        state.isLoading -> Box(Modifier.height(220.dp)) { LoadingState() }
                        state.error -> ErrorState(onRetry)
                        state.contacts.isEmpty() -> EmptyState(
                            if (state.query.isBlank()) stringResource(R.string.contact_empty) else stringResource(R.string.contact_no_result),
                            image = R.drawable.page_contacts_cat,
                        )
                        else -> Column(Modifier.padding(horizontal = 16.dp)) {
                            state.contacts.forEachIndexed { index, item ->
                                key(item.contact.id) {
                                    if (state.isSelectionMode) Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(item.contact.id in state.selectedContactIds,
                                            onCheckedChange = { onToggleContact(item.contact.id) }, enabled = !busy)
                                        Box(Modifier.weight(1f)) {
                                            val masked = item.copy(contact = item.contact.copy(phone = item.contact.phone?.let(ContactImportRules::maskPhone)))
                                            ContactListItem(masked) { if (!busy) onToggleContact(item.contact.id) }
                                        }
                                    } else ContactListItem(item) { onContactClick(item.contact.id) }
                                }
                                if (index < state.contacts.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    state.deletePreview?.let { preview ->
        val hasRecords = preview.giftRecordCount > 0
        AlertDialog(
            onDismissRequest = { if (!state.isDeleting) onDismissDelete() },
            title = { Text(stringResource(R.string.contact_bulk_confirm_title, preview.contactCount)) },
            text = {
                Column(verticalArrangement = spacedBy(12.dp)) {
                    if (state.deletePreviewChanged) Text(stringResource(R.string.contact_bulk_preview_changed), color = MaterialTheme.colorScheme.error)
                    Text(if (hasRecords) stringResource(R.string.contact_bulk_confirm_records, preview.contactsWithGiftRecords, preview.giftRecordCount)
                        else stringResource(R.string.contact_bulk_confirm_empty))
                    if (hasRecords) Row(
                        Modifier.fillMaxWidth().toggleable(state.cascadeConfirmed, enabled = !state.isDeleting, role = Role.Checkbox) { onConfirmCascade(it) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(state.cascadeConfirmed, onCheckedChange = null, enabled = !state.isDeleting)
                        Text(stringResource(R.string.contact_bulk_acknowledge, preview.giftRecordCount), Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                TextButton(onConfirmDelete, enabled = !state.isDeleting && (!hasRecords || state.cascadeConfirmed),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    if (state.isDeleting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(if (hasRecords) R.string.contact_bulk_confirm_danger else R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onDismissDelete, enabled = !state.isDeleting) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
