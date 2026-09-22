package com.yangsong.lizhang.flow

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.domain.model.*
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.screen.*
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** 仅使用虚构数据；设备截图保存在应用测试目录，不读取真实通讯录。 */
class GradientUiDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun 普通卡片内部保持背景透色() {
        val background = mutableStateOf(Color(0xFFBFDCEC))
        compose.setContent { LiZhangTheme(false) {
            Box(Modifier.size(200.dp).background(background.value)) {
                GlassCard(Modifier.fillMaxSize().testTag("通透卡片")) {
                    Column(Modifier.fillMaxSize().padding(24.dp)) { Text("示例内容") }
                }
            }
        } }
        fun sample(): Color {
            val pixels = compose.onNodeWithTag("通透卡片").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        val blue = sample()
        compose.runOnIdle { background.value = Color(0xFFF1D2BA) }
        val orange = sample()
        assertTrue("卡片内部必须透出蓝色背景", blue.blue > blue.red + .025f)
        assertTrue("卡片内部必须透出橘色背景", orange.red > orange.blue + .025f)
        assertTrue("内容区域不可覆盖不透明底色", kotlin.math.abs(blue.red - orange.red) > .06f)
    }

    @Test fun 六个页面深浅主题设备验收() {
        val page = mutableIntStateOf(0)
        val dark = mutableStateOf(false)
        val contact = Contact(1, "示例联系人", relationship = "朋友")
        val records = listOf(GiftRecordWithContact(
            GiftRecord(1, 1, 10000, EventType.WEDDING, 1789862400000L, GiftDirection.RECEIVED), contact.name))
        compose.setContent { LiZhangTheme(dark.value) {
            Box(Modifier.fillMaxSize()) {
                key(page.intValue, dark.value) {
                    when (page.intValue) {
                        0 -> HomeContent(HomeUiState(isLoading = false, year = 2026, received = 30000, given = 20000, recentRecords = records), {})
                        1 -> ContactsContent(ContactsUiState(isLoading = false,
                            contacts = listOf(ContactLedgerSummary(contact, 30000, 20000))), {}, {}, {}, {})
                        2 -> AddGiftContent(GiftEditorUiState(contacts = listOf(contact), contactId = 1, amount = "100", eventDate = 1789862400000L), {}, {}, {}, {}, {}, {}, {}, {})
                        3 -> NotificationsContent(NotificationsUiState(isLoading = false, remindersEnabled = true))
                        4 -> SettingsContent(SettingsUiState(), {}, {}, {}, {})
                    }
                }
                if (page.intValue in listOf(0, 1, 4)) BottomNavBar(
                    when(page.intValue) { 1 -> AppDestination.Contacts; 4 -> AppDestination.Settings; else -> AppDestination.Home },
                    {}, Modifier.align(Alignment.BottomCenter))
            }
        } }
        fun capture(name: String) {
            compose.waitForIdle()
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "ui-repair-review").apply { mkdirs() }
            File(directory, name + ".png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            for (index in 0..4) {
                compose.runOnIdle { page.intValue = index }
                compose.waitForIdle()
                if (index == 0) compose.onNodeWithContentDescription("记一笔礼金").assertDoesNotExist()
                capture("${if (night) "dark" else "light"}-$index")
                if (index == 1) {
                    compose.onNodeWithTag("联系人添加菜单").performClick()
                    compose.onNodeWithText("通讯录导入").assertIsDisplayed()
                    compose.onNodeWithText("手动添加").assertIsDisplayed()
                    capture("${if (night) "dark" else "light"}-menu")
                    compose.onNodeWithTag("联系人添加菜单").performClick()
                    compose.onNodeWithText("手动添加").assertDoesNotExist()
                }
                if (index == 4) {
                    compose.onNodeWithText("隐私说明").performScrollTo()
                    capture("${if (night) "dark" else "light"}-settings-bottom")
                }
                if (index == 2) {
                    compose.onNodeWithText("备注", substring = true).performScrollTo()
                    capture("${if (night) "dark" else "light"}-form-bottom")
                }
            }
        }
    }
}
