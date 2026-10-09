package com.yangsong.lizhang.flow

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.MainActivity
import com.yangsong.lizhang.R
import com.yangsong.lizhang.core.common.StartupFrameEvidence
import com.yangsong.lizhang.core.common.diagnoseStartupFrames
import com.yangsong.lizhang.ui.viewmodel.StartupDiagnosticEvent
import com.yangsong.lizhang.ui.viewmodel.StartupDiagnosticSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt

/** 每轮必须独立冷进程执行；不安装 Compose 测试时钟，所有图片来自系统实际合成屏幕。 */
class StartupRealFrameInstrumentedTest {
    @get:org.junit.Rule(order = 0) val acceptedPrivacy = com.yangsong.lizhang.fixtures.AcceptedPrivacyRule()
    private data class Snapshot(
        val requestedNanos: Long,
        val receivedNanos: Long,
        val hostId: Int?,
        val density: Float,
        val state: StartupDiagnosticSnapshot?,
    ) {
        val active get() = state?.let { it.visible && it.ready } == true
        val progress get() = state?.progress ?: 0f
    }

    private data class Frame(
        val before: Snapshot,
        val after: Snapshot,
        val afterScaling: Snapshot,
        val captureStartNanos: Long,
        val captureEndNanos: Long,
        val scalingStartNanos: Long,
        val scalingEndNanos: Long,
        val originalBytes: Int,
        val density: Float,
        val bitmap: Bitmap,
        val stableFinal: Boolean = false,
        val interactionFinal: Boolean = false,
    )

