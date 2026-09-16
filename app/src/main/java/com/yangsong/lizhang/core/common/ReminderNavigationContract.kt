package com.yangsong.lizhang.core.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.yangsong.lizhang.MainActivity

/** 系统提醒与应用内导航之间唯一公开的 Intent 协议。 */
object ReminderNavigationContract {
    const val ACTION_OPEN_RECORD = "com.yangsong.lizhang.action.OPEN_REMINDER_RECORD"
    const val EXTRA_RECORD_ID = "reminder_record_id"

    fun createOpenRecordIntent(context: Context, recordId: Long): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_RECORD
            data = Uri.Builder()
                .scheme("lizhang")
                .authority("reminder-record")
                .appendPath(recordId.toString())
                .build()
            putExtra(EXTRA_RECORD_ID, recordId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    fun readRecordId(intent: Intent?): Long? {
        if (intent?.action != ACTION_OPEN_RECORD) return null
        return intent.getLongExtra(EXTRA_RECORD_ID, 0L).takeIf { it > 0L }
    }
}
