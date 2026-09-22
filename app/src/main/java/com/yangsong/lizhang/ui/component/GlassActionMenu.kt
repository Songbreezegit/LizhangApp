package com.yangsong.lizhang.ui.component
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

data class GlassAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** 菜单由页面控制，便于统一处理返回键、管理模式与导航。 */
@Composable
fun GlassActionMenu(expanded: Boolean, onToggle: () -> Unit, onDismiss: () -> Unit, actions: List<GlassAction>) {
    val rotation by animateFloatAsState(if (expanded) 45f else 0f, tween(180), label = "添加按钮旋转")
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.forEachIndexed { index, action ->
            AnimatedVisibility(expanded,
                enter = fadeIn(tween(160, (actions.lastIndex - index) * 45)) +
                    slideInVertically(tween(200, (actions.lastIndex - index) * 45)) { it / 3 } + scaleIn(initialScale = .96f),
                exit = fadeOut(tween(100)) + slideOutVertically(tween(140)) { it / 3 } + scaleOut(targetScale = .96f),
            ) {
                GlassButton(onClick = { onDismiss(); action.onClick() }, colors = ButtonDefaults.buttonColors(containerColor = glassColor().copy(alpha = GlassTokens.FloatingAlpha), contentColor = MaterialTheme.colorScheme.primary)) {
                    Icon(action.icon, null)
                    Spacer(Modifier.width(10.dp))
                    Text(action.label)
                }
            }
        }
        GlassFab(onToggle, Modifier.testTag("联系人添加菜单").semantics {
            contentDescription = if (expanded) "关闭添加菜单" else "添加联系人"
            stateDescription = if (expanded) "已展开" else "已收起"
        }) { Icon(Icons.Outlined.Add, null, Modifier.rotate(rotation).size(28.dp)) }
    }
}
