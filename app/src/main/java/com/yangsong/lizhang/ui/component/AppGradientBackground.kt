package com.yangsong.lizhang.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.theme.*

/** 去掉参考图前景后保留奶油黄、白色和淡蓝紫的位置与细微纸纹。 */
@Composable
fun AppGradientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val fraction = LocalThemeDarkFraction.current
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(
        0f to DarkGradientTop, .42f to DarkBackground, 1f to DarkGradientBottom,
    ))) {
        // 纯背景按页面尺寸连续铺满，不参与无障碍与触摸；主题切换保留原进度。
        Image(painterResource(R.drawable.cream_periwinkle_gradient), contentDescription = null,
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
