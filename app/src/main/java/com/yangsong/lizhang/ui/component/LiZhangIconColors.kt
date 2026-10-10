package com.yangsong.lizhang.ui.component

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.ui.theme.LocalThemeDarkFraction

/** 底部导航所有状态统一使用橙色，选中状态继续通过位置和抬升区分。 */
val NavigationIconColor = Color(0xFFFF7F4F)

/** 透明页头位于浅蓝和粉色区域，局部使用黑色，不影响暗色卡片。 */
internal val LocalBrightGradientHeader = staticCompositionLocalOf { false }

@Composable
fun gradientHeaderColor(): Color =
    lerp(MaterialTheme.colorScheme.onSurface, Color.Black, LocalThemeDarkFraction.current)

@Composable
fun gradientHeaderSecondaryColor(): Color =
    lerp(MaterialTheme.colorScheme.onSurfaceVariant, Color.Black, LocalThemeDarkFraction.current)

/** 裸露在蓝粉背景上的浅色文字保留轻微轮廓，滚动至深浅区域都能辨认。 */
@Composable
fun gradientTextStyle(style: TextStyle): TextStyle {
    val fraction = LocalThemeDarkFraction.current
    return if (fraction == 0f) style else style.copy(
        shadow = Shadow(Color.Black.copy(alpha = .9f * fraction), blurRadius = 3f))
}

/** 快捷入口只描出图形轮廓，不添加底框；正文与卡片图标不继承此效果。 */
@Composable
fun Modifier.gradientIconOutline(painter: Painter): Modifier {
    val fraction = LocalThemeDarkFraction.current
    if (fraction == 0f) return this
    return drawBehind {
        val iconSize = size
        val offset = .5.dp.toPx()
        val outline = ColorFilter.tint(Color.Black.copy(alpha = .8f * fraction))
        with(painter) {
            translate(left = -offset) { draw(iconSize, colorFilter = outline) }
            translate(left = offset) { draw(iconSize, colorFilter = outline) }
            translate(top = -offset) { draw(iconSize, colorFilter = outline) }
            translate(top = offset) { draw(iconSize, colorFilter = outline) }
        }
    }
}

/** 功能图标跟随主题进度由黑过渡到白，并继承控件的禁用透明度。 */
@Composable
fun featureIconColor(): Color =
    (if (LocalBrightGradientHeader.current) Color.Black else
        lerp(Color.Black, Color.White, LocalThemeDarkFraction.current))
        .copy(alpha = LocalContentColor.current.alpha)
