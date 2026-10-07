package com.yangsong.lizhang.flow

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
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

/** 独立 instrumentation 冷进程执行；不安装 Compose 测试时钟，读取系统实际合成屏幕。 */
class StartupRealFrameInstrumentedTest {
    private data class Frame(
        val elapsedMillis: Long,
        val before: Float,
        val after: Float,
        val active: Boolean,
        val density: Float,
        val bitmap: Bitmap,
        val stableFinal: Boolean = false,
    )

    @Test fun 系统启动层退出后品牌确有多帧变化并露出真实首页() {
        assertTrue("真实中间帧验收需要启用系统动画", ValueAnimator.areAnimatorsEnabled())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // 在采样线程开始前完成自动化服务连接，避免首次截屏与 UiDevice 的连接配置互相竞争。
        val device = UiDevice.getInstance(instrumentation)
        val app = ApplicationProvider.getApplicationContext<LiZhangApplication>()
        val current = AtomicReference<MainActivity?>()
        val lifecycle = ActivityLifecycleCallback { activity, stage ->
            if (activity is MainActivity && stage == Stage.CREATED) current.set(activity)
        }
        instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(lifecycle) }
        val sampling = AtomicBoolean(true)
        val samplingFailure = AtomicReference<Throwable?>()
        val frames = Collections.synchronizedList(mutableListOf<Frame>())
        val origin = SystemClock.elapsedRealtime()
        val observer = Thread {
            try {
                while (sampling.get() && SystemClock.elapsedRealtime() - origin < 20_000) {
                    val activity = current.get()
                    val state = activity?.startupState
                    val before = state?.progress ?: 0f
                    val activeBefore = state?.let { it.visible && it.ready } == true
                    val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: continue
                    if (activity == null) {
                        bitmap.recycle()
                        SystemClock.sleep(25)
                        continue
                    }
                    val width = minOf(420, bitmap.width)
                    val scaled = Bitmap.createScaledBitmap(bitmap, width,
                        (bitmap.height * width.toFloat() / bitmap.width).roundToInt(), true)
                    val density = activity.resources.displayMetrics.density * width / bitmap.width
                    if (scaled !== bitmap) bitmap.recycle()
                    frames.add(Frame(SystemClock.elapsedRealtime() - origin, before, state!!.progress,
                        activeBefore && state.visible && state.ready, density, scaled))
                    if (frames.size >= 70 || (!state.visible && frames.size > 2)) break
                    SystemClock.sleep(25)
                }
            } catch (error: Throwable) {
                samplingFailure.set(error)
            }
        }.apply { name = "开屏真实画面采样"; start() }
        var finished = false
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
                // 保留真实页面首次露出的系统画面；所有 PNG 在采样后写入，避免编码延长采样间隔。
                observer.join(1500)
                sampling.set(false)
                observer.join(3000)
                scenario.onActivity { assertFalse("有限开屏结束后不残留遮挡", it.startupState.visible) }
                var pageTitle = ""
                var actualDensity = 1f
                scenario.onActivity {
                    pageTitle = it.getString(if (app.appContainer.onboardingRepository.state.value.completed)
                        R.string.home_recent else R.string.onboarding_intro_title)
                    actualDensity = it.resources.displayMetrics.density
                }
                val page = device.wait(Until.findObject(By.text(pageTitle)), 6000)
                assertNotNull("启动结束后实际首页或首次引导文字可见：$pageTitle", page)
                val visiblePage = requireNotNull(page)
                assertTrue("实际页面文字具有可见屏幕范围", visiblePage.visibleBounds.width() > 0 && visiblePage.visibleBounds.height() > 0)
                device.waitForIdle(1500)
                val finalBitmap = instrumentation.uiAutomation.takeScreenshot()
                assertNotNull("取得真实页面的稳定系统合成终帧", finalBitmap)
                val originalBitmap = requireNotNull(finalBitmap)
                val width = minOf(420, originalBitmap.width)
                val scaled = Bitmap.createScaledBitmap(originalBitmap, width,
                    (originalBitmap.height * width.toFloat() / originalBitmap.width).roundToInt(), true)
                val density = actualDensity * width / originalBitmap.width
                if (scaled !== originalBitmap) originalBitmap.recycle()
                frames.add(Frame(SystemClock.elapsedRealtime() - origin, 1f, 1f,
                    active = false, density = density, bitmap = scaled, stableFinal = true))
            }
        } finally {
            sampling.set(false)
            observer.join(3000)
            instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(lifecycle) }
        }
        samplingFailure.get()?.let { throw AssertionError("系统合成屏幕采样失败", it) }
        val captured = frames.toList()
        val folder = File(app.getExternalFilesDir(null), "startup-v096-real").apply { mkdirs() }
        // 只清理本测试生成的旧帧，避免多次运行的同名目录混入上一轮证据。
        folder.listFiles { file -> file.isFile && file.extension == "png" &&
            (file.name.startsWith("真实开屏_") || file.name == "真实页面_稳定终帧.png") }
            ?.forEach { check(it.delete()) }
        try {
            captured.forEachIndexed { index, frame ->
                val name = if (frame.stableFinal) "真实页面_稳定终帧.png"
                    else "真实开屏_${index}_${(frame.after * 100).roundToInt()}.png"
                File(folder, name).outputStream().use {
                    frame.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            File(folder, "采样说明.txt").writeText(captured.mapIndexed { index, frame ->
                "$index\t${frame.elapsedMillis}ms\t${frame.before}→${frame.after}\t启动层可见=${frame.active}\t名称笔画=${nameEdges(frame)}"
            }.joinToString("\n"))
            assertTrue("启动页面按有限时长完成", finished)
            val visible = captured.filter { it.active && it.before >= .20f && it.after <= .78f && nameEdges(it) >= 12 }
            assertTrue("系统启动图退场后至少采集三个带应用名的实际品牌中间帧，取得 ${visible.size} 帧", visible.size >= 3)
            assertTrue("品牌中间帧覆盖真实的一段时间轴", visible.last().after - visible.first().before >= .16f)
            assertTrue("系统画面中的图标或扩散圆环确实变化", changedBrandPixels(visible.first(), visible.last()) >= 60)
            val blank = captured.count { it.active && it.before < .64f && brandEdges(it) < 20 }
            assertEquals("启动层有效期间没有实际纯色空帧", 0, blank)
            println("真实开屏采样 ${captured.size} 帧，有效品牌中间帧 ${visible.size} 帧，空帧 $blank 帧")
        } finally {
            captured.forEach { it.bitmap.recycle() }
        }
    }

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

    /** 系统启动图没有下方的应用名；这个区域的真实笔画证明已露出 Compose 品牌画面。 */
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
}
