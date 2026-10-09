package com.yangsong.lizhang

import android.app.Application
import com.yangsong.lizhang.data.di.AppContainer
import com.yangsong.lizhang.core.common.StartupSession

class LiZhangApplication : Application() {
    internal val startupSession = StartupSession()
    /** 目前使用手写容器；未来替换为 Hilt 时仅需迁移此组合根。 */
    val appContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        if (appContainer.canProcessPersonalData && appContainer.onboardingRepository.state.value.completed) {
            appContainer.startReminderCoordination()
        }
    }
}
