package com.yangsong.lizhang.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.yangsong.lizhang.ui.component.BottomNavBar
import com.yangsong.lizhang.ui.component.LanguagePicker
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.screen.SettingsContent
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.SettingsUiState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@PreviewTest
@Preview(name = "中文设置", locale = "zh-rCN", widthDp = 360, heightDp = 900)
@Preview(name = "繁体中文设置", locale = "b+zh+Hant", widthDp = 360, heightDp = 900)
@Preview(name = "英文设置", locale = "en", widthDp = 360, heightDp = 900)
@Preview(name = "日文设置", locale = "ja", widthDp = 360, heightDp = 900)
@Preview(name = "韩文设置", locale = "ko", widthDp = 360, heightDp = 900)
@Preview(name = "西语设置", locale = "es", widthDp = 360, heightDp = 900)
@Preview(name = "法语设置", locale = "fr", widthDp = 360, heightDp = 900)
@Preview(name = "英文窄屏大字体", locale = "en", widthDp = 320, heightDp = 900, fontScale = 1.3f)
@Preview(name = "日文窄屏大字体", locale = "ja", widthDp = 320, heightDp = 900, fontScale = 1.3f)
@Preview(name = "英文深色设置", locale = "en", widthDp = 360, heightDp = 900, uiMode = 0x20)
@Composable
fun LocalizedSettingsPreview() {
    LiZhangTheme {
        val haze = rememberHazeState()
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                SettingsContent(SettingsUiState(), {}, {}, {}, {})
            }
            BottomNavBar(AppDestination.Settings, {}, Modifier.align(Alignment.BottomCenter), haze)
        }
    }
}

@PreviewTest
@Preview(name = "繁体中文语言选择", locale = "b+zh+Hant", widthDp = 320, heightDp = 720, fontScale = 1.3f)
@Preview(name = "英文语言选择", locale = "en", widthDp = 320, heightDp = 720, fontScale = 1.3f)
@Preview(name = "日文语言选择", locale = "ja", widthDp = 320, heightDp = 720, fontScale = 1.3f)
@Preview(name = "韩文语言选择", locale = "ko", widthDp = 320, heightDp = 720, fontScale = 1.3f)
@Preview(name = "西语语言选择", locale = "es", widthDp = 320, heightDp = 720, fontScale = 1.3f)
@Preview(name = "法语语言选择", locale = "fr", widthDp = 320, heightDp = 720, fontScale = 1.3f)
@Preview(name = "韩文深色语言选择", locale = "ko", widthDp = 320, heightDp = 720, fontScale = 1.3f, uiMode = 0x20)
@Composable
fun LocalizedLanguagePickerPreview() {
    LiZhangTheme { LanguagePicker {} }
}
