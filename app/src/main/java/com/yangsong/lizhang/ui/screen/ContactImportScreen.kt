package com.yangsong.lizhang.ui.screen
import com.yangsong.lizhang.ui.component.GlassCheckbox
import com.yangsong.lizhang.ui.component.pressToggleable
import com.yangsong.lizhang.ui.component.GlassSurface
import com.yangsong.lizhang.ui.component.AppScaffold
import com.yangsong.lizhang.ui.component.GlassTextButton
import com.yangsong.lizhang.ui.component.GlassButton
import com.yangsong.lizhang.ui.component.GlassCard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.contact.ContactImportRules
import com.yangsong.lizhang.domain.model.ContactImportResult
import com.yangsong.lizhang.domain.model.ContactImportStatus
import com.yangsong.lizhang.ui.component.AppTextField
import com.yangsong.lizhang.ui.component.AppTopBar
import com.yangsong.lizhang.ui.viewmodel.*
import com.yangsong.lizhang.domain.onboarding.ExplainedPermission
import com.yangsong.lizhang.domain.onboarding.PermissionPolicy
import com.yangsong.lizhang.domain.onboarding.PermissionAction
import com.yangsong.lizhang.ui.onboarding.PermissionExplanationDialog

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
fun ContactImportScreen(
    viewModel: ContactImportViewModel,
    onBack: () -> Unit,
    onImported: (ContactImportResult) -> Unit,
    onboardingViewModel: OnboardingViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.activity()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val permission = ExplainedPermission.CONTACTS
    var launched by rememberSaveable { mutableStateOf(false) }
    var requesting by rememberSaveable { mutableStateOf(false) }
    var showExplanation by rememberSaveable { mutableStateOf(false) }
    var explanationBlocked by rememberSaveable { mutableStateOf(false) }
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    fun deniedState() = if (activity != null &&
        onboardingViewModel.permissionHistory(permission).requested &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CONTACTS)
    ) ContactPermissionState.BLOCKED else ContactPermissionState.DENIED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        requesting = false
        viewModel.permissionChanged(if (allowed) ContactPermissionState.GRANTED else deniedState())
    }
    val request: () -> Unit = {
        if (!requesting) {
            onboardingViewModel.markPermissionRequested(permission)
            requesting = true
            launcher.launch(Manifest.permission.READ_CONTACTS)
        }
    }
    val explainOrRequest: () -> Unit = {
        val rationale = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.READ_CONTACTS) } == true
        when (PermissionPolicy.action(granted(), onboardingViewModel.permissionHistory(permission), rationale)) {
            PermissionAction.USE_FEATURE -> viewModel.permissionChanged(ContactPermissionState.GRANTED)
            PermissionAction.REQUEST -> request()
            PermissionAction.EXPLAIN, PermissionAction.OPEN_SETTINGS -> {
                explanationBlocked = deniedState() == ContactPermissionState.BLOCKED
                onboardingViewModel.markExplanationSeen(permission)
                showExplanation = true
            }
        }
    }
    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            when {
                granted() -> viewModel.permissionChanged(ContactPermissionState.GRANTED)
                !onboardingViewModel.permissionHistory(permission).requested -> {
                    viewModel.permissionChanged(ContactPermissionState.DENIED)
                    explainOrRequest()
                }
                else -> viewModel.permissionChanged(deniedState())
            }
        }
    }
    DisposableEffect(lifecycle, requesting) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !requesting) {
                if (granted()) viewModel.permissionChanged(ContactPermissionState.GRANTED)
                else if (launched) viewModel.permissionChanged(deniedState())
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.importResult) { state.importResult?.let(onImported) }
    BackHandler(state.isImporting) { /* 等待事务完成，避免导入结果在离开页面后丢失。 */ }
    ContactImportContent(
        state, onBack = onBack, onQuery = viewModel::updateQuery,
        onToggle = viewModel::toggle, onSelectAll = viewModel::selectAll,
        onImport = viewModel::importSelected, onRetry = viewModel::load,
        onPermission = explainOrRequest,
    )
    if (showExplanation) PermissionExplanationDialog(permission, explanationBlocked,
        onDismiss = { showExplanation = false },
        onContinue = {
            showExplanation = false
            if (explanationBlocked) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
            else if (granted()) viewModel.permissionChanged(ContactPermissionState.GRANTED) else request()
        })
}

