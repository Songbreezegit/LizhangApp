package com.yangsong.lizhang.domain.model

/** 自定义名称只覆盖 OTHER；标准名称由调用方按展示场景提供。 */
fun eventDisplayText(eventType: EventType, customEventName: String?, standardName: (EventType) -> String): String =
    customEventName?.trim()?.takeIf { eventType == EventType.OTHER && it.isNotEmpty() } ?: standardName(eventType)
