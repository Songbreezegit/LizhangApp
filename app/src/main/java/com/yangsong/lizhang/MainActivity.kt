package com.yangsong.lizhang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.yangsong.lizhang.domain.model.AppThemeMode
import com.yangsong.lizhang.ui.navigation.LiZhangNavGraph
import com.yangsong.lizhang.ui.theme.LiZhangTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as LiZhangApplication).appContainer
        setContent {
            val themeMode by appContainer.themeRepository.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            LiZhangTheme(darkTheme = darkTheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    LiZhangNavGraph(appContainer)
                }
            }
        }
    }
}
