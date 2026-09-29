package com.yangsong.lizhang.domain.export

import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.eventDisplayText

/** 展示层提供翻译后的文案；导出逻辑不依赖 Android 或当前语言。 */
data class GiftExportLabels(
    val headers: List<String>,
    val sheetName: String,
    val directions: Map<GiftDirection, String>,
    val events: Map<EventType, String>,
)

internal fun GiftRecord.eventExportLabel(labels: GiftExportLabels): String =
    eventDisplayText(eventType, customEventName) { labels.events.getValue(it) }
