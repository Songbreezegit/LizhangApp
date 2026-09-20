package com.yangsong.lizhang.domain.repository

import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.ContactImportResult
import com.yangsong.lizhang.domain.model.ContactLedgerSummary
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun observeContacts(query: String = ""): Flow<List<Contact>>
    fun observeContactSummaries(query: String = ""): Flow<List<ContactLedgerSummary>>
    fun observeContact(contactId: Long): Flow<Contact?>
    suspend fun create(contact: Contact): Long
    /** 批量导入：在同一事务内重新检查标准化号码，返回实际写入与跳过数量。 */
    suspend fun createAll(contacts: List<Contact>): ContactImportResult
    /** 对完整候选快照执行事务内复查，仅新建已选择且允许导入的记录。 */
    suspend fun importDeviceContacts(selections: List<com.yangsong.lizhang.domain.model.ContactImportSelection>): ContactImportResult
    suspend fun update(contact: Contact)
    suspend fun delete(contact: Contact)
}
