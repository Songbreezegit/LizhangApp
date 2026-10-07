package com.yangsong.lizhang.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.yangsong.lizhang.BuildConfig

/** 只保存一次启动的内存进度；配置重建继续原时间轴，不新增页面或持久化偏好。 */
class StartupAnimationViewModel(
    private val monotonicClock: () -> Long = System::nanoTime,
    private val diagnosticsEnabled: Boolean = BuildConfig.DEBUG,
) : ViewModel() {
    var visible by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var ready by mutableStateOf(false)
        private set
    private var initialized = false
    private var firstFrameNanos: Long? = null
    val hasStarted get() = firstFrameNanos != null
    private val diagnosticEvents = ArrayDeque<StartupDiagnosticEvent>()

    /** 仅包含非敏感启动状态；由主线程读取，不能用这些数值代替实际系统画面。 */
    internal fun diagnosticSnapshot() = StartupDiagnosticSnapshot(
        monotonicNanos = monotonicClock(), visible = visible, ready = ready,
        progress = progress, initialized = initialized, firstFrameNanos = firstFrameNanos,
    )

    internal fun diagnostics(): List<StartupDiagnosticEvent> = diagnosticEvents.toList()

    internal fun recordWindowEvent(event: String, hostId: Int) = recordEvent(event, hostId = hostId)

    private fun recordEvent(event: String, hostId: Int? = null, frameNanos: Long? = null) {
        if (!diagnosticsEnabled) return
        if (diagnosticEvents.size >= 128) diagnosticEvents.removeFirst()
        diagnosticEvents.addLast(StartupDiagnosticEvent(event, hostId, frameNanos, diagnosticSnapshot()))
    }

    fun initialize(eligible: Boolean, animationsEnabled: Boolean) {
        if (initialized) return
        initialized = true
        visible = eligible && animationsEnabled
        if (!visible) progress = 1f
        recordEvent("初始化：冷启动机会=$eligible，系统动画=$animationsEnabled")
    }

    /** 还未起步时，必须等待当前窗口自己的实际提交，不能借用旧窗口的就绪信号。 */
    fun awaitWindow() {
        if (!hasStarted) ready = false
        recordEvent("等待当前窗口")
    }

    fun markReady() {
        if (visible) ready = true
        recordEvent("窗口已就绪")
    }

    fun onFrame(frameNanos: Long) {
        if (!visible || !ready) return
        val start = firstFrameNanos ?: frameNanos.also { firstFrameNanos = it }
        progress = ((frameNanos - start) / (DURATION_MILLIS * 1_000_000f)).coerceIn(0f, 1f)
        recordEvent("动画帧回调", frameNanos = frameNanos)
        if (progress >= 1f) finish()
    }

    fun finish() {
        visible = false
        progress = 1f
        ready = false
        recordEvent("启动层已释放")
    }

    companion object {
        const val DURATION_MILLIS = 880L
    }
}

internal data class StartupDiagnosticSnapshot(
    val monotonicNanos: Long,
    val visible: Boolean,
    val ready: Boolean,
    val progress: Float,
    val initialized: Boolean,
    val firstFrameNanos: Long?,
)

internal data class StartupDiagnosticEvent(
    val event: String,
    val hostId: Int?,
    val frameNanos: Long?,
    val state: StartupDiagnosticSnapshot,
)
