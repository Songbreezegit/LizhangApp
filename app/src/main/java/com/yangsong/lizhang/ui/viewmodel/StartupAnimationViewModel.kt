package com.yangsong.lizhang.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/** 只保存一次启动的内存进度；配置重建继续原时间轴，不新增页面或持久化偏好。 */
class StartupAnimationViewModel : ViewModel() {
    var visible by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var ready by mutableStateOf(false)
        private set
    private var initialized = false
    private var firstFrameNanos: Long? = null
    val hasStarted get() = firstFrameNanos != null

    fun initialize(eligible: Boolean, animationsEnabled: Boolean) {
        if (initialized) return
        initialized = true
        visible = eligible && animationsEnabled
        if (!visible) progress = 1f
    }

    /** 还未起步时，必须等待当前窗口自己的实际提交，不能借用旧窗口的就绪信号。 */
    fun awaitWindow() {
        if (!hasStarted) ready = false
    }

    fun markReady() {
        if (visible) ready = true
    }

    fun onFrame(frameNanos: Long) {
        if (!visible || !ready) return
        val start = firstFrameNanos ?: frameNanos.also { firstFrameNanos = it }
        progress = ((frameNanos - start) / (DURATION_MILLIS * 1_000_000f)).coerceIn(0f, 1f)
        if (progress >= 1f) finish()
    }

    fun finish() {
        visible = false
        progress = 1f
        ready = false
    }

    companion object {
        const val DURATION_MILLIS = 880L
    }
}
