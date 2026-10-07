package com.yangsong.lizhang.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    BottomNavigationItem(AppDestination.Home, R.string.nav_home, Icons.Outlined.Home),
    BottomNavigationItem(AppDestination.Contacts, R.string.nav_contacts, Icons.Outlined.PersonOutline),
    BottomNavigationItem(AppDestination.AddGift, R.string.nav_add_gift, Icons.Outlined.EditNote),
    BottomNavigationItem(AppDestination.Settings, R.string.nav_settings, Icons.Outlined.AccountCircle),
)

private object NavigationMotion {
    val FloatingDiameter = 48.dp
    val FloatingRadius = FloatingDiameter / 2
    val IconLift = 14.dp
    val MinimumHeight = 88.dp
    val HorizontalPadding = 36.dp
    val NotchHalfWidth = 33.dp
    val NotchDepth = 32.dp
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
    // 弹簧保留改选瞬间的速度；首次组合直接采用当前目的地。
    val position by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = .86f, stiffness = 300f, visibilityThreshold = .001f),
        label = "导航浮钮与凹槽位置",
    )
    val direction = LocalLayoutDirection.current
    val interactions = remember { bottomNavigationItems.map { MutableInteractionSource() } }
    BoxWithConstraints(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
            .heightIn(min = NavigationMotion.MinimumHeight),
    ) {
        // 大字体扩展标签宽度；窄屏仍给每个入口至少 48dp，凹槽边缘按实际空间收拢。
        val preferredPadding = if (LocalDensity.current.fontScale >= 1.2f) 16.dp else NavigationMotion.HorizontalPadding
        val horizontalPadding = minOf(preferredPadding, ((maxWidth - 192.dp) / 2).coerceAtLeast(0.dp))
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
                .pressFeedback(interactions[selectedIndex], CircleShape, enabled = enabled, pressedScale = .94f)
                .frostedGlassFrame(hazeState, CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = GlassTokens.SelectedAlpha), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // 图标随浮钮一起滑行，避免移动途中只剩空圆钮、新图标提前悬空。
            AnimatedContent(
                targetState = selectedIndex,
                transitionSpec = {
                    (fadeIn(tween(160)) + scaleIn(tween(260, easing = FastOutSlowInEasing), initialScale = .65f))
                        .togetherWith(fadeOut(tween(110)) + scaleOut(tween(140), targetScale = .65f))
                        .using(SizeTransform(clip = false))
                },
                label = "浮钮图标交接",
            ) { index ->
                Icon(bottomNavigationItems[index].icon, contentDescription = null,
                    modifier = Modifier.size(23.dp).testTag("底部导航浮动图标${bottomNavigationItems[index].destination.route}"),
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = horizontalPadding).selectableGroup(),
        ) {
            bottomNavigationItems.forEachIndexed { index, item ->
                val selected = selectedIndex == index
                val pressed by interactions[index].collectIsPressedAsState()
                val scale by navigationPressScale(pressed)
                val lift by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                    label = "导航原位图标交接${item.destination.route}",
                )
                val tint by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(180),
                    label = "导航图标文字颜色${item.destination.route}",
                )
                // 外层标签只供布局检查；真实可点击节点继续保留功能引导标签与坐标。
                Box(Modifier.weight(1f).testTag("底部导航项${item.destination.route}")) {
                    Column(
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
                            .padding(top = NavigationMotion.FloatingRadius, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                      Column(
                        Modifier.fillMaxWidth()
                            .then(item.destination.featureGuideTarget()?.let { Modifier.featureGuideVisualAnchor(it) } ?: Modifier),
                        horizontalAlignment = Alignment.CenterHorizontally,
                      ) {
                        Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                modifier = Modifier.size(23.dp).testTag("底部导航图标${item.destination.route}")
                                    .graphicsLayer {
                                        translationY = -NavigationMotion.IconLift.toPx() * lift
                                        alpha = 1f - lift
                                        scaleX = scale * (1f - .18f * lift)
                                        scaleY = scale * (1f - .18f * lift)
                                    },
                                tint = tint,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        BasicText(
                            stringResource(item.label),
                            modifier = Modifier.fillMaxWidth().testTag("底部导航标签${item.destination.route}"),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = tint,
                                // 固定字重和允许换行，让语言、选中态与大字体都不裁切标签。
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                            ),
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
    animationSpec = tween(if (pressed) 90 else 180),
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
