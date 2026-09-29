package com.yangsong.lizhang.ui.mapper

import android.content.Context
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.export.GiftExportLabels
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection

/** 点击导出时读取页面语言，避免 ViewModel 跨重建持有旧语言文案。 */
fun Context.giftExportLabels(): GiftExportLabels = GiftExportLabels(
    headers = listOf(R.string.field_contact, R.string.export_amount, R.string.field_direction,
        R.string.export_event_type, R.string.export_event_date, R.string.field_notes, R.string.export_created_time)
        .map { getString(it) },
    sheetName = getString(R.string.export_sheet_name),
    directions = GiftDirection.entries.associateWith { getString(it.labelRes()) },
    events = EventType.entries.associateWith { getString(it.labelRes()) },
)
