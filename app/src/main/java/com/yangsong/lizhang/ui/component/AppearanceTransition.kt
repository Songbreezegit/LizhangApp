package com.yangsong.lizhang.ui.component

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewTreeObserver
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.yangsong.lizhang.core.common.ThemeOperationDiagnostics
import androidx.core.os.LocaleListCompat
import com.yangsong.lizhang.domain.model.AppLanguage
import com.yangsong.lizhang.ui.viewmodel.AppearanceTransitionViewModel
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.flow.first

/** 坐标均为窗口坐标；捕获与覆盖共享 content 根的原点和尺寸。 */
interface AppearanceActions {
    fun circular(center: Offset, targetDark: Boolean, change: () -> Unit)
    fun colors(change: () -> Unit)
    fun language(target: AppLanguage)
    fun cancel()
}
val LocalAppearanceActions = staticCompositionLocalOf<AppearanceActions?> { null }
val LocalPendingDark = staticCompositionLocalOf<Boolean?> { null }
val LocalCurrentLanguage = staticCompositionLocalOf<AppLanguage?> { null }
internal const val SNAPSHOT_BUDGET_BYTES = 8 * 1024 * 1024
internal fun snapshotSize(width: Int, height: Int): Pair<Int, Int> {
    val scale = min(1.0, sqrt(SNAPSHOT_BUDGET_BYTES / (width.toDouble() * height * 4)))
    return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
}
internal fun revealRadius(width: Float, height: Float, x: Float, y: Float): Float =
    ceil(maxOf(hypot(x, y), hypot(width - x, y), hypot(x, height - y),
        hypot(width - x, height - y))) + 2f

