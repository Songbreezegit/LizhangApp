package com.yangsong.lizhang.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.yangsong.lizhang.ui.theme.*

/** 所有页面共用柔和中性背景，滚动时保持连续，内容卡片承担主要层级。 */
@Composable
fun AppGradientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val fraction = LocalThemeDarkFraction.current
    val top = lerp(GradientTop, DarkGradientTop, fraction)
    val middle = lerp(CreamBackground, DarkBackground, fraction)
    val bottom = lerp(GradientBottom, DarkGradientBottom, fraction)
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(
        0f to top, .42f to middle, 1f to bottom,
    )), content = content)
}

@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    AppGradientBackground(modifier) {
        Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground, topBar = topBar, bottomBar = bottomBar,
            snackbarHost = snackbarHost, content = content)
    }
}
