package com.yangsong.lizhang.ui.component
import androidx.compose.runtime.getValue

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.semantics.Role
import dev.chrisbanes.haze.HazeState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.ui.theme.LocalEffectiveDarkTheme

data class GlassAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** 菜单由页面控制，便于统一处理返回键、管理模式与导航。 */
@Composable
fun GlassActionMenu(expanded: Boolean, onToggle: () -> Unit, onDismiss: () -> Unit, actions: List<GlassAction>, hazeState: HazeState) {
    val menuDescription = androidx.compose.ui.res.stringResource(if (expanded) com.yangsong.lizhang.R.string.menu_close else com.yangsong.lizhang.R.string.contact_create)
    val expandedDescription = androidx.compose.ui.res.stringResource(if (expanded) com.yangsong.lizhang.R.string.state_expanded else com.yangsong.lizhang.R.string.state_collapsed)
    val rotation by animateFloatAsState(if (expanded) 45f else 0f, tween(180), label = "添加按钮旋转")
    val actionShape = RoundedCornerShape(GlassTokens.ControlRadius)
    val actionFrame = if (LocalEffectiveDarkTheme.current) Modifier.frostedGlassFrame(hazeState, actionShape)
        else Modifier.clip(actionShape).background(MaterialTheme.colorScheme.primaryContainer)
    val actionTextColor = if (LocalEffectiveDarkTheme.current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.forEachIndexed { index, action ->
            AnimatedVisibility(expanded,
                enter = fadeIn(tween(160, (actions.lastIndex - index) * 45)) +
                    slideInVertically(tween(200, (actions.lastIndex - index) * 45)) { it / 3 } + scaleIn(initialScale = .96f),
                exit = fadeOut(tween(100)) + slideOutVertically(tween(140)) { it / 3 } + scaleOut(targetScale = .96f),
            ) {
                Row(
                    Modifier.heightIn(min = 48.dp)
                        .pressClickable(role = Role.Button, shape = actionShape) { onDismiss(); action.onClick() }
                        .then(actionFrame)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                        Icon(action.icon, null, tint = featureIconColor())
                        Spacer(Modifier.width(10.dp))
                        Text(action.label, color = actionTextColor, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        GlassFab(onToggle, Modifier.testTag("联系人添加菜单").semantics {
            contentDescription = menuDescription
            stateDescription = expandedDescription
        }, hazeState = hazeState) { Icon(LiZhangIcons.Plus, null, Modifier.rotate(rotation).size(28.dp), tint = featureIconColor()) }
    }
}
