package com.yangsong.lizhang.ui

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.yangsong.lizhang.ui.screen.HomeContent
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.HomeUiState

/** 使用实际年度汇总文字与实体卡片；预览同层绘制菜单布局，跨窗口定位和模糊由仪器验收覆盖。 */
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
