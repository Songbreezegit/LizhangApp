package com.yangsong.lizhang.ui.component

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.yangsong.lizhang.ui.theme.LocalEffectiveDarkTheme

/** 底部导航所有状态统一使用浅杏色，选中状态继续通过位置和抬升区分。 */
val NavigationIconColor = Color(0xFFFFCBAE)

/** 功能图标浅色为黑、深色为白；继承控件的禁用透明度，保证暗色模式可读。 */
@Composable
fun featureIconColor(): Color =
    (if (LocalEffectiveDarkTheme.current) Color.White else Color.Black)
        .copy(alpha = LocalContentColor.current.alpha)
