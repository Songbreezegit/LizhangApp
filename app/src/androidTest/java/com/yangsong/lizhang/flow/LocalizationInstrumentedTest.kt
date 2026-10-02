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
    fun 主题开关与弹窗切换后保留设置页() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN"))
        }
        compose.waitForIdle()
        compose.onNodeWithText("我的").performClick()
        compose.onNode(isToggleable()).performScrollTo()
        compose.onNode(isToggleable()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("语言").assertIsDisplayed()
        compose.onNode(isToggleable()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("主题设置").performClick()
        val options = compose.onAllNodes(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role,
                androidx.compose.ui.semantics.Role.RadioButton) and hasAnyAncestor(isDialog()),
        )
        options.assertCountEquals(3)
        options[2].performClick()
        compose.waitForIdle()
        options[2].assertIsSelected()
        options[1].performClick()
        compose.waitForIdle()
        options[1].assertIsSelected()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("语言").assertIsDisplayed()
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

}
