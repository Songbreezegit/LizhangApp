package com.yangsong.lizhang.ui.component

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.yangsong.lizhang.ui.theme.LocalThemeDarkFraction

/** 底部导航所有状态统一使用浅杏色，选中状态继续通过位置和抬升区分。 */
val NavigationIconColor = Color(0xFFFFCBAE)

/** 功能图标跟随主题进度由黑过渡到白，并继承控件的禁用透明度。 */
@Composable
fun featureIconColor(): Color =
    lerp(Color.Black, Color.White, LocalThemeDarkFraction.current)
        .copy(alpha = LocalContentColor.current.alpha)
