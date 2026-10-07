package com.yangsong.lizhang.ui.component

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import com.yangsong.lizhang.ui.viewmodel.StartupAnimationViewModel

/** 首帧提交且系统启动层退出后才开始计时，避免把品牌动画消耗在不可见的窗口后面。 */
internal class StartupWindowGate(
    private val activity: Activity,
    private val state: StartupAnimationViewModel,
    private val waitForSystemSplash: Boolean,
) {
    private val root = activity.findViewById<View>(android.R.id.content)
    private var detached = false
    private var scheduled = false
    private var contentCommitted = false
    private var systemSplashRemoved = Build.VERSION.SDK_INT < 31 || !waitForSystemSplash
    private val releaseTimeout = Runnable {
        // 少数系统不派发启动层退出回调时直接进入真实页面，不让启动覆盖无限等待。
        if (!detached && !state.ready) state.finish()
    }
    private val drawListener = ViewTreeObserver.OnDrawListener {
        if (!scheduled && state.visible && root.width > 0 && root.height > 0) {
            scheduled = true
            val committed = Runnable {
                if (!detached) {
                    contentCommitted = true
                    releaseIfReady()
                    if (!systemSplashRemoved) root.postDelayed(releaseTimeout, 1500L)
                }
            }
            if (Build.VERSION.SDK_INT >= 29 && root.isHardwareAccelerated) {
                root.viewTreeObserver.registerFrameCommitCallback(committed)
                root.postInvalidateOnAnimation()
            } else {
                // API 26–28 没有提交回调，在本次 onDraw 返回后确认画面已完成绘制。
                root.post(committed)
            }
        }
    }

    fun attach() {
        if (!state.visible || state.hasStarted) return
        state.awaitWindow()
        root.viewTreeObserver.addOnDrawListener(drawListener)
        if (Build.VERSION.SDK_INT >= 31 && waitForSystemSplash) {
            activity.splashScreen.setOnExitAnimationListener { splash ->
                splash.remove()
                if (!detached) {
                    systemSplashRemoved = true
                    releaseIfReady()
                }
            }
        }
    }

    private fun releaseIfReady() {
        if (!contentCommitted || !systemSplashRemoved) return
        root.removeCallbacks(releaseTimeout)
        state.markReady()
    }

    fun detach() {
        if (detached) return
        detached = true
        root.removeCallbacks(releaseTimeout)
        if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnDrawListener(drawListener)
        if (Build.VERSION.SDK_INT >= 31 && waitForSystemSplash) activity.splashScreen.clearOnExitAnimationListener()
    }
}
