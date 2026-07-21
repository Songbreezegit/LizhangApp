package com.yangsong.lizhang.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.viewmodel.ContactEditorViewModel

@Composable
fun ContactEditorScreen(viewModel: ContactEditorViewModel, onBack: () -> Unit, onDeleted: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val operationFailed = stringResource(R.string.contact_operation_failed)

    LaunchedEffect(state.isSaved, state.isDeleted) {
        when {
            state.isDeleted -> onDeleted()
            state.isSaved -> onBack()
        }
    }
    LaunchedEffect(state.operationFailed) {
        if (state.operationFailed) snackbar.showSnackbar(operationFailed)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(if (state.isNewContact) R.string.contact_create else R.string.contact_edit),
                onBack = onBack,
                action = if (state.isNewContact) null else {
                    {
                        IconButton(onClick = { showDeleteConfirm = true }, enabled = !state.isDeleting) {
                            Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.contact_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
        snackbarHost = { CenteredSnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isLoading -> LoadingState()
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PageIllustration(R.drawable.page_contacts_cat, Modifier.fillMaxWidth().height(145.dp))
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(stringResource(R.string.contact_basic_info), style = MaterialTheme.typography.titleMedium)
                        AppTextField(
                            value = state.name,
                            onValueChange = viewModel::updateName,
                            label = stringResource(R.string.contact_name),
                            error = if (state.nameError) stringResource(R.string.contact_name_required) else null,
                        )
                        AppTextField(state.phone, viewModel::updatePhone, stringResource(R.string.contact_phone))
                        AppTextField(state.relationship, viewModel::updateRelationship, stringResource(R.string.contact_relationship))
                        AppMultilineTextField(
                            state.notes,
                            viewModel::updateNotes,
                            stringResource(R.string.field_notes),
                            placeholder = stringResource(R.string.contact_notes_hint),
                        )
                    }
                }
                PrimaryButton(
                    text = stringResource(R.string.contact_save),
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth(),
                    loading = state.isSaving,
                    enabled = !state.isDeleting,
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showDeleteConfirm) ConfirmDialog(
        title = stringResource(R.string.contact_delete_title),
        message = stringResource(R.string.contact_delete_message),
        confirmText = stringResource(R.string.action_delete),
        cancelText = stringResource(R.string.action_cancel),
        onConfirm = { showDeleteConfirm = false; viewModel.delete() },
        onDismiss = { showDeleteConfirm = false },
        danger = true,
    )
}
