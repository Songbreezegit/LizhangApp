package com.yangsong.lizhang.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contacts",
    indices = [Index(value = ["name"]), Index(value = ["phone"])],
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val relationship: String? = null,
    val notes: String? = null,
    val createdTime: Long = System.currentTimeMillis(),
)