@Composable
fun ContactImportContent(
    state: ContactImportUiState,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onImport: () -> Unit,
    onRetry: () -> Unit,
    onPermission: () -> Unit,
) {
    AppScaffold(
        topBar = { AppTopBar(stringResource(R.string.contact_import_title), if (state.isImporting) null else onBack) },
        bottomBar = {
            GlassSurface {
                GlassButton(
                    onClick = onImport,
                    enabled = state.selectedKeys.isNotEmpty() && !state.isLoading && !state.isImporting &&
                        state.permissionState == ContactPermissionState.GRANTED,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) {
                    if (state.isImporting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(androidx.compose.ui.res.pluralStringResource(R.plurals.contact_import_action, state.selectedKeys.size, state.selectedKeys.size))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.contact_import_privacy), style = MaterialTheme.typography.bodySmall) }
            if (state.permissionState != ContactPermissionState.GRANTED) {
                item {
                    Text(stringResource(if (state.permissionState == ContactPermissionState.BLOCKED) R.string.contact_import_permission_blocked else R.string.contact_import_permission_denied))
                    GlassButton(onClick = onPermission) {
                        Text(stringResource(if (state.permissionState == ContactPermissionState.BLOCKED) R.string.contact_import_settings else R.string.contact_import_authorize))
                    }
                    GlassTextButton(onClick = onBack) { Text(stringResource(R.string.contact_import_back)) }
                }
            } else if (state.isLoading) {
                item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else {
                item {
                    Text(stringResource(R.string.contact_import_description))
                    AppTextField(state.query, onQuery, stringResource(R.string.contact_import_search))
                    Row {
                        GlassTextButton(onClick = { onSelectAll(true) }, enabled = !state.isImporting) { Text(stringResource(R.string.contact_import_select_all)) }
                        GlassTextButton(onClick = { onSelectAll(false) }, enabled = !state.isImporting) { Text(stringResource(R.string.contact_import_deselect_all)) }
                    }
                }
                state.error?.let { error ->
                    item {
                        Text(stringResource(if (error == ContactImportError.READ) R.string.contact_import_read_failed else R.string.contact_import_failed), color = MaterialTheme.colorScheme.error)
                        if (error == ContactImportError.READ) GlassTextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                    }
                }
                if (state.loaded && state.contacts.isEmpty()) {
                    item { Text(stringResource(R.string.contact_import_empty)) }
                } else if (state.loaded && state.contacts.all { it.exists }) {
                    item { Text(stringResource(R.string.contact_import_all_exist)) }
                }
                if (state.contacts.isNotEmpty() && state.visibleContacts.isEmpty()) {
                    item { Text(stringResource(R.string.contact_no_result)) }
                }
                items(state.visibleContacts, key = { it.contact.phone }) { row ->
                    ContactImportRow(row, row.contact.phone in state.selectedKeys, !state.isImporting, onToggle)
                }
            }
        }
    }
}

@Composable
private fun ContactImportRow(row: ContactImportCandidate, selected: Boolean, enabled: Boolean, onToggle: (String) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().pressToggleable(selected, enabled = enabled && !row.exists, role = Role.Checkbox) { onToggle(row.contact.phone) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassCheckbox(selected, onCheckedChange = null, enabled = enabled && !row.exists)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(row.contact.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (row.exists) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                Text(ContactImportRules.maskPhone(row.contact.phone), style = MaterialTheme.typography.bodySmall)
                if (row.exists) Text(stringResource(R.string.contact_import_exists), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (row.status == ContactImportStatus.POSSIBLE_DUPLICATE) Text(stringResource(R.string.contact_import_same_name), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
