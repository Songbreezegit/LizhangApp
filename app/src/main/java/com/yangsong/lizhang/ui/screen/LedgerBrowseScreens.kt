package com.yangsong.lizhang.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
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
    Scaffold(topBar = { AppTopBar(title, onBack) }) { padding ->
        when {
            state.isLoading -> LoadingState()
            state.error -> ErrorState(viewModel::retry)
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { PageIllustration(R.drawable.page_add_cat, Modifier.fillMaxWidth().height(175.dp)) }
                item {
                    SectionHeader(stringResource(R.string.records_count, state.records.size))
                }
                if (state.records.isEmpty()) {
                    item { EmptyState(stringResource(R.string.records_empty, title), image = R.drawable.page_add_cat) }
                } else {
                    item {
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
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
    Scaffold(topBar = { AppTopBar(stringResource(R.string.shortcut_calendar), onBack) }) { padding ->
        when {
            state.isLoading -> LoadingState()
            state.error -> ErrorState(viewModel::retry)
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { CalendarCard(state, viewModel::previousMonth, viewModel::nextMonth, viewModel::selectDay) }
                item { SectionHeader(stringResource(R.string.calendar_day_records, state.month, state.selectedDay)) }
                if (state.selectedRecords.isEmpty()) {
                    item { EmptyState(stringResource(R.string.calendar_empty), image = R.drawable.page_add_cat) }
                } else {
                    items(state.selectedRecords) { item -> GiftRecordListItem(item) { onRecordClick(item.record.id) } }
                }
            }
        }
    }
}

@Composable
private fun CalendarCard(state: CalendarUiState, onPrevious: () -> Unit, onNext: () -> Unit, onDay: (Int) -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onPrevious) { Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.calendar_previous)) }
                Text(stringResource(R.string.calendar_month_title, state.year, state.month), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onNext) { Icon(Icons.Outlined.ChevronRight, stringResource(R.string.calendar_next)) }
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
    Surface(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f).padding(2.dp),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(day.toString(), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            if (hasRecord) Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(5.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {}
        }
    }
}

@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel, onBack: () -> Unit, onRecordClick: (Long) -> Unit) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAdvanceDialog by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }
    val permissionDenied = stringResource(R.string.reminder_permission_denied)
    fun hasNotificationPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.setRemindersEnabled(true)
        else scope.launch { snackbar.showSnackbar(permissionDenied) }
    }

    LaunchedEffect(state.remindersEnabled) {
        if (state.remindersEnabled && !hasNotificationPermission()) {
            viewModel.setRemindersEnabled(false)
        }
    }

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.nav_notifications), onBack) },
        snackbarHost = { CenteredSnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isLoading -> LoadingState()
            state.error -> ErrorState(viewModel::retry)
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { PageIllustration(R.drawable.page_statistics_cat, Modifier.fillMaxWidth().height(175.dp)) }
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp),
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
                            Switch(
                                checked = state.remindersEnabled,
                                onCheckedChange = { enabled ->
                                    when {
                                        !enabled -> viewModel.setRemindersEnabled(false)
                                        hasNotificationPermission() -> viewModel.setRemindersEnabled(true)
                                        else -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                            )
                        }
                    }
                }
                item {
                    ReminderScheduleCard(
                        advanceDays = state.reminderAdvanceDays,
                        hour = state.reminderHour,
                        minute = state.reminderMinute,
                        onAdvanceClick = { showAdvanceDialog = true },
                        onTimeClick = { showTimeDialog = true },
                    )
                }
                item { Text(stringResource(R.string.notifications_desc), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (state.upcoming.isEmpty()) {
                    item { EmptyState(stringResource(R.string.notifications_empty), description = stringResource(R.string.notifications_empty_desc), image = R.drawable.page_statistics_cat) }
                } else {
                    items(state.upcoming) { item -> GiftRecordListItem(item) { onRecordClick(item.record.id) } }
                }
            }
        }
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

@Composable
private fun ReminderScheduleCard(
    advanceDays: Int,
    hour: Int,
    minute: Int,
    onAdvanceClick: () -> Unit,
    onTimeClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
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
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReminderAdvanceDialog(selectedDays: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_advance_title)) },
        text = {
            Column {
                supportedReminderAdvanceDays.forEach { days ->
                    val label = reminderAdvanceLabel(days)
                    Surface(onClick = { onSelect(days) }, color = MaterialTheme.colorScheme.surface) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedDays == days, onClick = null)
                            Text(label, Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_time_dialog_title)) },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(timePickerState) } },
        confirmButton = {
            TextButton(onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
