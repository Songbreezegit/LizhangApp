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

/** 所有页面共用连续的低饱和背景，滚动内容不会重启渐变。 */
@Composable
fun AppGradientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val fraction = LocalThemeDarkFraction.current
    val colors = listOf(lerp(GradientTop, DarkGradientTop, fraction),
        lerp(CreamBackground, DarkBackground, fraction), lerp(GradientBottom, DarkGradientBottom, fraction))
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
