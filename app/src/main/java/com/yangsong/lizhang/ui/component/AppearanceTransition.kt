package com.yangsong.lizhang.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

/** 仅包裹外观设置操作；预览及独立组件仍可直接执行原操作。 */
val LocalAppearanceTransition = staticCompositionLocalOf<(() -> Unit) -> Unit> { { it() } }
val LocalAppearanceOpacity = staticCompositionLocalOf<() -> Float> { { 1f } }

/** 不复制导航树、不保存画面；语言重建时只恢复淡入标记。 */
@Composable
fun AppearanceTransition(content: @Composable () -> Unit) {
    var entering by rememberSaveable { mutableStateOf(false) }
    val opacity = remember { Animatable(if (entering) 0f else 1f) }
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        MaterialTheme.colorScheme.background,
        tween(320, easing = FastOutSlowInEasing),
        label = "外观背景过渡",
    )
    LaunchedEffect(Unit) {
        if (entering) {
            opacity.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
            entering = false
        }
    }
    val transition: (() -> Unit) -> Unit = { change ->
        if (!running) {
            running = true
            scope.launch {
                try {
                    opacity.animateTo(0f, tween(100, easing = FastOutSlowInEasing))
                    entering = true
                    change()
                    // 等待新资源完成组合；若重建，新的宿主会接续淡入。
                    withFrameNanos { }
                    withFrameNanos { }
                    opacity.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
                    entering = false
                } finally {
                    running = false
                }
            }
        }
    }
    CompositionLocalProvider(
        LocalAppearanceTransition provides transition,
        LocalAppearanceOpacity provides { opacity.value },
    ) {
        Box(Modifier.fillMaxSize().background(background)) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = opacity.value }) { content() }
        }
    }
}