/** 只捕获窗口一次，画面不进入任何保存状态，也不落盘。 */
class AppearanceTransitionHost(
    private val activity: Activity,
    private val state: AppearanceTransitionViewModel,
    private val captureWindowForTest: ((Bitmap, (Int) -> Unit) -> Unit)? = null,
) : AppearanceActions {
    private val handler = Handler(Looper.getMainLooper())
    private val hostId = state.nextHost()
    private val root = activity.findViewById<FrameLayout>(android.R.id.content)
    private val overlay = SnapshotOverlay(activity, state)
    private var animator: ValueAnimator? = null
    private var capturing = false
    private var latest: (() -> Unit)? = null
    private var pendingApply: (() -> Unit)? = null
    private var pendingLanguage: AppLanguage? = null
    private var pendingId = 0L
    private var detached = false
    private var attached = false
    private var drawScheduled = false
    private var drawnDark: Boolean? = null
    private var drawnLanguage = AppLanguage.SYSTEM
    private var drawnResourceLanguage = ""
    private var listState: LazyListState? = null
    private var themeRowBounds: RectF? = null
    private var themeSwitchBounds: RectF? = null
    private var languageRowBounds: RectF? = null
    private var themeChecked = false
    private var themeOperation: ThemeOperationDiagnostics.Operation? = null
    private var touchActive = false
    internal val diagnosticHostId get() = hostId
    private val ownsState get() = state.ownsSnapshotObserver(hostId)
    internal data class ThemeInputSnapshot(val hostId: Long, val requestId: Long, val scrolling: Boolean,
        val position: AppearanceTransitionViewModel.ListPosition?, val switchBounds: RectF?, val rowBounds: RectF?,
        val languageBounds: RectF?, val attached: Boolean, val focused: Boolean, val navigationReady: Boolean)
    internal fun themeInputSnapshot() = ThemeInputSnapshot(hostId, state.requestId,
        listState?.isScrollInProgress == true, currentListPosition, themeSwitchBounds?.let(::RectF),
        themeRowBounds?.let(::RectF), languageRowBounds?.let(::RectF), attached && !detached,
        activity.hasWindowFocus(), isNavigationReady)
    internal fun languageTargetBounds(bounds: androidx.compose.ui.geometry.Rect) {
        languageRowBounds = RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    /** Activity 分发链上的旁观记录，不消费或改写任何触摸。 */
    fun observeThemeTouch(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            touchActive = themeRowBounds?.contains(event.x, event.y) == true
            if (touchActive) themeOperation = ThemeOperationDiagnostics.Operation(
                state.requestId + 1, hostId, !themeChecked, ThemeOperationDiagnostics.nextTouchId())
            if (touchActive) ThemeOperationDiagnostics.observe(themeOperation)
        }
        if (!touchActive) return
        val stage = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_CANCEL -> "CANCEL"
            else -> return
        }
        ThemeOperationDiagnostics.record(stage, themeOperation,
            "hitSwitch=${themeSwitchBounds?.contains(event.x, event.y)} x=${event.x} y=${event.y} eventTime=${event.eventTime} " +
                "scrolling=${listState?.isScrollInProgress} position=$currentListPosition switchBounds=$themeSwitchBounds")
        if (event.actionMasked != MotionEvent.ACTION_DOWN) touchActive = false
    }
    internal fun themeTargetBounds(switch: Boolean, bounds: androidx.compose.ui.geometry.Rect, checked: Boolean) {
        themeChecked = checked
        val value = RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
        if (switch) themeSwitchBounds = value else themeRowBounds = value
    }
    internal fun themeCallback(source: String, targetDark: Boolean, suppliedDark: Boolean? = null) {
        themeOperation = ThemeOperationDiagnostics.Operation(state.requestId + 1, hostId, targetDark,
            themeOperation?.touchId ?: 0)
        ThemeOperationDiagnostics.observe(themeOperation)
        ThemeOperationDiagnostics.record("callback.$source", themeOperation, "suppliedDark=$suppliedDark")
    }
    internal var isNavigationReady = false
        private set
    private val drawListener = ViewTreeObserver.OnDrawListener {
        drawnDark?.let { pageDrawn(it, drawnLanguage, drawnResourceLanguage) }
    }
    private val layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        if (detached || !ownsState) return@OnLayoutChangeListener
        state.snapshot?.let {
            if (it.width != root.width || it.height != root.height) state.clear()
        }
    }


    fun attach() {
        if (attached || detached) return
        attached = true
        ThemeOperationDiagnostics.enableDeviceRecording()
        ThemeOperationDiagnostics.record("host.attach", ThemeOperationDiagnostics.Operation(state.requestId, hostId, null, 0))
        // 在首次绘制前接上 Activity ViewModel 中的语言快照。
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        root.viewTreeObserver.addOnDrawListener(drawListener)
        state.observeSnapshots(hostId) {
            ThemeOperationDiagnostics.record("snapshot.listener", themeOperation,
                "listenerHost=$hostId snapshotId=${state.snapshot?.id} originHost=${state.snapshot?.originHost}")
            if (state.snapshot == null) {
                val previous = animator
                animator = null
                previous?.cancel()
                overlay.visibility = View.GONE
            }
            else { overlay.visibility = View.VISIBLE; overlay.invalidate() }
        }
        overlay.visibility = if (state.snapshot == null) View.GONE else View.VISIBLE
        root.addOnLayoutChangeListener(layoutChangeListener)
    }
    fun updateDrawTarget(dark: Boolean, language: AppLanguage, resourceLanguage: String) {
        if (drawnDark != dark) ThemeOperationDiagnostics.record("Compose.theme", themeOperation, "dark=$dark")
        drawnDark = dark
        drawnLanguage = language
        drawnResourceLanguage = resourceLanguage
        // Compose 可只更新 RenderNode；显式请求窗口绘制以获得对应主题的帧提交。
        if (state.snapshot != null) root.postInvalidateOnAnimation()
    }
    internal fun trackList(value: LazyListState?) { listState = value }
    internal fun navigationReady(value: Boolean) {
        isNavigationReady = value
        if (value && (state.snapshot != null || state.languageRequest != null)) root.postInvalidateOnAnimation()
    }
    internal val currentListPosition get() = listState?.let {
        AppearanceTransitionViewModel.ListPosition(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset,
            it.layoutInfo.viewportSize.width, it.layoutInfo.viewportSize.height)
    }
    internal suspend fun restoreList(value: LazyListState) {
        if (detached || !ownsState) return
        val request = state.languageRequest ?: return
        if (request.originHost == hostId || request.listRestored) return
        request.listPosition?.let { position ->
            // 等实际视口恢复，避免新 ComposeView 的暂时零 Insets 把滚动偏移夹到错误上限。
            snapshotFlow { (state.languageRequest === request && !detached && ownsState) to value.layoutInfo.viewportSize }.first {
                !it.first || (it.second.width == position.viewportWidth && it.second.height == position.viewportHeight)
            }
            if (state.languageRequest !== request || detached || !ownsState) return
            value.scrollToItem(position.index, position.offset)
        }
        if (state.languageRequest === request && !detached && ownsState) {
            state.lastRestoredPosition = currentListPosition
            request.listRestored = true
            root.postInvalidateOnAnimation()
        }
    }
    override fun circular(center: Offset, targetDark: Boolean, change: () -> Unit) {
        themeOperation = ThemeOperationDiagnostics.Operation(state.requestId + 1, hostId, targetDark,
            themeOperation?.touchId ?: 0)
        ThemeOperationDiagnostics.record("host.circular", themeOperation, "detached=$detached pendingLanguage=$pendingLanguage")
        if (detached || !ownsState) return
        if (pendingLanguage != null) {
            // 新主题操作中断语言覆盖，但两个不同设置的用户意图都要提交。
            cancel()
            ThemeOperationDiagnostics.during(themeOperation, change)
            return
        }
        state.animateColors = false
        state.pendingDark = targetDark
        capture(targetDark = targetDark, center = center, change = change)
    }
    override fun colors(change: () -> Unit) {
        if (detached || !ownsState) return
        if (pendingLanguage != null) cancel()
        latest = null
        pendingApply = null
        pendingLanguage = null
        state.pendingDark = null
        state.nextRequest()
        state.animateColors = ValueAnimator.areAnimatorsEnabled()
        change()
    }
    override fun language(target: AppLanguage) {
        language(target, currentListPosition)
    }
    internal fun language(target: AppLanguage, originalPosition: AppearanceTransitionViewModel.ListPosition?) {
        if (detached || !ownsState) return
        if (target == currentAppLanguage()) {
            if (pendingLanguage != null) { pendingApply = null; cancel() }
            return
        }
        if (pendingApply != null && pendingLanguage == null) cancel()
        state.animateColors = false
        state.pendingDark = null
        // 捕获前固定原有列表位置；覆盖层接入或窗口 Insets 派发不能改写本次恢复目标。
        val requestedPosition = originalPosition.takeIf { listState != null && !detached }
        // API 33 的进程 Resources 可能已包含应用语言，系统语言必须读取 LocaleManager。
        val systemLocales = if (Build.VERSION.SDK_INT >= 33)
            activity.getSystemService(android.app.LocaleManager::class.java).systemLocales
        else activity.applicationContext.resources.configuration.locales
        val desiredLocales = buildList {
            if (target != AppLanguage.SYSTEM) add(java.util.Locale.forLanguageTag(target.localeTag))
            for (index in 0 until systemLocales.size()) add(systemLocales[index])
        }.distinct().joinToString(",") { it.toLanguageTag() }
        val willRecreate = desiredLocales != activity.resources.configuration.locales.toLanguageTags()
        val submit = {
            if (willRecreate) state.prepareLanguage(AppearanceTransitionViewModel.LanguageRequest(
                state.requestId, target, hostId, requestedPosition))
            state.languageSubmissions++
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(target.localeTag))
            state.languagePreference = target
        }
        // 有效语言相同时只保存偏好，不等待一次并不会发生的配置重建。
        if (!willRecreate) {
            latest = null
            pendingApply = null
            pendingLanguage = null
            state.nextRequest()
            submit()
        } else capture(language = target, change = submit)
    }
    private fun capture(
        targetDark: Boolean? = null,
        center: Offset = Offset.Zero,
        language: AppLanguage? = null,
        change: () -> Unit,
    ) {
        // 失效宿主不得分配新请求；已经接受且未被替代的目标由 cancel / detach 收尾。
        if (detached || !ownsState) return
        val id = state.nextRequest()
        val operation = if (targetDark != null) themeOperation?.copy(requestId = id) else null
        if (operation != null) themeOperation = operation
        pendingId = id
        pendingApply = change
        pendingLanguage = language
        val apply = {
            if (pendingId == id && pendingApply != null) {
                pendingApply = null
                pendingLanguage = null
                ThemeOperationDiagnostics.record("intent.apply", operation)
                ThemeOperationDiagnostics.during(operation, change)
                state.pendingDark = null
            }
        }
        // 捕获也有超时；窗口不可用时直接应用最新意图，不等待或永久遮挡。
        handler.postDelayed({
            if (!detached && id == state.requestId && pendingApply != null) {
                ThemeOperationDiagnostics.record("capture.timeout", operation)
                latest = null
                state.nextRequest()
                apply()
            }
        }, 600L)
        val start: () -> Unit = start@{
            if (detached || id != state.requestId) return@start
            if (!ValueAnimator.areAnimatorsEnabled() || root.width == 0 || root.height == 0) {
                ThemeOperationDiagnostics.record("capture.bypass", operation,
                    "animations=${ValueAnimator.areAnimatorsEnabled()} width=${root.width} height=${root.height}")
                apply()
                return@start
            }
            val position = IntArray(2).also(root::getLocationInWindow)
            val width = root.width
            val height = root.height
            val (bw, bh) = snapshotSize(width, height)
            val bitmap = try { Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888) }
                catch (_: OutOfMemoryError) { apply(); return@start }
            capturing = true
            ThemeOperationDiagnostics.record("PixelCopy.start", operation, "width=$width height=$height")
            try {
                val completed: (Int) -> Unit = { result ->
                        ThemeOperationDiagnostics.record("PixelCopy.result", operation, "result=$result detached=$detached currentRequest=${state.requestId}")
                        capturing = false
                        if (!detached && id == state.requestId) {
                            if (result == PixelCopy.SUCCESS) {
                                state.install(AppearanceTransitionViewModel.Snapshot(
                                    id, bitmap, width, height,
                                    (center.x - position[0]).coerceIn(0f, width.toFloat()),
                                    (center.y - position[1]).coerceIn(0f, height.toFloat()),
                                    targetDark, language, originHost = hostId,
                                ))
                            } else bitmap.recycle()
                            try { apply(); root.invalidate() } catch (_: RuntimeException) { state.clear() }
                        } else bitmap.recycle()
                        latest?.also { latest = null }?.invoke()
                    }
                if (captureWindowForTest != null) captureWindowForTest.invoke(bitmap, completed)
                else PixelCopy.request(activity.window,
                    Rect(position[0], position[1], position[0] + width, position[1] + height), bitmap, completed, handler)
            } catch (_: RuntimeException) {
                ThemeOperationDiagnostics.record("PixelCopy.exception", operation)
                capturing = false
                bitmap.recycle()
                if (id == state.requestId && !detached) apply()
            }
        }
        // 未完成捕获时只保留最新意图，释放旧目标后再分配画面。
        if (capturing) latest = start else start()
    }
    /** 由实时导航树绘制完成后调用，不能用固定帧数或延时替代。 */
    fun pageDrawn(dark: Boolean, language: AppLanguage, resourceLanguage: String) {
        if (detached || !ownsState) return
        if (!isNavigationReady) return
        val request = state.languageRequest
        if (request != null && !request.listRestored) return
        val shot = state.snapshot ?: run {
            if (request != null && request.originHost != hostId && request.target == language) state.clear()
            return
        }
        if (shot.started || drawScheduled || shot.id != state.requestId) return
        // 全局 locales 可能先更新；旧 Activity 不得抢先消费重建交接。
        if (shot.language != null && shot.originHost == hostId) return
        if (shot.targetDark != null && shot.targetDark != dark) return
        if (shot.language != null && (shot.language != language ||
                (shot.language != AppLanguage.SYSTEM &&
                    AppLanguage.fromLanguageTag(resourceLanguage) != shot.language))) return
        drawScheduled = true
        val committed = Runnable {
            drawScheduled = false
            if (!detached && ownsState && state.snapshot === shot && !shot.started) {
                ThemeOperationDiagnostics.record("frame.committed", themeOperation, "snapshotId=${shot.id}")
                shot.started = true
                animate(shot)
            }
        }
        if (Build.VERSION.SDK_INT >= 29 && root.isHardwareAccelerated) {
            root.viewTreeObserver.registerFrameCommitCallback(committed)
            root.postInvalidateOnAnimation()
        } else {
            // 旧系统在窗口 onDraw 返回后确认本次 Canvas 绘制结束；
            // 支持提交回调时必须等真正提交，不能让 post 抢先消费交接。
            root.post(committed)
        }
    }
    private fun animate(shot: AppearanceTransitionViewModel.Snapshot) {
        val operation = themeOperation?.takeIf { it.requestId == shot.id }
        ThemeOperationDiagnostics.record("transition.start", operation, "snapshotId=${shot.id}")
        if (shot.language == null) state.circularHandoffs++ else state.languageHandoffs++
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (shot.language == null) 420L else 140L
            interpolator = android.animation.TimeInterpolator { FastOutSlowInEasing.transform(it) }
            addUpdateListener {
                if (!detached && ownsState && state.snapshot === shot) {
                    shot.progress = it.animatedValue as Float
                    overlay.invalidate()
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    ThemeOperationDiagnostics.record("transition.end", operation, "snapshotId=${shot.id}")
                    if (!detached && ownsState && state.snapshot === shot) state.clear()
                }
            })
            start()
        }
    }
    override fun cancel() {
        ThemeOperationDiagnostics.record("host.cancel", themeOperation, "detached=$detached currentRequest=${state.requestId}")
        if (detached) return
        if (!ownsState) {
            // 旧宿主只收尾自己的待提交目标；新请求已替代时不得反向提交或清理共享状态。
            val apply = pendingApply.takeIf { pendingId == state.requestId }
            pendingApply = null
            pendingLanguage = null
            latest = null
            ThemeOperationDiagnostics.during(themeOperation) { apply?.invoke() }
            if (apply != null && pendingId == state.requestId) state.pendingDark = null
            return
        }
        val apply = pendingApply
        pendingApply = null
        pendingLanguage = null
        latest = null
        state.nextRequest()
        state.animateColors = false
        ThemeOperationDiagnostics.during(themeOperation) { apply?.invoke() }
        state.pendingDark = null
    }
    fun detach(preserveLanguage: Boolean) {
        ThemeOperationDiagnostics.record("host.detach", ThemeOperationDiagnostics.Operation(state.requestId, hostId, state.pendingDark, 0), "detached=$detached preserveLanguage=$preserveLanguage")
        if (detached) return
        root.removeOnLayoutChangeListener(layoutChangeListener)
        attached = false
        val change = pendingApply.takeIf { pendingId == state.requestId }
        pendingApply = null
        pendingLanguage = null
        detached = true
        if (change != null) runCatching { ThemeOperationDiagnostics.during(themeOperation, change) }
        if (ownsState || (change != null && pendingId == state.requestId)) state.pendingDark = null
        listState = null
        latest = null
        animator?.cancel()
        if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnDrawListener(drawListener)
        if (!preserveLanguage || (state.snapshot?.language == null && state.languageRequest == null)) {
            root.removeView(overlay)
            if (ownsState) state.nextRequest()
        }
        state.releaseSnapshotObserver(hostId)
        // 语言重建时保留旧窗口的最后一帧，直到系统替换 content；
        // 提前移除会使复用的窗口在新 Activity 首次绘制前提交空内容。
    }
}

