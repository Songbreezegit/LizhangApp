package com.yangsong.lizhang.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.ContactLedgerSummary
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.ui.component.BottomNavBar
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.screen.HomeContent
import com.yangsong.lizhang.ui.screen.ContactsContent
import com.yangsong.lizhang.ui.screen.SettingsContent
import com.yangsong.lizhang.ui.screen.CreateEncryptedBackupDialog
import com.yangsong.lizhang.ui.screen.RestoreBackupPasswordDialog
import com.yangsong.lizhang.ui.screen.PrivacyContent
import com.yangsong.lizhang.ui.screen.AddGiftContent
import com.yangsong.lizhang.ui.screen.DiscardGiftChangesDialog
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.HomeUiState
import com.yangsong.lizhang.ui.viewmodel.ContactSort
import com.yangsong.lizhang.ui.viewmodel.ContactsUiState
import com.yangsong.lizhang.ui.viewmodel.SettingsUiState
import com.yangsong.lizhang.ui.viewmodel.GiftEditorUiState

@PreviewTest
@Preview(name = "首页标准状态", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun HomeStandardScreenshotTest() {
    LiZhangTheme(darkTheme = false) {
        Box(Modifier.fillMaxSize()) {
            HomeContent(
                state = HomeUiState(
                    isLoading = false,
                    year = 2026,
                    recentRecords = listOf(
                        record(1, "表姐", 88_800, GiftDirection.RECEIVED, EventType.WEDDING),
                        record(2, "张同学", 60_000, GiftDirection.GIVEN, EventType.WEDDING),
                    ),
                    received = 866_000,
                    given = 295_000,
                ),
                onNavigate = {},
            )
            BottomNavBar(AppDestination.Home, {}, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@PreviewTest
@Preview(
    name = "首页空状态大字体",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
    fontScale = 1.3f,
)
@Composable
fun HomeEmptyLargeTextScreenshotTest() {
    LiZhangTheme(darkTheme = false) {
        Box(Modifier.fillMaxSize()) {
            HomeContent(
                state = HomeUiState(isLoading = false, year = 2026),
                onNavigate = {},
            )
            BottomNavBar(AppDestination.Home, {}, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@PreviewTest
@Preview(name = "联系人标准状态", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ContactsStandardScreenshotTest() {
    LiZhangTheme(darkTheme = false) {
        Box(Modifier.fillMaxSize()) {
            ContactsContent(
                state = ContactsUiState(
                    contacts = listOf(
                        ContactLedgerSummary(
                            contact = Contact(id = 1, name = "王阿姨", phone = "13800000000"),
                            receivedInCents = 88_800,
                            givenInCents = 20_000,
                        ),
                        ContactLedgerSummary(
                            contact = Contact(id = 2, name = "张同学", relationship = "同学"),
                            receivedInCents = 0,
                            givenInCents = 60_000,
                        ),
                    ),
                    sort = ContactSort.RECENT,
                    isLoading = false,
                ),
                onQueryChange = {},
                onSortChange = {},
                onContactClick = {},
                onAddContact = {},
            )
            BottomNavBar(AppDestination.Contacts, {}, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@PreviewTest
@Preview(
    name = "联系人空状态大字体",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
    fontScale = 1.3f,
)
@Composable
fun ContactsEmptyLargeTextScreenshotTest() {
    LiZhangTheme(darkTheme = false) {
        Box(Modifier.fillMaxSize()) {
            ContactsContent(
                state = ContactsUiState(isLoading = false),
                onQueryChange = {},
                onSortChange = {},
                onContactClick = {},
                onAddContact = {},
            )
            BottomNavBar(AppDestination.Contacts, {}, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@PreviewTest
@Preview(name = "记一笔标准状态", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun AddGiftStandardScreenshotTest() {
    LiZhangTheme(darkTheme = false) {
        AddGiftContent(
            state = GiftEditorUiState(
                contactId = 1,
                amount = "200",
                contacts = listOf(Contact(id = 1, name = "王阿姨")),
                direction = GiftDirection.GIVEN,
                eventType = EventType.BIRTHDAY,
            ),
            onBack = {},
            onContactClick = {},
            onAmountChange = {},
            onDateClick = {},
            onDirectionChange = {},
            onEventTypeChange = {},
            onNotesChange = {},
            onSave = {},
        )
    }
}

@PreviewTest
@Preview(name = "记一笔放弃填写确认", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun AddGiftDiscardConfirmationScreenshotTest() {
    LiZhangTheme(darkTheme = false) {
        DiscardGiftChangesDialog(
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "记一笔深色大字体",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
    fontScale = 1.3f,
)
@Composable
fun AddGiftDarkLargeTextScreenshotTest() {
    LiZhangTheme(darkTheme = true) {
        AddGiftContent(
            state = GiftEditorUiState(
                contactId = 1,
                amount = "800",
                contacts = listOf(Contact(id = 1, name = "王阿姨")),
            ),
            onBack = {},
            onContactClick = {},
            onAmountChange = {},
            onDateClick = {},
            onDirectionChange = {},
            onEventTypeChange = {},
            onNotesChange = {},
            onSave = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "记一笔深色大字体空表单",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
    fontScale = 1.3f,
)
@Composable
fun AddGiftDarkLargeEmptyScreenshotTest() {
    LiZhangTheme(darkTheme = true) {
        AddGiftContent(
            state = GiftEditorUiState(),
            onBack = {},
            onContactClick = {},
            onAmountChange = {},
            onDateClick = {},
            onDirectionChange = {},
            onEventTypeChange = {},
            onNotesChange = {},
            onSave = {},
        )
    }
}

@PreviewTest
@Preview(name = "我的标准状态", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun SettingsScreenshotTest() {
    LiZhangTheme(darkTheme = false) {
        Box(Modifier.fillMaxSize()) {
            SettingsContent(
                SettingsUiState(),
                onCsvExport = {},
                onExcelExport = {},
                onBackup = {},
                onThemeModeChange = {},
            )
            BottomNavBar(AppDestination.Settings, {}, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@PreviewTest
@Preview(name = "我的深色模式", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun SettingsDarkScreenshotTest() {
    LiZhangTheme(darkTheme = true) {
        Box(Modifier.fillMaxSize()) {
            SettingsContent(
                SettingsUiState(),
                onCsvExport = {},
                onExcelExport = {},
                onBackup = {},
                onThemeModeChange = {},
            )
            BottomNavBar(AppDestination.Settings, {}, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@PreviewTest
@Preview(name = "创建加密备份密码", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun CreateEncryptedBackupScreenshotTest() {
    LiZhangTheme(darkTheme = false) {
        CreateEncryptedBackupDialog(
            isPreparing = false,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@PreviewTest
@Preview(name = "加密备份密码错误", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun RestoreEncryptedBackupErrorScreenshotTest() {
    LiZhangTheme(darkTheme = false) {
        RestoreBackupPasswordDialog(
            fileIdentity = byteArrayOf(1),
            isReading = false,
            isPasswordInvalid = true,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "隐私说明大字体",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
    fontScale = 1.3f,
)
@Composable
fun PrivacyLargeTextScreenshotTest() {
    LiZhangTheme(darkTheme = false) {
        PrivacyContent()
    }
}

private fun record(
    id: Long,
    contactName: String,
    amountInCents: Long,
    direction: GiftDirection,
    eventType: EventType,
) = GiftRecordWithContact(
    record = GiftRecord(
        id = id,
        contactId = id,
        amountInCents = amountInCents,
        eventType = eventType,
        eventDate = 1_752_787_200_000,
        direction = direction,
    ),
    contactName = contactName,
)
