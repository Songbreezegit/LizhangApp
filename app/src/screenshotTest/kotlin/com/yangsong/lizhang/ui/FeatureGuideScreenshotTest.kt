package com.yangsong.lizhang.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.yangsong.lizhang.domain.onboarding.FeatureGuideStep
import com.yangsong.lizhang.domain.onboarding.OnboardingState
import com.yangsong.lizhang.ui.component.BottomNavBar
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.onboarding.*
import com.yangsong.lizhang.ui.screen.HomeContent
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.HomeUiState
import dev.chrisbanes.haze.rememberHazeState

@Composable
private fun GuidePreview(step: FeatureGuideStep) {
    LiZhangTheme {
        val registry = remember { FeatureGuideTargetRegistry() }
        val haze = rememberHazeState()
        CompositionLocalProvider(LocalFeatureGuideTargetRegistry provides registry) {
            Box(Modifier.fillMaxSize()) {
                HomeContent(HomeUiState(isLoading = false), {})
                BottomNavBar(AppDestination.Home, {}, Modifier.align(Alignment.BottomCenter), haze)
                FeatureGuideOverlay(OnboardingState(completed = true, featureGuideStep = step), true, registry, {}, {})
            }
        }
    }
}

@PreviewTest
@Preview(name = "记一笔浅色", locale = "zh-rCN", widthDp = 360, heightDp = 800)
@Preview(name = "记一笔英文", locale = "en", widthDp = 360, heightDp = 800)
@Preview(name = "记一笔日文大字体", locale = "ja", widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
fun FeatureGuideStep1Preview() = GuidePreview(FeatureGuideStep.ADD_RECORD)

@PreviewTest
@Preview(name = "提醒深色", locale = "zh-rCN", widthDp = 360, heightDp = 800, uiMode = 0x20)
@Preview(name = "提醒韩文深色大字体", locale = "ko", widthDp = 320, heightDp = 640, uiMode = 0x20, fontScale = 1.5f)
@Composable
fun FeatureGuideStep3Preview() = GuidePreview(FeatureGuideStep.REMINDERS)
