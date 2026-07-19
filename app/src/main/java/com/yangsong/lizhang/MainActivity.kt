package com.yangsong.lizhang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.yangsong.lizhang.ui.navigation.LiZhangNavGraph
import com.yangsong.lizhang.ui.theme.LiZhangTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as LiZhangApplication).appContainer
        setContent {
            LiZhangTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    LiZhangNavGraph(appContainer)
                }
            }
        }
    }
}
