package com.yangsong.lizhang.ui.onboarding

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.screen.AddGiftScreen
import com.yangsong.lizhang.ui.screen.ContactDetailScreen
import com.yangsong.lizhang.ui.screen.ContactEditorScreen
import com.yangsong.lizhang.ui.screen.ContactsScreen
import com.yangsong.lizhang.ui.screen.GiftRecordDetailScreen
import com.yangsong.lizhang.ui.viewmodel.GuidePracticeStep
import com.yangsong.lizhang.ui.viewmodel.GuidePracticeUiState
import com.yangsong.lizhang.ui.viewmodel.GuidePracticeViewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** 原样复用真实页面和真实 ViewModel；控制器只向独立内存仓储保存、删除示例。 */
@Composable
fun GuidePracticeScreen(viewModel: GuidePracticeViewModel, onExit: () -> Unit, onComplete: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.closed) return
    val currentStep = state.step
    val exit = onExit
    val next = {
        if (currentStep == GuidePracticeStep.CONTACTS_CLEAN) onComplete()
        else viewModel.next(currentStep)
    }
    val enabled = ValueAnimator.areAnimatorsEnabled()
    // 填写、保存与删除确认仍在原页面上演示，不把同一表单重新淡出再装入。
    val page = when (state.step) {
        GuidePracticeStep.CONTACTS_CLEAN -> GuidePracticeStep.CONTACTS
        GuidePracticeStep.CONTACT_EDITOR_FILLED -> GuidePracticeStep.CONTACT_EDITOR_EMPTY
        GuidePracticeStep.CONTACT_AFTER_GIFT_DELETE -> GuidePracticeStep.CONTACT_DETAIL
        GuidePracticeStep.GIFT_EDITOR_FILLED -> GuidePracticeStep.GIFT_EDITOR_EMPTY
        GuidePracticeStep.DELETE_GIFT_CONFIRM -> GuidePracticeStep.GIFT_DETAIL
        GuidePracticeStep.DELETE_CONTACT_CONFIRM -> GuidePracticeStep.CONTACT_DELETE
        else -> state.step
    }
    val reveal = remember(page) { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(page, enabled) {
        if (enabled) reveal.animateTo(1f, tween(260, easing = CubicBezierEasing(.2f, 0f, 0f, 1f))) else reveal.snapTo(1f)
    }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    val isContactList = state.step == GuidePracticeStep.CONTACTS || state.step == GuidePracticeStep.CONTACTS_CLEAN
    val confirming = state.step == GuidePracticeStep.DELETE_GIFT_CONFIRM || state.step == GuidePracticeStep.DELETE_CONTACT_CONFIRM
    val pageReady = practicePageReady(viewModel, state.step)
    val haze = rememberHazeState()
    AppGradientBackground(Modifier.testTag("引导练习页")) {
        // 示例页面的保存反馈和选择状态只活在本会话，不进入导航的进程恢复状态。
        CompositionLocalProvider(LocalSaveableStateRegistry provides null) {
            key(page) {
                Box(Modifier.fillMaxSize().hazeSource(haze).graphicsLayer {
                    alpha = reveal.value
                    translationX = 24.dp.toPx() * direction * (1f - reveal.value)
                }.clearAndSetSemantics { }) {
                    when (state.step) {
                        GuidePracticeStep.CONTACTS, GuidePracticeStep.CONTACTS_CLEAN -> ContactsScreen(
                            viewModel.contacts, onContactClick = { viewModel.next(currentStep) }, onAddContact = next,
                        )
                        GuidePracticeStep.CONTACT_EDITOR_EMPTY, GuidePracticeStep.CONTACT_EDITOR_FILLED -> ContactEditorScreen(
                            viewModel.contactEditor, onBack = viewModel::contactSaved, onDeleted = {},
                        )
                        GuidePracticeStep.CONTACT_DETAIL, GuidePracticeStep.CONTACT_AFTER_GIFT_DELETE -> ContactDetailScreen(
                            viewModel.contactDetail, onBack = exit, onEdit = {}, onAdd = next, onRecordClick = {},
                        )
                        GuidePracticeStep.GIFT_EDITOR_EMPTY, GuidePracticeStep.GIFT_EDITOR_FILLED -> AddGiftScreen(
                            viewModel.giftEditor, onBack = viewModel::giftSaved,
                        )
                        GuidePracticeStep.GIFT_DETAIL, GuidePracticeStep.DELETE_GIFT_CONFIRM -> GiftRecordDetailScreen(
                            viewModel.giftDetail, onBack = viewModel::giftDeleted, onEdit = {},
                        )
                        GuidePracticeStep.CONTACT_DELETE, GuidePracticeStep.DELETE_CONTACT_CONFIRM -> ContactEditorScreen(
                            viewModel.contactDeletion, onBack = {}, onDeleted = viewModel::contactDeleted,
                        )
                    }
                    if (isContactList) BottomNavBar(AppDestination.Contacts, {}, Modifier.align(Alignment.BottomCenter), haze, enabled = false)
                }
            }
        }
        // 只由“下一步”推进；拦截底页触摸和滚动，保留其原有布局及保存成功反馈。
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).consume()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }.clearAndSetSemantics { })
        if (!confirming) BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val bottom = if (isContactList) 104.dp else 8.dp
            val available = (maxHeight - bottom - 16.dp).coerceAtLeast(80.dp)
            WalkthroughCoach(state, enabled, next, exit, Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp).padding(bottom = bottom).heightIn(max = available), ready = pageReady)
        }
        // 每次换页重新在子页面之后注册，系统返回始终退出整段演示。
        key(state.step) { BackHandler(onBack = exit) }
    }
    if (confirming) {
        val gift = state.step == GuidePracticeStep.DELETE_GIFT_CONFIRM
        // 删除框与功能气泡处于同一窗口，讲解、错误和退出不会被模态遮罩挡住。
        GlassDialog(
            onDismissRequest = exit,
            title = { Text(stringResource(if (gift) R.string.record_delete_title else R.string.contact_delete_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(if (gift) R.string.record_delete_message else R.string.contact_delete_message))
                    WalkthroughCoach(state, enabled, next, exit, showActions = false)
                }
            },
            confirmButton = {
                GlassTextButton(next, enabled = pageReady && !state.busy) {
                    Text(if (state.busy) "正在删除示例…" else if (!pageReady) "等待示例加载…" else "下一步：删除示例",
                        color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { GlassTextButton(exit) { Text("退出引导") } },
        )
    }
}

/** 等当前真实页面拿到示例，再允许用户继续，避免跳过尚未显示的操作结果。 */
@Composable
private fun practicePageReady(viewModel: GuidePracticeViewModel, step: GuidePracticeStep): Boolean = when (step) {
    GuidePracticeStep.CONTACTS, GuidePracticeStep.CONTACTS_CLEAN -> {
        val page by viewModel.contacts.uiState.collectAsStateWithLifecycle()
        !page.isLoading && !page.error && (step != GuidePracticeStep.CONTACTS_CLEAN || page.contacts.isEmpty())
    }
    GuidePracticeStep.CONTACT_EDITOR_EMPTY, GuidePracticeStep.CONTACT_EDITOR_FILLED -> {
        val page by viewModel.contactEditor.uiState.collectAsStateWithLifecycle()
        !page.isLoading && !page.loadFailed
    }
    GuidePracticeStep.CONTACT_DETAIL, GuidePracticeStep.CONTACT_AFTER_GIFT_DELETE -> {
        val page by viewModel.contactDetail.uiState.collectAsStateWithLifecycle()
        !page.isLoading && !page.error && !page.notFound && page.contact != null &&
            (step != GuidePracticeStep.CONTACT_AFTER_GIFT_DELETE || page.records.isEmpty())
    }
    GuidePracticeStep.GIFT_EDITOR_EMPTY, GuidePracticeStep.GIFT_EDITOR_FILLED -> {
        val page by viewModel.giftEditor.uiState.collectAsStateWithLifecycle()
        !page.isLoading && !page.loadFailed && page.contacts.any { it.id == page.contactId }
    }
    GuidePracticeStep.GIFT_DETAIL, GuidePracticeStep.DELETE_GIFT_CONFIRM -> {
        val page by viewModel.giftDetail.uiState.collectAsStateWithLifecycle()
        !page.isLoading && !page.loadFailed && page.item != null
    }
    GuidePracticeStep.CONTACT_DELETE, GuidePracticeStep.DELETE_CONTACT_CONFIRM -> {
        val page by viewModel.contactDeletion.uiState.collectAsStateWithLifecycle()
        !page.isLoading && !page.loadFailed
    }
}

private data class CoachCopy(val title: String, val explanation: String)
private fun coachCopy(step: GuidePracticeStep): CoachCopy = when (step) {
    GuidePracticeStep.CONTACTS -> CoachCopy("新建联系人", "这是 App 的联系人页。联系人是每笔收礼、随礼的往来对象。下一步打开“新建联系人”。")
    GuidePracticeStep.CONTACT_EDITOR_EMPTY -> CoachCopy("填写联系人", "姓名用于辨认往来对象，关系帮助分类。下一步自动填入“示例小礼”和“朋友”，不用准备真实资料。")
    GuidePracticeStep.CONTACT_EDITOR_FILLED -> CoachCopy("保存联系人", "示例资料已填好。正式使用时点此页“保存联系人”；这次点击下一步，走相同保存流程并只写入演示内存。")
    GuidePracticeStep.CONTACT_DETAIL -> CoachCopy("查看联系人往来", "这里汇总同一个联系人的收礼、随礼和往来记录。下一步从“记一笔”进入礼金表单。")
    GuidePracticeStep.GIFT_EDITOR_EMPTY -> CoachCopy("一笔礼金的内容", "联系人、金额、日期、收送方向、事件和备注组成一笔往来。下一步自动填入 200 元生日收礼示例。")
    GuidePracticeStep.GIFT_EDITOR_FILLED -> CoachCopy("保存礼金", "资料已填好。下一步使用真实保存流程，显示成功反馈后进入详情；示例不会出现在正式账本中。")
    GuidePracticeStep.GIFT_DETAIL -> CoachCopy("核对与删除礼金", "详情页可以核对、编辑或删除单笔记录。下一步打开删除确认，了解如何撤回记错的礼金。")
    GuidePracticeStep.DELETE_GIFT_CONFIRM -> CoachCopy("确认删除礼金", "删除单笔记录不会删除联系人。确认框里的下一步会删除这笔演示礼金。")
    GuidePracticeStep.CONTACT_AFTER_GIFT_DELETE -> CoachCopy("礼金删除后的往来", "这笔礼金已从往来记录和合计中移除，联系人仍在。下一步进入联系人编辑页，学习删除联系人。")
    GuidePracticeStep.CONTACT_DELETE -> CoachCopy("删除联系人", "编辑页右上角可删除联系人；正式使用时也会删除该联系人的所有礼金往来。下一步打开确认框。")
    GuidePracticeStep.DELETE_CONTACT_CONFIRM -> CoachCopy("确认删除联系人", "确认框会提醒关联记录也将被删除。本次只清理刚刚建立的演示联系人。")
    GuidePracticeStep.CONTACTS_CLEAN -> CoachCopy("示例已清理", "联系人页重新回到空列表。你已经看过创建、记账和删除的完整流程；正式账本没有发生变化。")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WalkthroughCoach(state: GuidePracticeUiState, enabled: Boolean, onNext: () -> Unit,
    onExit: () -> Unit, modifier: Modifier = Modifier, showActions: Boolean = true, ready: Boolean = true) {
    var collapsed by remember(state.step) { mutableStateOf(false) }
    val reveal = remember(state.step) { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(state.step, enabled) {
        // Figma 十二步气泡的 Position / Opacity 为独立线性轨道，共用 240ms 进度。
        if (enabled) reveal.animateTo(1f, tween(240, easing = LinearEasing)) else reveal.snapTo(1f)
    }
    val copy = coachCopy(state.step)
    val confirm = state.step == GuidePracticeStep.DELETE_GIFT_CONFIRM || state.step == GuidePracticeStep.DELETE_CONTACT_CONFIRM
    GlassCard(modifier.fillMaxWidth().graphicsLayer {
        alpha = reveal.value
        translationY = 8.dp.toPx() * (1f - reveal.value)
    }.testTag("漫游引导操作卡").semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(painterResource(R.drawable.launcher_cat), null, Modifier.size(36.dp), contentScale = ContentScale.Fit)
                Column(Modifier.weight(1f)) {
                    Text("演示模式 · 示例不会保存", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(copy.title, style = MaterialTheme.typography.titleMedium)
                }
                GlassIconButton({ collapsed = !collapsed }) {
                    Icon(if (collapsed) LiZhangIcons.ChevronDown else LiZhangIcons.ChevronUp,
                        if (collapsed) "展开说明" else "收起说明")
                }
            }
            if (!collapsed) {
                val descriptionModifier = if (showActions) Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()) else Modifier
                Column(descriptionModifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${state.step.ordinal + 1} / ${GuidePracticeStep.entries.size} · 由你点下一步推进",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(copy.explanation, style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (showActions) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GlassTextButton(onExit) { Text("退出引导") }
                if (!confirm) PrimaryButton(if (!ready) "等待示例加载…" else if (state.step == GuidePracticeStep.CONTACTS_CLEAN) "完成引导" else "下一步",
                    onNext, loading = state.busy, enabled = ready && !state.busy, modifier = Modifier.testTag("漫游引导下一步"))
            }
        }
    }
}

/** 首页邀请不遮罩、不抢导航，也不自动开始。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuidePracticeInvitation(onStart: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier.testTag("漫游引导邀请")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.launcher_cat), null, Modifier.size(60.dp), contentScale = ContentScale.Fit)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("想先试着记一笔吗？", style = MaterialTheme.typography.titleMedium)
                    Text("跟着小猫走一遍，示例不会保存。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            PracticeFlowPreview()
            Text("建联系人  ·  记礼金  ·  完成", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GlassTextButton(onSkip) { Text("稍后再说") }
                GlassButton(onStart) { Text("开始练习") }
            }
        }
    }
}

@Composable
private fun PracticeFlowPreview() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassCard(Modifier.weight(1f)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(LiZhangIcons.UserRoundPlus, null, tint = MaterialTheme.colorScheme.secondary)
                Text("联系人", style = MaterialTheme.typography.labelLarge)
            }
        }
        Icon(LiZhangIcons.ArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        GlassCard(Modifier.weight(1f)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(LiZhangIcons.Banknote, null, tint = MaterialTheme.colorScheme.primary)
                Text("礼金往来", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
