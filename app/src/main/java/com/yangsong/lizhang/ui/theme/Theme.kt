package com.yangsong.lizhang.ui.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val Light=lightColorScheme(primary=CoralPrimary,onPrimary=CardWhite,primaryContainer=CoralContainer,secondary=MintPrimary,secondaryContainer=MintContainer,tertiary=ApricotPrimary,tertiaryContainer=ApricotContainer,background=CreamBackground,surface=CardWhite,onBackground=InkPrimary,onSurface=InkPrimary,onSurfaceVariant=InkSecondary,outline=SoftDivider,error=AppError)
private val Dark=darkColorScheme(primary=DarkCoral,onPrimary=DarkBackground,primaryContainer=DarkCoralContainer,secondary=Color(0xFF72D89F),tertiary=Color(0xFFFFC76F),background=DarkBackground,surface=DarkSurface,onBackground=DarkText,onSurface=DarkText,onSurfaceVariant=DarkSecondary,outline=DarkDivider,error=Color(0xFFFF8A94))
@Composable fun LiZhangTheme(darkTheme:Boolean=isSystemInDarkTheme(),content:@Composable ()->Unit){MaterialTheme(colorScheme=if(darkTheme)Dark else Light,typography=LiZhangTypography,content=content)}
