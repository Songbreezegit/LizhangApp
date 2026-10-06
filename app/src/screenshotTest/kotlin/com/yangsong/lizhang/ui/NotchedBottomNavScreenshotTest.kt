package com.yangsong.lizhang.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.yangsong.lizhang.ui.component.BottomNavBar
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@PreviewTest @Preview(name = "首页浮钮", locale = "zh-rCN", widthDp = 360, heightDp = 200)
@Composable fun NotchedNavigationHomePreview() = NavigationFixture(AppDestination.Home)

@PreviewTest @Preview(name = "联系人浮钮", locale = "zh-rCN", widthDp = 360, heightDp = 200)
@Composable fun NotchedNavigationContactsPreview() = NavigationFixture(AppDestination.Contacts)

@PreviewTest @Preview(name = "记一笔浮钮", locale = "zh-rCN", widthDp = 360, heightDp = 200)
@Composable fun NotchedNavigationAddGiftPreview() = NavigationFixture(AppDestination.AddGift)

@PreviewTest @Preview(name = "我的浮钮", locale = "zh-rCN", widthDp = 360, heightDp = 200)
@Composable fun NotchedNavigationSettingsPreview() = NavigationFixture(AppDestination.Settings)

@PreviewTest @Preview(name = "首页深色浮钮", locale = "zh-rCN", widthDp = 360, heightDp = 200, uiMode = 0x20)
@Composable fun NotchedNavigationDarkPreview() = NavigationFixture(AppDestination.Home)

@PreviewTest @Preview(name = "西语窄屏大字体", locale = "es", widthDp = 320, heightDp = 240, fontScale = 1.5f)
@Composable fun NotchedNavigationSpanishPreview() = NavigationFixture(AppDestination.Contacts)

@PreviewTest @Preview(name = "法语窄屏大字体", locale = "fr", widthDp = 320, heightDp = 240, fontScale = 1.5f)
@Composable fun NotchedNavigationFrenchPreview() = NavigationFixture(AppDestination.Settings)

@PreviewTest @Preview(name = "RTL我的浮钮", locale = "zh-rCN", widthDp = 320, heightDp = 200)
@Composable fun NotchedNavigationRtlPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        NavigationFixture(AppDestination.Settings)
    }
}

/** 使用人工图案确认真实凹槽透出背景，避免用同色矩形掩盖轮廓。 */
@Composable
private fun NavigationFixture(current: AppDestination) {
    LiZhangTheme {
        val haze = rememberHazeState()
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize().hazeSource(haze)) {
                val stripe = 12.dp.toPx()
                for (index in 0..(size.width / stripe).toInt()) {
                    drawRect(if (index % 2 == 0) Color(0xFF9CB5CF) else Color(0xFFE4B48E),
                        Offset(index * stripe, 0f), Size(stripe, size.height))
                }
            }
            BottomNavBar(current, {}, Modifier.align(Alignment.BottomCenter), haze)
        }
    }
}
