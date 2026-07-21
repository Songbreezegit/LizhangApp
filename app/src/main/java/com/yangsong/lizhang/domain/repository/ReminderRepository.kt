package com.yangsong.lizhang.domain.repository

import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import kotlinx.coroutines.flow.StateFlow

interface ReminderRepository {
    val enabled: StateFlow<Boolean>
    fun setEnabled(enabled: Boolean)
    fun synchronize(records: List<GiftRecordWithContact>)
}
