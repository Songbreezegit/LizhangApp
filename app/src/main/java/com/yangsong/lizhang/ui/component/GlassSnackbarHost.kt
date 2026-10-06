package com.yangsong.lizhang.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** 反馈语义独立于文案，避免通过字符串判断成功或失败。 */
data class GlassSnackbarVisuals(
    override val message: String,
    val isError: Boolean = false,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals

/** 使用 SnackbarHost 原有轻淡入淡出与无障碍时长管理，仅替换呈现样式。 */
@Composable
fun GlassSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data ->
        GlassSnackbar(
            message = data.visuals.message,
            isError = (data.visuals as? GlassSnackbarVisuals)?.isError == true,
            actionLabel = data.visuals.actionLabel,
            onAction = data::performAction,
        )
    }
}

@Composable
fun GlassSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val accent = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        modifier.fillMaxWidth().glassFrame(RoundedCornerShape(20.dp), floating = true)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle, null, tint = accent)
        Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium)
        if (actionLabel != null) TextButton(onAction) { Text(actionLabel, color = accent) }
    }
}