/** 窗口复用时 Insets 会重新派发；在正确视口下恢复原有列表项与偏移再交接。 */
@Composable
fun AppearanceListRestoration(listState: LazyListState) {
    val host = LocalAppearanceActions.current as? AppearanceTransitionHost ?: return
    val view = LocalView.current
    val density = LocalDensity.current
    val top = WindowInsets.systemBars.getTop(density)
    val bottom = WindowInsets.systemBars.getBottom(density)
    DisposableEffect(host, listState) {
        host.trackList(listState)
        onDispose { host.trackList(null) }
    }
    LaunchedEffect(host, listState, top, bottom) {
        val nativeInsets = androidx.core.view.ViewCompat.getRootWindowInsets(view)
            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        if (nativeInsets != null && nativeInsets.top == top && nativeInsets.bottom == bottom) host.restoreList(listState)
    }
}

/** Canvas 裁掉旧画面的圆，底部导航与页面共用这一边界。 */
internal class SnapshotOverlay(activity: Activity, private val state: AppearanceTransitionViewModel) : View(activity) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val hole = Path()
    private val destination = RectF()
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; isClickable = false; setWillNotDraw(false) }
    override fun onDraw(canvas: Canvas) {
        val shot = state.snapshot ?: return
        if (shot.width != width || shot.height != height || shot.bitmap.isRecycled) return
        val checkpoint = canvas.save()
        if (shot.language == null) {
            hole.reset()
            hole.addCircle(shot.x, shot.y,
                revealRadius(width.toFloat(), height.toFloat(), shot.x, shot.y) * shot.progress, Path.Direction.CW)
            canvas.clipOutPath(hole)
            paint.alpha = 255
        } else paint.alpha = ((1f - shot.progress) * 255).toInt()
        destination.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(shot.bitmap, null, destination, paint)
        canvas.restoreToCount(checkpoint)
    }
}
@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun AppearanceTransition(host: AppearanceTransitionHost, dark: Boolean, language: AppLanguage,
    resourceLanguage: String, content: @Composable () -> Unit) {
    SideEffect { host.updateDrawTarget(dark, language, resourceLanguage) }
    CompositionLocalProvider(LocalAppearanceActions provides host) {
        Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) { content() }
    }
}
