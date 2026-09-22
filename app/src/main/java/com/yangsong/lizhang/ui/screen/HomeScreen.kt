package com.yangsong.lizhang.ui.screen
import com.yangsong.lizhang.ui.component.AppScaffold
import com.yangsong.lizhang.ui.component.GlassCard
import com.yangsong.lizhang.ui.component.GlassIconButton

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.core.util.CurrencyFormatter
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.theme.*
import com.yangsong.lizhang.ui.viewmodel.HomeUiState
import com.yangsong.lizhang.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(viewModel: HomeViewModel, onNavigate: (AppDestination) -> Unit, onRecordClick: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(state, onNavigate, onRecordClick, viewModel::selectYear, viewModel::retry)
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onNavigate: (AppDestination) -> Unit,
    onRecordClick: (Long) -> Unit = {},
    onYearSelected: (Int) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    AppScaffold { padding ->
        when {
            state.isLoading -> LoadingState()
            state.error -> ErrorState(onRetry)
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 124.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { HomeHeader({ onNavigate(AppDestination.Search) }, { onNavigate(AppDestination.Notifications) }) }
                item { HeroSummaryCard(state, onYearSelected) }
                item {
                    QuickActions(
                        onReceived = { onNavigate(AppDestination.ReceivedRecords) },
                        onGiven = { onNavigate(AppDestination.GivenRecords) },
                        onCalendar = { onNavigate(AppDestination.Calendar) },
                        onStats = { onNavigate(AppDestination.Statistics) },
                    )
                }
                item {
                    GlassCard(shape = RoundedCornerShape(GlassTokens.Radius)) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            SectionHeader(stringResource(R.string.home_recent), stringResource(R.string.action_all)) { onNavigate(AppDestination.Search) }
                            if (state.recentRecords.isEmpty()) {
                                EmptyState(
                                    stringResource(R.string.home_empty_title),
                                    stringResource(R.string.home_empty_desc),
                                    stringResource(R.string.action_add_gift),
                                    { onNavigate(AppDestination.AddGift) },
                                    R.drawable.page_add_cat,
                                )
                            } else {
                                state.recentRecords.take(4).forEachIndexed { index, item ->
                                    GiftRecordListItem(item) { onRecordClick(item.record.id) }
                                    if (index < state.recentRecords.take(4).lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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
private fun HomeHeader(onSearch: () -> Unit, onNotice: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.app_tagline), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }
        GlassIconButton(onSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.action_search), Modifier.size(28.dp)) }
        GlassIconButton(onNotice) {
            Icon(Icons.Outlined.NotificationsNone, stringResource(R.string.nav_notifications), Modifier.size(28.dp))
        }
    }
}

@Composable
private fun HeroSummaryCard(state: HomeUiState, onYearSelected: (Int) -> Unit) {
    var yearMenuExpanded by remember { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(GlassTokens.Radius)) {
        Box(Modifier.fillMaxWidth()) {
            Image(illustrationPainter(R.drawable.home_hero_cat), null, Modifier.matchParentSize().padding(start = 64.dp, bottom = 12.dp), contentScale = ContentScale.Fit, alignment = Alignment.BottomEnd)
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Box {
                    Surface(
                        onClick = { yearMenuExpanded = true },
                        shape = RoundedCornerShape(14.dp),
                        color = glassColor(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.year_format, state.year), fontWeight = FontWeight.Bold)
                            Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.home_choose_year))
                        }
                    }
                    DropdownMenu(
                        expanded = yearMenuExpanded,
                        onDismissRequest = { yearMenuExpanded = false },
                        shape = RoundedCornerShape(GlassTokens.ControlRadius),
                        containerColor = glassColor().copy(alpha = floatingGlassAlpha()),
                    ) {
                        state.availableYears.forEach { year ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.year_format, year)) },
                                onClick = {
                                    yearMenuExpanded = false
                                    onYearSelected(year)
                                },
                                leadingIcon = if (year == state.year) {
                                    { Icon(Icons.Outlined.Check, null) }
                                } else null,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.home_year_received), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CurrencyFormatter.formatCents(state.received), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.home_year_given), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CurrencyFormatter.formatCents(state.given), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text("${stringResource(R.string.home_net)}  ${CurrencyFormatter.formatCents(state.net)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun QuickActions(onReceived: () -> Unit, onGiven: () -> Unit, onCalendar: () -> Unit, onStats: () -> Unit) {
    GlassCard(shape = RoundedCornerShape(GlassTokens.Radius)) {
        FlowRow(Modifier.fillMaxWidth().padding(vertical = 18.dp), maxItemsInEachRow = if (LocalDensity.current.fontScale >= 1.2f) 2 else 4, horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            QuickAction(R.string.shortcut_received, Icons.Outlined.CardGiftcard, glassColor(), MaterialTheme.colorScheme.primary, onReceived)
            QuickAction(R.string.shortcut_given, Icons.Outlined.MarkEmailRead, glassColor(), MaterialTheme.colorScheme.secondary, onGiven)
            QuickAction(R.string.shortcut_calendar, Icons.Outlined.CalendarMonth, glassColor(), MaterialTheme.colorScheme.primary, onCalendar)
            QuickAction(R.string.shortcut_statistics, Icons.Outlined.BarChart, glassColor(), MaterialTheme.colorScheme.secondary, onStats)
        }
    }
}

@Composable
private fun QuickAction(label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, background: Color, tint: Color, onClick: () -> Unit) {
    Column(Modifier.width(if (LocalDensity.current.fontScale >= 1.2f) 140.dp else 76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = background) {
            Icon(icon, null, Modifier.padding(14.dp).size(28.dp), tint = tint)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
