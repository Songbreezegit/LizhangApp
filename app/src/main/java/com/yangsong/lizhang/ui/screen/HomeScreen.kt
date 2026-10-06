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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.theme.*
import com.yangsong.lizhang.ui.viewmodel.HomeUiState
import com.yangsong.lizhang.ui.viewmodel.HomeViewModel
import com.yangsong.lizhang.ui.onboarding.*

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
    Box(Modifier.fillMaxSize()
        .onGloballyPositioned { pageBounds = it.boundsInRoot() }
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
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.home_recent), Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    GlassTextButton({ onNavigate(AppDestination.Search) }) {
                                        Text(stringResource(R.string.action_all), style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (state.recentRecords.isEmpty()) {
                                    RecentRecordsEmptyState { onNavigate(AppDestination.AddGift) }
                                } else {
                                    state.recentRecords.take(4).forEachIndexed { index, item ->
                                        GiftRecordListItem(item) { onRecordClick(item.record.id) }
                                        if (index < state.recentRecords.take(4).lastIndex) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
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
            GlassIconButton(onNotice, Modifier.featureGuideTarget(FeatureGuideTarget.REMINDERS)) {
                Icon(Icons.Outlined.NotificationsNone, stringResource(R.string.nav_notifications), Modifier.size(28.dp))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HeroSummaryCard(
    state: HomeUiState,
    onYearSelected: (Int) -> Unit,
    yearMenuExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onButtonBounds: (Rect) -> Unit,
    onMenuBounds: (Rect) -> Unit,
) {
    val yearMenuHazeState = rememberHazeState()
    val expandedDescription = stringResource(if (yearMenuExpanded) R.string.state_expanded else R.string.state_collapsed)
    val anchor = remember { YearMenuAnchor() }
    Layout(modifier = Modifier.fillMaxWidth(), content = {
        GlassCard(Modifier.fillMaxWidth().hazeSource(yearMenuHazeState), shape = RoundedCornerShape(GlassTokens.Radius)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                // 年份入口保持顶部对齐，菜单仍由 20dp 内边距和本帧入口高度定位。
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        Surface(
                            onClick = { onExpandedChange(!yearMenuExpanded) },
                            modifier = Modifier.testTag("年份入口")
                                .layout { measurable, constraints ->
                                    val button = measurable.measure(constraints)
                                    anchor.height = button.height
                                    layout(button.width, button.height) { button.placeRelative(0, 0) }
                                }
                                .semantics { stateDescription = expandedDescription }
                                .onGloballyPositioned {
                                    onButtonBounds(it.boundsInRoot())
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = .07f),
                        ) {
                            Row(
                                Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.year_format, state.year), Modifier.weight(1f, fill = false),
                                    fontWeight = FontWeight.Bold)
                                Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.home_choose_year), Modifier.size(24.dp))
                            }
                        }
                    }
                    Image(illustrationPainter(R.drawable.home_hero_cat), null, Modifier.size(80.dp),
                        contentScale = ContentScale.Fit)
                }
                Spacer(Modifier.height(12.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val amountWidth = Modifier.widthIn(min = minOf(128.dp, maxWidth), max = maxWidth)
                    // 先按金额实际宽度排版；长金额和大字体放到下一行，不压缩或省略数字。
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp), maxItemsInEachRow = 2) {
                        AnnualAmount(R.string.home_year_received, state.received, MaterialTheme.colorScheme.primary, amountWidth)
                        AnnualAmount(R.string.home_year_given, state.given, MaterialTheme.colorScheme.secondary, amountWidth)
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.contact_net_amount, stringResource(R.string.home_net),
                    com.yangsong.lizhang.ui.mapper.displayAmount(state.net)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
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
private fun AnnualAmount(label: Int, amount: Long, tint: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
        Text(com.yangsong.lizhang.ui.mapper.displayAmount(amount), color = tint,
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecentRecordsEmptyState(onAddGift: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(illustrationPainter(R.drawable.page_add_cat), null, Modifier.size(88.dp), contentScale = ContentScale.Fit)
        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(stringResource(R.string.home_empty_desc), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        PrimaryButton(stringResource(R.string.action_add_gift), onAddGift)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun QuickActions(onReceived: () -> Unit, onGiven: () -> Unit, onCalendar: () -> Unit, onStats: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
        val density = LocalDensity.current
        val columns = if (density.fontScale >= 1.2f || maxWidth < 304.dp) 2 else 4
        val gap = 12.dp
        // 先按实际像素扣除间距并向下取整，避免各列分别取整后把最后一项挤到下一行。
        val itemWidth = with(density) {
            ((constraints.maxWidth - gap.roundToPx() * (columns - 1)).coerceAtLeast(0) / columns).toDp()
        }
        FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAction(R.string.shortcut_received, Icons.Outlined.CardGiftcard, MaterialTheme.colorScheme.primary,
                onReceived, Modifier.width(itemWidth))
            QuickAction(R.string.shortcut_given, Icons.Outlined.MarkEmailRead, MaterialTheme.colorScheme.secondary,
                onGiven, Modifier.width(itemWidth))
            QuickAction(R.string.shortcut_calendar, Icons.Outlined.CalendarMonth, MaterialTheme.colorScheme.primary,
                onCalendar, Modifier.width(itemWidth))
            QuickAction(R.string.shortcut_statistics, Icons.Outlined.BarChart, MaterialTheme.colorScheme.secondary,
                onStats, Modifier.width(itemWidth))
        }
    }
}

@Composable
private fun QuickAction(label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color,
    onClick: () -> Unit, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).background(tint.copy(alpha = .09f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(26.dp), tint = tint)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center)
    }
}
