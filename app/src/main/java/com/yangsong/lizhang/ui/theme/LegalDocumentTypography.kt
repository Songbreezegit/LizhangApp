package com.yangsong.lizhang.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/** 仅供离线法律正文使用，不改变应用其他页面和系统字号设置。 */
object LegalDocumentTypography {
    val Body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
        textAlign = TextAlign.Left,
    )
    val Title = Body.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
    val SectionTitle = Body.copy(fontSize = 18.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
    val Subheading = Body.copy(fontWeight = FontWeight.SemiBold)
}
