package com.yangsong.lizhang.ui.screen
import com.yangsong.lizhang.ui.component.AppScaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.yangsong.lizhang.ui.component.GlassTextButton
import com.yangsong.lizhang.ui.component.GlassButton
import com.yangsong.lizhang.ui.component.GlassCard
import com.yangsong.lizhang.ui.component.GlassChip
import com.yangsong.lizhang.ui.component.GlassDialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.testTag
import com.yangsong.lizhang.ui.component.GlassAction
import com.yangsong.lizhang.ui.component.GlassActionMenu
import com.yangsong.lizhang.ui.component.GlassSearchBar
import com.yangsong.lizhang.ui.component.GlassSurface
import com.yangsong.lizhang.ui.component.GlassTokens
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.AppTopBar
import com.yangsong.lizhang.ui.component.ContactListItem
import com.yangsong.lizhang.ui.component.EmptyState
import com.yangsong.lizhang.ui.component.ErrorState
import com.yangsong.lizhang.ui.component.LoadingState
import com.yangsong.lizhang.ui.component.PageIllustration
import com.yangsong.lizhang.ui.viewmodel.ContactSort
import com.yangsong.lizhang.ui.viewmodel.ContactsUiState
import com.yangsong.lizhang.ui.viewmodel.ContactsViewModel

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
    initiallyExpanded: Boolean = false,
) {
    val busy = state.isDeleting || state.isPreparingDelete
    val focusManager = LocalFocusManager.current
    var menuExpanded by remember { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(state.isSelectionMode) { if (state.isSelectionMode) menuExpanded = false }
    BackHandler(menuExpanded && !state.isSelectionMode) { menuExpanded = false }
    Box(Modifier.fillMaxSize()) {
    AppScaffold(
        topBar = {
            if (state.isSelectionMode) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    GlassTextButton(onExitSelection, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
                    Text(stringResource(R.string.contact_bulk_selected, state.selectedContactIds.size),
                        Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    GlassTextButton(onSelectAllVisible, enabled = !busy && !state.isLoading && !state.error && state.contacts.isNotEmpty()) {
                        Text(stringResource(R.string.contact_bulk_select_all))
                    }
                }
            } else AppTopBar(stringResource(R.string.nav_contacts), action = {
                GlassTextButton({ menuExpanded = false; focusManager.clearFocus(); onEnterSelection() }) {
                    Text(stringResource(R.string.contact_bulk_manage))
                }
            })
        },
        bottomBar = {
            if (state.isSelectionMode) GlassSurface {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    GlassTextButton(onClearSelection, enabled = !busy && state.selectedContactIds.isNotEmpty()) {
                        Text(stringResource(R.string.contact_bulk_clear))
                    }
                    GlassButton(onRequestDelete, enabled = !busy && state.selectedContactIds.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.contact_bulk_delete_action, state.selectedContactIds.size))
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("联系人列表"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp,
                bottom = if (state.isSelectionMode) 24.dp else GlassTokens.ListBottomClearance),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!state.isSelectionMode) item {
                PageIllustration(R.drawable.page_contacts_cat, Modifier.fillMaxWidth().height(88.dp))
            }
            item { GlassSearchBar(state.query, onQueryChange, stringResource(R.string.contact_search_hint)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(state.sort == ContactSort.RECENT, { onSortChange(ContactSort.RECENT) },
                        { Text(stringResource(R.string.contact_sort_recent)) })
                    GlassChip(state.sort == ContactSort.NAME, { onSortChange(ContactSort.NAME) },
                        { Text(stringResource(R.string.contact_sort_name)) })
                }
            }
            when {
                state.isLoading -> item { Box(Modifier.height(220.dp)) { LoadingState() } }
                state.error -> item { ErrorState(onRetry) }
                state.contacts.isEmpty() -> item { EmptyState(
                    if (state.query.isBlank()) stringResource(R.string.contact_empty) else stringResource(R.string.contact_no_result),
                    image = R.drawable.page_contacts_cat,
                ) }
                else -> items(state.contacts, key = { it.contact.id }, contentType = { "联系人" }) { item ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Box(Modifier.padding(horizontal = 14.dp)) {
                            if (state.isSelectionMode) Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(item.contact.id in state.selectedContactIds,
                                    onCheckedChange = { onToggleContact(item.contact.id) }, enabled = !busy)
                                Box(Modifier.weight(1f)) {
                                    val masked = item.copy(contact = item.contact.copy(phone = item.contact.phone?.let(ContactImportRules::maskPhone)))
                                    ContactListItem(masked) { if (!busy) onToggleContact(item.contact.id) }
                                }
                            } else ContactListItem(item) { onContactClick(item.contact.id) }
                        }
                    }
                }
            }
        }
    }
    if (menuExpanded && !state.isSelectionMode) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = GlassTokens.ScrimAlpha)).testTag("关闭联系人菜单遮罩").clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
            onClickLabel = "关闭添加菜单",
        ) { menuExpanded = false })
    }
    if (!state.isSelectionMode) {
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = GlassTokens.BottomClearance)) {
            GlassActionMenu(menuExpanded, { focusManager.clearFocus(); menuExpanded = !menuExpanded }, { menuExpanded = false },
                listOf(GlassAction("通讯录导入", Icons.Outlined.Contacts, onImportContacts),
                    GlassAction("手动添加", Icons.Outlined.PersonAdd, onAddContact)))
        }
    }
    }
    state.deletePreview?.let { preview ->
        val hasRecords = preview.giftRecordCount > 0
        GlassDialog(
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
                GlassTextButton(onConfirmDelete, enabled = !state.isDeleting && (!hasRecords || state.cascadeConfirmed),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    if (state.isDeleting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(if (hasRecords) R.string.contact_bulk_confirm_danger else R.string.action_delete))
                }
            },
            dismissButton = { GlassTextButton(onDismissDelete, enabled = !state.isDeleting) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
