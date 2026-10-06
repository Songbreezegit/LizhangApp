package com.yangsong.lizhang.fixtures

import com.yangsong.lizhang.domain.export.GiftExportLabels
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection

/** 仅用于纯 JVM 测试的展示文案，不包含用户数据。 */
val chineseExportLabels = GiftExportLabels(
    listOf("联系人", "金额（元）", "往来方向", "事件类型", "事件日期", "备注", "创建时间"),
    "礼金记录",
    mapOf(GiftDirection.RECEIVED to "收到", GiftDirection.GIVEN to "送出"),
    mapOf(EventType.WEDDING to "婚礼", EventType.FULL_MONTH to "满月", EventType.BIRTHDAY to "生日",
        EventType.HOUSEWARMING to "乔迁", EventType.FESTIVAL to "节日", EventType.OTHER to "其他"),
)
