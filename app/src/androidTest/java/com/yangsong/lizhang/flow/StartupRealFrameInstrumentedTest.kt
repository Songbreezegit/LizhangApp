package com.yangsong.lizhang.flow

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
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
import com.yangsong.lizhang.ui.component.StartupDropMotion
import com.yangsong.lizhang.ui.viewmodel.StartupAnimationViewModel
import com.yangsong.lizhang.ui.viewmodel.StartupDiagnosticEvent
import com.yangsong.lizhang.ui.viewmodel.StartupDiagnosticSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** 每轮必须独立冷进程执行；不安装 Compose 测试时钟，所有图片来自系统实际合成屏幕。 */
class StartupRealFrameInstrumentedTest {
    private val destination get() = InstrumentationRegistry.getArguments().getString("startupDestination", "home").also {
        require(it == "home" || it == "privacy") { "startupDestination只能为home或privacy" }
    }
    @get:org.junit.Rule(order = 0) val privacyPrecondition = TestRule { base, description ->
        if (destination == "privacy") object : Statement() {
            override fun evaluate() {
                // 只允许专属全新安装；不删除偏好，也不通过业务前置规则写入同意。
                val app = ApplicationProvider.getApplicationContext<LiZhangApplication>()
                val consent = app.appContainer.privacyConsentRepository.state.value
                assertNull("首次隐私启动必须使用从未同意的全新测试安装", consent.acceptedVersion)
                assertNull("首次隐私启动必须使用从未拒绝的全新测试安装", consent.declinedVersion)
                assertFalse("首次隐私场景不能提前挂载业务页面", app.appContainer.canProcessPersonalData)
                base.evaluate()
            }
        } else com.yangsong.lizhang.fixtures.AcceptedPrivacyRule().apply(base, description)
    }
    private data class Snapshot(
        val requestedNanos: Long,
        val receivedNanos: Long,
        val hostId: Int?,
        val density: Float,
        val viewport: Rect,
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
        val viewport: Rect,
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
                "最终入口=$destination\n设计时长=${StartupAnimationViewModel.DURATION_MILLIS}ms；落地=${StartupDropMotion.LANDING_MS}ms；" +
                "圆渐变揭示=${StartupDropMotion.LANDING_MS}..${StartupDropMotion.REVEAL_END_MS}ms；" +
                "退场=${StartupDropMotion.REVEAL_END_MS}..${StartupAnimationViewModel.DURATION_MILLIS}ms\n" +
                "小猫采样窗口=${LANDING_PROGRESS}..${FADE_START_PROGRESS}\n动画速度由设备清单记录\n" +
                "时基=System.nanoTime；以下调用和状态均使用同一单调时钟\n",
        )
        assertTrue("本用例必须独立冷进程执行，启动机会不能被之前用例消耗", coldOpportunity)

