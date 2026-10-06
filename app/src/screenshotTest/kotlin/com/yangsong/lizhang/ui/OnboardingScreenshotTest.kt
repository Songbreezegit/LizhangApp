package com.yangsong.lizhang.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.yangsong.lizhang.domain.onboarding.OnboardingMode
import com.yangsong.lizhang.ui.onboarding.OnboardingScreen
import com.yangsong.lizhang.ui.theme.LiZhangTheme

@PreviewTest
@Preview(name = "首页中文", locale = "zh-rCN", widthDp = 360, heightDp = 800)
@Preview(name = "首页英文深色", locale = "en", widthDp = 360, heightDp = 800, uiMode = 0x20)
@Preview(name = "首页日文", locale = "ja", widthDp = 360, heightDp = 800)
@Preview(name = "首页韩文", locale = "ko", widthDp = 360, heightDp = 800)
@Composable
fun OnboardingIntroPreview() { LiZhangTheme { OnboardingScreen(OnboardingMode.FIRST_LAUNCH, {}) } }

@PreviewTest
@Preview(name = "功能中文", locale = "zh-rCN", widthDp = 360, heightDp = 800)
@Preview(name = "功能英文大字体", locale = "en", widthDp = 320, heightDp = 720, fontScale = 1.3f)
@Preview(name = "功能日文深色", locale = "ja", widthDp = 360, heightDp = 800, uiMode = 0x20)
@Preview(name = "功能韩文", locale = "ko", widthDp = 360, heightDp = 800)
@Composable
fun OnboardingFeaturesPreview() { LiZhangTheme { OnboardingScreen(OnboardingMode.FIRST_LAUNCH, {}, initialPage = 1) } }

@PreviewTest
@Preview(name = "隐私中文", locale = "zh-rCN", widthDp = 360, heightDp = 800)
@Preview(name = "隐私英文", locale = "en", widthDp = 360, heightDp = 800)
@Preview(name = "隐私日文大字体", locale = "ja", widthDp = 320, heightDp = 720, fontScale = 1.3f)
@Preview(name = "隐私韩文深色", locale = "ko", widthDp = 360, heightDp = 800, uiMode = 0x20)
@Composable
fun OnboardingPrivacyPreview() { LiZhangTheme { OnboardingScreen(OnboardingMode.FIRST_LAUNCH, {}, initialPage = 2) } }
