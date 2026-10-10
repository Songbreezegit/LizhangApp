package com.yangsong.lizhang.ui.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.math.hypot

/** 将 Figma 的九百毫秒时间轨换算为任意窗口的几何，不持有窗口、状态或第二套时钟。 */
internal object StartupDropMotion {
    const val DURATION_MS = 900f
    const val CAT_SIZE_DP = 112f
    const val LANDING_MS = 260f
    const val REVEAL_END_MS = 620f
    private val fallEasing = CubicBezierEasing(.45f, 0f, 1f, 1f)
    private val bounceEasing = CubicBezierEasing(.2f, 0f, .2f, 1f)
    private val revealEasing = CubicBezierEasing(.2f, 0f, 0f, 1f)
    private val rippleStarts = listOf(260f, 310f, 360f)
    private val rippleRadiusFractions = listOf(1f, .92f, .84f)
    private val rippleOpacities = listOf(.65f, .50f, .35f)

    fun frameAt(elapsedMs: Float, viewportWidthDp: Float, viewportHeightDp: Float): StartupDropFrame {
        val time = elapsedMs.coerceIn(0f, DURATION_MS)
        val centerY = viewportHeightDp / 2f
        // 参考稿 844dp 高时从中心 Y=-64dp 落到 422dp；各窗口起点均完全位于顶部之外。
        val startOffset = -centerY - CAT_SIZE_DP / 2f - 8f
        val offsetY = when {
            time < LANDING_MS -> startOffset * (1f - fallEasing.transform(segment(time, 0f, LANDING_MS)))
            time < 280f -> 0f
            time < 320f -> -12f * bounceEasing.transform(segment(time, 280f, 320f))
            time < 380f -> -12f * (1f - bounceEasing.transform(segment(time, 320f, 380f)))
            else -> 0f
        }
        val squash = when {
            time < LANDING_MS -> 0f
            time < 280f -> bounceEasing.transform(segment(time, LANDING_MS, 280f))
            time < 320f -> 1f - bounceEasing.transform(segment(time, 280f, 320f))
            else -> 0f
        }
        val maximumRadius = maximumRadius(viewportWidthDp, viewportHeightDp)
        val ripples = rippleStarts.indices.map { index ->
            val local = segment(time, rippleStarts[index], rippleStarts[index] + 360f)
            val target = maxOf(CAT_SIZE_DP / 2f, maximumRadius * rippleRadiusFractions[index])
            StartupRippleFrame(
                radiusDp = CAT_SIZE_DP / 2f + (target - CAT_SIZE_DP / 2f) * revealEasing.transform(local),
                opacity = if (time < rippleStarts[index]) 0f else rippleOpacities[index] * (1f - local),
            )
        }
        return StartupDropFrame(
            catOffsetYDp = offsetY,
            catScaleX = 1f + .06f * squash,
            catScaleY = 1f - .06f * squash,
            revealRadiusDp = revealRadius(time, viewportWidthDp, viewportHeightDp),
            overlayAlpha = overlayAlpha(time),
            ripples = ripples,
        )
    }

    /** 半对角线保证圆形揭示覆盖四角；宽高可同时使用 dp 或 px。 */
    fun revealRadius(elapsedMs: Float, viewportWidth: Float, viewportHeight: Float): Float =
        maximumRadius(viewportWidth, viewportHeight) *
            revealEasing.transform(segment(elapsedMs, LANDING_MS, REVEAL_END_MS))

    fun overlayAlpha(elapsedMs: Float): Float =
        1f - FastOutSlowInEasing.transform(segment(elapsedMs, REVEAL_END_MS, DURATION_MS))

    /** 白底阶段使用深色系统栏图标；暗色渐变到达上下边缘后恢复浅色图标。 */
    fun darkSystemBarIcons(elapsedMs: Float, viewportWidth: Float, viewportHeight: Float,
        startupVisible: Boolean, darkTheme: Boolean): Boolean = !darkTheme ||
        (startupVisible && (viewportHeight <= 0f ||
            revealRadius(elapsedMs, viewportWidth, viewportHeight) < viewportHeight / 2f))

    private fun maximumRadius(width: Float, height: Float): Float = hypot(width / 2f, height / 2f)

    private fun segment(time: Float, start: Float, end: Float): Float =
        ((time - start) / (end - start)).coerceIn(0f, 1f)
}

internal data class StartupDropFrame(
    val catOffsetYDp: Float,
    val catScaleX: Float,
    val catScaleY: Float,
    val revealRadiusDp: Float,
    val overlayAlpha: Float,
    val ripples: List<StartupRippleFrame>,
)

internal data class StartupRippleFrame(val radiusDp: Float, val opacity: Float)
