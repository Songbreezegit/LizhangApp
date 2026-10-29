package com.yangsong.lizhang.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.R
import dev.chrisbanes.haze.HazeState

data class GlassAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** 菜单状态与外部点击仍由页面控制，浮层窗口负责背景模糊。 */
@Composable
fun GlassActionMenu(expanded: Boolean, onToggle: () -> Unit, onDismiss: () -> Unit,
    actions: List<GlassAction>, hazeState: HazeState) {
    val rotation by animateFloatAsState(if (expanded) 45f else 0f, tween(180), label = "添加按钮旋转")
    if (!expanded) ActionMenuToggle(false, onToggle, hazeState, rotation)
    else {
        // 页面上的占位保留引导锚点，不在模糊的背景中再绘制一份菜单按钮。
        Box(Modifier.size(56.dp)) {
            GlassAnchoredPopup(alignment = Alignment.BottomEnd) { progress ->
                Column(
                    Modifier.frostedOverlayMotion(progress, TransformOrigin(1f, 1f)).clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClickLabel = stringResource(R.string.menu_close), onClick = onDismiss,
                    ),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val shape = RoundedCornerShape(GlassTokens.ControlRadius)
                    actions.forEach { action ->
                        Row(
                            Modifier.heightIn(min = 48.dp)
                                .pressClickable(role = Role.Button, shape = shape) { onDismiss(); action.onClick() }
                                .frostedOverlayFrame(shape).padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(action.icon, null, tint = featureIconColor())
                            Spacer(Modifier.width(10.dp))
                            Text(action.label, color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    ActionMenuToggle(true, onToggle, hazeState, rotation)
                }
            }
        }
    }
}

@Composable
private fun ActionMenuToggle(expanded: Boolean, onToggle: () -> Unit, hazeState: HazeState, rotation: Float) {
    val description = stringResource(if (expanded) R.string.menu_close else R.string.contact_create)
    val expandedDescription = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    GlassFab(onToggle, Modifier.testTag("联系人添加菜单").semantics {
        contentDescription = description
        stateDescription = expandedDescription
    }, hazeState = hazeState) {
        Icon(LiZhangIcons.Plus, null, Modifier.rotate(rotation).size(28.dp), tint = featureIconColor())
    }
}