    @Test fun 系统启动层退出后品牌确有多帧变化并露出真实首页() {
        assertTrue("真实中间帧验收需要启用系统动画", ValueAnimator.areAnimatorsEnabled())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // 此连接与 PNG 编码延期在原交付中已经实现，本轮继续保留。
        val device = UiDevice.getInstance(instrumentation)
        val app = ApplicationProvider.getApplicationContext<LiZhangApplication>()
        val args = InstrumentationRegistry.getArguments()
        val runId = args.getString("repairRunId") ?: args.getString("v096RunId")
            ?: "独立冷进程_${SystemClock.elapsedRealtime()}_${Process.myPid()}"
        require(runId.matches(Regex("[\\p{L}\\p{N}_.-]+"))) { "运行编号不能包含路径分隔符" }
        val folder = File(app.getExternalFilesDir(null), "startup-v096-real/$runId")
        check(!folder.exists()) { "本轮证据目录已存在，不覆盖历史尝试：$runId" }
        check(folder.mkdirs()) { "无法创建独立启动证据目录" }
        val origin = System.nanoTime()
        val current = AtomicReference<MainActivity?>()
        val frames = Collections.synchronizedList(mutableListOf<Frame>())
        val captureNotes = Collections.synchronizedList(mutableListOf<String>())
        val sampling = AtomicBoolean(true)
        val samplingFailure = AtomicReference<Throwable?>()
        val diagnostics = AtomicReference<List<StartupDiagnosticEvent>>(emptyList())
        var completedBefore = false
        var coldOpportunity = false
        instrumentation.runOnMainSync {
            completedBefore = app.appContainer.onboardingRepository.state.value.completed
            coldOpportunity = !app.startupSession.hasClaimed
        }
        File(folder, "运行前置.txt").writeText(
            "runId=$runId\nPID=${Process.myPid()}\nAPI=${Build.VERSION.SDK_INT}\n" +
                "首次引导已完成=$completedBefore\n进程启动机会尚未消费=$coldOpportunity\n" +
                "设计时长=900ms\n品牌采样窗口=.20..78\n动画速度由设备清单记录\n" +
                "时基=System.nanoTime；以下调用和状态均使用同一单调时钟\n",
        )
        assertTrue("本用例必须独立冷进程执行，启动机会不能被之前用例消耗", coldOpportunity)

        fun snapshot(): Snapshot {
            val requested = System.nanoTime()
            var result: Snapshot? = null
            instrumentation.runOnMainSync {
                val activity = current.get()
                // Activity 与全部 Compose 状态在主线程一次读取，采样线程只读取不可变值。
                result = Snapshot(requested, System.nanoTime(), activity?.let(System::identityHashCode),
                    activity?.resources?.displayMetrics?.density ?: app.resources.displayMetrics.density,
                    activity?.startupState?.diagnosticSnapshot())
            }
            return requireNotNull(result)
        }

        fun takeFrame(stableFinal: Boolean = false): Frame? {
            val before = snapshot()
            val started = System.nanoTime()
            val bitmap = try {
                instrumentation.uiAutomation.takeScreenshot()
            } catch (error: Throwable) {
                captureNotes.add("截图开始ms=${millis(started - origin)}；异常结束ms=${millis(System.nanoTime() - origin)}；progress前=${before.progress}；${error.stackTraceToString()}")
                throw error
            }
            val ended = System.nanoTime()
            // 截图结束立即取一致快照，不能把缩放耗时算入该图片的 after 进度。
            val after = snapshot()
            if (bitmap == null) {
                captureNotes.add("截图开始ms=${millis(started - origin)}；结束ms=${millis(ended - origin)}；progress前/后=${before.progress}/${after.progress}；系统截图返回空")
                return null
            }
            val scalingStarted = System.nanoTime()
            val width = minOf(420, bitmap.width)
            val originalBytes = bitmap.allocationByteCount
            val scaled = Bitmap.createScaledBitmap(bitmap, width,
                (bitmap.height * width.toFloat() / bitmap.width).roundToInt(), true)
            val density = before.density * width / bitmap.width
            if (scaled !== bitmap) bitmap.recycle()
            val scaledAt = System.nanoTime()
            // 保留旧规则的缩放后关联点，逐帧输出旧/新谓词对照，阈值完全相同。
            val afterScaling = snapshot()
            return Frame(before, after, afterScaling, started, ended, scalingStarted, scaledAt,
                originalBytes, density, scaled, stableFinal)
        }

        val lifecycle = ActivityLifecycleCallback { activity, stage ->
            if (activity is MainActivity && stage == Stage.CREATED) current.set(activity)
        }
        instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(lifecycle) }
        var bufferLimit = false
        val observer = Thread {
            try {
                var retainedBytes = 0L
                while (sampling.get() && System.nanoTime() - origin < 20_000_000_000L) {
                    // 尚无目标 Activity 时不分配截图；进程启动层的完整覆盖必要时另用连续系统录制。
                    if (current.get() == null) {
                        SystemClock.sleep(5)
                        continue
                    }
                    val frame = takeFrame() ?: continue
                    frames.add(frame)
                    retainedBytes += frame.bitmap.allocationByteCount
                    if (frame.after.state?.visible == false && frames.size > 2) break
                    if (frames.size >= 70 || retainedBytes >= 64L * 1024 * 1024) {
                        bufferLimit = true
                        break
                    }
                    // 动画期间不额外固定休眠；截图与主线程快照本身已提供同步边界。
                }
            } catch (error: Throwable) {
                samplingFailure.set(error)
            }
        }.apply { name = "开屏真实画面采样"; start() }
        var finished = false
        var pageVisible = false
        var interactionSucceeded = false
        var scenarioFailure: Throwable? = null
        try {
            val intent = Intent(app, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                val deadline = SystemClock.elapsedRealtime() + 12_000
                while (SystemClock.elapsedRealtime() < deadline) {
                    scenario.onActivity { finished = !it.startupState.visible && it.appearanceHost.isNavigationReady }
                    if (finished) break
                    SystemClock.sleep(30)
                }
                observer.join(1500)
                sampling.set(false)
                observer.join(3000)
                scenario.onActivity {
                    diagnostics.set(it.startupState.diagnostics())
                    assertFalse("有限开屏结束后不残留遮挡", it.startupState.visible)
                }
                var pageTitle = ""
                var interactionLabel = ""
                var expectedAfterInteraction = ""
                scenario.onActivity {
                    pageTitle = it.getString(R.string.home_recent)
                    interactionLabel = it.getString(R.string.nav_settings)
                    expectedAfterInteraction = it.getString(R.string.settings_dark)
                    assertTrue("隐私已确认后的当前入口使用首页功能引导", app.appContainer.onboardingRepository.state.value.completed)
                }
                val page = device.wait(Until.findObject(By.text(pageTitle)), 6000)
                assertNotNull("启动结束后实际首页文字可见：$pageTitle", page)
                val bounds = requireNotNull(page).visibleBounds
                pageVisible = bounds.width() > 0 && bounds.height() > 0
                assertTrue("实际页面文字具有可见屏幕范围", pageVisible)
                device.waitForIdle(1500)
                val finalFrame = takeFrame(stableFinal = true)
                assertNotNull("取得真实页面的稳定系统合成终帧", finalFrame)
                frames.add(requireNotNull(finalFrame))
                // 真实触摸后页面必须改变，单纯 visible=false 或找到文字不能证明覆盖层释放。
                val target = device.wait(Until.findObject(By.desc(interactionLabel)), 3000)
                assertNotNull("终帧真实页面具有可操作入口：$interactionLabel", target)
                val targetBounds = requireNotNull(target).visibleBounds
                assertTrue("终帧入口启用且可见", target.isEnabled && !targetBounds.isEmpty)
                device.click(targetBounds.centerX(), targetBounds.centerY())
                interactionSucceeded = device.wait(Until.hasObject(By.text(expectedAfterInteraction)), 6000) == true
                assertTrue("启动覆盖释放后真实触摸可以操作页面：$expectedAfterInteraction", interactionSucceeded)
                scenario.onActivity { assertFalse("页面操作后品牌层不重新覆盖", it.startupState.visible) }
                takeFrame(stableFinal = true)?.let { frames.add(it.copy(interactionFinal = true)) }
            }
        } catch (error: Throwable) {
            scenarioFailure = error
        } finally {
            sampling.set(false)
            observer.join(3000)
            instrumentation.runOnMainSync {
                current.get()?.let { diagnostics.set(it.startupState.diagnostics()) }
                ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(lifecycle)
            }
        }
        val captured = frames.toList()
        try {
            captured.forEachIndexed { index, frame ->
                val name = if (frame.interactionFinal) "真实页面_交互后.png"
                    else if (frame.stableFinal) "真实页面_稳定终帧.png"
                    else "真实开屏_${index}_${(frame.after.progress * 100).roundToInt()}.png"
                File(folder, name).outputStream().use { frame.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            val visible = captured.filter { exclusionReasons(it).isEmpty() }
            val oldVisible = captured.filter { exclusionReasons(it, oldAssociation = true).isEmpty() }
            val blank = captured.count { !it.stableFinal && it.before.active && it.before.progress < .64f && brandEdges(it) < 20 }
            val trace = diagnostics.get()
            val animationCallbacks = trace.filter { it.frameNanos != null }
            val maximumDispatchGap = animationCallbacks.zipWithNext().maxOfOrNull {
                (first, second) -> second.state.monotonicNanos - first.state.monotonicNanos
            } ?: 0L
            val maximumCapture = captured.filter { !it.stableFinal && (it.before.active || it.after.active) }
                .maxOfOrNull { it.captureEndNanos - it.captureStartNanos } ?: 0L
            val firstAnimation = animationCallbacks.firstOrNull()?.state?.monotonicNanos
            val contentCommit = trace.firstOrNull { it.event == "内容首帧已提交" }?.state?.monotonicNanos
            val splashRemove = trace.firstOrNull { it.event == "系统启动层已调用移除" }?.state?.monotonicNanos
            val changed = if (visible.size >= 2) changedBrandPixels(visible.first(), visible.last()) else 0
            val span = if (visible.size >= 2) visible.last().captureStartNanos - visible.first().captureEndNanos else 0L
            val startedBeforeContent = firstAnimation != null && contentCommit != null && firstAnimation < contentCommit
            val startedBeforeSplash = firstAnimation != null && splashRemove != null && firstAnimation < splashRemove
            val diagnosis = diagnoseStartupFrames(StartupFrameEvidence(visible.size, changed, span,
                maximumCapture, maximumDispatchGap, startedBeforeContent, startedBeforeSplash)).description
            File(folder, "采样说明.txt").writeText(buildString {
                appendLine("runId=$runId；旧/新均要求主线程快照可见且就绪、before>=.20、after<=.78、名称笔画>=12")
                appendLine("新规则在截图返回后立即关联；旧规则对照在缩放结束后关联；不放宽进度或像素阈值")
                appendLine("调用前后快照等待包含在各请求/收到时间中；capture 时长仅含系统截图；scaling 含缩放及原图回收")
                appendLine("序号\t截图开始ms\t截图结束ms\t截图耗时ms\t缩放耗时ms\t快照前请求/收到ms\t快照后请求/收到ms\tprogress前/后/缩放后\t宿主前/后\t原图分配bytes\t保留图bytes\t额外固定休眠ms\t名称笔画\t旧规则排除\t新规则排除")
                captured.forEachIndexed { index, frame ->
                    appendLine("$index\t${millis(frame.captureStartNanos - origin)}\t${millis(frame.captureEndNanos - origin)}\t" +
                        "${millis(frame.captureEndNanos - frame.captureStartNanos)}\t${millis(frame.scalingEndNanos - frame.scalingStartNanos)}\t" +
                        "${millis(frame.before.requestedNanos - origin)}/${millis(frame.before.receivedNanos - origin)}\t" +
                        "${millis(frame.after.requestedNanos - origin)}/${millis(frame.after.receivedNanos - origin)}\t" +
                        "${frame.before.progress}/${frame.after.progress}/${frame.afterScaling.progress}\t${frame.before.hostId}/${frame.after.hostId}\t" +
                        "${frame.originalBytes}\t${frame.bitmap.allocationByteCount}\t0\t${nameEdges(frame)}\t" +
                        "${exclusionReasons(frame, true).ifEmpty { listOf("有效") }.joinToString("；")}\t" +
                        exclusionReasons(frame).ifEmpty { listOf("有效") }.joinToString("；"))
                }
                appendLine("旧判定有效=${oldVisible.size}；新判定有效=${visible.size}；保守真实时间跨度ms=${millis(span)}；品牌变化像素=$changed；空帧=$blank")
                appendLine("最大截图耗时ms=${millis(maximumCapture)}；动画帧回调最大交付间隔ms=${millis(maximumDispatchGap)}；有界缓冲已耗尽=$bufferLimit")
                appendLine("页面结束=$finished；真实页面可见=$pageVisible；真实触摸成功=$interactionSucceeded；诊断=$diagnosis")
                appendLine("场景异常=${scenarioFailure?.stackTraceToString() ?: "无"}")
                appendLine("采样异常=${samplingFailure.get()?.stackTraceToString() ?: "无"}")
                appendLine("空截图或截图异常记录=${captureNotes.joinToString("\n")}")
            })
            File(folder, "窗口与动画时间线.txt").writeText(buildString {
                appendLine("单调时间ms\t宿主\t事件\tvisible\tready\tprogress\t帧调度时间ms\t回调交付时间减帧调度ms")
                trace.forEach { event ->
                    appendLine("${millis(event.state.monotonicNanos - origin)}\t${event.hostId}\t${event.event}\t" +
                        "${event.state.visible}\t${event.state.ready}\t${event.state.progress}\t" +
                        "${event.frameNanos?.let { millis(it - origin) }}\t${event.frameNanos?.let { millis(event.state.monotonicNanos - it) }}")
                }
            })
            scenarioFailure?.let { throw AssertionError("启动场景失败；原始证据已保留于 $runId", it) }
            samplingFailure.get()?.let { throw AssertionError("系统合成屏幕采样失败；原始证据已保留于 $runId", it) }
            assertFalse("采样线程必须退出后再检查或回收图片", observer.isAlive)
            assertFalse("有界采集缓冲耗尽属于采集不足，不能声明通过", bufferLimit)
            assertTrue("启动页面按有限时长完成", finished)
            assertNotNull("记录当前内容首帧提交时间", contentCommit)
            assertNotNull("记录实际动画起始帧时间", firstAnimation)
            if (Build.VERSION.SDK_INT >= 31) assertNotNull("记录系统启动层退出时间", splashRemove)
            assertFalse("动画必须在内容提交与系统启动层退出之后开始", startedBeforeContent || startedBeforeSplash)
            assertTrue("系统启动图退场后至少采集三个带应用名的实际品牌中间帧，取得 ${visible.size} 帧；$diagnosis", visible.size >= 3)
            assertTrue("品牌中间帧保守覆盖至少 140.8ms 的真实时间跨度，实际 ${millis(span)}ms", span >= MINIMUM_SPAN_NANOS)
            assertTrue("系统画面中的图标或扩散圆环确实变化，取得 $changed 个变化像素", changed >= 60)
            assertEquals("启动层有效期间没有实际纯色空帧", 0, blank)
            assertTrue("最终真实页面可见且启动层不再遮挡实际触摸", pageVisible && interactionSucceeded)
            println("真实开屏 $runId：${captured.size} 帧；旧有效 ${oldVisible.size}，新有效 ${visible.size}；空帧 $blank；$diagnosis")
        } finally {
            if (!observer.isAlive) captured.forEach { it.bitmap.recycle() }
        }
    }

    private fun exclusionReasons(frame: Frame, oldAssociation: Boolean = false): List<String> {
        val after = if (oldAssociation) frame.afterScaling else frame.after
        return buildList {
            if (frame.stableFinal) add("稳定终帧，不作为品牌中间帧")
            if (frame.before.hostId != after.hostId) add("截图跨越宿主重建")
            if (!frame.before.active || !after.active) add("截图关联窗口并非两端均为可播放品牌层")
            if (frame.before.progress < .20f) add("调用前进度低于.20")
            if (after.progress > .78f) add("关联后进度高于.78")
            if (nameEdges(frame) < 12) add("系统画面未显示足够应用名笔画")
        }
    }

    private fun millis(nanos: Long) = nanos / 1_000_000.0

    private fun contrast(a: Int, b: Int): Int = maxOf(abs(Color.red(a) - Color.red(b)),
        abs(Color.green(a) - Color.green(b)), abs(Color.blue(a) - Color.blue(b)))

    private fun edges(frame: Frame, leftDp: Float, topDp: Float, rightDp: Float, bottomDp: Float): Int {
        val image = frame.bitmap
        val left = (image.width / 2f + leftDp * frame.density).roundToInt().coerceIn(0, image.width - 2)
        val right = (image.width / 2f + rightDp * frame.density).roundToInt().coerceIn(left + 1, image.width - 1)
        val top = (image.height / 2f + topDp * frame.density).roundToInt().coerceIn(0, image.height - 2)
        val bottom = (image.height / 2f + bottomDp * frame.density).roundToInt().coerceIn(top + 1, image.height - 1)
        var edges = 0
        for (y in top until bottom) for (x in left until right) {
            val pixel = image.getPixel(x, y)
            if (maxOf(contrast(pixel, image.getPixel(x + 1, y)), contrast(pixel, image.getPixel(x, y + 1))) >= 22) edges++
        }
        return edges
    }

    /** 系统启动图没有下方应用名；真实笔画证明屏幕已经显示 Compose 品牌画面。 */
    private fun nameEdges(frame: Frame) = edges(frame, -90f, 77f, 90f, 139f)
    private fun brandEdges(frame: Frame) = edges(frame, -120f, -125f, 120f, 139f)

    private fun changedBrandPixels(first: Frame, last: Frame): Int {
        val image = first.bitmap
        if (image.width != last.bitmap.width || image.height != last.bitmap.height) return 0
        val radius = (120f * first.density).roundToInt()
        val left = (image.width / 2 - radius).coerceAtLeast(0)
        val right = (image.width / 2 + radius).coerceAtMost(image.width)
        val top = (image.height / 2 - radius).coerceAtLeast(0)
        val bottom = (image.height / 2 + 70f * first.density).roundToInt().coerceAtMost(image.height)
        var changed = 0
        for (y in top until bottom) for (x in left until right) {
            if (contrast(image.getPixel(x, y), last.bitmap.getPixel(x, y)) >= 8) changed++
        }
        return changed
    }

    companion object {
        private const val MINIMUM_SPAN_NANOS = 140_800_000L
    }
}
