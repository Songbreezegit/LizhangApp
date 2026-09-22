package com.yangsong.lizhang.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.theme.LiZhangTheme

@PreviewTest @GlassConfigurations @Composable
fun CustomEventDialogScreenshot() { LiZhangTheme { AppGradientBackground { CustomEventDialog("升学宴", {}, {}) } } }

@PreviewTest @GlassConfigurations @androidx.compose.ui.tooling.preview.Preview(name = "浅色412双行", widthDp = 412, heightDp = 500) @Composable
fun CustomEventSelectedScreenshot() { LiZhangTheme { AppScaffold { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.Center) {
        EventTypeSelector(EventType.OTHER, {}, "升学宴", {})
    }
} } }
