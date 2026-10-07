package com.yangsong.lizhang.ui

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.yangsong.lizhang.ui.screen.HomeContent
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.HomeUiState

/** 使用实际年度汇总文字与实体卡片作为菜单背景，覆盖浅深主题与大字体。 */
@PreviewTest @GlassConfigurations @Composable
fun HomeYearMenuScreenshot() {
    LiZhangTheme {
        HomeContent(
            HomeUiState(isLoading = false, year = 2026, availableYears = listOf(2026, 2025),
                received = 866000, given = 295000),
            onNavigate = {},
            initiallyYearMenuExpanded = true,
        )
    }
}
