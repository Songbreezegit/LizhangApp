package com.yangsong.lizhang.flow

import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.MainActivity
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.mapper.giftExportLabels
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 只操作空白测试模拟器的语言设置，不创建或导出联系人数据。 */
class LocalizationInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun 恢复系统语言() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
    }

    @Test
    fun 四语言资源复数和默认回退() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for ((tag, title) in listOf("zh-CN" to "语言", "en" to "Language", "ja" to "言語", "ko" to "언어", "fr" to "语言")) {
            val config = Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
            val localized = context.createConfigurationContext(config)
            assertEquals(tag, title, localized.getString(R.string.language))
            assertEquals("简体中文", localized.getString(R.string.language_chinese))
            assertEquals("English", localized.getString(R.string.language_english))
            assertEquals("日本語", localized.getString(R.string.language_japanese))
            assertEquals("한국어", localized.getString(R.string.language_korean))
            if (tag == "en") {
                assertEquals("Amount (CNY)", localized.giftExportLabels().headers[1])
                assertEquals("Gift Records", localized.giftExportLabels().sheetName)
                assertEquals("1 record", localized.resources.getQuantityString(R.plurals.records_count, 1, 1))
                assertEquals("2 records", localized.resources.getQuantityString(R.plurals.records_count, 2, 2))
            }
        }
        val chineseFirst = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags("zh-CN,en-US"))
        }
        assertEquals("语言", context.createConfigurationContext(chineseFirst).getString(R.string.language))
    }

    @Test
    fun 语言选择器切换四语言并在重建后保留选择() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN"))
        }
        compose.waitForIdle()
        compose.onNodeWithText("我的").performClick()
        var languageTitle = "语言"
        for ((nativeName, title) in listOf("English" to "Language", "日本語" to "言語", "한국어" to "언어", "简体中文" to "语言")) {
            compose.onNodeWithText(languageTitle).performScrollTo().performClick()
            listOf("简体中文", "English", "日本語", "한국어").forEach {
                compose.onNode(hasText(it) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            }
            compose.onNode(hasText(nativeName) and hasAnyAncestor(isDialog())).performClick()
            compose.waitForIdle()
            compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
            languageTitle = title
        }
        compose.onNodeWithText("语言").performScrollTo().performClick()
        compose.onNode(hasText("跟随系统") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty)
        }
    }
}
