package com.yangsong.lizhang.ui.onboarding

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import com.yangsong.lizhang.domain.onboarding.FeatureGuideStep
import com.yangsong.lizhang.ui.navigation.AppDestination

enum class FeatureGuideTarget(val step: FeatureGuideStep) {
    ADD_RECORD(FeatureGuideStep.ADD_RECORD),
    CONTACTS(FeatureGuideStep.CONTACTS),
    REMINDERS(FeatureGuideStep.REMINDERS),
    SETTINGS(FeatureGuideStep.SETTINGS);
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

    fun bounds(target: FeatureGuideTarget): Rect? = targets[target]?.bounds

    internal fun register(target: FeatureGuideTarget, owner: Any, bounds: Rect) {
        targets[target] = Registration(owner, bounds)
    }

    internal fun unregister(target: FeatureGuideTarget, owner: Any) {
        // 导航离场组件不能清除恢复后新组件的坐标。
        if (targets[target]?.owner === owner) targets.remove(target)
    }
}

val LocalFeatureGuideTargetRegistry = staticCompositionLocalOf<FeatureGuideTargetRegistry?> { null }

/** 控件只报告位置，不判断步骤、不消费触摸，也不持久化坐标。 */
fun Modifier.featureGuideTarget(target: FeatureGuideTarget): Modifier = composed {
    val registry = LocalFeatureGuideTargetRegistry.current
    val owner = remember(registry, target) { Any() }
    DisposableEffect(registry, target, owner) {
        onDispose { registry?.unregister(target, owner) }
    }
    fun report(coordinates: LayoutCoordinates) {
        val bounds = coordinates.boundsInRoot()
        // LazyColumn 滚动后只剩部分可见、或目标尚未布局时，等待它再次完整出现。
        if (bounds.width >= coordinates.size.width - .5f && bounds.height >= coordinates.size.height - .5f &&
            bounds.width > 0f && bounds.height > 0f) {
            registry?.register(target, owner, bounds)
        } else registry?.unregister(target, owner)
    }
    // 放置时先报告，气泡在同一帧的后续放置阶段即可读取；全局回调持续校正滚动等变化。
    testTag("功能引导目标${target.name}").onPlaced(::report).onGloballyPositioned(::report)
}
