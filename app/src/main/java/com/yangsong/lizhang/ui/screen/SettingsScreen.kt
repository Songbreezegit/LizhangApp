package com.yangsong.lizhang.ui.screen
import com.yangsong.lizhang.ui.onboarding.FeatureGuideTarget
import com.yangsong.lizhang.ui.onboarding.featureGuideTarget
import com.yangsong.lizhang.ui.component.GlassSwitch
import com.yangsong.lizhang.ui.component.pressSelectable
import com.yangsong.lizhang.ui.component.LanguagePicker
import com.yangsong.lizhang.ui.component.LocalAppearanceActions
import com.yangsong.lizhang.ui.component.LocalPendingDark
import com.yangsong.lizhang.ui.component.LocalCurrentLanguage
import com.yangsong.lizhang.ui.theme.LocalEffectiveDarkTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.runtime.DisposableEffect
import com.yangsong.lizhang.ui.mapper.giftExportLabels
import com.yangsong.lizhang.ui.component.currentAppLanguage
import com.yangsong.lizhang.ui.component.displayName
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.ChevronRight
import com.yangsong.lizhang.ui.component.glassSwitchColors
import com.yangsong.lizhang.ui.component.AppScaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.yangsong.lizhang.ui.component.GlassTokens
import com.yangsong.lizhang.ui.component.GlassTextButton
import com.yangsong.lizhang.ui.component.GlassButton
import com.yangsong.lizhang.ui.component.GlassCard
import com.yangsong.lizhang.ui.component.GlassDialog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.yangsong.lizhang.ui.component.AppearanceListRestoration
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.core.util.DateFormatter
import com.yangsong.lizhang.domain.backup.BackupArchiveCodec
import com.yangsong.lizhang.domain.backup.BackupEncryptionCodec
import com.yangsong.lizhang.domain.model.AppThemeMode
import com.yangsong.lizhang.ui.component.AppTopBar
import com.yangsong.lizhang.ui.component.CenteredSnackbarHost
import com.yangsong.lizhang.ui.component.PageIllustration
import com.yangsong.lizhang.ui.component.SettingsRow
import com.yangsong.lizhang.ui.viewmodel.ExportDocument
import com.yangsong.lizhang.ui.viewmodel.ExportFormat
import com.yangsong.lizhang.ui.viewmodel.SettingsMessage
import com.yangsong.lizhang.ui.viewmodel.SettingsUiState
import com.yangsong.lizhang.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onFontGuide: () -> Unit,
    onAbout: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var documentToSave by remember { mutableStateOf<ExportDocument?>(null) }
    var showBackupActions by remember { mutableStateOf(false) }
    var showCreateBackupPassword by remember { mutableStateOf(false) }
    var showThemeOptions by remember { mutableStateOf(false) }
    val transition = LocalAppearanceActions.current
    val changeTheme: (AppThemeMode) -> Unit = { mode ->
        if (mode != state.themeMode) {
            if (transition != null) transition.colors { viewModel.setThemeMode(mode) }
            else viewModel.setThemeMode(mode)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if ((context as? android.app.Activity)?.isChangingConfigurations != true) transition?.cancel()
        }
    }

    fun saveDocument(uri: android.net.Uri?, format: ExportFormat) {
        val document = documentToSave?.takeIf { it.format == format }
        documentToSave = null
        if (uri == null || document == null) {
            viewModel.reportSaveCancelled(format)
        } else {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri, "w")?.use { stream ->
                            stream.write(document.bytes)
                        } ?: error("无法打开导出文件")
                    }.isSuccess
                }
                viewModel.reportSaveResult(format, saved)
            }
        }
    }

    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        saveDocument(uri, ExportFormat.CSV)
    }
    val createExcel = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri ->
        saveDocument(uri, ExportFormat.EXCEL)
    }
    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupArchiveCodec.MIME_TYPE),
    ) { uri ->
        saveDocument(uri, ExportFormat.BACKUP)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(it)?.use { stream -> stream.readBackupBytes() }
                            ?: error("无法读取备份文件")
                    }
                }
                bytes.onSuccess(viewModel::inspectBackup).onFailure { viewModel.reportBackupReadFailed() }
            }
        }
    }

    LaunchedEffect(state.pendingExport) {
        state.pendingExport?.let { document ->
            documentToSave = document
            viewModel.consumePendingExport()
            when (document.format) {
                ExportFormat.CSV -> createCsv.launch(document.fileName)
                ExportFormat.EXCEL -> createExcel.launch(document.fileName)
                ExportFormat.BACKUP -> createBackup.launch(document.fileName)
            }
        }
    }

    val message = when (state.message) {
        SettingsMessage.EXPORT_EMPTY -> stringResource(R.string.settings_export_empty)
        SettingsMessage.CSV_PREPARE_FAILED -> stringResource(R.string.settings_csv_prepare_failed)
        SettingsMessage.EXCEL_PREPARE_FAILED -> stringResource(R.string.settings_excel_prepare_failed)
        SettingsMessage.CSV_SAVE_SUCCESS -> stringResource(R.string.settings_csv_saved)
        SettingsMessage.EXCEL_SAVE_SUCCESS -> stringResource(R.string.settings_excel_saved)
        SettingsMessage.CSV_SAVE_FAILED -> stringResource(R.string.settings_csv_save_failed)
        SettingsMessage.EXCEL_SAVE_FAILED -> stringResource(R.string.settings_excel_save_failed)
        SettingsMessage.CSV_SAVE_CANCELLED -> stringResource(R.string.settings_csv_cancelled)
        SettingsMessage.EXCEL_SAVE_CANCELLED -> stringResource(R.string.settings_excel_cancelled)
        SettingsMessage.BACKUP_PREPARE_FAILED -> stringResource(R.string.settings_backup_prepare_failed)
        SettingsMessage.BACKUP_SAVE_SUCCESS -> stringResource(R.string.settings_backup_saved)
        SettingsMessage.BACKUP_SAVE_FAILED -> stringResource(R.string.settings_backup_save_failed)
        SettingsMessage.BACKUP_SAVE_CANCELLED -> stringResource(R.string.settings_backup_save_cancelled)
        SettingsMessage.BACKUP_READ_FAILED -> stringResource(R.string.settings_backup_read_failed)
        SettingsMessage.BACKUP_RESTORE_SUCCESS -> stringResource(R.string.settings_backup_restore_success)
        SettingsMessage.BACKUP_RESTORE_FAILED -> stringResource(R.string.settings_backup_restore_failed)
        null -> null
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    SettingsContent(
        state = state,
        onCsvExport = { viewModel.prepareCsvExport(context.giftExportLabels()) },
        onExcelExport = { viewModel.prepareExcelExport(context.giftExportLabels()) },
        onBackup = { showBackupActions = true },
        onThemeModeChange = viewModel::setThemeMode,
        onThemeOptions = { showThemeOptions = true },
        onFontGuide = onFontGuide,
        onAbout = onAbout,
        onPrivacy = onPrivacy,
        snackbarHost = { CenteredSnackbarHost(snackbar) },
    )

    if (showBackupActions) {
        GlassDialog(
            onDismissRequest = { showBackupActions = false },
            title = { Text(stringResource(R.string.settings_backup)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_backup_description))
                    GlassButton(
                        onClick = {
                            showBackupActions = false
                            showCreateBackupPassword = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_backup_create_encrypted))
                    }
                    GlassTextButton(
                        onClick = {
                            showBackupActions = false
                            viewModel.prepareBackupExport()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_backup_create_plain))
                    }
                    GlassTextButton(
                        onClick = {
                            showBackupActions = false
                            openBackup.launch(arrayOf(BackupArchiveCodec.MIME_TYPE))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_backup_restore_action))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                GlassTextButton(onClick = { showBackupActions = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showCreateBackupPassword) {
        CreateEncryptedBackupDialog(
            isPreparing = state.isPreparingBackup,
            onConfirm = { password ->
                showCreateBackupPassword = false
                viewModel.prepareBackupExport(password)
            },
            onDismiss = { showCreateBackupPassword = false },
        )
    }

    state.encryptedBackupAwaitingPassword?.let { encryptedBytes ->
        RestoreBackupPasswordDialog(
            fileIdentity = encryptedBytes,
            isReading = state.isReadingBackup,
            isPasswordInvalid = state.isBackupPasswordInvalid,
            onConfirm = viewModel::unlockEncryptedBackup,
            onDismiss = viewModel::cancelBackupPassword,
        )
    }

    if (showThemeOptions) {
        GlassDialog(
            onDismissRequest = { showThemeOptions = false },
            title = { Text(stringResource(R.string.settings_theme)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    ThemeModeOption(AppThemeMode.SYSTEM, state.themeMode, changeTheme)
                    ThemeModeOption(AppThemeMode.LIGHT, state.themeMode, changeTheme)
                    ThemeModeOption(AppThemeMode.DARK, state.themeMode, changeTheme)
                }
            },
            confirmButton = {
                GlassTextButton(onClick = { showThemeOptions = false }) {
                    Text(stringResource(R.string.action_done))
                }
            },
        )
    }

    state.pendingRestore?.let { pending ->
        GlassDialog(
            onDismissRequest = { if (!state.isRestoringBackup) viewModel.cancelRestore() },
            title = { Text(stringResource(R.string.settings_backup_restore_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_backup_restore_message,
                        com.yangsong.lizhang.ui.mapper.displayDateTime(pending.summary.createdTime),
                        pending.summary.contactCount,
                        pending.summary.giftRecordCount,
                        pending.summary.sourceDatabaseVersion,
                    ),
                )
            },
            confirmButton = {
                GlassButton(onClick = viewModel::confirmRestore, enabled = !state.isRestoringBackup) {
                    if (state.isRestoringBackup) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.settings_backup_restore_confirm))
                    }
                }
            },
            dismissButton = {
                GlassTextButton(onClick = viewModel::cancelRestore, enabled = !state.isRestoringBackup) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
fun CreateEncryptedBackupDialog(
    isPreparing: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    val passwordTooShort = attempted && password.length < BackupEncryptionCodec.MIN_PASSWORD_LENGTH
    val confirmationMismatch = attempted && password != confirmation

    GlassDialog(
        onDismissRequest = { if (!isPreparing) onDismiss() },
        title = { Text(stringResource(R.string.settings_backup_password_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_backup_password_create_description))
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.settings_backup_password),
                    isError = passwordTooShort,
                    supportingText = if (passwordTooShort) {
                        androidx.compose.ui.res.pluralStringResource(R.plurals.settings_backup_password_too_short, BackupEncryptionCodec.MIN_PASSWORD_LENGTH, BackupEncryptionCodec.MIN_PASSWORD_LENGTH,
                        )
                    } else null,
                )
                PasswordField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = stringResource(R.string.settings_backup_password_confirm),
                    isError = confirmationMismatch,
                    supportingText = if (confirmationMismatch) {
                        stringResource(R.string.settings_backup_password_mismatch)
                    } else null,
                )
            }
        },
        confirmButton = {
            GlassButton(
                onClick = {
                    attempted = true
                    if (password.length >= BackupEncryptionCodec.MIN_PASSWORD_LENGTH &&
                        password == confirmation
                    ) {
                        onConfirm(password)
                    }
                },
                enabled = !isPreparing,
            ) {
                Text(stringResource(R.string.settings_backup_create_encrypted))
            }
        },
        dismissButton = {
            GlassTextButton(onClick = onDismiss, enabled = !isPreparing) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
fun RestoreBackupPasswordDialog(
    fileIdentity: ByteArray,
    isReading: Boolean,
    isPasswordInvalid: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember(fileIdentity) { mutableStateOf("") }
    GlassDialog(
        onDismissRequest = { if (!isReading) onDismiss() },
        title = { Text(stringResource(R.string.settings_backup_password_restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_backup_password_restore_description))
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.settings_backup_password),
                    isError = isPasswordInvalid,
                    supportingText = if (isPasswordInvalid) {
                        stringResource(R.string.settings_backup_password_invalid)
                    } else null,
                )
            }
        },
        confirmButton = {
            GlassButton(
                onClick = { if (password.isNotEmpty()) onConfirm(password) },
                enabled = password.isNotEmpty() && !isReading,
            ) {
                if (isReading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.action_confirm))
                }
            }
        },
        dismissButton = {
            GlassTextButton(onClick = onDismiss, enabled = !isReading) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    supportingText: String?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = isError,
        supportingText = supportingText?.let { message -> { Text(message) } },
        shape = RoundedCornerShape(18.dp),
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onCsvExport: () -> Unit,
    onExcelExport: () -> Unit,
    onBackup: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onThemeOptions: () -> Unit = {},
    onFontGuide: () -> Unit = {},
    onAbout: () -> Unit = {},
    onPrivacy: () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
) {
    val transition = LocalAppearanceActions.current
    val effectiveDark = LocalPendingDark.current ?: LocalEffectiveDarkTheme.current
    var switchCenter by remember { mutableStateOf(Offset.Zero) }
    fun setDark(target: Boolean, source: String, suppliedDark: Boolean? = null) {
        (transition as? com.yangsong.lizhang.ui.component.AppearanceTransitionHost)?.themeCallback(source, target, suppliedDark)
        val change = { onThemeModeChange(if (target) AppThemeMode.DARK else AppThemeMode.LIGHT) }
        if (transition != null) transition.circular(switchCenter, target, change) else change()
    }
    var showLanguagePicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    AppearanceListRestoration(listState)
    if (showLanguagePicker) LanguagePicker { showLanguagePicker = false }
    AppScaffold(
        topBar = { AppTopBar(stringResource(R.string.nav_settings)) },
        snackbarHost = snackbarHost,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("设置列表"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 124.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            state = listState,
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    PageIllustration(R.drawable.page_settings_cat, Modifier.size(64.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                SettingsGroup {
                    SettingsRow(
                        Icons.Outlined.Backup,
                        stringResource(R.string.settings_backup),
                        onClick = onBackup,
                        modifier = Modifier.featureGuideTarget(FeatureGuideTarget.SETTINGS_PAGE),
                        accent = MaterialTheme.colorScheme.secondary,
                        trailing = if (
                            state.isPreparingBackup || state.isReadingBackup || state.isRestoringBackup
                        ) {
                            { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                        } else null,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SettingsRow(
                        Icons.Outlined.TableView,
                        stringResource(R.string.settings_excel),
                        onClick = onExcelExport,
                        accent = MaterialTheme.colorScheme.secondary,
                        trailing = if (state.isPreparingExcel) {
                            { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                        } else null,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SettingsRow(
                        Icons.Outlined.Description,
                        stringResource(R.string.settings_csv),
                        onClick = onCsvExport,
                        accent = MaterialTheme.colorScheme.secondary,
                        trailing = if (state.isPreparingCsv) {
                            { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                        } else null,
                    )
                }
            }
            item {
                SettingsGroup {
                    SettingsRow(
                        Icons.Outlined.Language,
                        stringResource(R.string.language),
                        onClick = { showLanguagePicker = true },
                        modifier = Modifier.testTag("语言设置行").onGloballyPositioned {
                            (transition as? com.yangsong.lizhang.ui.component.AppearanceTransitionHost)
                                ?.languageTargetBounds(it.boundsInWindow())
                        },
                        trailing = {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text((LocalCurrentLanguage.current ?: currentAppLanguage()).displayName(), Modifier.widthIn(max = 80.dp),
                                    style = MaterialTheme.typography.bodyMedium)
                                androidx.compose.material3.Icon(Icons.Outlined.ChevronRight, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    val themeDescription = when (state.themeMode) {
                        AppThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                        AppThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                        AppThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                    }
                    SettingsRow(
                        Icons.Outlined.Palette,
                        stringResource(R.string.settings_theme),
                        themeDescription,
                        onClick = onThemeOptions,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SettingsRow(
                        Icons.Outlined.DarkMode,
                        stringResource(R.string.settings_dark),
                        themeDescription,
                        onClick = { setDark(!effectiveDark, "row") },
                        diagnosticName = "深色模式行",
                        modifier = Modifier.testTag("深色模式行").onGloballyPositioned {
                            (transition as? com.yangsong.lizhang.ui.component.AppearanceTransitionHost)
                                ?.themeTargetBounds(false, it.boundsInWindow(), effectiveDark)
                        },
                        trailing = {
                            GlassSwitch(colors = glassSwitchColors(),
                                diagnosticName = "深色模式开关",
                                modifier = Modifier.testTag("深色模式开关").onGloballyPositioned {
                                    switchCenter = it.positionInWindow() + Offset(it.size.width / 2f, it.size.height / 2f)
                                    (transition as? com.yangsong.lizhang.ui.component.AppearanceTransitionHost)
                                        ?.themeTargetBounds(true, it.boundsInWindow(), effectiveDark)
                                },
                                checked = effectiveDark,
                                onCheckedChange = { setDark(it, "switch", it) },
                            )
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SettingsRow(
                        Icons.Outlined.TextFields,
                        stringResource(R.string.settings_font),
                        stringResource(R.string.settings_font_description),
                        onClick = onFontGuide,
                    )
                }
            }
            item {
                SettingsGroup {
                    SettingsRow(
                        Icons.Outlined.Info,
                        stringResource(R.string.settings_about),
                        stringResource(R.string.settings_version, com.yangsong.lizhang.BuildConfig.VERSION_NAME),
                        onClick = onAbout,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SettingsRow(
                        Icons.Outlined.PrivacyTip,
                        stringResource(R.string.settings_privacy),
                        stringResource(R.string.settings_privacy_description),
                        onClick = onPrivacy,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeModeOption(
    mode: AppThemeMode,
    selected: AppThemeMode,
    onSelect: (AppThemeMode) -> Unit,
) {
    val label = when (mode) {
        AppThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
        AppThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        AppThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
    }
    Row(
        Modifier.fillMaxWidth().pressSelectable(selected = selected == mode, role = Role.RadioButton,
            onClick = { onSelect(mode) }).padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected == mode, onClick = null, modifier = Modifier.size(48.dp))
        Text(label, Modifier.padding(top = 12.dp))
    }
}

private fun InputStream.readBackupBytes(): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        require(total <= BackupEncryptionCodec.MAX_DOCUMENT_BYTES) { "备份文件超过大小限制" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    GlassCard(
        modifier = Modifier.testTag("设置功能分组"),
        shape = RoundedCornerShape(GlassTokens.Radius),
    ) {
        Column(Modifier.padding(horizontal = 16.dp), content = content)
    }
}
