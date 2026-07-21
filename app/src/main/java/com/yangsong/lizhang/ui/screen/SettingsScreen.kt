package com.yangsong.lizhang.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.AppTopBar
import com.yangsong.lizhang.ui.component.CenteredSnackbarHost
import com.yangsong.lizhang.ui.component.PageIllustration
import com.yangsong.lizhang.ui.component.SectionHeader
import com.yangsong.lizhang.ui.component.SettingsRow
import com.yangsong.lizhang.ui.viewmodel.ExportDocument
import com.yangsong.lizhang.ui.viewmodel.SettingsMessage
import com.yangsong.lizhang.ui.viewmodel.SettingsUiState
import com.yangsong.lizhang.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var documentToSave by remember { mutableStateOf<ExportDocument?>(null) }

    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val document = documentToSave
        documentToSave = null
        if (uri == null || document == null) {
            viewModel.reportCsvSaveCancelled()
        } else {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                            stream.write(document.content.toByteArray(Charsets.UTF_8))
                        } ?: error("无法打开导出文件")
                    }.isSuccess
                }
                viewModel.reportCsvSaveResult(saved)
            }
        }
    }

    LaunchedEffect(state.pendingCsv) {
        state.pendingCsv?.let { document ->
            documentToSave = document
            viewModel.consumePendingCsv()
            createCsv.launch(document.fileName)
        }
    }

    val message = when (state.message) {
        SettingsMessage.CSV_EMPTY -> stringResource(R.string.settings_csv_empty)
        SettingsMessage.CSV_PREPARE_FAILED -> stringResource(R.string.settings_csv_prepare_failed)
        SettingsMessage.CSV_SAVE_SUCCESS -> stringResource(R.string.settings_csv_saved)
        SettingsMessage.CSV_SAVE_FAILED -> stringResource(R.string.settings_csv_save_failed)
        SettingsMessage.CSV_SAVE_CANCELLED -> stringResource(R.string.settings_csv_cancelled)
        null -> null
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val unavailable = stringResource(R.string.settings_unavailable)
    SettingsContent(
        state = state,
        onCsvExport = viewModel::prepareCsvExport,
        onUnavailable = { scope.launch { snackbar.showSnackbar(unavailable) } },
        snackbarHost = { CenteredSnackbarHost(snackbar) },
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onCsvExport: () -> Unit,
    onUnavailable: () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.nav_settings)) },
        snackbarHost = snackbarHost,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 124.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { PageIllustration(R.drawable.page_settings_cat, Modifier.fillMaxWidth().height(190.dp)) }
            item {
                SettingsGroup(stringResource(R.string.settings_data)) {
                    SettingsRow(Icons.Outlined.Backup, stringResource(R.string.settings_backup), onClick = onUnavailable)
                    SettingsRow(Icons.Outlined.TableView, stringResource(R.string.settings_excel), onClick = onUnavailable)
                    SettingsRow(
                        Icons.Outlined.Description,
                        stringResource(R.string.settings_csv),
                        onClick = onCsvExport,
                        trailing = if (state.isPreparingCsv) {
                            { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                        } else null,
                    )
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_display)) {
                    SettingsRow(Icons.Outlined.Palette, stringResource(R.string.settings_theme), stringResource(R.string.settings_follow_system), onClick = onUnavailable)
                    SettingsRow(
                        Icons.Outlined.DarkMode,
                        stringResource(R.string.settings_dark),
                        stringResource(R.string.settings_follow_system),
                        onClick = onUnavailable,
                        trailing = { Switch(isSystemInDarkTheme(), null) },
                    )
                    SettingsRow(Icons.Outlined.TextFields, stringResource(R.string.settings_font), onClick = onUnavailable)
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_about_group)) {
                    SettingsRow(Icons.Outlined.Info, stringResource(R.string.settings_about), stringResource(R.string.settings_version), onClick = onUnavailable)
                    SettingsRow(Icons.Outlined.PrivacyTip, stringResource(R.string.settings_privacy), onClick = onUnavailable)
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        SectionHeader(title)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp), content = content)
        }
    }
}