        fun snapshot(): Snapshot {
            val requested = System.nanoTime()
            var result: Snapshot? = null
            instrumentation.runOnMainSync {
                val activity = current.get()
                val decor = activity?.window?.decorView
                val location = IntArray(2)
                decor?.getLocationOnScreen(location)
                // Activity 与全部 Compose 状态在主线程一次读取，采样线程只读取不可变值。
                result = Snapshot(requested, System.nanoTime(), activity?.let(System::identityHashCode),
                    activity?.resources?.displayMetrics?.density ?: app.resources.displayMetrics.density,
                    Rect(location[0], location[1], location[0] + (decor?.width ?: 0),
                        location[1] + (decor?.height ?: 0)),
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
            val screenScale = width.toFloat() / bitmap.width
            val viewport = Rect((before.viewport.left * screenScale).roundToInt(),
                (before.viewport.top * screenScale).roundToInt(), (before.viewport.right * screenScale).roundToInt(),
                (before.viewport.bottom * screenScale).roundToInt())
            if (scaled !== bitmap) bitmap.recycle()
            val scaledAt = System.nanoTime()
            // 保留缩放后关联点作采集对照；验收始终使用截图返回后的立即快照。
            val afterScaling = snapshot()
            return Frame(before, after, afterScaling, started, ended, scalingStarted, scaledAt,
                originalBytes, density, viewport, scaled, stableFinal)
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
                    if (destination == "privacy") {
                        pageTitle = it.getString(R.string.privacy_notice_title)
                        interactionLabel = it.getString(R.string.action_back)
                        expectedAfterInteraction = it.getString(R.string.privacy_notice_declined_title)
                        assertFalse("开屏结束后仍由首次隐私告知阻止业务挂载", app.appContainer.canProcessPersonalData)
                    } else {
                        pageTitle = it.getString(R.string.home_recent)
                        interactionLabel = it.getString(R.string.nav_settings)
                        expectedAfterInteraction = it.getString(R.string.settings_dark)
                        assertTrue("隐私已确认后的当前入口使用首页功能引导", app.appContainer.onboardingRepository.state.value.completed)
                    }
                }
                val page = device.wait(Until.findObject(By.text(pageTitle)), 6000)
                assertNotNull("启动结束后实际目标页面文字可见：$pageTitle", page)
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
                scenario.onActivity {
                    assertFalse("页面操作后品牌层不重新覆盖", it.startupState.visible)
                    if (destination == "privacy") assertFalse("真实返回触摸不会隐式同意", app.appContainer.canProcessPersonalData)
                }
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
            val scaledAssociation = captured.filter { exclusionReasons(it, scaledAssociation = true).isEmpty() }
            val sceneMetrics = captured.associateWith { StartupScenePixels.background(it.bitmap, it.viewport, it.density) }
            val rippleRays = captured.associateWith { StartupScenePixels.rippleRays(it.bitmap, it.viewport, it.density) }
            val whiteStart = captured.filter { !it.stableFinal && it.before.active && it.after.active &&
                it.before.hostId == it.after.hostId && it.before.viewport == it.after.viewport &&
                it.after.progress < LANDING_PROGRESS && !it.viewport.isEmpty }
            val revealFrames = visible.filter { it.before.progress >= LANDING_PROGRESS }
            val revealGrowth = if (revealFrames.size >= 2) sceneMetrics.getValue(revealFrames.last()).coloredFraction -
                sceneMetrics.getValue(revealFrames.first()).coloredFraction else 0f
            val revealRadiusGrowth = if (revealFrames.size >= 2) sceneMetrics.getValue(revealFrames.last()).maximumColoredRadius -
                sceneMetrics.getValue(revealFrames.first()).maximumColoredRadius else 0f
            // 0进度白底是有意的起帧；小猫已经进入后的纯白或缺失仍按实际空帧失败。
            val blank = captured.count { !it.stableFinal && it.before.active && it.after.active &&
                it.before.progress >= .20f && it.after.progress <= FADE_START_PROGRESS && catPixels(it) < 12 }
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
            // 旧诊断器以900ms设计为基准；只按总时长等比例换算时间，不改变帧数与像素门槛。
            val diagnosis = diagnoseStartupFrames(StartupFrameEvidence(visible.size, changed, referenceNanos(span),
                referenceNanos(maximumCapture), referenceNanos(maximumDispatchGap), startedBeforeContent, startedBeforeSplash)).description
            File(folder, "采样说明.txt").writeText(buildString {
                appendLine("runId=$runId；目标入口=$destination；主线程快照可见且就绪、before>=${LANDING_PROGRESS}、after<=${FADE_START_PROGRESS}、小猫特征像素>=12")
                appendLine("已移除旧版应用名识别；系统层设计为纯白且无静止猫；0进度纯白允许，落地前背景必须纯白")
                appendLine("采集从Activity创建开始；完整进程开头的系统启动层仍须独立连续系统录制，不以本组采集补全未见画面")
                appendLine("实际DecorView屏幕范围确定猫下落区域与圆形中心；${StartupDropMotion.REVEAL_END_MS}ms退场后的底层页面不混入中间帧")
                appendLine("验收在截图返回后立即关联；缩放结束后关联仅作采集对照，不放宽帧数、跨度或变化像素阈值")
                appendLine("调用前后快照等待包含在各请求/收到时间中；capture 时长仅含系统截图；scaling 含缩放及原图回收")
                appendLine("序号\t截图开始ms\t截图结束ms\t截图耗时ms\t缩放耗时ms\t快照前请求/收到ms\t快照后请求/收到ms\tprogress前/后/缩放后\t宿主前/后\t原图分配bytes\t保留图bytes\t额外固定休眠ms\t实际窗口范围\t小猫特征像素\t渐变覆盖比例\t实际扩散半径dp\t细涟漪射线\t缩放后关联排除\t立即关联排除")
                captured.forEachIndexed { index, frame ->
                    appendLine("$index\t${millis(frame.captureStartNanos - origin)}\t${millis(frame.captureEndNanos - origin)}\t" +
                        "${millis(frame.captureEndNanos - frame.captureStartNanos)}\t${millis(frame.scalingEndNanos - frame.scalingStartNanos)}\t" +
                        "${millis(frame.before.requestedNanos - origin)}/${millis(frame.before.receivedNanos - origin)}\t" +
                        "${millis(frame.after.requestedNanos - origin)}/${millis(frame.after.receivedNanos - origin)}\t" +
                        "${frame.before.progress}/${frame.after.progress}/${frame.afterScaling.progress}\t${frame.before.hostId}/${frame.after.hostId}\t" +
                        "${frame.originalBytes}\t${frame.bitmap.allocationByteCount}\t0\t${frame.viewport}\t${catPixels(frame)}\t" +
                        "${sceneMetrics.getValue(frame).coloredFraction}\t${sceneMetrics.getValue(frame).maximumColoredRadius / frame.density}\t${rippleRays.getValue(frame)}\t" +
                        "${exclusionReasons(frame, true).ifEmpty { listOf("有效") }.joinToString("；")}\t" +
                        exclusionReasons(frame).ifEmpty { listOf("有效") }.joinToString("；"))
                }
                appendLine("缩放后关联有效=${scaledAssociation.size}；立即关联有效=${visible.size}；保守真实时间跨度ms=${millis(span)}；品牌变化像素=$changed；小猫进入后空帧=$blank")
                appendLine("落地前采样=${whiteStart.size}；揭示阶段采样=${revealFrames.size}；渐变覆盖增长=$revealGrowth；实际扩散半径增长px=$revealRadiusGrowth；明暗水波帧=${revealFrames.count { rippleRays.getValue(it) > 0 }}")
                appendLine("真实跨度门槛ms=${millis(MINIMUM_SPAN_NANOS)}；时间比例=${StartupAnimationViewModel.DURATION_MILLIS}/900；帧数>=3与变化像素>=60保持")
                appendLine("最大截图耗时ms=${millis(maximumCapture)}；动画帧回调最大交付间隔ms=${millis(maximumDispatchGap)}；有界缓冲已耗尽=$bufferLimit")
                appendLine("页面结束=$finished；真实页面可见=$pageVisible；真实触摸成功=$interactionSucceeded；基础帧覆盖诊断=$diagnosis；新增阶段契约仍以独立断言为准")
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
            assertTrue("系统启动层退场后至少采集三个带小猫的实际品牌中间帧，取得 ${visible.size} 帧；$diagnosis", visible.size >= 3)
            assertTrue("品牌中间帧保守覆盖至少 ${millis(MINIMUM_SPAN_NANOS)}ms 的真实时间跨度，实际 ${millis(span)}ms", span >= MINIMUM_SPAN_NANOS)
            assertTrue("系统画面中的图标或扩散圆环确实变化，取得 $changed 个变化像素", changed >= 60)
            assertTrue("至少采集一个${StartupDropMotion.LANDING_MS}ms落地前的白底阶段，否则起帧证据不足", whiteStart.isNotEmpty())
            assertTrue("落地前实际背景必须纯白", whiteStart.all { sceneMetrics.getValue(it).coloredFraction < .005f })
            assertTrue("至少采集两个${StartupDropMotion.REVEAL_END_MS}ms退场前的圆渐变揭示帧，否则扩散证据不足", revealFrames.size >= 2)
            assertTrue("落地后的实际渐变区域和外沿继续扩大，覆盖增长=$revealGrowth；半径增长px=$revealRadiusGrowth",
                revealGrowth > .02f && revealRadiusGrowth > 12f * (revealFrames.firstOrNull()?.density ?: 1f))
            assertTrue("实际揭示帧存在同一圆周的多角度明暗水波；缺少该阶段采样不能声明通过",
                revealFrames.any { rippleRays.getValue(it) > 0 })
            assertEquals("小猫实际进入后的有效动画期间没有纯白或小猫缺失帧", 0, blank)
            assertTrue("最终真实页面可见且启动层不再遮挡实际触摸", pageVisible && interactionSucceeded)
            println("真实开屏 $runId：${captured.size} 帧；缩放后关联 ${scaledAssociation.size}，立即关联 ${visible.size}；空帧 $blank；$diagnosis")
        } finally {
            if (!observer.isAlive) captured.forEach { it.bitmap.recycle() }
        }
    }

    private fun exclusionReasons(frame: Frame, scaledAssociation: Boolean = false): List<String> {
        val after = if (scaledAssociation) frame.afterScaling else frame.after
        return buildList {
            if (frame.stableFinal) add("稳定终帧，不作为品牌中间帧")
            if (frame.before.hostId != after.hostId) add("截图跨越宿主重建")
            if (frame.viewport.isEmpty || frame.before.viewport != after.viewport) add("实际窗口范围未就绪或截图跨越窗口变化")
            if (!frame.before.active || !after.active) add("截图关联窗口并非两端均为可播放品牌层")
            if (frame.before.progress < LANDING_PROGRESS) add("调用前尚未到达${StartupDropMotion.LANDING_MS}ms落地阶段")
            if (after.progress > FADE_START_PROGRESS) add("关联后已进入${StartupDropMotion.REVEAL_END_MS}ms退场，不混入底层页面")
            if (catPixels(frame) < 12) add("小猫下落区域没有实际小猫特征")
        }
    }

    private fun millis(nanos: Long) = nanos / 1_000_000.0
    private fun referenceNanos(nanos: Long) = nanos * 900L / StartupAnimationViewModel.DURATION_MILLIS

    private fun contrast(a: Int, b: Int): Int = maxOf(abs(Color.red(a) - Color.red(b)),
        abs(Color.green(a) - Color.green(b)), abs(Color.blue(a) - Color.blue(b)))

    private fun catPixels(frame: Frame) = StartupScenePixels.catPixels(frame.bitmap, frame.viewport, frame.density)

    private fun changedBrandPixels(first: Frame, last: Frame): Int {
        val image = first.bitmap
        if (image.width != last.bitmap.width || image.height != last.bitmap.height) return 0
        // 相同实际窗口内同时覆盖纵向小猫和向四角扩散的背景，排除系统栏图标。
        val bounds = StartupScenePixels.contentBounds(image, first.viewport, first.density)
        var changed = 0
        for (y in bounds.top until bounds.bottom) for (x in bounds.left until bounds.right) {
            if (contrast(image.getPixel(x, y), last.bitmap.getPixel(x, y)) >= 8) changed++
        }
        return changed
    }

    companion object {
        private const val MINIMUM_SPAN_NANOS = 140_800_000L * StartupAnimationViewModel.DURATION_MILLIS / 900L
        private const val LANDING_PROGRESS = StartupDropMotion.LANDING_MS / StartupAnimationViewModel.DURATION_MILLIS
        private const val FADE_START_PROGRESS = StartupDropMotion.REVEAL_END_MS / StartupAnimationViewModel.DURATION_MILLIS
    }
}

/** 两组设备测试只分析实际图片；不调用生产运动插值，避免以状态值代替屏幕证据。 */
internal object StartupScenePixels {
    data class Background(val coloredFraction: Float, val maximumColoredRadius: Float)

    fun contentBounds(image: Bitmap, viewport: Rect, density: Float): Rect {
        // 顶底32dp仅排除系统栏符号；圆形中心始终取完整DecorView，不能取扣除栏后的中心。
        val inset = (32f * density).roundToInt()
        return Rect(viewport.left.coerceIn(0, image.width), (viewport.top + inset).coerceIn(0, image.height),
            viewport.right.coerceIn(0, image.width), (viewport.bottom - inset).coerceIn(0, image.height))
    }

    fun catPixels(image: Bitmap, viewport: Rect, density: Float, falling: Boolean = true): Int {
        if (viewport.isEmpty) return 0
        val bounds = contentBounds(image, viewport, density)
        val centerX = viewport.exactCenterX()
        val centerY = viewport.exactCenterY()
        // 原图112dp；76dp半宽覆盖轻压缩、抗锯齿以及落地的12dp回弹。
        val radius = 76f * density
        val left = (centerX - radius).roundToInt().coerceIn(bounds.left, bounds.right)
        val right = (centerX + radius).roundToInt().coerceIn(bounds.left, bounds.right)
        val top = if (falling) bounds.top else (centerY - radius).roundToInt().coerceIn(bounds.top, bounds.bottom)
        val bottom = (centerY + radius).roundToInt().coerceIn(bounds.top, bounds.bottom)
        var pixels = 0
        for (y in top until bottom) for (x in left until right) {
            val pixel = image.getPixel(x, y)
            // 原小猫红包与棕毛的饱和暖色；纸纹、淡色涟漪和深色渐变均不能冒充小猫。
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            if (red > green + 24 && red > blue + 24 && green < 180) pixels++
        }
        return pixels
    }

    fun background(image: Bitmap, viewport: Rect, density: Float): Background {
        if (viewport.isEmpty) return Background(0f, 0f)
        val bounds = contentBounds(image, viewport, density)
        val centerX = viewport.exactCenterX()
        val centerY = viewport.exactCenterY()
        val catRadius = 76f * density
        var total = 0
        var colored = 0
        var maximumRadius = 0f
        for (y in bounds.top until bounds.bottom step 2) for (x in bounds.left until bounds.right step 2) {
            // 从屏幕顶部到落点的窄列排除下落中的猫，只度量实际背景揭示。
            if (abs(x - centerX) < catRadius && y < centerY + catRadius) continue
            total++
            if (whiteDistance(image.getPixel(x, y)) >= 10) {
                colored++
                maximumRadius = maxOf(maximumRadius, hypot(x - centerX, y - centerY))
            }
        }
        return Background(if (total > 0) colored.toFloat() / total else 0f, maximumRadius)
    }

    private data class WaveHit(val ray: Int, val radius: Float)

    /** 检查径向局部明暗和同半径圆弧；水波可以覆盖已揭示渐变，不要求两侧都是白色。 */
    fun rippleRays(image: Bitmap, viewport: Rect, density: Float): Int {
        if (viewport.isEmpty) return 0
        val bounds = contentBounds(image, viewport, density)
        val centerX = viewport.exactCenterX()
        val centerY = viewport.exactCenterY()
        // 生产波带18→32dp；各侧2dp仅容纳缩放抗锯齿，不能把整块渐变当成波带。
        val halfBand = maxOf(3, (18f * density).roundToInt())
        val smoothing = maxOf(1, (2f * density).roundToInt())
        val minimumBand = maxOf(3, (4f * density).roundToInt())
        val radiusTolerance = maxOf(2f, 4f * density)
        val startRadius = (80f * density).roundToInt()
        val hits = mutableListOf<WaveHit>()
        for (index in 0 until 16) {
            val angle = index * Math.PI / 8.0
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            val pixels = buildList {
                var radius = startRadius
                while (true) {
                    val x = (centerX + radius * dx).roundToInt()
                    val y = (centerY + radius * dy).roundToInt()
                    if (!bounds.contains(x, y)) break
                    add(image.getPixel(x, y))
                    radius++
                }
            }
            if (pixels.size < 2 * (halfBand + smoothing) + 1) continue
            val red = DoubleArray(pixels.size + 1)
            val green = DoubleArray(pixels.size + 1)
            val blue = DoubleArray(pixels.size + 1)
            for (sample in pixels.indices) {
                red[sample + 1] = red[sample] + Color.red(pixels[sample])
                green[sample + 1] = green[sample] + Color.green(pixels[sample])
                blue[sample + 1] = blue[sample] + Color.blue(pixels[sample])
            }
            fun mean(channel: DoubleArray, sample: Int): Float {
                val left = (sample - smoothing).coerceAtLeast(0)
                val right = (sample + smoothing + 1).coerceAtMost(pixels.size)
                return ((channel[right] - channel[left]) / (right - left)).toFloat()
            }
            fun stableBackground(sample: Int): Boolean {
                var redMin = 255
                var greenMin = 255
                var blueMin = 255
                var redMax = 0
                var greenMax = 0
                var blueMax = 0
                for (offset in sample - smoothing..sample + smoothing) {
                    val pixel = pixels[offset]
                    redMin = minOf(redMin, Color.red(pixel))
                    greenMin = minOf(greenMin, Color.green(pixel))
                    blueMin = minOf(blueMin, Color.blue(pixel))
                    redMax = maxOf(redMax, Color.red(pixel))
                    greenMax = maxOf(greenMax, Color.green(pixel))
                    blueMax = maxOf(blueMax, Color.blue(pixel))
                }
                return maxOf(redMax - redMin, greenMax - greenMin, blueMax - blueMin) <= 12
            }
            val rayHits = mutableListOf<WaveHit>()
            for (center in halfBand + smoothing until pixels.size - halfBand - smoothing step smoothing) {
                val left = center - halfBand
                val right = center + halfBand
                // 两端不能取在文字或其它前景上，以免正文留下偶合的圆弧假象。
                if (!stableBackground(left) || !stableBackground(right)) continue
                val redLeft = mean(red, left)
                val greenLeft = mean(green, left)
                val blueLeft = mean(blue, left)
                val redRight = mean(red, right)
                val greenRight = mean(green, right)
                val blueRight = mean(blue, right)
                // 波带两侧应回到近似同一背景；白→彩色揭示边界、卡片边缘不能当水波。
                if (maxOf(abs(redLeft - redRight), abs(greenLeft - greenRight), abs(blueLeft - blueRight)) > 10f) continue
                val lumaLeft = luma(redLeft, greenLeft, blueLeft)
                val lumaRight = luma(redRight, greenRight, blueRight)
                var minimumResidual = 0f
                var maximumResidual = 0f
                var strongestResidual = 0f
                var peak = center
                var firstBand = -1
                var lastBand = -1
                for (sample in left + smoothing..right - smoothing) {
                    val fraction = (sample - left).toFloat() / (right - left)
                    val baseline = lumaLeft + (lumaRight - lumaLeft) * fraction
                    val residual = luma(mean(red, sample), mean(green, sample), mean(blue, sample)) - baseline
                    minimumResidual = minOf(minimumResidual, residual)
                    maximumResidual = maxOf(maximumResidual, residual)
                    if (abs(residual) > strongestResidual) {
                        strongestResidual = abs(residual)
                        peak = sample
                    }
                    if (abs(residual) >= 3f) {
                        if (firstBand < 0) firstBand = sample
                        lastBand = sample
                    }
                }
                // 平滑背景没有局部峰谷；很深的文字笔画也不能冒充淡色水波。
                val bandWidth = lastBand - firstBand + 1
                if (strongestResidual < 6f || strongestResidual > 90f ||
                    maximumResidual - minimumResidual < 6f || firstBand < 0 ||
                    bandWidth < minimumBand || bandWidth > 2 * halfBand) continue
                val radius = (startRadius + peak).toFloat()
                if (rayHits.none { abs(it.radius - radius) <= radiusTolerance }) rayHits += WaveHit(index, radius)
            }
            hits += rayHits
        }
        return hits.maxOfOrNull { hit ->
            val matchingRays = hits.filter { abs(it.radius - hit.radius) <= radiusTolerance }.map { it.ray }.distinct()
            val angularSeparation = matchingRays.maxOfOrNull { first ->
                matchingRays.maxOfOrNull { second ->
                    val separation = abs(first - second)
                    minOf(separation, 16 - separation)
                } ?: 0
            } ?: 0
            // 至少四条射线且跨90度圆弧；单段正文、矩形边或零星纸纹不足以证明圆形水波。
            if (matchingRays.size >= 4 && angularSeparation >= 4) matchingRays.size else 0
        } ?: 0
    }

    private fun luma(red: Float, green: Float, blue: Float) = (54f * red + 183f * green + 19f * blue) / 256f

    private fun whiteDistance(pixel: Int) = maxOf(255 - Color.red(pixel), 255 - Color.green(pixel), 255 - Color.blue(pixel))
}
