package com.yangsong.lizhang.data.repository

import com.yangsong.lizhang.data.local.dao.ContactDao
import com.yangsong.lizhang.data.mapper.toDomain
import com.yangsong.lizhang.data.mapper.toEntity
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.ContactLedgerSummary
import com.yangsong.lizhang.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomContactRepository(private val contactDao: ContactDao) : ContactRepository {
    override fun observeContacts(query: String): Flow<List<Contact>> =
        contactDao.observeSearch(query.trim()).map { entities -> entities.map { it.toDomain() } }

    override fun observeContactSummaries(query: String): Flow<List<ContactLedgerSummary>> =
        contactDao.observeSummaries(query.trim()).map { rows -> rows.map { it.toDomain() } }

    override fun observeContact(contactId: Long): Flow<Contact?> =
        contactDao.observeById(contactId).map { it?.toDomain() }

    override suspend fun create(contact: Contact): Long = contactDao.insert(contact.toEntity())
    override suspend fun createAll(contacts: List<Contact>) = withContext(Dispatchers.IO) {
        contactDao.importContacts(contacts.map { it.toEntity() })
    }
    override suspend fun importDeviceContacts(selections: List<com.yangsong.lizhang.domain.model.ContactImportSelection>) = withContext(Dispatchers.IO) {
        contactDao.importDeviceContacts(selections)
    }
    override suspend fun update(contact: Contact) = contactDao.update(contact.toEntity())
    override suspend fun delete(contact: Contact) = contactDao.delete(contact.toEntity())
}
