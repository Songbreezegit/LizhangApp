package com.yangsong.lizhang.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.screen.GiftSaveBar
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@PreviewTest @GlassConfigurations @Composable
fun FrostedNavigationScreenshot() = FrostedFixture(0)

@PreviewTest @GlassConfigurations @Composable
fun FrostedContactFabScreenshot() = FrostedFixture(1)

@PreviewTest @GlassConfigurations @Composable
fun FrostedSpeedDialScreenshot() = FrostedFixture(2)

@PreviewTest @GlassConfigurations @Composable
fun FrostedSaveScreenshot() = FrostedFixture(3)

/** 图案和文字同时穿过浮层，避免纯色截图掩盖背景采样丢失。 */
@Composable
private fun FrostedFixture(surface: Int) {
    LiZhangTheme {
        val haze = rememberHazeState()
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                Canvas(Modifier.fillMaxSize()) {
                    val stripe = 6.dp.toPx()
                    for (x in 0..(size.width / stripe).toInt()) {
                        drawRect(if (x % 2 == 0) Color(0xFF9CB5CF) else Color(0xFFE4B48E),
                            Offset(x * stripe, 0f), Size(stripe, size.height))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(32) { Text("示例背景 $it · 联系人 / 备注 / ¥100.00") }
                }
            }
            when (surface) {
                0 -> BottomNavBar(AppDestination.Home, {}, Modifier.align(Alignment.BottomCenter), haze)
                1, 2 -> Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                    GlassActionMenu(surface == 2, {}, {}, listOf(
                        GlassAction("通讯录导入", LiZhangIcons.ContactRound) {},
                        GlassAction("手动添加", LiZhangIcons.UserRoundPlus) {},
                    ), haze)
                }
                3 -> GiftSaveBar(false, {}, Modifier.align(Alignment.BottomCenter), hazeState = haze)
            }
        }
    }
}
