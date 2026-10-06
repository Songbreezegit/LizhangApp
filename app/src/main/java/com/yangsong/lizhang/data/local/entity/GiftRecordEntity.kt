package com.yangsong.lizhang.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection

@Entity(
    tableName = "gift_records",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["contactId"]), Index(value = ["eventDate"])],
)
data class GiftRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val amountInCents: Long,
    val eventType: EventType,
    val eventDate: Long,
    val direction: GiftDirection,
    val notes: String? = null,
    val createdTime: Long = System.currentTimeMillis(),
    @androidx.room.ColumnInfo(defaultValue = "NULL") val customEventName: String? = null,
)
