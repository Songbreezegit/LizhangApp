package com.yangsong.lizhang

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.animation.ValueAnimator
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshotFlow
import com.yangsong.lizhang.ui.component.LocalPendingDark
import com.yangsong.lizhang.ui.component.LocalCurrentLanguage
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.yangsong.lizhang.ui.component.AppearanceTransitionHost
import com.yangsong.lizhang.ui.component.currentAppLanguage
import com.yangsong.lizhang.ui.viewmodel.AppearanceTransitionViewModel
import com.yangsong.lizhang.ui.viewmodel.OnboardingViewModel
import com.yangsong.lizhang.ui.viewmodel.PrivacyConsentViewModel
import com.yangsong.lizhang.ui.privacy.PrivacyNoticeGate
import com.yangsong.lizhang.ui.viewmodel.StartupAnimationViewModel
import com.yangsong.lizhang.ui.component.BrandStartupOverlay
import com.yangsong.lizhang.ui.component.StartupWindowGate
import com.yangsong.lizhang.ui.component.StartupDropMotion
import com.yangsong.lizhang.domain.model.AppThemeMode
import com.yangsong.lizhang.core.common.ReminderNavigationContract
import com.yangsong.lizhang.ui.navigation.LiZhangNavGraph
import com.yangsong.lizhang.ui.navigation.ReminderLaunchRequest
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.component.AppearanceTransition
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.clearAndSetSemantics

