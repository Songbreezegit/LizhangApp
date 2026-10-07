package com.yangsong.lizhang.flow

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.ui.component.GlassTextButton
import com.yangsong.lizhang.ui.component.GlassSwitch
import com.yangsong.lizhang.ui.component.PrimaryButton
import com.yangsong.lizhang.ui.component.pressClickable
import com.yangsong.lizhang.ui.component.pressSelectable
import com.yangsong.lizhang.ui.component.pressToggleable
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** 使用人工测试按钮采样真实像素，覆盖触点、取消、禁用以及键盘输入。 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PressFeedbackInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private var clicks = 0
    private lateinit var inputMode: InputModeManager
    private val canvas get() = compose.onNodeWithTag("按压采样画布")
    private val target get() = compose.onNodeWithTag("按压测试按钮")

    private fun setup(dark: Boolean = false) {
        compose.setChineseContent {
            inputMode = LocalInputModeManager.current
            LiZhangTheme(darkTheme = dark) {
                Box(Modifier.size(280.dp, 160.dp).background(MaterialTheme.colorScheme.surface)
                    .testTag("按压采样画布"), contentAlignment = Alignment.Center) {
                    GlassTextButton({ clicks++ }, Modifier.size(220.dp, 64.dp).testTag("按压测试按钮")) {
                        Text("测试操作")
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(900)
        compose.waitForIdle()
    }

    private fun frame(name: String): ImageBitmap = canvas.captureToImage().also { image ->
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "press-v096/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun difference(first: ImageBitmap, second: ImageBitmap): Int {
        val a = first.toPixelMap()
        val b = second.toPixelMap()
        return (0 until a.height).sumOf { y ->
            (0 until a.width).count { x ->
                abs(a[x, y].red - b[x, y].red) + abs(a[x, y].green - b[x, y].green) +
                    abs(a[x, y].blue - b[x, y].blue) > .025f
            }
        }
    }

    private fun verifyTouch(dark: Boolean) {
        setup(dark)
        val suffix = if (dark) "深色" else "浅色"
        val initial = frame("$suffix-静止")
        target.performTouchInput { down(Offset(width * .24f, height * .5f)) }
        compose.mainClock.advanceTimeBy(144)
        val pressed = frame("$suffix-按下中间帧")
        assertTrue("按下产生可见光晕与下压，不能只有逻辑进度变化", difference(initial, pressed) > 200)
        val idlePixels = initial.toPixelMap()
        val pixels = pressed.toPixelMap()
        val colored = (0 until pixels.height).sumOf { y ->
            (0 until pixels.width).count { x ->
                pixels[x, y].red - pixels[x, y].blue > idlePixels[x, y].red - idlePixels[x, y].blue + .02f
            }
        }
        assertTrue("光晕带主题暖色，不能替换为灰色覆盖", colored > 100)
        target.performTouchInput { up() }
        settle()
        assertEquals(1, clicks)
        assertTrue("释放后恢复原有表面", difference(initial, frame("$suffix-释放复位")) < 30)
    }

    @Test fun 浅色按钮真实按下出现主题光晕释放后复位() = verifyTouch(false)
    @Test fun 深色按钮真实按下出现主题光晕释放后复位() = verifyTouch(true)

    @Test fun 手势取消不触发点击并清除光晕() {
        setup()
        val initial = frame("取消-起点")
        target.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        assertTrue(difference(initial, frame("取消-按下")) > 200)
        target.performTouchInput { cancel() }
        settle()
        assertEquals(0, clicks)
        assertTrue(difference(initial, frame("取消-复位")) < 30)
    }

    @Test fun 列表滑动取消按压而不触发列表项() {
        compose.setChineseContent {
            LiZhangTheme {
                Column(Modifier.size(280.dp, 240.dp).verticalScroll(rememberScrollState()).testTag("滚动列表")) {
                    repeat(20) { index ->
                        Box(Modifier.fillMaxWidth().height(64.dp).testTag("滚动项$index")
                            .pressClickable { clicks++ }.background(MaterialTheme.colorScheme.surface)) {
                            Text("测试条目 $index")
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("滚动列表").performTouchInput {
            down(Offset(width / 2f, height * .7f))
            advanceEventTime(140)
            moveTo(Offset(width / 2f, height * .2f), 180)
            up()
        }
        compose.waitForIdle()
        assertEquals("滚动竞争仍由标准 clickable 取消", 0, clicks)
        compose.onNodeWithTag("滚动项0").assertIsNotDisplayed()
    }

    @Test fun 禁用和保存中保持不可点击且没有按压光晕() {
        compose.setChineseContent {
            LiZhangTheme {
                Column(Modifier.size(280.dp, 220.dp).background(MaterialTheme.colorScheme.surface)
                    .testTag("按压采样画布")) {
                    PrimaryButton("不可操作", { clicks++ }, Modifier.testTag("禁用按钮"), enabled = false)
                    PrimaryButton("保存中", { clicks++ }, Modifier.testTag("保存按钮"), loading = true)
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val disabled = compose.onNodeWithTag("禁用按钮").assertIsNotEnabled()
        val before = disabled.captureToImage()
        disabled.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        assertEquals("禁用按钮不绘制按压反馈", 0, difference(before, disabled.captureToImage()))
        disabled.performTouchInput { up() }
        compose.onNodeWithTag("保存按钮").assertIsNotEnabled().performTouchInput { click() }
        assertEquals(0, clicks)
    }

    @Test fun 键盘焦点有轮廓并且回车只触发一次点击() {
        setup()
        val initial = frame("键盘-无焦点")
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        target.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.mainClock.advanceTimeBy(160)
        target.assertIsFocused()
        val focused = frame("键盘-焦点轮廓")
        assertTrue("焦点可见", difference(initial, focused) > 100)
        target.performKeyInput { keyDown(Key.Enter) }
        compose.mainClock.advanceTimeBy(160)
        assertTrue("键盘按下也有光晕", difference(focused, frame("键盘-按下")) > 100)
        target.performKeyInput { keyUp(Key.Enter) }
        settle()
        assertEquals(1, clicks)
        target.assertIsFocused()
    }

    @Test fun 单选和复选保留选择语义且回调只执行一次() {
        val selected = mutableStateOf(false)
        val checked = mutableStateOf(false)
        var selections = 0
        var toggles = 0
        compose.setChineseContent {
            LiZhangTheme {
                Column {
                    Box(Modifier.size(220.dp, 56.dp).testTag("单选项").pressSelectable(selected.value,
                        role = Role.RadioButton, onClick = { selected.value = true; selections++ })) { Text("单选") }
                    Box(Modifier.size(220.dp, 56.dp).testTag("复选项").pressToggleable(checked.value,
                        role = Role.Checkbox, onValueChange = { checked.value = it; toggles++ })) { Text("复选") }
                }
            }
        }
        compose.onNodeWithTag("单选项").assertIsNotSelected().performClick().assertIsSelected()
        compose.onNodeWithTag("复选项").assertIsOff().performClick().assertIsOn()
        assertEquals(1, selections)
        assertEquals(1, toggles)
    }

    @Test fun 可点击设置行中的开关真实触摸只切换一次且连续点按不丢失() {
        val checked = mutableStateOf(false)
        var toggles = 0
        fun toggle(value: Boolean) { checked.value = value; toggles++ }
        compose.setChineseContent {
            LiZhangTheme {
                Row(Modifier.width(320.dp).pressClickable { toggle(!checked.value) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("测试开关", Modifier.weight(1f))
                    GlassSwitch(checked.value, ::toggle, Modifier.testTag("触摸开关"))
                }
            }
        }
        val control = compose.onNodeWithTag("触摸开关")
        repeat(8) { index ->
            control.performTouchInput { click() }
            compose.waitForIdle()
            assertEquals("子开关点击不会被父行吞掉或重复提交", index + 1, toggles)
            assertEquals(index % 2 == 0, checked.value)
        }
    }
}
