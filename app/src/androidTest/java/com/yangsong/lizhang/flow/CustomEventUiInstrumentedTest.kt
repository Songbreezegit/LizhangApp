package com.yangsong.lizhang.flow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.MAX_CUSTOM_EVENT_NAME_LENGTH
import com.yangsong.lizhang.ui.component.CustomEventDialog
import com.yangsong.lizhang.ui.component.EventTypeSelector
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CustomEventUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun 深浅色普通与大字体事件每行间距均为10dp() {
        val font = mutableFloatStateOf(1f)
        val dark = mutableStateOf(false)
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, font.floatValue)) {
                LiZhangTheme(dark.value) {
                    Box(Modifier.width(300.dp)) { EventTypeSelector(EventType.OTHER, {}, "升学宴", {}) }
                }
            }
        }
        val labels = listOf(R.string.event_wedding, R.string.event_full_month, R.string.event_birthday,
            R.string.event_housewarming, R.string.event_festival, R.string.event_other).map(context::getString) + "升学宴"
        for (isDark in listOf(false, true)) for (scale in listOf(1f, 1.5f)) {
            compose.runOnIdle { dark.value = isDark; font.floatValue = scale }
            val rows = labels.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
                .groupBy { it.top }.toSortedMap().values.toList()
            assertTrue("受限宽度必须自然形成多行", rows.size >= 2)
            rows.zipWithNext().forEach { (previous, next) ->
                val gap = next.minOf { it.top } - previous.maxOf { it.bottom }
                assertEquals("每两行统一留10dp且不能重叠", 10f * density, gap, 2f)
            }
        }
    }

    @Test fun 弹窗资源文案空白超长与20个Unicode字符边界() {
        var result: String? = null
        compose.setContent { LiZhangTheme { CustomEventDialog(onConfirm = { result = it }, onDismiss = {}) } }
        compose.onNodeWithText(context.getString(R.string.event_custom_title)).assertIsDisplayed()
        val field = compose.onNodeWithTag("自定义事件名称")
        field.performClick()
        compose.onNodeWithText(context.getString(R.string.event_custom_hint)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.event_custom_limit, MAX_CUSTOM_EVENT_NAME_LENGTH)).assertIsDisplayed()
        val add = compose.onNodeWithText(context.getString(R.string.action_add))
        field.performTextInput("   ")
        add.performClick()
        compose.onNodeWithText(context.getString(R.string.event_custom_required)).assertIsDisplayed()
        assertNull(result)
        field.performTextReplacement("😀".repeat(21))
        add.performClick()
        compose.onNodeWithText(context.getString(R.string.event_custom_too_long, MAX_CUSTOM_EVENT_NAME_LENGTH)).assertIsDisplayed()
        assertNull(result)
        field.performTextReplacement("  " + "😀".repeat(20) + "  ")
        add.performClick()
        compose.runOnIdle { assertEquals("😀".repeat(20), result) }
    }
}
