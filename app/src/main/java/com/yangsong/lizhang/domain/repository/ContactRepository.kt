package com.yangsong.lizhang.domain.repository

import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.ContactLedgerSummary
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun observeContacts(query: String = ""): Flow<List<Contact>>
    fun observeContactSummaries(query: String = ""): Flow<List<ContactLedgerSummary>>
    fun observeContact(contactId: Long): Flow<Contact?>
    suspend fun create(contact: Contact): Long
    suspend fun update(contact: Contact)
    suspend fun delete(contact: Contact)
}
