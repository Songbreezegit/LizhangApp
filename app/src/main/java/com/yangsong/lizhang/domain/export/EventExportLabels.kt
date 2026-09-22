package com.yangsong.lizhang.domain.export

import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.model.eventDisplayText

/** 导出保持原有固定中文业务格式，不依赖 Android 资源。 */
internal fun GiftRecord.eventExportLabel(): String = eventDisplayText(eventType, customEventName) {
    when (it) {
        EventType.WEDDING -> "婚礼"
        EventType.FULL_MONTH -> "满月"
        EventType.BIRTHDAY -> "生日"
        EventType.HOUSEWARMING -> "乔迁"
        EventType.FESTIVAL -> "节日"
        EventType.OTHER -> "其他"
    }
}
