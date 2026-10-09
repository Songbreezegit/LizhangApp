package com.yangsong.lizhang.ui.onboarding

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.geometry.RoundRect
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
import com.yangsong.lizhang.domain.onboarding.FeatureGuideStep
import kotlin.math.min
import kotlin.math.roundToInt

/** 同窗遮罩聚焦真实控件；保留控件操作，不自动填写、保存或跨页演示。 */
@Composable
fun FeatureGuideOverlay(
    state: OnboardingState,
    isHome: Boolean,
    registry: FeatureGuideTargetRegistry,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    step: FeatureGuideStep = state.featureGuideStep,
    target: FeatureGuideTarget? = FeatureGuideTarget.entries.firstOrNull { it.step == step },
) {
    if (!isHome || !state.completed || step == FeatureGuideStep.COMPLETED || target == null) return
    val registeredBounds = registry.bounds(target)
    val reveal = remember(target) { Animatable(if (ValueAnimator.areAnimatorsEnabled()) 0f else 1f) }
    LaunchedEffect(target, registeredBounds != null) {
        if (registeredBounds == null) {
            if (ValueAnimator.areAnimatorsEnabled()) reveal.snapTo(0f)
        } else if (ValueAnimator.areAnimatorsEnabled()) reveal.animateTo(1f, tween(240, easing = LinearEasing))
        else reveal.snapTo(1f)
    }
    var initialPlacement by remember(target) { mutableStateOf(true) }
    // 首次允许兄弟控件在同帧先报告位置；以后目标离场时一并移除气泡语义节点。
    if (!initialPlacement && registeredBounds == null) return
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    val color = MaterialTheme.colorScheme.primary
    val bubbleColor = GuideCloudPalette.surface
    val bubbleBorderColor = GuideCloudPalette.outline
    var origin by remember { mutableStateOf(Offset.Zero) }
    var placement by remember(target) { mutableStateOf<GuideBubblePlacement?>(null) }
    SubcomposeLayout(
        modifier = modifier.fillMaxSize().testTag("功能引导高亮")
            .onPlaced { origin = it.positionInRoot(); initialPlacement = false }
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawBehind {
                val anchor = registry.bounds(target)?.translate(-origin)?.inflate(6.dp.toPx()) ?: return@drawBehind
                val progress = reveal.value
                val stroke = 2.dp.toPx()
                val radius = if (target == FeatureGuideTarget.ADD_RECORD || target == FeatureGuideTarget.RECORD_SAVE) 28.dp.toPx() else 16.dp.toPx()
                val mask = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addRoundRect(RoundRect(anchor, CornerRadius(radius)))
                }
                drawPath(mask, Color.Black.copy(alpha = .46f * progress))
                drawRoundRect(color.copy(alpha = .08f * progress), topLeft = anchor.topLeft, size = anchor.size,
                    cornerRadius = CornerRadius(radius))
                drawRoundRect(color.copy(alpha = .85f * progress), topLeft = anchor.topLeft + Offset(stroke / 2, stroke / 2),
                    size = Size((anchor.width - stroke).coerceAtLeast(0f), (anchor.height - stroke).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius(radius), style = Stroke(stroke))
                val placed = placement ?: return@drawBehind
                val movement = Offset(0f, 8.dp.toPx() * (1f - progress))
                // 椭圆合并在测量后只做一次；动画只平移整条轮廓，与猫咪和正文同步。
                withTransform({ translate(top = movement.y) }) {
                    drawPath(placed.cloud.path, bubbleColor.copy(alpha = bubbleColor.alpha * progress))
                    drawPath(placed.cloud.path, bubbleBorderColor.copy(alpha = bubbleBorderColor.alpha * progress),
                        style = Stroke(1.dp.toPx()))
                }
                val cloud = placed.cloud.copy(body = placed.cloud.body.translate(movement),
                    safeRect = placed.cloud.safeRect.translate(movement), lobes = placed.cloud.lobes.map { it.translate(movement) })
                guideThoughtBubbles(anchor, cloud, placed.cat?.translate(movement), placed.safe,
                    placed.cloudAbove, 1.dp.toPx()).forEach { dot ->
                    drawCircle(bubbleColor.copy(alpha = bubbleColor.alpha * progress), dot.radius, dot.center)
                    drawCircle(bubbleBorderColor.copy(alpha = bubbleBorderColor.alpha * progress), dot.radius, dot.center,
                        style = Stroke(1.dp.toPx()))
                }
            },
    ) { constraints ->
        val gap = 12.dp.roundToPx()
        // 预留 48dp，使 +8dp 入场的全过程仍能放下固定大小的两个圆泡。
        val thoughtGap = 48.dp.roundToPx()
        val horizontalMargin = 16.dp.roundToPx()
        val left = (insets.getLeft(density, direction) - origin.x).roundToInt().coerceAtLeast(0) + horizontalMargin
        val right = constraints.maxWidth - insets.getRight(density, direction) - horizontalMargin
        val top = (insets.getTop(density) - origin.y).roundToInt().coerceAtLeast(0) + gap
        fun space(anchor: Rect?): GuideSpace {
            val navTop = if (target == FeatureGuideTarget.ADD_RECORD) listOf(FeatureGuideTarget.ADD_RECORD, FeatureGuideTarget.CONTACTS, FeatureGuideTarget.SETTINGS)
                .mapNotNull { registry.interactionBounds(it)?.top ?: registry.bounds(it)?.top }.minOrNull()
                else null
            // 顶部目标的气泡也必须停在实际底栏上方。
            val bottom = min(constraints.maxHeight - insets.getBottom(density) - gap,
                navTop?.let { (it - origin.y).roundToInt() - gap } ?: constraints.maxHeight)
            val aboveEnd = min(anchor?.top?.roundToInt()?.minus(thoughtGap) ?: bottom, bottom)
            val belowStart = maxOf(anchor?.bottom?.roundToInt()?.plus(thoughtGap) ?: top, top)
            return GuideSpace(aboveEnd, belowStart, (aboveEnd - top).coerceAtLeast(0), (bottom - belowStart).coerceAtLeast(0), bottom)
        }
        val width = min((right - left).coerceAtLeast(0), 360.dp.roundToPx())
        layout(constraints.maxWidth, constraints.maxHeight) {
            // 同窗兄弟控件先放置，再按本帧的锚点测量气泡，避免首次布局采用过期高度。
            val anchor = registry.bounds(target)?.translate(-origin)?.inflate(6.dp.toPx()) ?: run {
                placement = null
                return@layout
            }
            val placedSpace = space(anchor)
            val availableHeight = maxOf(placedSpace.above, placedSpace.below)
            if (width == 0 || availableHeight == 0) {
                placement = null
                return@layout
            }
            val bubbleX = (anchor.center.x.roundToInt() - width / 2).coerceIn(left, right - width)
            val widthScale = width / 360.dp.toPx()
            // 装饰先缩小，让普通短稿至少有 208dp 云体；长文按真实文字和按钮自然增高。
            val heightScale = (availableHeight - 208.dp.toPx() * density.fontScale) / 40.dp.toPx()
            val decorationScale = min(heightScale, width / 224.dp.toPx()).coerceIn(0f, 1f)
            val decorationTopInset = 40.dp.toPx() * decorationScale
            val cloudVerticalScale = min(1f, (availableHeight - decorationTopInset) / 160.dp.toPx())
            val bubble = subcompose(target) {
                FeatureGuideBubble(step, onNext, onSkip, Modifier.graphicsLayer {
                    alpha = reveal.value
                    translationY = 8.dp.toPx() * (1f - reveal.value)
                }, decorationScale = decorationScale, widthScale = widthScale, cloudVerticalScale = cloudVerticalScale)
            }.single().measure(Constraints(minWidth = width, maxWidth = width, maxHeight = availableHeight))
            val preferBelow = target == FeatureGuideTarget.REMINDERS || target == FeatureGuideTarget.REMINDERS_PAGE ||
                target == FeatureGuideTarget.RECORD_CONTACT || target == FeatureGuideTarget.SEARCH
            val placeBelow = if (preferBelow) placedSpace.below >= bubble.height else placedSpace.above < bubble.height
            val bubbleY = if (placeBelow) placedSpace.belowStart else placedSpace.aboveEnd - bubble.height
            val bubbleBounds = Rect(bubbleX.toFloat(), bubbleY.toFloat(),
                (bubbleX + bubble.width).toFloat(), (bubbleY + bubble.height).toFloat())
            val cat = if (decorationScale > 0f) Rect(
                bubbleX + 186.dp.toPx() * widthScale - 64.dp.toPx() * decorationScale, bubbleY.toFloat(),
                bubbleX + 186.dp.toPx() * widthScale + 64.dp.toPx() * decorationScale,
                bubbleY + 64.dp.toPx() * decorationScale,
            ) else null
            val cloudBody = Rect(bubbleBounds.left, bubbleBounds.top + decorationTopInset, bubbleBounds.right, bubbleBounds.bottom)
            val safe = Rect(left.toFloat(), top.toFloat(), right.toFloat(), placedSpace.bottom.toFloat())
            // 避免每个动画帧重复布尔合并；锚点的本帧位置仍由圆泡函数读取。
            if (placement?.cloud?.body != cloudBody || placement?.cat != cat || placement?.safe != safe || placement?.cloudAbove != !placeBelow) {
                placement = GuideBubblePlacement(guideCloudGeometry(cloudBody, 1.dp.toPx()), cat, safe, !placeBelow)
            }
            bubble.place(bubbleX, bubbleY)
        }
    }
}

