package com.yangsong.lizhang.ui.mapper

import androidx.annotation.StringRes
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection

@StringRes
fun EventType.labelRes(): Int = when (this) {
    EventType.WEDDING -> R.string.event_wedding
    EventType.FULL_MONTH -> R.string.event_full_month
    EventType.BIRTHDAY -> R.string.event_birthday
    EventType.HOUSEWARMING -> R.string.event_housewarming
    EventType.FESTIVAL -> R.string.event_festival
    EventType.OTHER -> R.string.event_other
}

@StringRes
fun GiftDirection.labelRes(): Int = when (this) {
    GiftDirection.RECEIVED -> R.string.direction_received
    GiftDirection.GIVEN -> R.string.direction_given
}
