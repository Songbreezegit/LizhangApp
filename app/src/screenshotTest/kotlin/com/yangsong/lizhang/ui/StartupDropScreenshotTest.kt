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
@Preview(name = "浅色开屏190毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f)
@Composable
fun StartupDropLight190Screenshot() = StartupDropScreenshot(190f)

@PreviewTest
@Preview(name = "浅色开屏380毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f)
@Composable
fun StartupDropLight380Screenshot() = StartupDropScreenshot(380f)

@PreviewTest
@Preview(name = "浅色开屏412毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f)
@Composable
fun StartupDropLight412Screenshot() = StartupDropScreenshot(412f)

@PreviewTest
@Preview(name = "浅色开屏492毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f)
@Composable
fun StartupDropLight492Screenshot() = StartupDropScreenshot(492f)

@PreviewTest
@Preview(name = "浅色开屏588毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f)
@Composable
fun StartupDropLight588Screenshot() = StartupDropScreenshot(588f)

@PreviewTest
@Preview(name = "浅色开屏720毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f)
@Composable
fun StartupDropLight720Screenshot() = StartupDropScreenshot(720f)

@PreviewTest
@Preview(name = "浅色开屏940毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f)
@Composable
fun StartupDropLight940Screenshot() = StartupDropScreenshot(940f)

@PreviewTest
@Preview(name = "浅色开屏1100毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f)
@Composable
fun StartupDropLight1100Screenshot() = StartupDropScreenshot(1100f)

@PreviewTest
@Preview(name = "暗色开屏0毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun StartupDropDark0Screenshot() = StartupDropScreenshot(0f, darkTheme = true)

@PreviewTest
@Preview(name = "暗色开屏720毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun StartupDropDark720Screenshot() = StartupDropScreenshot(720f, darkTheme = true)

@PreviewTest
@Preview(name = "暗色开屏940毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun StartupDropDark940Screenshot() = StartupDropScreenshot(940f, darkTheme = true)

@PreviewTest
@Preview(name = "暗色开屏1100毫秒", locale = "zh-rCN", widthDp = 392, heightDp = 844, fontScale = 1f,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun StartupDropDark1100Screenshot() = StartupDropScreenshot(1100f, darkTheme = true)
