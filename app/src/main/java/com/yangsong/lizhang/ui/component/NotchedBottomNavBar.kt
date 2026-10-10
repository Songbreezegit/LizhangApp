package com.yangsong.lizhang.ui.component

import android.animation.ValueAnimator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.onboarding.featureGuideTarget
import com.yangsong.lizhang.ui.onboarding.featureGuideVisualAnchor
import dev.chrisbanes.haze.HazeState

private data class BottomNavigationItem(
    val destination: AppDestination,
    val label: Int,
    val icon: ImageVector,
)

private val bottomNavigationItems = listOf(
    BottomNavigationItem(AppDestination.Home, R.string.nav_home, LiZhangIcons.House),
    BottomNavigationItem(AppDestination.Contacts, R.string.nav_contacts, LiZhangIcons.UsersRound),
    BottomNavigationItem(AppDestination.AddGift, R.string.nav_add_gift, LiZhangIcons.NotebookPen),
    BottomNavigationItem(AppDestination.Settings, R.string.nav_settings, LiZhangIcons.CircleUserRound),
)

private object NavigationMotion {
    val FloatingDiameter = 48.dp
    val FloatingRadius = FloatingDiameter / 2
    val MinimumHeight = 88.dp
    val IconLift = (MinimumHeight - FloatingRadius) / 2
    val HorizontalPadding = 36.dp
    val NotchHalfWidth = 33.dp
    val NotchDepth = 32.dp
    // Figma 各轨道分别映射：位置、浮动缩放与透明度、原位位移与透明度。
    val PositionEasing = CubicBezierEasing(.2f, 0f, 0f, 1f)
    val FloatingScaleEasing = CubicBezierEasing(.5f, 0f, .5f, 1f)
    val FloatingOpacityEasing = CubicBezierEasing(.5f, 0f, .5f, 1f)
    val OriginalIconEasing = LinearEasing
    // 颜色与手指按压属于原有交互反馈，继续保留既有曲线。
    val ColorEasing = PositionEasing
    val PressEasing = PositionEasing
    const val FollowDurationMillis = 360
    const val PressedDurationMillis = 110
    const val IconEnterDurationMillis = 160
    const val IconScaleDurationMillis = 220
    const val OriginalExitDurationMillis = 120
    const val OriginalReturnDurationMillis = 180
}

