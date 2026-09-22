package com.yangsong.lizhang.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.ContactLedgerSummary
import com.yangsong.lizhang.ui.component.BottomNavBar
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.screen.ContactsContent
import com.yangsong.lizhang.ui.screen.HomeContent
import com.yangsong.lizhang.ui.screen.NotificationsContent
import com.yangsong.lizhang.ui.screen.ReminderAdvanceDialog
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.ContactsUiState
import com.yangsong.lizhang.ui.viewmodel.HomeUiState
import com.yangsong.lizhang.ui.viewmodel.NotificationsUiState

// 所有截图仅使用虚构展示数据，不读取设备联系人或数据库。
@Preview(name = "浅色360", widthDp = 360, heightDp = 800)
@Preview(name = "深色390", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "大字体412", widthDp = 412, heightDp = 915, fontScale = 1.5f)
@Preview(name = "深色大字体360", widthDp = 360, heightDp = 800, fontScale = 1.5f, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class GlassConfigurations

@PreviewTest @GlassConfigurations @Composable
fun GlassHomeScreenshot() {
    LiZhangTheme {
        Box(Modifier.fillMaxSize()) {
            HomeContent(HomeUiState(isLoading = false, year = 2026, received = 120000, given = 60000), {})
            BottomNavBar(AppDestination.Home, {}, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@PreviewTest @GlassConfigurations @Composable
fun GlassContactsScreenshot() = ContactsFixture(false)

@PreviewTest @GlassConfigurations @Composable
fun GlassSpeedDialScreenshot() = ContactsFixture(true)

@PreviewTest @GlassConfigurations @Composable
fun GlassContactManagementScreenshot() = ContactsFixture(false, true)

@Composable
private fun ContactsFixture(expanded: Boolean, managing: Boolean = false) {
    LiZhangTheme {
        Box(Modifier.fillMaxSize()) {
            ContactsContent(ContactsUiState(isLoading = false, isSelectionMode = managing,
                contacts = (1L..8L).map { ContactLedgerSummary(Contact(it, "示例联系人$it", relationship = "朋友"), 20000, 10000) }),
                {}, {}, {}, {}, initiallyExpanded = expanded)
            if (!managing) BottomNavBar(AppDestination.Contacts, {}, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@PreviewTest @GlassConfigurations @Composable
fun GlassRemindersScreenshot() {
    LiZhangTheme { NotificationsContent(NotificationsUiState(isLoading = false, remindersEnabled = true, reminderAdvanceDays = 3)) }
}

@PreviewTest @GlassConfigurations @Composable
fun GlassReminderDialogScreenshot() {
    LiZhangTheme { ReminderAdvanceDialog(3, {}, {}) }
}

@PreviewTest @GlassConfigurations @Composable
fun GlassAddGiftScreenshot() {
    LiZhangTheme {
        com.yangsong.lizhang.ui.screen.AddGiftContent(
            com.yangsong.lizhang.ui.viewmodel.GiftEditorUiState(eventDate = 1789862400000L), {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@PreviewTest @GlassConfigurations @Composable
fun GlassSettingsScreenshot() {
    LiZhangTheme {
        Box(Modifier.fillMaxSize()) {
            com.yangsong.lizhang.ui.screen.SettingsContent(
                com.yangsong.lizhang.ui.viewmodel.SettingsUiState(), {}, {}, {}, {})
            BottomNavBar(AppDestination.Settings, {}, Modifier.align(Alignment.BottomCenter))
        }
    }
}
