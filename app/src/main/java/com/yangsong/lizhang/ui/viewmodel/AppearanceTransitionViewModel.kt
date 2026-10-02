package com.yangsong.lizhang.ui.viewmodel

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.yangsong.lizhang.domain.model.AppLanguage

/** Activity 作用域只保留一张内存画面，不参与保存或持久化。 */
class AppearanceTransitionViewModel : ViewModel() {
    data class ListPosition(val index: Int, val offset: Int, val viewportWidth: Int, val viewportHeight: Int)
    class LanguageRequest(val id: Long, val target: AppLanguage, val originHost: Long,
        val listPosition: ListPosition?, var listRestored: Boolean = listPosition == null)
    class Snapshot(val id: Long, val bitmap: Bitmap, val width: Int, val height: Int,
        val x: Float, val y: Float, val targetDark: Boolean?, val language: AppLanguage?,
        var progress: Float = 0f, var started: Boolean = false, val originHost: Long = 0)
    private var hostSequence = 0L
    fun nextHost(): Long = ++hostSequence
    var animateColors by mutableStateOf(false)
    var pendingDark by mutableStateOf<Boolean?>(null)
    var languagePreference by mutableStateOf(AppLanguage.SYSTEM)
    var snapshot by mutableStateOf<Snapshot?>(null)
        private set
    var languageRequest by mutableStateOf<LanguageRequest?>(null)
        private set
    internal val hostCount get() = hostSequence
    internal var lastLanguagePosition: ListPosition? = null
        private set
    internal var lastLanguageTarget: AppLanguage? = null
        private set
    internal var lastRestoredPosition: ListPosition? = null
    var requestId = 0L
        private set
    // 测试只观察计数，不记录画面或业务数据。
    var languageSubmissions = 0
        internal set
    var languageHandoffs = 0
        internal set
    var circularHandoffs = 0
        internal set
    var onSnapshotChanged: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { clear() }
    fun nextRequest(): Long { clear(); return ++requestId }
    fun install(value: Snapshot) {
        clear()
        snapshot = value
        handler.postDelayed(timeout, if (value.language == null) 1500L else 4000L)
        onSnapshotChanged?.invoke()
    }
    fun prepareLanguage(value: LanguageRequest) {
        lastLanguagePosition = value.listPosition
        lastLanguageTarget = value.target
        languageRequest = value
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 4000L)
    }
    fun clear() {
        handler.removeCallbacks(timeout)
        // 释放引用；不 recycle 已经交给硬件渲染线程的画面。
        snapshot = null
        languageRequest = null
        onSnapshotChanged?.invoke()
    }
    override fun onCleared() { onSnapshotChanged = null; clear() }
}
