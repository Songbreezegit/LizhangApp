package com.yangsong.lizhang.core.common

/** 全新主窗口可播放；已存在窗口的恢复、通知直达和配置重建不增加启动动画。 */
internal class StartupSession {
    private var claimed = false
    internal val hasClaimed get() = claimed

    fun claim(restoringState: Boolean, directEntry: Boolean): Boolean {
        claimed = true
        // 清理任务不一定结束进程，不能用进程内旧标记屏蔽下一次全新窗口。
        // 同一窗口是否已初始化由 StartupAnimationViewModel 保证。
        return !restoringState && !directEntry
    }
}
