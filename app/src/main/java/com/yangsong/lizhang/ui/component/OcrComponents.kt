package com.yangsong.lizhang.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.theme.*
import com.yangsong.lizhang.ui.viewmodel.OcrPendingRecordUi

@Composable
fun OcrPendingRecordItem(
    record: OcrPendingRecordUi,
    onChange: (OcrPendingRecordUi) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (record.lowConfidence) StatusTag(stringResource(R.string.ocr_low_confidence), ApricotContainer, ApricotPrimary)
                    if (record.possibleDuplicate) StatusTag(stringResource(R.string.ocr_duplicate), CoralContainer, CoralStrong)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.action_delete)) }
            }
            AppTextField(record.name, { onChange(record.copy(name = it)) }, stringResource(R.string.contact_name))
            AppTextField(record.amount, { onChange(record.copy(amount = it)) }, stringResource(R.string.field_amount))
            AppTextField(record.date, { onChange(record.copy(date = it)) }, stringResource(R.string.field_date))
        }
    }
}

@Composable
private fun StatusTag(text: String, background: androidx.compose.ui.graphics.Color, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = background) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, style = MaterialTheme.typography.labelSmall)
    }
}
