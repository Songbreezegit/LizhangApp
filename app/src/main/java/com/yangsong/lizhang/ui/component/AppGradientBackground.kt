package com.yangsong.lizhang.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.theme.*

/** 两套参考图均去除前景，保留各自渐变的位置与细微纸纹。 */
@Composable
fun AppGradientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val fraction = LocalThemeDarkFraction.current
    Box(modifier.fillMaxSize()) {
        if (fraction > 0f) Image(resourceBitmapPainter(R.drawable.blue_pink_indigo_gradient), contentDescription = null,
            modifier = Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
        // 浅色背景逐渐淡出以揭示深色背景，主题切换继续沿用同一进度。
        if (fraction < 1f) Image(resourceBitmapPainter(R.drawable.cream_periwinkle_gradient), contentDescription = null,
            modifier = Modifier.matchParentSize(), contentScale = ContentScale.FillBounds,
            alpha = (1f - fraction).coerceIn(0f, 1f))
        content()
    }
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
