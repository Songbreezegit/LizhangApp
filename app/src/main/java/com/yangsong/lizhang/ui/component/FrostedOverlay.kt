package com.yangsong.lizhang.ui.component

import android.animation.ValueAnimator
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.yangsong.lizhang.ui.theme.LocalThemeDarkFraction
import java.util.function.Consumer
import kotlin.math.roundToInt

/** 原生窗口模糊的半径使用物理像素，与设计要求的 26px 一致。 */
object FrostedOverlayTokens {
    const val BlurRadiusPx = 26
    const val PanelAlpha = .42f
    const val InitialScale = .94f
    const val DurationMillis = 260
    const val BlurredDim = .08f
    const val FallbackDim = .24f
}

@Composable
internal fun frostedOverlayColor(): Color = lerp(
    Color.White, MaterialTheme.colorScheme.surface,
    LocalThemeDarkFraction.current,
).copy(alpha = FrostedOverlayTokens.PanelAlpha)

@Composable
internal fun rememberFrostedOverlayProgress(): State<Float> {
    val preview = LocalInspectionMode.current
    val progress = remember(preview) {
        Animatable(if (preview || !ValueAnimator.areAnimatorsEnabled()) 1f else 0f)
    }
    LaunchedEffect(progress) {
        progress.animateTo(1f, tween(FrostedOverlayTokens.DurationMillis, easing = FastOutSlowInEasing))
    }
    return progress.asState()
}

internal fun Modifier.frostedOverlayMotion(
    progress: State<Float>,
    origin: TransformOrigin = TransformOrigin.Center,
): Modifier = graphicsLayer {
    val fraction = progress.value
    scaleX = FrostedOverlayTokens.InitialScale + (1f - FrostedOverlayTokens.InitialScale) * fraction
    scaleY = scaleX
    alpha = fraction
    transformOrigin = origin
}

@Composable
internal fun Modifier.frostedOverlayFrame(shape: Shape): Modifier =
    floatingGlassShadow(shape).clip(shape).background(frostedOverlayColor())
        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .12f), shape)

/**
 * 只修改当前浮层自己的窗口，不修改 Activity。窗口退出后系统同时移除模糊，
 * 避免语言切换在弹窗分离后仍捕获到页面的残余模糊。
 */
@Composable
internal fun FrostedOverlayWindow(progress: State<Float>, popup: Boolean = false) {
    // 预览没有真实弹窗窗口；仅绘制最终面板，不调用原生窗口服务。
    if (LocalInspectionMode.current) return
    val view = LocalView.current
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(WindowManager::class.java) }
    val window = (view.parent as? DialogWindowProvider)?.window
    var blurEnabled by remember(manager) {
        mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.isCrossWindowBlurEnabled)
    }
    DisposableEffect(manager, view, window, popup) {
        val listener = Consumer<Boolean> { blurEnabled = it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.addCrossWindowBlurEnabledListener(context.mainExecutor, listener)
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.removeCrossWindowBlurEnabledListener(listener)
            }
            // 只清理当前浮层；嵌套弹窗关闭时不影响下层窗口，也不恢复系统退出动画。
            if (view.isAttachedToWindow) {
                updateFrostedWindow(window, view, manager, popup, blurRadius = 0, dimAmount = 0f)
            }
        }
    }
    val fraction = progress.value
    SideEffect {
        updateFrostedWindow(window, view, manager, popup,
            blurRadius = if (blurEnabled) (FrostedOverlayTokens.BlurRadiusPx * fraction).roundToInt() else 0,
            dimAmount = (if (blurEnabled) FrostedOverlayTokens.BlurredDim else FrostedOverlayTokens.FallbackDim) * fraction)
    }
}

private fun updateFrostedWindow(window: Window?, view: View, manager: WindowManager, popup: Boolean,
    blurRadius: Int, dimAmount: Float) {
    val popupView = view.rootView
    val attributes = window?.attributes ?: if (popup) {
        (popupView.layoutParams as? WindowManager.LayoutParams)?.takeIf {
            it.type == WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
        }
    } else null
    if (attributes == null) return
    attributes.flags = if (dimAmount > 0f) attributes.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
        else attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
    attributes.dimAmount = dimAmount
    attributes.windowAnimations = 0
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        attributes.flags = if (blurRadius > 0) attributes.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            else attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
        attributes.blurBehindRadius = blurRadius
    }
    if (window != null) window.attributes = attributes
    else if (popupView.isAttachedToWindow) manager.updateViewLayout(popupView, attributes)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassModalBottomSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val progress = rememberFrostedOverlayProgress()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.frostedOverlayMotion(progress, TransformOrigin(.5f, 1f)),
        shape = RoundedCornerShape(topStart = GlassTokens.FloatingRadius, topEnd = GlassTokens.FloatingRadius),
        containerColor = frostedOverlayColor(),
        tonalElevation = 0.dp,
        scrimColor = Color.Transparent,
    ) {
        FrostedOverlayWindow(progress)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val progress = rememberFrostedOverlayProgress()
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        modifier = Modifier.frostedOverlayMotion(progress),
        shape = RoundedCornerShape(GlassTokens.FloatingRadius),
        colors = DatePickerDefaults.colors(containerColor = frostedOverlayColor()),
        tonalElevation = 0.dp,
    ) {
        FrostedOverlayWindow(progress)
        content()
    }
}

/** 菜单仍让外部事件交给原页面处理，保持入口再次点击、滚动和返回关闭语义。 */
@Composable
internal fun GlassAnchoredPopup(alignment: Alignment = Alignment.TopStart, content: @Composable (State<Float>) -> Unit) {
    val progress = rememberFrostedOverlayProgress()
    val properties = remember { PopupProperties(focusable = false, dismissOnClickOutside = false) }
    if (LocalInspectionMode.current) {
        // Layoutlib 只验证面板与页面布局；真实多窗口与系统模糊由仪器/实机验收覆盖。
        content(progress)
    } else {
        Popup(alignment = alignment, properties = properties) {
            FrostedOverlayWindow(progress, popup = true)
            content(progress)
        }
    }
}
