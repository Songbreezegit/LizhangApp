package com.yangsong.lizhang

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.animation.ValueAnimator
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import com.yangsong.lizhang.ui.component.LocalPendingDark
import com.yangsong.lizhang.ui.component.LocalCurrentLanguage
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.yangsong.lizhang.ui.component.AppearanceTransitionHost
import com.yangsong.lizhang.ui.component.currentAppLanguage
import com.yangsong.lizhang.ui.viewmodel.AppearanceTransitionViewModel
import com.yangsong.lizhang.ui.viewmodel.OnboardingViewModel
import com.yangsong.lizhang.ui.viewmodel.StartupAnimationViewModel
import com.yangsong.lizhang.ui.component.BrandStartupOverlay
import com.yangsong.lizhang.ui.component.StartupWindowGate
import com.yangsong.lizhang.ui.onboarding.OnboardingScreen
import com.yangsong.lizhang.domain.onboarding.OnboardingMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yangsong.lizhang.domain.model.AppThemeMode
import com.yangsong.lizhang.core.common.ReminderNavigationContract
import com.yangsong.lizhang.ui.navigation.LiZhangNavGraph
import com.yangsong.lizhang.ui.navigation.ReminderLaunchRequest
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.component.AppearanceTransition
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

class MainActivity : AppCompatActivity() {
    internal lateinit var appearanceHost: AppearanceTransitionHost
    internal lateinit var appearanceState: AppearanceTransitionViewModel
    internal lateinit var startupState: StartupAnimationViewModel
    private lateinit var startupWindowGate: StartupWindowGate
    private val reminderLaunchRequest = MutableStateFlow<ReminderLaunchRequest?>(null)
    private var nextRequestKey = 0L
    private val openRemindersRequest = MutableStateFlow<Long?>(null)
    private var appliedLocaleTags = ""
    private var localeRecreationRequested = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 只旁观设置控件的真实触摸，不消费事件或改变手势仲裁。
        if (BuildConfig.DEBUG && ::appearanceHost.isInitialized) appearanceHost.observeThemeTouch(event)
        return super.dispatchTouchEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_LiZhang)
        super.onCreate(savedInstanceState)
        appliedLocaleTags = resources.configuration.locales.toLanguageTags()
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
        appearanceState = ViewModelProvider(this)[AppearanceTransitionViewModel::class.java]
        appearanceState.languagePreference = currentAppLanguage()
        appearanceHost = AppearanceTransitionHost(this, appearanceState)
        // 复用的 Decor 还可能持有旧 Activity 的返回键调度器；
        // 必须先更新所有视图树所有者，重建后的返回键才能交给当前导航。
        initializeViewTreeOwners()
        // 客户端重建会复用 Decor；移除已销毁 Activity 的 ComposeView，
        // 让 setContent 为新 Activity 安装正确的生命周期与保存状态所有者。
        findViewById<android.view.ViewGroup>(android.R.id.content).removeAllViews()
        setContent {
            val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
            val startupVisible = startupState.visible
            LaunchedEffect(onboardingState.completed) {
                if (onboardingState.completed) appContainer.startReminderCoordination()
            }
            val themeMode by appContainer.themeRepository.themeMode.collectAsStateWithLifecycle()
            val pendingReminder by reminderLaunchRequest.collectAsStateWithLifecycle()
            val pendingOpenReminders by openRemindersRequest.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            LiZhangTheme(darkTheme = darkTheme, animateColors = appearanceState.animateColors) {
                AppearanceTransition(appearanceHost, darkTheme, appearanceState.languagePreference,
                    resources.configuration.locales[0].toLanguageTag()) {
                    CompositionLocalProvider(LocalPendingDark provides appearanceState.pendingDark,
                        LocalCurrentLanguage provides appearanceState.languagePreference) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Box(Modifier.fillMaxSize()) {
                                Box(if (startupVisible) Modifier.fillMaxSize().clearAndSetSemantics { }
                                    else Modifier.fillMaxSize()) {
                                    if (!onboardingState.completed) {
                                        // 引导没有业务导航条目，直接随当前 Activity 生命周期同步就绪。
                                        // 复用窗口重建时不依赖 Compose 状态收集的恢复时机。
                                        DisposableEffect(lifecycle, appearanceHost) {
                                            val observer = LifecycleEventObserver { owner, _ ->
                                                appearanceHost.navigationReady(owner.lifecycle.currentState == Lifecycle.State.RESUMED)
                                            }
                                            lifecycle.addObserver(observer)
                                            onDispose { lifecycle.removeObserver(observer) }
                                        }
                                        OnboardingScreen(OnboardingMode.FIRST_LAUNCH,
                                            onFinish = { onboardingViewModel.finish(OnboardingMode.FIRST_LAUNCH) })
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
        appearanceHost.attach()
        startupWindowGate = StartupWindowGate(this, startupState,
            waitForSystemSplash = savedInstanceState == null && startupState.visible && !startupState.hasStarted)
        startupWindowGate.attach()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val localesChanged = appliedLocaleTags != newConfig.locales.toLanguageTags()
        super.onConfigurationChanged(newConfig)
        appliedLocaleTags = resources.configuration.locales.toLanguageTags()
        if (localesChanged && !localeRecreationRequested) {
            // 仍实际重建 Activity，但由客户端保留窗口后重建，避免系统 locale
            // 配置重启先清空合成窗口；不再次提交语言设置。
            localeRecreationRequested = true
            recreate()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            startupState.finish()
            appearanceHost.cancel()
        }
    }

    override fun onDestroy() {
        startupWindowGate.detach()
        appearanceHost.detach(isChangingConfigurations)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isReminderIntent(intent)) startupState.finish()
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
