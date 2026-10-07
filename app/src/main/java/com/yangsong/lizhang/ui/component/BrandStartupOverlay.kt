package com.yangsong.lizhang.ui.component

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.viewmodel.StartupAnimationViewModel

/** 品牌轻弹入场与单次柔和扩散；总时长固定，底下的真实页面同步准备。 */
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
                alpha = 1f - FastOutSlowInEasing.transform(((elapsed - 560f) / 320f).coerceIn(0f, 1f))
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(208.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().testTag("品牌开屏扩散")) {
                    val elapsed = state.progress * StartupAnimationViewModel.DURATION_MILLIS
                    val ring = LinearOutSlowInEasing.transform((elapsed / 740f).coerceIn(0f, 1f))
                    val radius = (56f + 46f * ring).dp.toPx()
                    drawCircle(accent.copy(alpha = .07f * (1f - ring)), radius)
                    drawCircle(accent.copy(alpha = .32f * (1f - ring)), radius,
                        style = Stroke(1.8.dp.toPx()))
                }
                Image(painterResource(R.drawable.launcher_cat), contentDescription = null,
                    modifier = Modifier.size(112.dp).testTag("品牌开屏图标").graphicsLayer {
                        val elapsed = state.progress * StartupAnimationViewModel.DURATION_MILLIS
                        val enter = LinearOutSlowInEasing.transform((elapsed / 300f).coerceIn(0f, 1f))
                        val settle = FastOutSlowInEasing.transform(((elapsed - 300f) / 180f).coerceIn(0f, 1f))
                        val brandScale = .76f + .30f * enter - .06f * settle
                        scaleX = brandScale
                        scaleY = brandScale
                        translationY = (1f - enter) * 10.dp.toPx()
                    })
            }
            Text(appName, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("品牌开屏名称").graphicsLayer {
                    val elapsed = state.progress * StartupAnimationViewModel.DURATION_MILLIS
                    val textEnter = FastOutSlowInEasing.transform(((elapsed - 110f) / 200f).coerceIn(0f, 1f))
                    alpha = textEnter
                    translationY = (1f - textEnter) * 8.dp.toPx()
                })
        }
    }
}
