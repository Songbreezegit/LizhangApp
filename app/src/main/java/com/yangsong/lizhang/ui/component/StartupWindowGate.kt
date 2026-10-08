package com.yangsong.lizhang.ui.component

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.window.SplashScreenView
import com.yangsong.lizhang.ui.viewmodel.StartupAnimationViewModel

/** 内容实际绘制后交接；提交或系统退出回调缺失时降级播放，不把已绘制内容硬切掉。 */
internal class StartupWindowGate(
    activity: Activity,
    private val state: StartupAnimationViewModel,
    private val waitForSystemSplash: Boolean,
    private val windowCallbacks: StartupWindowCallbacks = AndroidStartupWindowCallbacks(activity),
) {
    private val root = activity.findViewById<View>(android.R.id.content)
    private val handler = Handler(Looper.getMainLooper())
    private var attached = false
    private var detached = false
    private var scheduled = false
    private var drawObserved = false
    private var dispatchingDraw = false
    private var contentDrawn = false
    private var contentCommitted = false
    private var systemSplashRemoved = !windowCallbacks.supportsSystemSplash || !waitForSystemSplash
    private var callbacksCleared = false
    private var preDrawObserver: ViewTreeObserver? = null
    private var drawObserver: ViewTreeObserver? = null
    private var contentCommit: Runnable? = null
    private val diagnosticHostId = System.identityHashCode(this)
    private val currentWindow get() = !detached && state.ownsWindow(diagnosticHostId)
    private val releaseTimeout: Runnable = Runnable {
        if (currentWindow && state.visible && !state.ready) {
            if (drawObserved && !contentDrawn) {
                // 本次绘制已经返回消息队列，先让排在后面的绘制确认执行，不能误判为未绘制。
                handler.post(releaseTimeout)
            } else if (contentDrawn) {
                startPlayback("窗口交接达到截止时间：按已绘制内容降级播放")
            } else {
                state.recordWindowEvent("内容绘制回调超时", diagnosticHostId)
                cancel()
            }
        }
    }
    private val handoffGrace: Runnable = Runnable {
        if (currentWindow && state.visible && !state.ready && contentDrawn) {
            val missing = when {
                !contentCommitted && !systemSplashRemoved -> "内容提交与系统启动层退出回调"
                !contentCommitted -> "内容提交回调"
                else -> "系统启动层退出回调"
            }
            startPlayback("绘制后宽限结束：缺少$missing，降级播放")
        }
    }
    private val playbackTimeout: Runnable = Runnable {
        if (currentWindow && state.visible) {
            state.recordWindowEvent("启动动画帧回调超时", diagnosticHostId)
            cancel()
        }
    }
    private val removeDrawListener: Runnable = Runnable {
        val observer = drawObserver?.takeIf { it.isAlive } ?: root.viewTreeObserver
        if (observer.isAlive) observer.removeOnDrawListener(drawListener)
        drawObserver = null
    }
    private val removePreDrawListener: Runnable = Runnable {
        val observer = preDrawObserver?.takeIf { it.isAlive } ?: root.viewTreeObserver
        if (observer.isAlive) observer.removeOnPreDrawListener(preDrawListener)
        preDrawObserver = null
    }
    private val contentCommitOnMain: Runnable = Runnable { onContentCommitted() }
    private val contentDrawOnMain: Runnable = Runnable { onContentDrawn() }
    private val preDrawListener: ViewTreeObserver.OnPreDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (!scheduled && !callbacksCleared && currentWindow && state.visible && root.width > 0 && root.height > 0) {
            scheduled = true
            state.recordWindowEvent("内容即将绘制：已注册本帧提交回调", diagnosticHostId)
            val committed: Runnable = Runnable {
                // 系统或厂商实现的提交回调线程不同；所有状态与清理仍回到主线程。
                if (Looper.myLooper() == Looper.getMainLooper()) contentCommitOnMain.run()
                else handler.post(contentCommitOnMain)
            }
            contentCommit = committed
            // ViewRootImpl 在 OnDraw 之前收集提交回调，必须从 OnPreDraw 注册。
            windowCallbacks.afterContentDraw(root, committed)
            removePreDrawListener.run()
        }
        true
    }
    private val drawListener: ViewTreeObserver.OnDrawListener = ViewTreeObserver.OnDrawListener {
        dispatchingDraw = true
        try {
            if (!drawObserved && !callbacksCleared && currentWindow && state.visible && root.width > 0 && root.height > 0) {
                drawObserved = true
                state.recordWindowEvent("内容开始绘制", diagnosticHostId)
                // 主队列中的确认发生在本次 OnDraw 与窗口绘制分发返回之后。
                handler.post(contentDrawOnMain)
                // Android 不允许在 OnDraw 分发中直接移除监听器。
                handler.post(removeDrawListener)
            }
        } finally {
            dispatchingDraw = false
        }
    }

    fun attach() {
        if (attached || detached || !state.visible) return
        attached = true
        state.recordWindowEvent("窗口已挂载：等待系统启动层=$waitForSystemSplash", diagnosticHostId)
        state.awaitWindow(diagnosticHostId)
        // 在任何绘制或系统回调之前安装，不依赖 View 是否已附着；重建沿用原绝对截止时间。
        handler.postDelayed(releaseTimeout, state.windowTimeoutRemainingMillis())
        val observer = root.viewTreeObserver
        preDrawObserver = observer.also { it.addOnPreDrawListener(preDrawListener) }
        drawObserver = observer.also { it.addOnDrawListener(drawListener) }
        if (windowCallbacks.supportsSystemSplash && waitForSystemSplash) {
            windowCallbacks.setSplashExitListener { removeSplash ->
                removeSplash()
                state.recordWindowEvent("系统启动层已调用移除", diagnosticHostId)
                if (currentWindow && state.visible && !callbacksCleared) {
                    systemSplashRemoved = true
                    releaseIfReady()
                } else state.recordWindowEvent("旧窗口启动层退出回调已忽略", diagnosticHostId)
            }
        }
        root.postInvalidateOnAnimation()
    }

    private fun onContentDrawn() {
        if (!currentWindow || !state.visible || callbacksCleared || contentDrawn) return
        contentDrawn = true
        state.recordWindowEvent("内容绘制已返回主队列", diagnosticHostId)
        releaseIfReady()
        if (!state.ready) {
            // 宽限不越过已有窗口或播放截止时间；无需等待缺失回调到 1500ms 后突然消失。
            handler.postDelayed(handoffGrace, minOf(HANDOFF_CALLBACK_GRACE_MILLIS, state.windowTimeoutRemainingMillis()))
        }
    }

    private fun onContentCommitted() {
        if (currentWindow && state.visible && !callbacksCleared && !contentCommitted) {
            contentCommitted = true
            state.recordWindowEvent("内容首帧已提交", diagnosticHostId)
            releaseIfReady()
        } else state.recordWindowEvent("旧窗口内容提交回调已忽略", diagnosticHostId)
    }

    private fun releaseIfReady() {
        if (!currentWindow || !state.visible || state.ready || !contentDrawn || !contentCommitted || !systemSplashRemoved) return
        startPlayback("内容与系统启动层完成交接")
    }

    private fun startPlayback(event: String) {
        if (!currentWindow || !state.visible || state.ready || !contentDrawn) return
        handler.removeCallbacks(releaseTimeout)
        handler.removeCallbacks(handoffGrace)
        state.recordWindowEvent(event, diagnosticHostId)
        // 先清理已接管的系统启动层，再允许应用内动画播放；降级路径共用同样的清理。
        clearWindowCallbacks()
        state.markReady(diagnosticHostId)
        // 动画第一帧缺失或后续帧停止时，仍能够释放输入与语义覆盖。
        handler.postDelayed(playbackTimeout, state.playbackTimeoutRemainingMillis())
    }

    fun cancel() {
        if (currentWindow) state.finish()
        detach()
    }

    private fun clearWindowCallbacks() {
        if (callbacksCleared) return
        callbacksCleared = true
        handler.removeCallbacks(handoffGrace)
        handler.removeCallbacks(contentCommitOnMain)
        handler.removeCallbacks(contentDrawOnMain)
        handler.removeCallbacks(removePreDrawListener)
        handler.removeCallbacks(removeDrawListener)
        contentCommit?.let { windowCallbacks.cancelContentDraw(root, it) }
        contentCommit = null
        removePreDrawListener.run()
        if (dispatchingDraw) handler.post(removeDrawListener) else removeDrawListener.run()
        if (currentWindow && windowCallbacks.supportsSystemSplash && waitForSystemSplash) {
            windowCallbacks.clearSplashExitListener()
        }
    }

    fun detach() {
        if (detached) return
        state.recordWindowEvent("窗口已分离", diagnosticHostId)
        // 清理系统回调时仍保留当前宿主所有权；旧宿主不能注销新窗口的退出监听。
        clearWindowCallbacks()
        detached = true
        handler.removeCallbacks(releaseTimeout)
        handler.removeCallbacks(playbackTimeout)
        handler.removeCallbacks(handoffGrace)
        state.releaseWindow(diagnosticHostId)
    }

    private companion object {
        const val HANDOFF_CALLBACK_GRACE_MILLIS = 200L
    }
}

