package com.yangsong.lizhang.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.yangsong.lizhang.ui.component.AppGradientBackground
import com.yangsong.lizhang.ui.component.StartupDropScene
import com.yangsong.lizhang.ui.theme.LiZhangTheme

/** 直接预览生产时间轨；底层沿用主窗口 Surface 与页面渐变，淡出阶段不会落到透明画布。 */
@Composable
private fun StartupDropScreenshot(elapsedMs: Float, darkTheme: Boolean = false) {
    LiZhangTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AppGradientBackground {
                StartupDropScene(elapsedMs = elapsedMs, modifier = Modifier.matchParentSize())
            }
        }
    }
}

@PreviewTest
@Preview(name = "浅色开屏0毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f)
@Composable
fun StartupDropLight0Screenshot() = StartupDropScreenshot(0f)

@PreviewTest
@Preview(name = "浅色开屏130毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f)
@Composable
fun StartupDropLight130Screenshot() = StartupDropScreenshot(130f)

@PreviewTest
@Preview(name = "浅色开屏260毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f)
@Composable
fun StartupDropLight260Screenshot() = StartupDropScreenshot(260f)

@PreviewTest
@Preview(name = "浅色开屏280毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f)
@Composable
fun StartupDropLight280Screenshot() = StartupDropScreenshot(280f)

@PreviewTest
@Preview(name = "浅色开屏320毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f)
@Composable
fun StartupDropLight320Screenshot() = StartupDropScreenshot(320f)

@PreviewTest
@Preview(name = "浅色开屏380毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f)
@Composable
fun StartupDropLight380Screenshot() = StartupDropScreenshot(380f)

@PreviewTest
@Preview(name = "浅色开屏500毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f)
@Composable
fun StartupDropLight500Screenshot() = StartupDropScreenshot(500f)

@PreviewTest
@Preview(name = "浅色开屏620毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f)
@Composable
fun StartupDropLight620Screenshot() = StartupDropScreenshot(620f)

@PreviewTest
@Preview(name = "浅色开屏760毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f)
@Composable
fun StartupDropLight760Screenshot() = StartupDropScreenshot(760f)

@PreviewTest
@Preview(name = "暗色开屏0毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun StartupDropDark0Screenshot() = StartupDropScreenshot(0f, darkTheme = true)

@PreviewTest
@Preview(name = "暗色开屏500毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun StartupDropDark500Screenshot() = StartupDropScreenshot(500f, darkTheme = true)

@PreviewTest
@Preview(name = "暗色开屏620毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun StartupDropDark620Screenshot() = StartupDropScreenshot(620f, darkTheme = true)

@PreviewTest
@Preview(name = "暗色开屏760毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun StartupDropDark760Screenshot() = StartupDropScreenshot(760f, darkTheme = true)
