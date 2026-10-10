package com.yangsong.lizhang.ui.component

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.theme.CoralContainer
import com.yangsong.lizhang.ui.theme.MintContainer
import com.yangsong.lizhang.ui.viewmodel.StartupAnimationViewModel

/** 原礼账小猫下落、轻回弹触发水波；页面仍在下方同步准备，沿用同一启动时钟。 */
@Composable
fun BrandStartupOverlay(state: StartupAnimationViewModel, modifier: Modifier = Modifier) {
    if (!state.visible) return
    val appName = stringResource(R.string.app_name)
    LaunchedEffect(state, state.ready) {
        if (!state.ready) return@LaunchedEffect
        while (state.visible) {
            if (!ValueAnimator.areAnimatorsEnabled()) {
                state.finish()
                break
            }
            withFrameNanos(state::onFrame)
        }
    }
    BackHandler(onBack = state::finish)
    StartupDropScene(
        elapsedMs = { state.progress * StartupAnimationViewModel.DURATION_MILLIS },
        modifier = modifier.fillMaxSize().testTag("品牌开屏")
            .semantics(mergeDescendants = true) { paneTitle = appName }
            .pointerInput(Unit) {
                // 覆盖期间不把触摸传到尚未显示的记账页面。
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    )
}

/** 固定时间的同一生产场景，供关键阶段预览；不创建动画时钟或改变启动门禁。 */
@Composable
fun StartupDropScene(elapsedMs: Float, modifier: Modifier = Modifier) =
    StartupDropScene(elapsedMs = { elapsedMs }, modifier = modifier)

@Composable
private fun StartupDropScene(elapsedMs: () -> Float, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().graphicsLayer {
        // 状态仅在绘制阶段读取，逐帧更新不会重组整个启动页面。
        alpha = StartupDropMotion.overlayAlpha(elapsedMs())
    }.background(Color.White), contentAlignment = Alignment.Center) {
        val widthDp = maxWidth.value
        val heightDp = maxHeight.value
        AppGradientBackground(Modifier.matchParentSize().drawWithCache {
            val revealPath = Path()
            onDrawWithContent {
                val radius = StartupDropMotion.revealRadius(elapsedMs(), size.width, size.height)
                if (radius > 0f) {
                    revealPath.rewind()
                    revealPath.addOval(Rect(center.x - radius, center.y - radius,
                        center.x + radius, center.y + radius))
                    // 只扩大圆形裁切，保留现有渐变图的位置、比例和暗色版本。
                    clipPath(revealPath) { this@onDrawWithContent.drawContent() }
                }
            }
        }) {}
        Canvas(Modifier.matchParentSize().testTag("品牌开屏扩散")) {
            val frame = StartupDropMotion.frameAt(elapsedMs(), widthDp, heightDp)
            val colors = listOf(MintContainer, CoralContainer, MintContainer)
            frame.ripples.forEachIndexed { index, ripple ->
                if (ripple.opacity > 0f) {
                    val radius = ripple.radiusDp.dp.toPx()
                    val halfBand = ripple.bandWidthDp.dp.toPx() / 2f
                    val outerRadius = radius + halfBand
                    fun bandPosition(offset: Float) = (radius + halfBand * offset) / outerRadius
                    // 用透明宽波带模拟水面起伏：内侧轻阴影、柔和彩色波体、外侧白色高光。
                    // 普通径向画刷兼容旧版 Android，不依赖着色器或新增动画时钟。
                    val wave = Brush.radialGradient(
                        0f to Color.Transparent,
                        bandPosition(-1f) to Color.Transparent,
                        bandPosition(-.55f) to Color(0xFF67778B).copy(alpha = .15f * ripple.opacity),
                        bandPosition(0f) to colors[index].copy(alpha = .36f * ripple.opacity),
                        bandPosition(.5f) to Color.White.copy(alpha = .60f * ripple.opacity),
                        1f to Color.Transparent,
                        center = center,
                        radius = outerRadius,
                    )
                    drawCircle(brush = wave, radius = outerRadius, center = center)
                }
            }
        }
        Image(illustrationPainter(R.drawable.launcher_cat), contentDescription = null,
            modifier = Modifier.size(StartupDropMotion.CAT_SIZE_DP.dp).testTag("品牌开屏图标").graphicsLayer {
                val frame = StartupDropMotion.frameAt(elapsedMs(), widthDp, heightDp)
                transformOrigin = TransformOrigin(.5f, 1f)
                translationY = frame.catOffsetYDp.dp.toPx()
                scaleX = frame.catScaleX
                scaleY = frame.catScaleY
            })
    }
}
