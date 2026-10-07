package com.yangsong.lizhang.core.common

/** 进程内只消费一次启动机会；通知入口和系统恢复也会消费，回到桌面后不补播。 */
internal class StartupSession {
    private var claimed = false

    fun claim(restoringState: Boolean, directEntry: Boolean): Boolean {
        val firstActivity = !claimed
        claimed = true
        return firstActivity && !restoringState && !directEntry
    }
}
