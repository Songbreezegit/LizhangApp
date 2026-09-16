package com.yangsong.lizhang.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.core.util.CurrencyFormatter
import com.yangsong.lizhang.core.util.DateFormatter
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.mapper.labelRes
import com.yangsong.lizhang.ui.theme.CoralStrong
import com.yangsong.lizhang.ui.theme.MintPrimary
import com.yangsong.lizhang.ui.viewmodel.GiftRecordDetailViewModel
import com.yangsong.lizhang.domain.model.GiftDirection

@Composable
fun GiftRecordDetailScreen(
    viewModel: GiftRecordDetailViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val deleteFailed = stringResource(R.string.record_delete_failed)

    LaunchedEffect(state.isDeleted) { if (state.isDeleted) onBack() }
    LaunchedEffect(state.deleteFailed) { if (state.deleteFailed) snackbar.showSnackbar(deleteFailed) }

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.record_detail_title), onBack) },
        snackbarHost = { CenteredSnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isLoading -> LoadingState()
            state.loadFailed -> ErrorState(viewModel::retry)
            else -> state.item?.let { item ->
                val record = item.record
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PageIllustration(R.drawable.page_add_cat, Modifier.fillMaxWidth().height(175.dp))
                    AmountSummaryCard(
                        label = stringResource(record.direction.labelRes()),
                        amount = record.amountInCents,
                        modifier = Modifier.fillMaxWidth(),
                        tint = if (record.direction == GiftDirection.RECEIVED) CoralStrong else MintPrimary,
                    )
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 18.dp)) {
                            DetailRow(stringResource(R.string.field_contact), item.contactName)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            DetailRow(stringResource(R.string.field_event), stringResource(record.eventType.labelRes()))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            DetailRow(stringResource(R.string.field_date), DateFormatter.format(record.eventDate))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            DetailRow(stringResource(R.string.field_direction), stringResource(record.direction.labelRes()))
                            if (!record.notes.isNullOrBlank()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                DetailRow(stringResource(R.string.field_notes), record.notes)
                            }
                        }
                    }
                    PrimaryButton(
                        text = stringResource(R.string.record_edit),
                        onClick = { onEdit(record.id) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.Edit,
                    )
                    SecondaryButton(
                        text = stringResource(R.string.record_delete),
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.DeleteOutline,
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }

    if (showDeleteConfirm) ConfirmDialog(
        title = stringResource(R.string.record_delete_title),
        message = stringResource(R.string.record_delete_message),
        confirmText = stringResource(R.string.action_delete),
        cancelText = stringResource(R.string.action_cancel),
        onConfirm = { showDeleteConfirm = false; viewModel.delete() },
        onDismiss = { showDeleteConfirm = false },
        danger = true,
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.width(78.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f))
    }
}
