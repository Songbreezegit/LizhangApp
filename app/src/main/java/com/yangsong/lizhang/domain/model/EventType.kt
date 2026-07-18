package com.yangsong.lizhang.domain.model

/** 事件类型只保存稳定的枚举值，展示文字由 UI 层资源决定，便于未来多语言化。 */
enum class EventType {
    WEDDING,
    FULL_MONTH,
    BIRTHDAY,
    HOUSEWARMING,
    FESTIVAL,
    OTHER,
}
