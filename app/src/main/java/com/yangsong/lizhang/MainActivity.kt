package com.yangsong.lizhang

import android.content.Intent
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
import com.yangsong.lizhang.core.common.ReminderNavigationContract
import com.yangsong.lizhang.ui.navigation.LiZhangNavGraph
import com.yangsong.lizhang.ui.navigation.ReminderLaunchRequest
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val reminderLaunchRequest = MutableStateFlow<ReminderLaunchRequest?>(null)
    private var nextRequestKey = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleReminderIntent(intent)
        val appContainer = (application as LiZhangApplication).appContainer
        setContent {
            val themeMode by appContainer.themeRepository.themeMode.collectAsStateWithLifecycle()
            val pendingReminder by reminderLaunchRequest.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            LiZhangTheme(darkTheme = darkTheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    LiZhangNavGraph(
                        appContainer = appContainer,
                        reminderLaunchRequest = pendingReminder,
                        onReminderRequestConsumed = {
                            if (reminderLaunchRequest.value == pendingReminder) {
                                reminderLaunchRequest.value = null
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReminderIntent(intent)
    }

    private fun handleReminderIntent(intent: Intent?) {
        val recordId = ReminderNavigationContract.readRecordId(intent) ?: return
        reminderLaunchRequest.value = ReminderLaunchRequest(recordId, ++nextRequestKey)
        intent?.removeExtra(ReminderNavigationContract.EXTRA_RECORD_ID)
    }
}