/** 路由立即生效；同一位置进度驱动浮钮与真实凹槽，不改变业务导航。 */
@Composable
internal fun NotchedBottomNavigation(
    current: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier,
    hazeState: HazeState,
    enabled: Boolean = true,
) {
    val selectedIndex = bottomNavigationItems.indexOfFirst { it.destination == current }.coerceAtLeast(0)
    val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
    // Figma 稿中的浮钮与凹槽共用位置：改选时从当前位置追随最新路由，不排队。
    val position by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(if (animationsEnabled) NavigationMotion.FollowDurationMillis else 0,
            easing = NavigationMotion.PositionEasing),
        label = "导航浮钮与凹槽位置",
    )
    val selectionScale = remember { Animatable(1f) }
    var previousIndex by remember { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex) {
        // 首次出现保持最终态；快速改选取消旧关键帧，以当前缩放接续新动效。
        if (selectedIndex != previousIndex) {
            previousIndex = selectedIndex
            if (!ValueAnimator.areAnimatorsEnabled()) {
                selectionScale.snapTo(1f)
            } else {
                val currentScale = selectionScale.value
                selectionScale.animateTo(1f, keyframes {
                    durationMillis = NavigationMotion.FollowDurationMillis
                    currentScale at 0 using NavigationMotion.FloatingScaleEasing
                    .96f at NavigationMotion.PressedDurationMillis using NavigationMotion.FloatingScaleEasing
                    1f at NavigationMotion.FollowDurationMillis using NavigationMotion.FloatingScaleEasing
                })
            }
        }
    }
    val direction = LocalLayoutDirection.current
    val interactions = remember { bottomNavigationItems.map { MutableInteractionSource() } }
    BoxWithConstraints(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
            .heightIn(min = NavigationMotion.MinimumHeight),
    ) {
        // 仅显示图标，字号不再改变入口位置；窄屏仍给每个入口至少 48dp。
        val horizontalPadding = minOf(NavigationMotion.HorizontalPadding, ((maxWidth - 192.dp) / 2).coerceAtLeast(0.dp))
        val laneWidth = (maxWidth - horizontalPadding * 2) / bottomNavigationItems.size
        val physicalPosition = if (direction == LayoutDirection.Rtl) bottomNavigationItems.lastIndex - position else position
        val center = horizontalPadding + laneWidth * (physicalPosition + .5f)
        val shape = remember(center, maxWidth) { NotchedNavigationShape(center) }
        Box(
            Modifier.matchParentSize().padding(top = NavigationMotion.FloatingRadius)
                .testTag("底部导航主体").frostedGlassFrame(hazeState, shape),
        )
        // offset 采用物理坐标；RTL 只在上面的索引映射一次，避免再次镜像。
        Box(
            Modifier.align(AbsoluteAlignment.TopLeft).zIndex(1f)
                .absoluteOffset { androidx.compose.ui.unit.IntOffset((center - NavigationMotion.FloatingRadius).roundToPx(), 0) }
                .size(NavigationMotion.FloatingDiameter).testTag("底部导航浮钮")
                .graphicsLayer {
                    scaleX = if (animationsEnabled) selectionScale.value else 1f
                    scaleY = if (animationsEnabled) selectionScale.value else 1f
                }
                .pressFeedback(interactions[selectedIndex], CircleShape, enabled = enabled, pressedScale = 1f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.matchParentSize().then(bottomNavigationItems[selectedIndex].destination.featureGuideTarget()?.let {
                    Modifier.featureGuideVisualAnchor(it)
                } ?: Modifier),
                contentAlignment = Alignment.Center,
            ) {
                // 图标随浮钮一起滑行，避免移动途中只剩空圆钮、新图标提前悬空。
                AnimatedContent(
                    targetState = selectedIndex,
                    transitionSpec = {
                        (fadeIn(tween(if (animationsEnabled) NavigationMotion.IconEnterDurationMillis else 0,
                            easing = NavigationMotion.FloatingOpacityEasing)) + scaleIn(tween(
                            if (animationsEnabled) NavigationMotion.IconScaleDurationMillis else 0,
                            easing = NavigationMotion.FloatingScaleEasing), initialScale = .84f))
                            .togetherWith(fadeOut(tween(if (animationsEnabled) 110 else 0,
                                easing = NavigationMotion.FloatingOpacityEasing)) + scaleOut(tween(
                                if (animationsEnabled) NavigationMotion.OriginalExitDurationMillis else 0,
                                easing = NavigationMotion.FloatingScaleEasing), targetScale = .92f))
                            .using(SizeTransform(clip = false))
                    },
                    label = "浮钮图标交接",
                ) { index ->
                    Icon(bottomNavigationItems[index].icon, contentDescription = null,
                        modifier = Modifier.size(23.dp).testTag("底部导航浮动图标${bottomNavigationItems[index].destination.route}"),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = horizontalPadding).selectableGroup(),
        ) {
            bottomNavigationItems.forEachIndexed { index, item ->
                val selected = selectedIndex == index
                val label = stringResource(item.label)
                val pressed by interactions[index].collectIsPressedAsState()
                val scale by navigationPressScale(pressed)
                val lift by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = tween(if (!animationsEnabled) 0 else if (selected)
                        NavigationMotion.OriginalExitDurationMillis else NavigationMotion.OriginalReturnDurationMillis,
                        easing = NavigationMotion.OriginalIconEasing),
                    label = "导航原位图标交接${item.destination.route}",
                )
                val tint by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(if (animationsEnabled) NavigationMotion.OriginalReturnDurationMillis else 0,
                        easing = NavigationMotion.ColorEasing),
                    label = "导航图标颜色${item.destination.route}",
                )
                // 外层标签只供布局检查；真实可点击节点继续保留功能引导标签与坐标。
                Box(Modifier.weight(1f).testTag("底部导航项${item.destination.route}")) {
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = NavigationMotion.MinimumHeight)
                            .then(item.destination.featureGuideTarget()?.let { Modifier.featureGuideTarget(it, registerBounds = false) } ?: Modifier)
                            .selectable(
                                selected = selected,
                                interactionSource = interactions[index],
                                indication = null,
                                enabled = enabled,
                                role = Role.Tab,
                                onClick = { if (!selected) onNavigate(item.destination) },
                            )
                            .semantics { contentDescription = label }
                            .padding(top = NavigationMotion.FloatingRadius),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 原位图标的位移与透明度均为线性轨道，缩放仅保留手指按压反馈。
                        Box(
                            Modifier.size(23.dp).graphicsLayer {
                                translationY = -NavigationMotion.IconLift.toPx() * lift
                                alpha = 1f - lift
                                scaleX = scale
                                scaleY = scale
                            }
                                .then(if (!selected) item.destination.featureGuideTarget()?.let {
                                    Modifier.featureGuideVisualAnchor(it)
                                } ?: Modifier else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                modifier = Modifier.size(23.dp).testTag("底部导航图标${item.destination.route}"),
                                tint = tint,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun navigationPressScale(pressed: Boolean) = animateFloatAsState(
    targetValue = if (pressed) .92f else 1f,
    animationSpec = tween(if (!ValueAnimator.areAnimatorsEnabled()) 0 else if (pressed) 90 else 180,
        easing = NavigationMotion.PressEasing),
    label = "导航按压缩放",
)

/** 四段连续三次曲线构成 U 形缺口，背景、边框和外围阴影共享同一个轮廓。 */
private class NotchedNavigationShape(private val center: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val centerX = center.toPx().coerceIn(0f, size.width)
        val halfWidth = NavigationMotion.NotchHalfWidth.toPx().coerceAtMost(minOf(centerX, size.width - centerX))
        val depth = NavigationMotion.NotchDepth.toPx().coerceAtMost(size.height / 2)
        val corner = GlassTokens.FloatingRadius.toPx()
            .coerceAtMost(minOf(centerX - halfWidth, size.width - centerX - halfWidth, size.height / 2))
            .coerceAtLeast(0f)
        // 小于常规屏宽时等比收拢凹槽，避免曲线与外边缘相交。
        val horizontalScale = halfWidth / NavigationMotion.NotchHalfWidth.toPx()
        val verticalScale = depth / NavigationMotion.NotchDepth.toPx()
        fun x(value: Float) = centerX + value.dp.toPx() * horizontalScale
        fun y(value: Float) = value.dp.toPx() * verticalScale
        val path = Path().apply {
            moveTo(corner, 0f)
            lineTo(centerX - halfWidth, 0f)
            cubicTo(x(-30f), 0f, x(-29f), y(9f), x(-25f), y(17f))
            cubicTo(x(-21f), y(25f), x(-13f), depth, centerX, depth)
            cubicTo(x(13f), depth, x(21f), y(25f), x(25f), y(17f))
            cubicTo(x(29f), y(9f), x(30f), 0f, centerX + halfWidth, 0f)
            lineTo(size.width - corner, 0f)
            quadraticTo(size.width, 0f, size.width, corner)
            lineTo(size.width, size.height - corner)
            quadraticTo(size.width, size.height, size.width - corner, size.height)
            lineTo(corner, size.height)
            quadraticTo(0f, size.height, 0f, size.height - corner)
            lineTo(0f, corner)
            quadraticTo(0f, 0f, corner, 0f)
            close()
        }
        Outline.Generic(path)
    }
}
