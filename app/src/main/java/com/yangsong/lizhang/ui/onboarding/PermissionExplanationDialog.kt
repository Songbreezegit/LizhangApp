package com.yangsong.lizhang.ui.onboarding

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.onboarding.ExplainedPermission
import com.yangsong.lizhang.ui.component.GlassDialog
import com.yangsong.lizhang.ui.component.GlassTextButton

@Composable
fun PermissionExplanationDialog(permission: ExplainedPermission, blocked: Boolean, onDismiss: () -> Unit, onContinue: () -> Unit) {
    val contacts = permission == ExplainedPermission.CONTACTS
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (contacts) R.string.contact_permission_explanation_title else R.string.notification_permission_explanation_title)) },
        text = { Text(stringResource(when {
            blocked && contacts -> R.string.contact_import_permission_blocked
            blocked -> R.string.notification_permission_settings_body
            contacts -> R.string.contact_permission_explanation_body
            else -> R.string.notification_permission_explanation_body
        })) },
        dismissButton = { GlassTextButton(onDismiss) { Text(stringResource(R.string.permission_not_now)) } },
        confirmButton = { GlassTextButton(onContinue) { Text(stringResource(if (blocked) R.string.permission_open_settings else R.string.permission_continue)) } },
    )
}