/** 回归可注入迟到回调；默认实现使用系统提交和 Splash API，并显式清理挂起回调。 */
internal interface StartupWindowCallbacks {
    val supportsSystemSplash: Boolean
    /** 保留原接口名；硬件提交回调实际从 OnPreDraw 注册，覆盖当前绘制帧。 */
    fun afterContentDraw(root: View, committed: Runnable)
    fun cancelContentDraw(root: View, committed: Runnable) {}
    fun setSplashExitListener(listener: (() -> Unit) -> Unit)
    fun clearSplashExitListener()
}

private class AndroidStartupWindowCallbacks(private val activity: Activity) : StartupWindowCallbacks {
    override val supportsSystemSplash = Build.VERSION.SDK_INT >= 31
    private var commitObserver: ViewTreeObserver? = null
    private var removeCurrentSplash: (() -> Unit)? = null

    override fun afterContentDraw(root: View, committed: Runnable) {
        if (Build.VERSION.SDK_INT >= 29 && root.isHardwareAccelerated) {
            commitObserver = root.viewTreeObserver.also { it.registerFrameCommitCallback(committed) }
        } else {
            // 软件绘制没有提交回调；从 OnPreDraw 排队，在当前绘制分发返回后确认。
            root.post(committed)
        }
    }

    override fun cancelContentDraw(root: View, committed: Runnable) {
        root.removeCallbacks(committed)
        if (Build.VERSION.SDK_INT >= 29) {
            val observer = commitObserver?.takeIf { it.isAlive } ?: root.viewTreeObserver
            if (observer.isAlive) observer.unregisterFrameCommitCallback(committed)
        }
        commitObserver = null
    }

    override fun setSplashExitListener(listener: (() -> Unit) -> Unit) {
        if (Build.VERSION.SDK_INT >= 31) activity.splashScreen.setOnExitAnimationListener { splash ->
            var removed = false
            val remove: () -> Unit = {
                if (!removed) {
                    removed = true
                    splash.remove()
                    removeCurrentSplash = null
                }
            }
            removeCurrentSplash = remove
            listener(remove)
        }
    }

    override fun clearSplashExitListener() {
        if (Build.VERSION.SDK_INT >= 31) {
            removeCurrentSplash?.invoke()
            removeCurrentSplash = null
            // 系统可能已把启动 View 转交到 Decor，却尚未派发依赖绘制帧的退出回调。
            // 清除监听不会移除这种 View，必须通过公开 remove() 释放其窗口与 Surface。
            val decor = activity.window.peekDecorView() as? ViewGroup
            if (decor != null) {
                for (index in decor.childCount - 1 downTo 0) {
                    (decor.getChildAt(index) as? SplashScreenView)?.remove()
                }
            }
            activity.splashScreen.clearOnExitAnimationListener()
        }
    }
}
