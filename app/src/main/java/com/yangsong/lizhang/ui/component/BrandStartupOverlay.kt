package com.yangsong.lizhang.ui.component

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.viewmodel.StartupAnimationViewModel

/** 按 Figma 900ms 时间线播放轻弹与柔光；真实页面在下方同步准备。 */
@Composable
fun BrandStartupOverlay(state: StartupAnimationViewModel, modifier: Modifier = Modifier) {
    if (!state.visible) return
    val appName = stringResource(R.string.app_name)
    val accent = MaterialTheme.colorScheme.primary

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
    Box(
        modifier.fillMaxSize().testTag("品牌开屏")
            .graphicsLayer {
                // 逐帧状态只在绘制阶段读取，冷启动时不反复重组整个品牌层。
                val elapsed = state.progress * StartupAnimationViewModel.DURATION_MILLIS
                alpha = 1f - FastOutSlowInEasing.transform(((elapsed - 620f) /
                    (StartupAnimationViewModel.DURATION_MILLIS - 620f)).coerceIn(0f, 1f))
            }
            .background(MaterialTheme.colorScheme.background)
            .semantics(mergeDescendants = true) { paneTitle = appName }
            .pointerInput(Unit) {
                // 避免短暂覆盖期间点击到尚未可见的业务入口。
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // 开屏与即将显示的页面使用同一背景，不改变猫咪和柔光的播放时序。
        AppGradientBackground(Modifier.matchParentSize()) {}
        // 按用户更新后的无文字稿，让猫与柔光环独立锚定屏幕中心。
        Box(
            Modifier.size(112.dp).testTag("品牌开屏扩散").graphicsLayer {
                val elapsed = state.progress * StartupAnimationViewModel.DURATION_MILLIS
                val ring = LinearOutSlowInEasing.transform((elapsed / 620f).coerceIn(0f, 1f))
                val ringScale = 1f + (208f / 112f - 1f) * ring
                scaleX = ringScale
                scaleY = ringScale
                alpha = 1f - ring
            }
                .background(accent.copy(alpha = .05f), CircleShape)
                .border(1.6.dp, accent.copy(alpha = .18f), CircleShape),
        )
        Image(illustrationPainter(R.drawable.launcher_cat), contentDescription = null,
            modifier = Modifier.size(112.dp).testTag("品牌开屏图标").graphicsLayer {
                val elapsed = state.progress * StartupAnimationViewModel.DURATION_MILLIS
                val enter = LinearOutSlowInEasing.transform((elapsed / 300f).coerceIn(0f, 1f))
                val settle = FastOutSlowInEasing.transform(((elapsed - 300f) / 180f).coerceIn(0f, 1f))
                val brandScale = .76f + .28f * enter - .04f * settle
                scaleX = brandScale
                scaleY = brandScale
                translationY = (-8f * enter + 2f * settle).dp.toPx()
            })
    }
}
