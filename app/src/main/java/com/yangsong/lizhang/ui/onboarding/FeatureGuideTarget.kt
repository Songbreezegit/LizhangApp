package com.yangsong.lizhang.ui.onboarding

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.domain.onboarding.FeatureGuideStep
import com.yangsong.lizhang.ui.navigation.AppDestination

enum class FeatureGuideTarget(val step: FeatureGuideStep) {
    ADD_RECORD(FeatureGuideStep.ADD_RECORD),
    RECORD_CONTACT(FeatureGuideStep.RECORD_CONTACT),
    RECORD_AMOUNT(FeatureGuideStep.RECORD_AMOUNT),
    RECORD_DIRECTION(FeatureGuideStep.RECORD_DIRECTION),
    RECORD_SAVE(FeatureGuideStep.RECORD_SAVE),
    CONTACTS(FeatureGuideStep.CONTACTS),
    REMINDERS(FeatureGuideStep.REMINDERS),
    SETTINGS(FeatureGuideStep.SETTINGS),
    CONTACTS_PAGE(FeatureGuideStep.CONTACTS),
    REMINDERS_PAGE(FeatureGuideStep.REMINDERS),
    SETTINGS_PAGE(FeatureGuideStep.SETTINGS),
    CALENDAR(FeatureGuideStep.CALENDAR),
    SEARCH(FeatureGuideStep.SEARCH),
    STATISTICS(FeatureGuideStep.STATISTICS);
}

fun AppDestination.featureGuideTarget(): FeatureGuideTarget? = when (this) {
    AppDestination.AddGift -> FeatureGuideTarget.ADD_RECORD
    AppDestination.Contacts -> FeatureGuideTarget.CONTACTS
    AppDestination.Notifications -> FeatureGuideTarget.REMINDERS
    AppDestination.Settings -> FeatureGuideTarget.SETTINGS
    else -> null
}

@Stable
class FeatureGuideTargetRegistry {
    private data class Registration(val owner: Any, val bounds: Rect)
    private val targets = mutableStateMapOf<FeatureGuideTarget, Registration>()
    private val interactions = mutableStateMapOf<FeatureGuideTarget, Registration>()

    fun bounds(target: FeatureGuideTarget): Rect? {
        val bounds = targets[target]?.bounds ?: return null
        val saveBar = targets[FeatureGuideTarget.RECORD_SAVE]?.bounds
        // 保存栏是滚动内容的兄弟覆盖层，普通bounds裁切看不到这层遮挡。
        return bounds.takeUnless {
            target in scrollableRecordTargets && saveBar != null && bounds.bottom > saveBar.top
        }
    }
    fun interactionBounds(target: FeatureGuideTarget): Rect? = interactions[target]?.bounds

    internal fun register(target: FeatureGuideTarget, owner: Any, bounds: Rect, visual: Boolean = true) {
        (if (visual) targets else interactions)[target] = Registration(owner, bounds)
    }

    internal fun unregister(target: FeatureGuideTarget, owner: Any, visual: Boolean = true) {
        // 导航离场组件不能清除恢复后新组件的坐标。
        val entries = if (visual) targets else interactions
        if (entries[target]?.owner === owner) entries.remove(target)
    }
}

val LocalFeatureGuideTargetRegistry = staticCompositionLocalOf<FeatureGuideTargetRegistry?> { null }
val LocalActiveFeatureGuideTarget = staticCompositionLocalOf<FeatureGuideTarget?> { null }
private val scrollableRecordTargets = setOf(FeatureGuideTarget.RECORD_CONTACT,
    FeatureGuideTarget.RECORD_AMOUNT, FeatureGuideTarget.RECORD_DIRECTION)

/** 控件只报告位置，不判断步骤、不消费触摸，也不持久化坐标。 */
fun Modifier.featureGuideTarget(target: FeatureGuideTarget, registerBounds: Boolean = true): Modifier =
    testTag("功能引导目标${target.name}").featureGuideAnchorBounds(target, visual = false).then(
        if (registerBounds) Modifier.featureGuideAnchorBounds(target) else Modifier,
    )

/** 点击范围可以包含留白；引导边框只跟随真正看得见的图标、标签或控件。 */
fun Modifier.featureGuideVisualAnchor(target: FeatureGuideTarget): Modifier =
    testTag("功能引导视觉锚点${target.name}").featureGuideAnchorBounds(target)

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.featureGuideAnchorBounds(target: FeatureGuideTarget, visual: Boolean = true): Modifier = composed {
    val registry = LocalFeatureGuideTargetRegistry.current
    val activeTarget = LocalActiveFeatureGuideTarget.current
    val bringIntoView = remember { BringIntoViewRequester() }
    var targetSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val owner = remember(registry, target, visual) { Any() }
    DisposableEffect(registry, target, owner, visual) {
        onDispose { registry?.unregister(target, owner, visual) }
    }
    LaunchedEffect(activeTarget, target, visual, targetSize, density) {
        // 只滚动当前页面，不切换页面；窄屏和大字体时让真实目标完整进入可见区域。
        if (!visual && activeTarget == target && targetSize.height > 0) {
            withFrameNanos { }
            // 沿用表单现有124dp底部留白，滚动目标时预留固定保存栏的位置。
            val bottomClearance = if (target in scrollableRecordTargets) with(density) { 124.dp.toPx() } else 0f
            bringIntoView.bringIntoView(Rect(0f, 0f, targetSize.width.toFloat(), targetSize.height + bottomClearance))
        }
    }
    fun report(coordinates: LayoutCoordinates) {
        targetSize = coordinates.size
        val bounds = coordinates.boundsInRoot()
        // LazyColumn 滚动后只剩部分可见、或目标尚未布局时，等待它再次完整出现。
        if (bounds.width >= coordinates.size.width - .5f && bounds.height >= coordinates.size.height - .5f &&
            bounds.width > 0f && bounds.height > 0f) {
            registry?.register(target, owner, bounds, visual)
        } else registry?.unregister(target, owner, visual)
    }
    // 放置时先报告，气泡在同一帧的后续放置阶段即可读取；全局回调持续校正滚动等变化。
    bringIntoViewRequester(bringIntoView).onPlaced(::report).onGloballyPositioned(::report)
}
