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

@PreviewTest @GlassConfigurations @Composable
fun CustomEventSelectedScreenshot() { LiZhangTheme { AppGradientBackground {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        DirectionSelector(com.yangsong.lizhang.domain.model.GiftDirection.RECEIVED, {})
        Spacer(Modifier.height(30.dp))
        EventTypeSelector(EventType.OTHER, {}, "升学宴", {})
    }
} } }
