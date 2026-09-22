package com.yangsong.lizhang.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.model.MAX_CUSTOM_EVENT_NAME_LENGTH

@Composable
fun CustomEventDialog(initialName: String = "", onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val normalized = name.trim()
    val tooLong = normalized.codePointCount(0, normalized.length) > MAX_CUSTOM_EVENT_NAME_LENGTH
    val error = when {
        tooLong -> stringResource(R.string.event_custom_too_long, MAX_CUSTOM_EVENT_NAME_LENGTH)
        submitted && normalized.isEmpty() -> stringResource(R.string.event_custom_required)
        else -> null
    }
    GlassDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.event_custom_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().testTag("自定义事件名称"),
                    label = { Text(stringResource(R.string.event_custom_name)) }, placeholder = { Text(stringResource(R.string.event_custom_hint)) }, singleLine = true,
                    isError = error != null, supportingText = { Text(error ?: stringResource(R.string.event_custom_limit, MAX_CUSTOM_EVENT_NAME_LENGTH)) })
            }
        },
        confirmButton = { GlassButton(onClick = {
            submitted = true
            if (normalized.isNotEmpty() && !tooLong) onConfirm(normalized)
        }) { Text(stringResource(R.string.action_add)) } },
        dismissButton = { GlassTextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}
