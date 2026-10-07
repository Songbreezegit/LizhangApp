package com.yangsong.lizhang.core.common

import android.os.SystemClock
import android.util.Log
import com.yangsong.lizhang.BuildConfig

/** 只保留外观操作阶段和非敏感状态；有限缓冲仅供专项验收读取。 */
object ThemeOperationDiagnostics {
    data class Operation(val requestId: Long, val hostId: Long, val targetDark: Boolean?, val touchId: Long)
    private val entries = ArrayDeque<String>()
    private val current = ThreadLocal<Operation?>()
    private var touchSequence = 0L
    @Volatile private var deviceRecording = false
    @Volatile var observedOperation: Operation? = null
        private set
    fun observe(operation: Operation?) { observedOperation = operation }
    @Synchronized fun nextTouchId(): Long = ++touchSequence
    /** 仅由真实窗口宿主启用；普通 JVM 业务测试不依赖 Android 时钟或日志。 */
    fun enableDeviceRecording() { deviceRecording = BuildConfig.DEBUG }

    fun record(stage: String, operation: Operation? = current.get(), detail: String = "") {
        if (!deviceRecording) return
        val line = "t=${SystemClock.uptimeMillis()} requestId=${operation?.requestId ?: 0} " +
            "hostId=${operation?.hostId ?: 0} touchId=${operation?.touchId ?: 0} " +
            "targetDark=${operation?.targetDark} stage=$stage $detail"
        synchronized(entries) {
            if (entries.size == 1024) entries.removeFirst()
            entries.addLast(line)
        }
        Log.d("礼账主题诊断", line)
    }

    fun <T> during(operation: Operation?, block: () -> T): T {
        val previous = current.get()
        current.set(operation)
        return try { block() } finally { current.set(previous) }
    }

    fun lines(): List<String> = synchronized(entries) { entries.toList() }
}