private data class GuideSpace(val aboveEnd: Int, val belowStart: Int, val above: Int, val below: Int, val bottom: Int)
private data class GuideBubblePlacement(val cloud: GuideCloudGeometry, val cat: Rect?, val safe: Rect, val cloudAbove: Boolean)

internal data class GuidePointer(val baseLeft: Offset, val baseRight: Offset, val tip: Offset)

/** 保留既有几何校验的调用契约；新样式的绘制只使用两个独立圆泡。 */
internal fun guidePointer(anchor: Rect, bubble: Rect, cornerRadius: Float, halfWidth: Float): GuidePointer {
    val bubbleAbove = bubble.bottom <= anchor.top
    val baseY = if (bubbleAbove) bubble.bottom else bubble.top
    val radius = min(cornerRadius, min(bubble.width, bubble.height) / 2f).coerceAtLeast(0f)
    val tailHalfWidth = min(halfWidth, ((bubble.width - 2f * radius) / 2f).coerceAtLeast(0f))
    val inset = min(radius + tailHalfWidth, bubble.width / 2)
    val baseX = anchor.center.x.coerceIn(bubble.left + inset, bubble.right - inset)
    val tip = Offset(anchor.center.x, if (bubbleAbove) anchor.top else anchor.bottom)
    return GuidePointer(Offset(baseX - tailHalfWidth, baseY), Offset(baseX + tailHalfWidth, baseY), tip)
}
