package com.yangsong.lizhang.data.local

import androidx.room.TypeConverter
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection

class RoomConverters {
    @TypeConverter fun eventTypeToString(value: EventType): String = value.name
    @TypeConverter fun stringToEventType(value: String): EventType = EventType.valueOf(value)
    @TypeConverter fun directionToString(value: GiftDirection): String = value.name
    @TypeConverter fun stringToDirection(value: String): GiftDirection = GiftDirection.valueOf(value)
}
