package com.yangsong.lizhang.ui.onboarding

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.domain.onboarding.OnboardingState
import kotlin.math.min
import kotlin.math.roundToInt

/** 同窗普通 UI 层；不切换页面、不创建遮罩、不接管目标按钮的触摸。 */
@Composable
fun FeatureGuideOverlay(
    state: OnboardingState,
    isHome: Boolean,
    registry: FeatureGuideTargetRegistry,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isHome || !state.featureGuideVisible) return
    val target = FeatureGuideTarget.entries.firstOrNull { it.step == state.featureGuideStep } ?: return
    var initialPlacement by remember(target) { mutableStateOf(true) }
    val registeredBounds = registry.bounds(target)
    // 首次允许兄弟控件在同帧先报告位置；以后目标离场时一并移除气泡语义节点。
    if (!initialPlacement && registeredBounds == null) return
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    val color = MaterialTheme.colorScheme.primary
    val bubbleColor = MaterialTheme.colorScheme.surface
    var origin by remember { mutableStateOf(Offset.Zero) }
    var bubbleBounds by remember(target) { mutableStateOf<Rect?>(null) }
    SubcomposeLayout(
        modifier = modifier.fillMaxSize().testTag("功能引导高亮")
            .onPlaced { origin = it.positionInRoot(); initialPlacement = false }
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawBehind {
                val anchor = registry.bounds(target)?.translate(-origin)?.inflate(4.dp.toPx()) ?: return@drawBehind
                val stroke = 2.dp.toPx()
                drawRoundRect(color.copy(alpha = .85f), topLeft = anchor.topLeft + Offset(stroke / 2, stroke / 2),
                    size = Size((anchor.width - stroke).coerceAtLeast(0f), (anchor.height - stroke).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius(14.dp.toPx()), style = Stroke(stroke))
                val bubble = bubbleBounds ?: return@drawBehind
                val pointer = guidePointer(anchor, bubble, 24.dp.toPx(), 6.dp.toPx())
                // 气泡靠边时只移动箭头根部；尖端始终落在真实目标相邻边的中心。
                val arrow = Path().apply {
                    moveTo(pointer.baseLeft.x, pointer.baseLeft.y)
                    lineTo(pointer.tip.x, pointer.tip.y)
                    lineTo(pointer.baseRight.x, pointer.baseRight.y)
                }
                drawPath(arrow, bubbleColor)
                drawPath(arrow, color.copy(alpha = .85f), style = Stroke(1.5.dp.toPx()))
            },
    ) { constraints ->
        val gap = 12.dp.roundToPx()
        val left = (insets.getLeft(density, direction) - origin.x).roundToInt().coerceAtLeast(0) + gap
        val right = constraints.maxWidth - insets.getRight(density, direction) - gap
        val top = (insets.getTop(density) - origin.y).roundToInt().coerceAtLeast(0) + gap
        fun space(anchor: Rect?): GuideSpace {
            val navTop = listOf(FeatureGuideTarget.ADD_RECORD, FeatureGuideTarget.CONTACTS, FeatureGuideTarget.SETTINGS)
                .mapNotNull { registry.interactionBounds(it)?.top ?: registry.bounds(it)?.top }.minOrNull()
            // 顶部目标的气泡也必须停在实际底栏上方。
            val bottom = min(constraints.maxHeight - insets.getBottom(density) - gap,
                navTop?.let { (it - origin.y).roundToInt() - gap } ?: constraints.maxHeight)
            val aboveEnd = min(anchor?.top?.roundToInt()?.minus(gap) ?: bottom, bottom)
            val belowStart = maxOf(anchor?.bottom?.roundToInt()?.plus(gap) ?: top, top)
            return GuideSpace(aboveEnd, belowStart, (aboveEnd - top).coerceAtLeast(0), (bottom - belowStart).coerceAtLeast(0))
        }
        val width = min(((right - left).coerceAtLeast(0) * .86f).roundToInt(), 360.dp.roundToPx())
        layout(constraints.maxWidth, constraints.maxHeight) {
            // 同窗兄弟控件先放置，再按本帧的锚点测量气泡，避免首次布局采用过期高度。
            val anchor = registry.bounds(target)?.translate(-origin)?.inflate(4.dp.toPx()) ?: run {
                bubbleBounds = null
                return@layout
            }
            val placedSpace = space(anchor)
            val availableHeight = maxOf(placedSpace.above, placedSpace.below)
            if (width == 0 || availableHeight == 0) {
                bubbleBounds = null
                return@layout
            }
            val bubble = subcompose(target) {
                FeatureGuideBubble(state.featureGuideStep, onNext, onSkip)
            }.single().measure(Constraints(minWidth = width, maxWidth = width, maxHeight = availableHeight))
            val preferBelow = target == FeatureGuideTarget.REMINDERS
            val placeBelow = if (preferBelow) placedSpace.below >= bubble.height else placedSpace.above < bubble.height
            val bubbleY = if (placeBelow) placedSpace.belowStart else placedSpace.aboveEnd - bubble.height
            val bubbleX = (anchor.center.x.roundToInt() - bubble.width / 2).coerceIn(left, right - bubble.width)
            bubbleBounds = Rect(bubbleX.toFloat(), bubbleY.toFloat(),
                (bubbleX + bubble.width).toFloat(), (bubbleY + bubble.height).toFloat())
            bubble.place(bubbleX, bubbleY)
        }
    }
}

private data class GuideSpace(val aboveEnd: Int, val belowStart: Int, val above: Int, val below: Int)

internal data class GuidePointer(val baseLeft: Offset, val baseRight: Offset, val tip: Offset)

/** 使用物理坐标，RTL、边缘夹持和不同系统栏尺寸都不会再次镜像箭头。 */
internal fun guidePointer(anchor: Rect, bubble: Rect, cornerRadius: Float, halfWidth: Float): GuidePointer {
    val bubbleAbove = bubble.bottom <= anchor.top
    val baseY = if (bubbleAbove) bubble.bottom else bubble.top
    val inset = min(cornerRadius + halfWidth, bubble.width / 2)
    val baseX = anchor.center.x.coerceIn(bubble.left + inset, bubble.right - inset)
    val tip = Offset(anchor.center.x, if (bubbleAbove) anchor.top else anchor.bottom)
    return GuidePointer(Offset(baseX - halfWidth, baseY), Offset(baseX + halfWidth, baseY), tip)
}
