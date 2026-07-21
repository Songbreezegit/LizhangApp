package com.yangsong.lizhang.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.theme.CoralContainer
import com.yangsong.lizhang.ui.viewmodel.*
import java.io.File

@Composable
fun OcrImportScreen(viewModel: OcrImportViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraUri?.let { viewModel.recognize(it.toString()) }
    }
    val albumLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.recognize(it.toString()) }
    }
    Scaffold(topBar = { AppTopBar(stringResource(R.string.nav_ocr), onBack) }) { padding ->
        OcrImportContent(
            state = state,
            modifier = Modifier.padding(padding),
            onCamera = {
                val uri = createOcrPhotoUri(context)
                cameraUri = uri
                cameraLauncher.launch(uri)
            },
            onAlbum = { albumLauncher.launch("image/*") },
            onRetry = viewModel::reset,
            onUpdate = viewModel::update,
            onDelete = viewModel::remove,
            onConfirm = viewModel::confirmImport,
        )
    }
}

@Composable
fun OcrImportContent(
    state: OcrImportUiState,
    modifier: Modifier = Modifier,
    onCamera: () -> Unit,
    onAlbum: () -> Unit,
    onRetry: () -> Unit,
    onUpdate: (OcrPendingRecordUi) -> Unit,
    onDelete: (Long) -> Unit,
    onConfirm: () -> Unit,
) {
    var showConfirm by remember { mutableStateOf(false) }
    when (state.stage) {
        OcrStage.INTRO -> Column(
            modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageIllustration(R.drawable.page_add_cat, Modifier.fillMaxWidth().height(210.dp))
            OcrNoticeCard()
            Spacer(Modifier.weight(1f))
            PrimaryButton(stringResource(R.string.ocr_camera), onCamera, Modifier.fillMaxWidth(), icon = Icons.Outlined.PhotoCamera)
            SecondaryButton(stringResource(R.string.ocr_album), onAlbum, Modifier.fillMaxWidth(), icon = Icons.Outlined.PhotoLibrary)
        }

        OcrStage.RECOGNIZING, OcrStage.IMPORTING -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator()
                Text(stringResource(if (state.stage == OcrStage.RECOGNIZING) R.string.ocr_recognizing else R.string.ocr_importing))
            }
        }

        OcrStage.EMPTY, OcrStage.ERROR -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = stringResource(if (state.stage == OcrStage.EMPTY) R.string.ocr_empty else R.string.ocr_failed),
                description = stringResource(R.string.ocr_retry_description),
                action = stringResource(R.string.action_retry),
                onAction = onRetry,
                image = R.drawable.page_add_cat,
            )
        }

        OcrStage.PENDING -> LazyColumn(
            modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { OcrNoticeCard() }
            item { Text(stringResource(R.string.ocr_default_fields), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (state.validationFailed) {
                item { Text(stringResource(R.string.ocr_validation_failed), color = MaterialTheme.colorScheme.error) }
            }
            items(state.records, key = { it.id }) { record ->
                OcrPendingRecordItem(record, onUpdate, { onDelete(record.id) })
            }
            item {
                PrimaryButton(
                    stringResource(R.string.ocr_confirm_import),
                    { showConfirm = true },
                    Modifier.fillMaxWidth(),
                    enabled = state.records.isNotEmpty(),
                )
            }
        }

        OcrStage.SUCCESS -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                stringResource(R.string.ocr_success_count, state.importedCount),
                action = stringResource(R.string.action_done),
                onAction = onRetry,
                image = R.drawable.page_add_cat,
            )
        }
    }
    if (showConfirm) ConfirmDialog(
        title = stringResource(R.string.ocr_confirm_import),
        message = stringResource(R.string.ocr_pending_notice),
        confirmText = stringResource(R.string.action_confirm),
        cancelText = stringResource(R.string.action_cancel),
        onConfirm = { showConfirm = false; onConfirm() },
        onDismiss = { showConfirm = false },
    )
}

@Composable
private fun OcrNoticeCard() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ocr_intro_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.ocr_intro_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = RoundedCornerShape(16.dp), color = CoralContainer) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.VerifiedUser, null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.ocr_pending_notice))
                }
            }
        }
    }
}

private fun createOcrPhotoUri(context: android.content.Context): Uri {
    val directory = File(context.cacheDir, "ocr").apply { mkdirs() }
    val photo = File.createTempFile("lizhang_", ".jpg", directory)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photo)
}
