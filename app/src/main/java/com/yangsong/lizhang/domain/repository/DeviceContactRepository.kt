package com.yangsong.lizhang.domain.repository

import com.yangsong.lizhang.domain.model.DeviceContact

fun interface DeviceContactRepository {
    suspend fun readContacts(): List<DeviceContact>
}