class MainActivity : AppCompatActivity() {
    internal lateinit var appearanceHost: AppearanceTransitionHost
    internal lateinit var appearanceState: AppearanceTransitionViewModel
    internal lateinit var startupState: StartupAnimationViewModel
    private lateinit var startupWindowGate: StartupWindowGate
    private val reminderLaunchRequest = MutableStateFlow<ReminderLaunchRequest?>(null)
    private var nextRequestKey = 0L
    private val openRemindersRequest = MutableStateFlow<Long?>(null)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 只旁观设置控件的真实触摸，不消费事件或改变手势仲裁。
        if (BuildConfig.DEBUG && ::appearanceHost.isInitialized) appearanceHost.observeThemeTouch(event)
        return super.dispatchTouchEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_LiZhang)
        super.onCreate(savedInstanceState)
        // 保留窗口已有边到边配置，避免重复初始化。
        if (!window.decorView.isAttachedToWindow) enableEdgeToEdge()
        val app = application as LiZhangApplication
        startupState = ViewModelProvider(this)[StartupAnimationViewModel::class.java]
        startupState.initialize(app.startupSession.claim(
            restoringState = savedInstanceState != null,
            directEntry = isReminderIntent(intent),
        ), animationsEnabled = ValueAnimator.areAnimatorsEnabled())
        handleReminderIntent(intent)
        val appContainer = app.appContainer
        val onboardingViewModel = ViewModelProvider(this, OnboardingViewModel.factory(appContainer.onboardingRepository))[OnboardingViewModel::class.java]
        val privacyViewModel = ViewModelProvider(this, PrivacyConsentViewModel.factory(appContainer.privacyConsentRepository))[PrivacyConsentViewModel::class.java]
        onboardingViewModel.enterApp(appContainer.canProcessPersonalData)
        appearanceState = ViewModelProvider(this)[AppearanceTransitionViewModel::class.java]
        appearanceState.languagePreference = currentAppLanguage()
        appearanceHost = AppearanceTransitionHost(this, appearanceState)
        // 复用的 Decor 还可能持有旧 Activity 的返回键调度器；
        // 必须先更新所有视图树所有者，重建后的返回键才能交给当前导航。
        initializeViewTreeOwners()
        // 新 Activity 使用新的 ComposeView 与所有者；语言覆盖留在 content 根中，
        // 不随旧页面一同清空，直到新导航、窗口焦点和真实提交帧准备完成。
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        composeView.setContent {
            val resourceLocales = LocalConfiguration.current.locales.toLanguageTags()
            val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
            val privacyState by privacyViewModel.state.collectAsStateWithLifecycle()
            val startupVisible = startupState.visible
            SideEffect {
                // 返回中断、关闭动画及正常播完共用清理，避免退出监听留到下一次打开。
                if (!startupVisible && ::startupWindowGate.isInitialized) startupWindowGate.detach()
            }
            LaunchedEffect(privacyState.canProcessPersonalData, onboardingState.completed) {
                if (privacyState.canProcessPersonalData && onboardingState.completed) appContainer.startReminderCoordination()
            }
            val themeMode by appContainer.themeRepository.themeMode.collectAsStateWithLifecycle()
            val pendingReminder by reminderLaunchRequest.collectAsStateWithLifecycle()
            val pendingOpenReminders by openRemindersRequest.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            LaunchedEffect(darkTheme, startupState) {
                // 顶部浅蓝与粉色使用深色图标；底部随白底和已揭示的暗色边缘切换。
                snapshotFlow {
                    StartupDropMotion.darkSystemBarIcons(
                        elapsedMs = startupState.progress * StartupAnimationViewModel.DURATION_MILLIS,
                        viewportWidth = window.decorView.width.toFloat(),
                        viewportHeight = window.decorView.height.toFloat(),
                        startupVisible = startupState.visible,
                        darkTheme = darkTheme,
                    )
                }.collect { darkIcons ->
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = true
                        isAppearanceLightNavigationBars = darkIcons
                    }
                }
            }
            LiZhangTheme(darkTheme = darkTheme, animateColors = appearanceState.animateColors) {
                AppearanceTransition(appearanceHost, darkTheme, appearanceState.languagePreference,
                    resourceLocales) {
                    CompositionLocalProvider(LocalPendingDark provides appearanceState.pendingDark,
                        LocalCurrentLanguage provides appearanceState.languagePreference) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Box(Modifier.fillMaxSize()) {
                                Box(if (startupVisible) Modifier.fillMaxSize().clearAndSetSemantics { }
                                    else Modifier.fillMaxSize()) {
                                    if (!privacyState.canProcessPersonalData) {
                                        // 尚未确认时不挂载业务导航及 ViewModel，不读联系人、账本或提醒。
                                        SideEffect { appearanceHost.navigationReady(!startupVisible) }
                                        PrivacyNoticeGate(
                                            consentViewModel = privacyViewModel,
                                            legalDocumentRepository = appContainer.legalDocumentRepository,
                                            onAccepted = { onboardingViewModel.enterApp(true) },
                                            onExit = { finishAndRemoveTask() },
                                        )
                                    } else LiZhangNavGraph(
                                        appContainer = appContainer,
                                        onboardingViewModel = onboardingViewModel,
                                        reminderLaunchRequest = pendingReminder,
                                        onReminderRequestConsumed = {
                                            if (reminderLaunchRequest.value == pendingReminder) {
                                                reminderLaunchRequest.value = null
                                            }
                                        },
                                        openRemindersRequest = pendingOpenReminders,
                                        onOpenRemindersConsumed = { openRemindersRequest.value = null },
                                        guideEnabled = !startupVisible,
                                    )
                                }
                                BrandStartupOverlay(startupState)
                            }
                        }
                    }
                }
            }
        }
        appearanceHost.replaceContent(composeView)
        startupWindowGate = StartupWindowGate(this, startupState,
            waitForSystemSplash = savedInstanceState == null && startupState.visible && !startupState.hasStarted)
        startupWindowGate.attach()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // locale 与 layoutDirection 已由 Manifest 声明自行处理；ComposeView
        // 接收真实配置后重组资源，保留窗口、导航和输入状态，避免首次重建的黑帧。
        if (::appearanceState.isInitialized) appearanceState.languagePreference = currentAppLanguage()
    }

    override fun onStop() {
        super.onStop()
        // 配置重建保留时间轴，但旧窗口从停止时起便不再提供就绪或帧回调。
        if (::startupWindowGate.isInitialized) {
            if (isChangingConfigurations) startupWindowGate.detach() else startupWindowGate.cancel()
        }
        if (!isChangingConfigurations) {
            startupState.finish()
            appearanceHost.cancel()
        }
    }

    override fun onDestroy() {
        if (::startupWindowGate.isInitialized) startupWindowGate.detach()
        appearanceHost.detach(isChangingConfigurations)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isReminderIntent(intent)) {
            if (::startupWindowGate.isInitialized) startupWindowGate.cancel()
            startupState.finish()
        }
        handleReminderIntent(intent)
    }

    private fun isReminderIntent(intent: Intent?): Boolean =
        intent?.action == ReminderNavigationContract.ACTION_OPEN_REMINDERS ||
            ReminderNavigationContract.readRecordId(intent) != null

    private fun handleReminderIntent(intent: Intent?) {
        if (intent?.action == ReminderNavigationContract.ACTION_OPEN_REMINDERS) {
            openRemindersRequest.value = ++nextRequestKey
            intent.action = null
            return
        }
        val recordId = ReminderNavigationContract.readRecordId(intent) ?: return
        reminderLaunchRequest.value = ReminderLaunchRequest(recordId, ++nextRequestKey)
        intent?.removeExtra(ReminderNavigationContract.EXTRA_RECORD_ID)
    }
}
