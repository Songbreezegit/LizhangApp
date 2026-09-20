package com.yangsong.lizhang.data.contact

import android.content.ContentResolver
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.yangsong.lizhang.domain.model.DeviceContact
import com.yangsong.lizhang.domain.repository.DeviceContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 只读查询，无日志、文件缓存或网络传输。权限撤销由上层转换为可恢复状态。 */
class DeviceContactDataSource(private val resolver: ContentResolver) : DeviceContactRepository {
    override suspend fun readContacts(): List<DeviceContact> = withContext(Dispatchers.IO) {
        val cursor = resolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER),
            null, null, "${Phone.CONTACT_ID} ASC, ${Phone.NUMBER} ASC",
        ) ?: error("无法读取通讯录")
        cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(DeviceContact(it.getLong(0), it.getString(1).orEmpty(), it.getString(2).orEmpty()))
                }
            }
        }
    }
}
