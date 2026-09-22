package com.yangsong.lizhang.domain.model

/** 自定义名称只覆盖“其他”的记录文案，统计仍使用稳定枚举。 */
fun eventDisplayText(eventType: EventType, customEventName: String?, standardName: (EventType) -> String = ::standardEventName): String =
    customEventName?.trim()?.takeIf { eventType == EventType.OTHER && it.isNotEmpty() } ?: standardName(eventType)

fun GiftRecord.eventDisplayName(): String = eventDisplayText(eventType, customEventName)

private fun standardEventName(type: EventType): String = when (type) {
    EventType.WEDDING -> "婚礼"
    EventType.FULL_MONTH -> "满月"
    EventType.BIRTHDAY -> "生日"
    EventType.HOUSEWARMING -> "乔迁"
    EventType.FESTIVAL -> "节日"
    EventType.OTHER -> "其他"
}
