package com.yangsong.lizhang.flow

import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import java.util.Locale

/** 中文功能夹具不依赖测试设备系统语言，其他 Activity 组合局部值继续从宿主继承。 */
fun ComposeContentTestRule.setChineseContent(content: @Composable () -> Unit) {
    setContent { ChineseFixture(content) }
}

/** 保存状态的夹具仍由原恢复测试器持有组合与保存状态。 */
fun StateRestorationTester.setChineseContent(content: @Composable () -> Unit) {
    setContent { ChineseFixture(content) }
}

fun localizedChineseContext(context: Context, configuration: Configuration = context.resources.configuration): Context =
    context.createConfigurationContext(Configuration(configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) })

@Composable
@Suppress("DEPRECATION")
private fun ChineseFixture(content: @Composable () -> Unit) {
    val originalContext = LocalContext.current
    val originalConfiguration = LocalConfiguration.current
    val originalView = LocalView.current
    val originalRegistryOwner = requireNotNull(LocalActivityResultRegistryOwner.current)
    var hostReady by remember(originalView) { mutableStateOf(false) }
    DisposableEffect(originalView) {
        // Dialog/Popup 的独立组合从宿主 View.context 重新提供资源，单独的 LocalContext
        // 覆盖无法传入该窗口。仅在夹具存活期间调整测试宿主，并在结束后还原。
        val hostResources = originalView.resources
        val previous = Configuration(hostResources.configuration)
        val chineseHost = Configuration(previous).apply { setLocale(Locale.SIMPLIFIED_CHINESE) }
        hostResources.updateConfiguration(chineseHost, hostResources.displayMetrics)
        hostReady = true
        onDispose { hostResources.updateConfiguration(previous, hostResources.displayMetrics) }
    }
    val chinese = remember(originalContext, originalConfiguration) {
        localizedChineseContext(originalContext, originalConfiguration)
    }
    if (hostReady) {
        CompositionLocalProvider(
            LocalContext provides chinese,
            LocalConfiguration provides chinese.resources.configuration,
            LocalActivityResultRegistryOwner provides originalRegistryOwner,
        ) { content() }
    }
}
