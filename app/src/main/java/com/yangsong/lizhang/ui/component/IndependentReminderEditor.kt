package com.yangsong.lizhang.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.reminder.IndependentReminder
import com.yangsong.lizhang.domain.reminder.IndependentReminderPlanner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndependentReminderEditor(reminder: IndependentReminder?, onDismiss: () -> Unit,
    onSave: (IndependentReminder) -> Boolean) {
    var title by rememberSaveable(reminder?.id) { mutableStateOf(reminder?.title.orEmpty()) }
    var dateText by rememberSaveable(reminder?.id) { mutableStateOf((reminder?.date ?: LocalDate.now().plusDays(1)).toString()) }
    var annually by rememberSaveable(reminder?.id) { mutableStateOf(reminder?.annually ?: false) }
    var showDate by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val date = LocalDate.parse(dateText)
    val future = IndependentReminderPlanner.canSave(date)
    GlassDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(if (reminder == null) R.string.independent_add else R.string.independent_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { if (it.length <= 80) { title = it; saveFailed = false } },
                    modifier = Modifier.fillMaxWidth().testTag("独立提醒名称"),
                    label = { Text(stringResource(R.string.independent_title)) }, singleLine = true)
                GlassTextButton({ showDate = true }, Modifier.fillMaxWidth().testTag("独立提醒日期")) {
                    Text(stringResource(R.string.independent_date_value, date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))))
                }
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.independent_annually), Modifier.weight(1f).padding(top = 12.dp))
                    Switch(annually, { annually = it }, colors = glassSwitchColors(), modifier = Modifier.testTag("独立提醒每年重复"))
                }
                Text(stringResource(R.string.independent_date_rule), style = MaterialTheme.typography.bodySmall,
                    color = if (future) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                if (saveFailed) Text(stringResource(R.string.independent_save_failed), color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = {
            GlassButton(onClick = {
                val value = IndependentReminder(reminder?.id ?: 0, title.trim(), date, annually, reminder?.enabled ?: true)
                if (onSave(value)) onDismiss() else saveFailed = true
            }, enabled = title.isNotBlank() && future, modifier = Modifier.testTag("独立提醒保存")) { Text(stringResource(R.string.independent_save)) }
        }, dismissButton = { GlassTextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } })
    if (showDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() > LocalDate.now()
            })
        DatePickerDialog(onDismissRequest = { showDate = false },
            confirmButton = { GlassTextButton({
                picker.selectedDateMillis?.let { dateText = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                showDate = false
            }, enabled = picker.selectedDateMillis != null) { Text(stringResource(R.string.action_done)) } },
            dismissButton = { GlassTextButton({ showDate = false }) { Text(stringResource(R.string.action_cancel)) } }) {
            DatePicker(picker)
        }
    }
}
