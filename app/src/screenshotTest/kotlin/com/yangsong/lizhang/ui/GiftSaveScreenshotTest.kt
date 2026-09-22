package com.yangsong.lizhang.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.ui.component.GlassSnackbar
import com.yangsong.lizhang.ui.screen.AddGiftContent
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.GiftEditorUiState

@PreviewTest @GlassConfigurations @Composable
fun GiftSaveIdleScreenshot() = GiftSaveFixture()
@PreviewTest @GlassConfigurations @Composable
fun GiftSaveLoadingScreenshot() = GiftSaveFixture(loading = true)
@PreviewTest @GlassConfigurations @Composable
fun GiftSaveSuccessScreenshot() = GiftSaveFixture(message = "礼金记录已保存")
@PreviewTest @GlassConfigurations @Composable
fun GiftSaveFailureScreenshot() = GiftSaveFixture(message = "保存失败，请重试", error = true)

@Composable
private fun GiftSaveFixture(loading: Boolean = false, message: String? = null, error: Boolean = false) {
    LiZhangTheme {
        AddGiftContent(GiftEditorUiState(
            contacts = listOf(Contact(1, "示例联系人")), contactId = 1, amount = "100",
            eventDate = 1789862400000L, notes = "示例备注", isSaving = loading,
            isSaved = message != null && !error), {}, {}, {}, {}, {}, {}, {}, {},
            snackbarHost = {
                if (message != null) GlassSnackbar(message, Modifier.padding(horizontal = 24.dp), isError = error)
            })
    }
}
