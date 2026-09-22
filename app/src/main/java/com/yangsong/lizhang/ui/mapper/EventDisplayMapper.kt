package com.yangsong.lizhang.ui.mapper

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.model.eventDisplayText

@Composable
fun GiftRecord.eventDisplayLabel(): String {
    // 先在组合上下文读取资源，再交给不依赖 Compose 的领域选择规则。
    val standardLabel = stringResource(eventType.labelRes())
    return eventDisplayText(eventType, customEventName) { standardLabel }
}
