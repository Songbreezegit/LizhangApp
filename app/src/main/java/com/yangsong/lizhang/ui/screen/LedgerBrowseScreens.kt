package com.yangsong.lizhang.ui.screen

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.viewmodel.*

@Composable
fun DirectionRecordsScreen(
    viewModel: DirectionRecordsViewModel,
    direction: GiftDirection,
    onBack: () -> Unit,
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val title = stringResource(if (direction == GiftDirection.RECEIVED) R.string.shortcut_received else R.string.shortcut_given)
    Scaffold(topBar = { AppTopBar(title, onBack) }) { padding ->
        when {
            state.isLoading -> LoadingState()
            state.error -> ErrorState { }
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
                                    GiftRecordListItem(record)
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
fun CalendarScreen(viewModel: CalendarViewModel, onBack: () -> Unit) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    Scaffold(topBar = { AppTopBar(stringResource(R.string.shortcut_calendar), onBack) }) { padding ->
        when {
            state.isLoading -> LoadingState()
            state.error -> ErrorState { }
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
                    items(state.selectedRecords) { GiftRecordListItem(it) }
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
fun NotificationsScreen(viewModel: NotificationsViewModel, onBack: () -> Unit) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    Scaffold(topBar = { AppTopBar(stringResource(R.string.nav_notifications), onBack) }) { padding ->
        when {
            state.isLoading -> LoadingState()
            state.error -> ErrorState { }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { PageIllustration(R.drawable.page_statistics_cat, Modifier.fillMaxWidth().height(175.dp)) }
                item { Text(stringResource(R.string.notifications_desc), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (state.upcoming.isEmpty()) {
                    item { EmptyState(stringResource(R.string.notifications_empty), description = stringResource(R.string.notifications_empty_desc), image = R.drawable.page_statistics_cat) }
                } else {
                    items(state.upcoming) { GiftRecordListItem(it) }
                }
            }
        }
    }
}
