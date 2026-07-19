package com.yangsong.lizhang.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
fun HomeScreen(viewModel: HomeViewModel, onNavigate: (AppDestination) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(state, onNavigate)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(state: HomeUiState, onNavigate: (AppDestination) -> Unit) {
    var showEntryOptions by remember { mutableStateOf(false) }
    Scaffold(
        bottomBar = { BottomNavBar(AppDestination.Home, onNavigate) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showEntryOptions = true },
                containerColor = CoralPrimary,
                contentColor = Color.White,
                shape = CircleShape,
            ) { Icon(Icons.Outlined.Add, stringResource(R.string.action_add_gift), Modifier.size(30.dp)) }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState()
            state.error -> ErrorState { }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { HomeHeader({ onNavigate(AppDestination.Search) }, { onNavigate(AppDestination.Notifications) }) }
                item { HeroSummaryCard(state) }
                item {
                    QuickActions(
                        onReceived = { onNavigate(AppDestination.ReceivedRecords) },
                        onGiven = { onNavigate(AppDestination.GivenRecords) },
                        onCalendar = { onNavigate(AppDestination.Calendar) },
                        onStats = { onNavigate(AppDestination.Statistics) },
                    )
                }
                item {
                    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            SectionHeader(stringResource(R.string.home_recent), stringResource(R.string.action_all)) { onNavigate(AppDestination.Search) }
                            if (state.recentRecords.isEmpty()) {
                                EmptyState(
                                    stringResource(R.string.home_empty_title),
                                    stringResource(R.string.home_empty_desc),
                                    stringResource(R.string.action_add_gift),
                                    { showEntryOptions = true },
                                    R.drawable.page_add_cat,
                                )
                            } else {
                                state.recentRecords.take(4).forEachIndexed { index, item ->
                                    GiftRecordListItem(item)
                                    if (index < state.recentRecords.take(4).lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showEntryOptions) {
        ModalBottomSheet(onDismissRequest = { showEntryOptions = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.entry_choose_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.entry_choose_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
                GiftEntryOption(
                    stringResource(R.string.entry_manual_title),
                    stringResource(R.string.entry_manual_desc),
                    Icons.Outlined.EditNote,
                    CoralContainer,
                    CoralStrong,
                    { showEntryOptions = false; onNavigate(AppDestination.ManualGift) },
                )
                GiftEntryOption(
                    stringResource(R.string.entry_ocr_title),
                    stringResource(R.string.entry_ocr_desc),
                    Icons.Outlined.PhotoCamera,
                    MintContainer,
                    MintPrimary,
                    { showEntryOptions = false; onNavigate(AppDestination.OcrImport) },
                )
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
        IconButton(onSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.action_search), Modifier.size(28.dp)) }
        IconButton(onNotice) {
            Icon(Icons.Outlined.NotificationsNone, stringResource(R.string.nav_notifications), Modifier.size(28.dp))
        }
    }
}

@Composable
private fun HeroSummaryCard(state: HomeUiState) {
    Card(Modifier.fillMaxWidth().height(220.dp), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = BlushSurface), elevation = CardDefaults.cardElevation(2.dp)) {
        Box(Modifier.fillMaxSize()) {
            Image(painterResource(R.drawable.home_hero_cat), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Column(Modifier.fillMaxHeight().width(225.dp).padding(18.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .9f)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.year_format, state.year), fontWeight = FontWeight.Bold)
                        Icon(Icons.Outlined.KeyboardArrowDown, null)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.home_year_received), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CurrencyFormatter.formatCents(state.received), color = CoralStrong, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("${stringResource(R.string.home_year_given)}  ${CurrencyFormatter.formatCents(state.given)}", color = MintPrimary, fontWeight = FontWeight.SemiBold)
                Text("${stringResource(R.string.home_net)}  ${CurrencyFormatter.formatCents(state.net)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun QuickActions(onReceived: () -> Unit, onGiven: () -> Unit, onCalendar: () -> Unit, onStats: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            QuickAction(R.string.shortcut_received, Icons.Outlined.CardGiftcard, CoralContainer, CoralStrong, onReceived)
            QuickAction(R.string.shortcut_given, Icons.Outlined.MarkEmailRead, MintContainer, MintPrimary, onGiven)
            QuickAction(R.string.shortcut_calendar, Icons.Outlined.CalendarMonth, ApricotContainer, ApricotPrimary, onCalendar)
            QuickAction(R.string.shortcut_statistics, Icons.Outlined.BarChart, LavenderContainer, LavenderPrimary, onStats)
        }
    }
}

@Composable
private fun QuickAction(label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, background: Color, tint: Color, onClick: () -> Unit) {
    Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = background) {
            Icon(icon, null, Modifier.padding(14.dp).size(28.dp), tint = tint)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
