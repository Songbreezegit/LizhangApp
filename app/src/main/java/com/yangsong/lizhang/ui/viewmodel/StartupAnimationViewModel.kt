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
    private var windowHostId: Int? = null
    private var windowDeadlineNanos: Long? = null
    private var playbackDeadlineNanos: Long? = null
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

    /** 重建始终等待新窗口自己的提交；时间轴与绝对截止时间不随宿主重建重置。 */
    fun awaitWindow(hostId: Int? = null) {
        if (!visible) return
        windowHostId = hostId
        ready = false
        if (windowDeadlineNanos == null) {
            windowDeadlineNanos = monotonicClock() + WINDOW_WAIT_TIMEOUT_MILLIS * 1_000_000L
        }
        recordEvent("等待当前窗口", hostId = hostId)
    }

    fun markReady(hostId: Int? = null) {
        if (hostId != null && !ownsWindow(hostId)) return
        if (visible) {
            ready = true
            windowDeadlineNanos = null
            if (playbackDeadlineNanos == null) {
                playbackDeadlineNanos = monotonicClock() + PLAYBACK_TIMEOUT_MILLIS * 1_000_000L
            }
        }
        recordEvent("窗口已就绪", hostId = hostId)
    }

    internal fun ownsWindow(hostId: Int) = windowHostId == hostId

    internal fun releaseWindow(hostId: Int) {
        if (!ownsWindow(hostId)) return
        windowHostId = null
        ready = false
    }

    internal fun windowTimeoutRemainingMillis(): Long = remainingMillis(
        listOfNotNull(windowDeadlineNanos, playbackDeadlineNanos).minOrNull(),
    )

    internal fun playbackTimeoutRemainingMillis(): Long = remainingMillis(playbackDeadlineNanos)

    private fun remainingMillis(deadlineNanos: Long?): Long {
        if (deadlineNanos == null) return 0L
        val remainingNanos = (deadlineNanos - monotonicClock()).coerceAtLeast(0L)
        return (remainingNanos + 999_999L) / 1_000_000L
    }

    fun onFrame(frameNanos: Long) {
        if (!visible || !ready) return
        val now = monotonicClock()
        // 即使主线程超时任务还未执行，恢复派发的帧也不能继续已过期的动画。
        if (playbackDeadlineNanos?.let { now >= it } == true) {
            recordEvent("启动动画截止时间已过", frameNanos = frameNanos)
            finish()
            return
        }
        val start = firstFrameNanos ?: frameNanos.also {
            firstFrameNanos = it
            // 首帧等候仍受原截止时间约束；有效首帧到达后只补足一次完整播放余量。
            // 重建和重复就绪保留 firstFrameNanos，不能再延长播放截止时间。
            playbackDeadlineNanos = now + PLAYBACK_TIMEOUT_MILLIS * 1_000_000L
        }
        progress = ((frameNanos - start) / (DURATION_MILLIS * 1_000_000f)).coerceIn(0f, 1f)
        recordEvent("动画帧回调", frameNanos = frameNanos)
        if (progress >= 1f) finish()
    }

    fun finish() {
        if (!visible && !ready && progress == 1f) return
        visible = false
        progress = 1f
        ready = false
        windowDeadlineNanos = null
        playbackDeadlineNanos = null
        recordEvent("启动层已释放")
    }

    companion object {
        const val DURATION_MILLIS = 1300L
        internal const val WINDOW_WAIT_TIMEOUT_MILLIS = 1500L
        // 帧调度仍可能短暂延迟；独立截止时间覆盖首个动画帧缺失及播放中断。
        internal const val PLAYBACK_TIMEOUT_MILLIS = DURATION_MILLIS + 600L
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
