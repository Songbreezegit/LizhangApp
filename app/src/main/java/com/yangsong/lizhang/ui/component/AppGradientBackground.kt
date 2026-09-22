package com.yangsong.lizhang.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.yangsong.lizhang.ui.theme.*

/** 所有页面共用连续的低饱和背景，滚动内容不会重启渐变。 */
@Composable
fun AppGradientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val colors = if (dark) listOf(DarkGradientTop, DarkBackground, DarkGradientBottom)
        else listOf(GradientTop, CreamBackground, GradientBottom)
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(colors)), content = content)
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
