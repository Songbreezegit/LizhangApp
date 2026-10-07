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
    private val windowCallbacks: StartupWindowCallbacks = AndroidStartupWindowCallbacks(activity),
) {
    private val root = activity.findViewById<View>(android.R.id.content)
    private var detached = false
    private var scheduled = false
    private var contentCommitted = false
    private var systemSplashRemoved = !windowCallbacks.supportsSystemSplash || !waitForSystemSplash
    private val diagnosticHostId = System.identityHashCode(this)
    private val releaseTimeout = Runnable {
        // 少数系统不派发启动层退出回调时直接进入真实页面，不让启动覆盖无限等待。
        if (!detached && !state.ready) {
            state.recordWindowEvent("系统启动层退出回调超时", diagnosticHostId)
            state.finish()
        }
    }
    private val drawListener = ViewTreeObserver.OnDrawListener {
        if (!scheduled && state.visible && root.width > 0 && root.height > 0) {
            scheduled = true
            state.recordWindowEvent("内容开始绘制", diagnosticHostId)
            val committed = Runnable {
                if (!detached) {
                    contentCommitted = true
                    state.recordWindowEvent("内容首帧已提交", diagnosticHostId)
                    releaseIfReady()
                    if (!systemSplashRemoved) root.postDelayed(releaseTimeout, 1500L)
                } else state.recordWindowEvent("旧窗口内容提交回调已忽略", diagnosticHostId)
            }
            windowCallbacks.afterContentDraw(root, committed)
        }
    }

    fun attach() {
        if (!state.visible || state.hasStarted) return
        state.recordWindowEvent("窗口已挂载：等待系统启动层=$waitForSystemSplash", diagnosticHostId)
        state.awaitWindow()
        root.viewTreeObserver.addOnDrawListener(drawListener)
        if (windowCallbacks.supportsSystemSplash && waitForSystemSplash) {
            windowCallbacks.setSplashExitListener { removeSplash ->
                removeSplash()
                state.recordWindowEvent("系统启动层已调用移除", diagnosticHostId)
                if (!detached) {
                    systemSplashRemoved = true
                    releaseIfReady()
                } else state.recordWindowEvent("旧窗口启动层退出回调已忽略", diagnosticHostId)
            }
        }
    }

    private fun releaseIfReady() {
        if (!contentCommitted || !systemSplashRemoved) return
        root.removeCallbacks(releaseTimeout)
        state.recordWindowEvent("内容与系统启动层完成交接", diagnosticHostId)
        state.markReady()
    }

    fun detach() {
        if (detached) return
        detached = true
        state.recordWindowEvent("窗口已分离", diagnosticHostId)
        root.removeCallbacks(releaseTimeout)
        if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnDrawListener(drawListener)
        if (windowCallbacks.supportsSystemSplash && waitForSystemSplash) windowCallbacks.clearSplashExitListener()
    }
}

/** 故障回归可捕获迟到回调；默认实现仍使用原有系统提交与 Splash API，不改变交接时长。 */
internal interface StartupWindowCallbacks {
    val supportsSystemSplash: Boolean
    fun afterContentDraw(root: View, committed: Runnable)
    fun setSplashExitListener(listener: (() -> Unit) -> Unit)
    fun clearSplashExitListener()
}

private class AndroidStartupWindowCallbacks(private val activity: Activity) : StartupWindowCallbacks {
    override val supportsSystemSplash = Build.VERSION.SDK_INT >= 31

    override fun afterContentDraw(root: View, committed: Runnable) {
        if (Build.VERSION.SDK_INT >= 29 && root.isHardwareAccelerated) {
            root.viewTreeObserver.registerFrameCommitCallback(committed)
            root.postInvalidateOnAnimation()
        } else {
            // API 26–28 没有提交回调，在本次 onDraw 返回后确认画面已完成绘制。
            root.post(committed)
        }
    }

    override fun setSplashExitListener(listener: (() -> Unit) -> Unit) {
        if (Build.VERSION.SDK_INT >= 31) activity.splashScreen.setOnExitAnimationListener { splash ->
            listener { splash.remove() }
        }
    }

    override fun clearSplashExitListener() {
        if (Build.VERSION.SDK_INT >= 31) activity.splashScreen.clearOnExitAnimationListener()
    }
}
