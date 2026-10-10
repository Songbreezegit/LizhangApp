package com.yangsong.lizhang.ui.screen
import com.yangsong.lizhang.ui.component.gradientTextStyle
import com.yangsong.lizhang.ui.onboarding.FeatureGuideTarget
import com.yangsong.lizhang.ui.onboarding.featureGuideTarget
import com.yangsong.lizhang.ui.component.glassSwitchColors
import com.yangsong.lizhang.ui.component.AppScaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.yangsong.lizhang.ui.component.GlassTextButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import com.yangsong.lizhang.ui.component.GlassIconButton
import com.yangsong.lizhang.ui.component.GlassCard
import com.yangsong.lizhang.ui.component.GlassDialog
import com.yangsong.lizhang.ui.mapper.displayDate

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.yangsong.lizhang.domain.reminder.IndependentReminder
import com.yangsong.lizhang.domain.reminder.IndependentReminderPlanner
import com.yangsong.lizhang.domain.reminder.ReminderSettings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yangsong.lizhang.domain.onboarding.*
import com.yangsong.lizhang.ui.onboarding.PermissionExplanationDialog
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.reminder.supportedReminderAdvanceDays
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.viewmodel.*
import kotlinx.coroutines.launch

@Composable
fun DirectionRecordsScreen(
    viewModel: DirectionRecordsViewModel,
    direction: GiftDirection,
    onBack: () -> Unit,
    onRecordClick: (Long) -> Unit,
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val title = stringResource(if (direction == GiftDirection.RECEIVED) R.string.shortcut_received else R.string.shortcut_given)
    AppScaffold(topBar = { AppTopBar(title, onBack) }) { padding ->
        when {
            state.isLoading -> LoadingState()
            state.error -> ErrorState(viewModel::retry)
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    SectionHeader(androidx.compose.ui.res.pluralStringResource(R.plurals.records_count, state.records.size, state.records.size))
                }
                if (state.records.isEmpty()) {
                    item { EmptyState(stringResource(R.string.records_empty, title), image = R.drawable.page_add_cat) }
                } else {
                    item {
                        GlassCard(shape = RoundedCornerShape(GlassTokens.Radius)) {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                state.records.forEachIndexed { index, record ->
                                    GiftRecordListItem(record) { onRecordClick(record.record.id) }
                                    if (index < state.records.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarScreen(viewModel: CalendarViewModel, onBack: () -> Unit, onRecordClick: (Long) -> Unit) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    AppScaffold(topBar = { AppTopBar(stringResource(R.string.shortcut_calendar), onBack) }) { padding ->
        when {
            state.isLoading -> LoadingState()
            state.error -> ErrorState(viewModel::retry)
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { CalendarCard(state, viewModel::previousMonth, viewModel::nextMonth, viewModel::selectDay) }
                item { SectionHeader(stringResource(R.string.calendar_records_on, com.yangsong.lizhang.ui.mapper.displayCalendarDate(state.year, state.month, state.selectedDay))) }
                if (state.selectedRecords.isEmpty()) {
                    item { EmptyState(stringResource(R.string.calendar_empty), image = R.drawable.page_add_cat) }
                } else {
                    items(
                        items = state.selectedRecords,
                        key = { item -> item.record.id },
                    ) { item ->
                        GiftRecordListItem(item) { onRecordClick(item.record.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarCard(state: CalendarUiState, onPrevious: () -> Unit, onNext: () -> Unit, onDay: (Int) -> Unit) {
    GlassCard(Modifier.featureGuideTarget(FeatureGuideTarget.CALENDAR), shape = RoundedCornerShape(GlassTokens.Radius)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(onPrevious) { Icon(LiZhangIcons.ChevronLeft, stringResource(R.string.calendar_previous), tint = featureIconColor()) }
                Text(com.yangsong.lizhang.ui.mapper.displayYearMonth(state.year, state.month), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                GlassIconButton(onNext) { Icon(LiZhangIcons.ChevronRight, stringResource(R.string.calendar_next), tint = featureIconColor()) }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf(R.string.week_monday, R.string.week_tuesday, R.string.week_wednesday, R.string.week_thursday, R.string.week_friday, R.string.week_saturday, R.string.week_sunday).forEach {
                    Text(stringResource(it), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }
            val cells = List(state.firstDayOffset) { 0 } + (1..state.daysInMonth).toList()
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    (week + List(7 - week.size) { 0 }).forEach { day ->
                        if (day == 0) Spacer(Modifier.weight(1f).aspectRatio(1f))
                        else DayCell(day, day == state.selectedDay, day in state.recordDays, { onDay(day) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, selected: Boolean, hasRecord: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassClickableSurface(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f).padding(2.dp),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(day.toString(), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            if (hasRecord) Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(5.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {}
        }
    }
}

@Composable
@SuppressLint("InlinedApi")
fun NotificationsScreen(viewModel: NotificationsViewModel, onBack: () -> Unit, onboardingViewModel: OnboardingViewModel) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val permission = ExplainedPermission.NOTIFICATIONS
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var showExplanation by rememberSaveable { mutableStateOf(false) }
    var explanationBlocked by rememberSaveable { mutableStateOf(false) }
    var requestingPermission by rememberSaveable { mutableStateOf(false) }
    var enablingFromSettings by rememberSaveable { mutableStateOf(false) }
    var showAdvanceDialog by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<IndependentReminder?>(null) }
    var deleting by remember { mutableStateOf<IndependentReminder?>(null) }
    val permissionDenied = stringResource(R.string.reminder_permission_denied)
    fun hasNotificationPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        requestingPermission = false
        if (granted) viewModel.setRemindersEnabled(true)
        else scope.launch { snackbar.showSnackbar(permissionDenied) }
    }
    val requestPermission: () -> Unit = {
        if (!requestingPermission) {
            onboardingViewModel.markPermissionRequested(permission)
            requestingPermission = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val enableReminders: () -> Unit = {
        val rationale = (context as? Activity)?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
        } == true
        when (PermissionPolicy.action(hasNotificationPermission(), onboardingViewModel.permissionHistory(permission), rationale)) {
            PermissionAction.USE_FEATURE -> viewModel.setRemindersEnabled(true)
            PermissionAction.REQUEST -> requestPermission()
            PermissionAction.EXPLAIN, PermissionAction.OPEN_SETTINGS -> {
                explanationBlocked = onboardingViewModel.permissionHistory(permission).requested && !rationale
                onboardingViewModel.markExplanationSeen(permission)
                showExplanation = true
            }
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && enablingFromSettings) {
                enablingFromSettings = false
                if (hasNotificationPermission()) viewModel.setRemindersEnabled(true)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.remindersEnabled) {
        if (state.remindersEnabled && !hasNotificationPermission()) {
            viewModel.setRemindersEnabled(false)
        }
    }

    NotificationsContent(state, onBack, viewModel::retry, snackbar,
        onEnabledChange = { enabled ->
            when {
                !enabled -> viewModel.setRemindersEnabled(false)
                else -> enableReminders()
            }
        },
        onAdvanceClick = { showAdvanceDialog = true },
        onTimeClick = { showTimeDialog = true },
        onAdd = { editing = null; showEditor = true },
        onEdit = { editing = it; showEditor = true },
        onDelete = { deleting = it },
        onReminderEnabled = viewModel::setReminderEnabled,
    )
    if (showExplanation) PermissionExplanationDialog(permission, explanationBlocked,
        onDismiss = { showExplanation = false },
        onContinue = {
            showExplanation = false
            if (explanationBlocked) {
                enablingFromSettings = true
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            } else if (hasNotificationPermission()) viewModel.setRemindersEnabled(true) else requestPermission()
        })
    if (showEditor) IndependentReminderEditor(editing, { showEditor = false }, viewModel::saveReminder)
    deleting?.let { value ->
        GlassDialog(onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.independent_delete_title)) },
            text = { Text(stringResource(R.string.independent_delete_desc)) },
            confirmButton = { GlassTextButton({ viewModel.deleteReminder(value.id); deleting = null }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { GlassTextButton({ deleting = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
    if (showAdvanceDialog) {
        ReminderAdvanceDialog(
            selectedDays = state.reminderAdvanceDays,
            onDismiss = { showAdvanceDialog = false },
            onSelect = { days ->
                viewModel.updateReminderSchedule(days, state.reminderHour, state.reminderMinute)
                showAdvanceDialog = false
            },
        )
    }
    if (showTimeDialog) {
        ReminderTimeDialog(
            initialHour = state.reminderHour,
            initialMinute = state.reminderMinute,
            onDismiss = { showTimeDialog = false },
            onConfirm = { hour, minute ->
                viewModel.updateReminderSchedule(state.reminderAdvanceDays, hour, minute)
                showTimeDialog = false
            },
        )
    }
}

/** 无副作用的提醒页面视图，权限与调度仍由原页面和 ViewModel 处理。 */
@Composable
fun NotificationsContent(
    state: com.yangsong.lizhang.ui.viewmodel.NotificationsUiState,
    onBack: () -> Unit = {}, onRetry: () -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onEnabledChange: (Boolean) -> Unit = {}, onAdvanceClick: () -> Unit = {}, onTimeClick: () -> Unit = {},
    onAdd: () -> Unit = {}, onEdit: (IndependentReminder) -> Unit = {},
    onDelete: (IndependentReminder) -> Unit = {}, onReminderEnabled: (Long, Boolean) -> Unit = { _, _ -> },
) {
    AppScaffold(
        topBar = { AppTopBar(stringResource(R.string.nav_notifications), onBack) },
        snackbarHost = { CenteredSnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isLoading -> LoadingState()
            state.error -> ErrorState(onRetry)
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { PageIllustration(R.drawable.page_statistics_cat, Modifier.fillMaxWidth().height(64.dp).testTag("提醒顶部插画")) }
                item {
                    GlassCard(
                        shape = RoundedCornerShape(GlassTokens.Radius),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.reminder_switch_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    stringResource(R.string.reminder_switch_description_configurable),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            GlassSwitch(colors = glassSwitchColors(),
                                checked = state.remindersEnabled,
                                onCheckedChange = onEnabledChange,
                            )
                        }
                    }
                }
                item {
                    ReminderScheduleCard(
                        advanceDays = state.reminderAdvanceDays,
                        hour = state.reminderHour,
                        minute = state.reminderMinute,
                        onAdvanceClick = onAdvanceClick,
                        onTimeClick = onTimeClick,
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.independent_list_title), Modifier.weight(1f), style = gradientTextStyle(MaterialTheme.typography.titleMedium))
                        GlassTextButton(onAdd, Modifier.testTag("新增独立提醒").featureGuideTarget(FeatureGuideTarget.REMINDERS_PAGE)) {
                            Icon(LiZhangIcons.Plus, null, Modifier.size(18.dp), tint = featureIconColor())
                            Text(stringResource(R.string.independent_add), style = gradientTextStyle(MaterialTheme.typography.labelLarge))
                        }
                    }
                    Text(stringResource(R.string.notifications_desc), style = gradientTextStyle(MaterialTheme.typography.bodySmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.independent_storage_notice), style = gradientTextStyle(MaterialTheme.typography.bodySmall),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.reminders.isEmpty()) {
                    item {
                        GlassCard(Modifier.testTag("提醒空状态")) {
                            Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Icon(LiZhangIcons.Bell, null, Modifier.size(32.dp), tint = featureIconColor())
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(stringResource(R.string.notifications_empty), style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.notifications_empty_desc), style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                } else {
                    items(state.reminders, key = { it.id }) { reminder ->
                        IndependentReminderCard(reminder, state, onEdit, onDelete, onReminderEnabled)
                    }
                }
            }
        }
    }
}

@Composable
private fun IndependentReminderCard(reminder: IndependentReminder, state: NotificationsUiState,
    onEdit: (IndependentReminder) -> Unit, onDelete: (IndependentReminder) -> Unit,
    onEnabled: (Long, Boolean) -> Unit) {
    val settings = ReminderSettings(state.remindersEnabled, state.reminderAdvanceDays, state.reminderHour, state.reminderMinute)
    val plan = IndependentReminderPlanner.next(reminder, settings)
    val toggleDescription = stringResource(if (reminder.enabled) R.string.independent_disable else R.string.independent_enable)
    GlassCard(Modifier.testTag("独立提醒-${reminder.id}")) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(reminder.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                GlassSwitch(reminder.enabled, { onEnabled(reminder.id, it) }, colors = glassSwitchColors(),
                    modifier = Modifier.testTag("独立提醒开关-${reminder.id}").semantics { contentDescription = toggleDescription })
            }
            Text(stringResource(if (reminder.annually) R.string.independent_annually else R.string.independent_once),
                color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            Text(when {
                !reminder.enabled -> stringResource(R.string.independent_disabled)
                plan == null -> stringResource(R.string.independent_expired)
                else -> stringResource(R.string.independent_next, displayDate(plan.occurrence))
            }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassTextButton({ onEdit(reminder) }, Modifier.testTag("独立提醒编辑-${reminder.id}")) { Text(stringResource(R.string.action_edit)) }
                GlassTextButton({ onDelete(reminder) }, Modifier.testTag("独立提醒删除-${reminder.id}")) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}

@Composable
private fun ReminderScheduleCard(
    advanceDays: Int,
    hour: Int,
    minute: Int,
    onAdvanceClick: () -> Unit,
    onTimeClick: () -> Unit,
) {
    GlassCard(
        shape = RoundedCornerShape(GlassTokens.Radius),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.reminder_schedule_title),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            ReminderSettingRow(
                title = stringResource(R.string.reminder_advance_title),
                value = reminderAdvanceLabel(advanceDays),
                onClick = onAdvanceClick,
            )
            HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outline)
            ReminderSettingRow(
                title = stringResource(R.string.reminder_time_title),
                value = stringResource(R.string.reminder_time_value, hour, minute),
                onClick = onTimeClick,
            )
            Text(
                stringResource(R.string.reminder_schedule_description),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ReminderSettingRow(title: String, value: String, onClick: () -> Unit) {
    GlassClickableSurface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(LiZhangIcons.ChevronRight, contentDescription = null, tint = featureIconColor())
        }
    }
}

@Composable
fun ReminderAdvanceDialog(selectedDays: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_advance_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                supportedReminderAdvanceDays.forEach { days ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .pressSelectable(selectedDays == days, role = Role.RadioButton, onClick = { onSelect(days) })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedDays == days, onClick = null)
                        Text(reminderAdvanceLabel(days), Modifier.padding(start = 10.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { GlassTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun reminderAdvanceLabel(days: Int): String = stringResource(
    when (days) {
        1 -> R.string.reminder_advance_one_day
        3 -> R.string.reminder_advance_three_days
        7 -> R.string.reminder_advance_seven_days
        else -> R.string.reminder_advance_same_day
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_time_dialog_title)) },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(timePickerState) } },
        confirmButton = {
            GlassTextButton(onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = { GlassTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
