package com.yangsong.lizhang.ui.screen
import com.yangsong.lizhang.ui.component.AppScaffold
import com.yangsong.lizhang.ui.component.GlassCard
import com.yangsong.lizhang.ui.component.GlassIconButton

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.flow.drop
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
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
    initiallyYearMenuExpanded: Boolean = false,
) {
    val listState = rememberLazyListState()
    var yearMenuExpanded by remember { mutableStateOf(initiallyYearMenuExpanded) }
    var yearButtonBounds by remember { mutableStateOf(Rect.Zero) }
    var yearMenuBounds by remember { mutableStateOf(Rect.Zero) }
    var pageBounds by remember { mutableStateOf(Rect.Zero) }
    BackHandler(yearMenuExpanded) { yearMenuExpanded = false }
    LaunchedEffect(listState) {
        snapshotFlow { Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, listState.isScrollInProgress) }
            .drop(1).collect { yearMenuExpanded = false }
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { pageBounds = it.boundsInRoot() }
        .pointerInput(yearMenuExpanded, yearButtonBounds, yearMenuBounds) {
            // 在子控件消费事件之前观察外部触摸；不消费事件，保留页面滚动与原有点击。
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val point = down.position + pageBounds.topLeft
                if (yearMenuExpanded && !yearButtonBounds.contains(point) && !yearMenuBounds.contains(point)) {
                    yearMenuExpanded = false
                }
            }
        }) {
        AppScaffold { padding ->
            when {
                state.isLoading -> LoadingState()
                state.error -> ErrorState(onRetry)
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(padding).testTag("首页列表"),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 124.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { HomeHeader({ onNavigate(AppDestination.Search) }, { onNavigate(AppDestination.Notifications) }) }
                    item {
                        HeroSummaryCard(state, onYearSelected, yearMenuExpanded,
                            onExpandedChange = { yearMenuExpanded = it },
                            onButtonBounds = { yearButtonBounds = it },
                            onMenuBounds = { yearMenuBounds = it })
                    }
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
}

@Composable
private fun HomeHeader(onSearch: () -> Unit, onNotice: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.app_tagline), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(onSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.action_search), Modifier.size(28.dp)) }
            GlassIconButton(onNotice) {
                Icon(Icons.Outlined.NotificationsNone, stringResource(R.string.nav_notifications), Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun HeroSummaryCard(
    state: HomeUiState,
    onYearSelected: (Int) -> Unit,
    yearMenuExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onButtonBounds: (Rect) -> Unit,
    onMenuBounds: (Rect) -> Unit,
) {
    val yearMenuHazeState = rememberHazeState()
    val anchor = remember { YearMenuAnchor() }
    Layout(modifier = Modifier.fillMaxWidth(), content = {
        GlassCard(Modifier.fillMaxWidth().hazeSource(yearMenuHazeState), shape = RoundedCornerShape(GlassTokens.Radius)) {
            Box(Modifier.fillMaxWidth()) {
                Image(illustrationPainter(R.drawable.home_hero_cat), null, Modifier.matchParentSize().padding(start = 64.dp, bottom = 12.dp), contentScale = ContentScale.Fit, alignment = Alignment.BottomEnd)
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Box {
                        Surface(
                            onClick = { onExpandedChange(!yearMenuExpanded) },
                            modifier = Modifier.testTag("年份入口")
                                .layout { measurable, constraints ->
                                    val button = measurable.measure(constraints)
                                    anchor.height = button.height
                                    layout(button.width, button.height) { button.placeRelative(0, 0) }
                                }
                                .semantics { stateDescription = if (yearMenuExpanded) "已展开" else "已收起" }
                                .onGloballyPositioned {
                                    onButtonBounds(it.boundsInRoot())
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = glassColor(),
                        ) {
                            Row(
                                Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.year_format, state.year), fontWeight = FontWeight.Bold)
                                Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.home_choose_year))
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
        AnimatedVisibility(
            visible = yearMenuExpanded,
            enter = fadeIn(tween(160)) + expandVertically(tween(160), expandFrom = Alignment.Top),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(160), shrinkTowards = Alignment.Top),
        ) {
            FrostedYearMenu(
                years = state.availableYears,
                selectedYear = state.year,
                hazeState = yearMenuHazeState,
                modifier = Modifier.onGloballyPositioned { onMenuBounds(it.boundsInRoot()) },
                onYearSelected = { year ->
                    onExpandedChange(false)
                    onYearSelected(year)
                },
            )
        }
    }) { measurables, constraints ->
        // 先测量卡片及年份入口，再约束和放置菜单；首帧与预览无需等待位置回调重组。
        val card = measurables.first().measure(constraints.copy(minHeight = 0))
        val inset = 20.dp.roundToPx()
        val menuTop = inset + anchor.height + 8.dp.roundToPx()
        val menu = measurables.getOrNull(1)?.measure(constraints.copy(
            minWidth = 0, minHeight = 0,
            maxWidth = (card.width - inset * 2).coerceAtLeast(0),
            maxHeight = (card.height - menuTop - 8.dp.roundToPx()).coerceIn(0, 240.dp.roundToPx()),
        ))
        layout(card.width, card.height) {
            card.placeRelative(0, 0)
            menu?.placeRelative(inset, menuTop)
        }
    }
}

private class YearMenuAnchor {
    // 仅在当前布局测量中传递入口高度，不作为重组状态。
    var height: Int = 0
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
