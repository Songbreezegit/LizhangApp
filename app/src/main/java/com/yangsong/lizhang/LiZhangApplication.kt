package com.yangsong.lizhang

import android.app.Application
import com.yangsong.lizhang.data.di.AppContainer

class LiZhangApplication : Application() {
    /** 目前使用手写容器；未来替换为 Hilt 时仅需迁移此组合根。 */
    val appContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        appContainer.startReminderCoordination()
    }
}
