package com.yangsong.lizhang.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun CustomEventDialog(initialName: String = "", onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val normalized = name.trim()
    val tooLong = normalized.codePointCount(0, normalized.length) > 20
    val error = when {
        tooLong -> "事件名称最多20个字符"
        submitted && normalized.isEmpty() -> "请输入事件名称"
        else -> null
    }
    GlassDialog(onDismissRequest = onDismiss, title = { Text("自定义事件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().testTag("自定义事件名称"),
                    label = { Text("事件名称") }, placeholder = { Text("例如：升学宴") }, singleLine = true,
                    isError = error != null, supportingText = { Text(error ?: "最多20个字符") })
            }
        },
        confirmButton = { GlassButton(onClick = {
            submitted = true
            if (normalized.isNotEmpty() && !tooLong) onConfirm(normalized)
        }) { Text("添加") } },
        dismissButton = { GlassTextButton(onDismiss) { Text("取消") } })
}
