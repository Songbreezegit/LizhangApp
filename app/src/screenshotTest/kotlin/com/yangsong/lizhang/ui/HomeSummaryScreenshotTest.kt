package com.yangsong.lizhang.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.yangsong.lizhang.ui.screen.HomeContent
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.HomeUiState

/** 年份与完整金额构成主层级，不依赖装饰插图或金额省略。 */
@PreviewTest
@Preview(name = "年度收支双列", locale = "zh-rCN", widthDp = 412, heightDp = 915)
@Preview(name = "年度收支深色", locale = "zh-rCN", widthDp = 390, heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AnnualSummaryScreenshot() {
    LiZhangTheme {
        HomeContent(HomeUiState(isLoading = false, year = 2026, received = 120_000, given = 60_000), onNavigate = {})
    }
}

@PreviewTest
@Preview(name = "年度收支窄屏长金额", locale = "zh-rCN", widthDp = 320, heightDp = 915)
@Preview(name = "年度收支法语大字体", locale = "fr", widthDp = 360, heightDp = 1080, fontScale = 1.5f)
@Preview(name = "年度收支繁体大字体", locale = "b+zh+Hant", widthDp = 360, heightDp = 1080, fontScale = 1.3f)
@Composable
fun AnnualSummaryLongAmountScreenshot() {
    LiZhangTheme {
        HomeContent(HomeUiState(isLoading = false, year = 2026,
            received = 9_876_543_210, given = 12_345_678_900), onNavigate = {})
    }
}
