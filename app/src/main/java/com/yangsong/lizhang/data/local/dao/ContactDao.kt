package com.yangsong.lizhang.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.yangsong.lizhang.domain.contact.ContactImportRules
import com.yangsong.lizhang.domain.model.ContactImportResult
import com.yangsong.lizhang.data.local.entity.ContactEntity
import com.yangsong.lizhang.data.local.projection.ContactSummaryRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(contact: ContactEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(contacts: List<ContactEntity>)

    @Transaction
    suspend fun importContacts(contacts: List<ContactEntity>): ContactImportResult {
        val phones = getAllForBackup().mapNotNull { ContactImportRules.normalizePhone(it.phone) }.toMutableSet()
        val additions = contacts.mapNotNull { contact ->
            val phone = ContactImportRules.normalizePhone(contact.phone)
            if (contact.name.isBlank() || phone == null || !phones.add(phone)) null
            else contact.copy(id = 0, name = contact.name.trim(), phone = phone)
        }
        insertAll(additions)
        return ContactImportResult(additions.size, contacts.size - additions.size)
    }

    @Update
    suspend fun update(contact: ContactEntity)

    @Delete
    suspend fun delete(contact: ContactEntity)

    @Query("SELECT * FROM contacts ORDER BY id")
    suspend fun getAllForBackup(): List<ContactEntity>

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()

    @Query("SELECT * FROM contacts WHERE id = :contactId LIMIT 1")
    fun observeById(contactId: Long): Flow<ContactEntity?>

    @Query(
        """
        SELECT * FROM contacts
        WHERE name LIKE '%' || :query || '%' COLLATE NOCASE
           OR phone LIKE '%' || :query || '%' COLLATE NOCASE
           OR relationship LIKE '%' || :query || '%' COLLATE NOCASE
           OR notes LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY name COLLATE NOCASE, createdTime DESC
        """,
    )
    fun observeSearch(query: String): Flow<List<ContactEntity>>

    @Query(
        """
        SELECT contacts.*,
            COALESCE(SUM(CASE WHEN gift_records.direction = 'RECEIVED' THEN gift_records.amountInCents ELSE 0 END), 0) AS receivedInCents,
            COALESCE(SUM(CASE WHEN gift_records.direction = 'GIVEN' THEN gift_records.amountInCents ELSE 0 END), 0) AS givenInCents,
            COALESCE(MAX(gift_records.eventDate), contacts.createdTime) AS lastInteractionTime
        FROM contacts
        LEFT JOIN gift_records ON contacts.id = gift_records.contactId
        WHERE contacts.name LIKE '%' || :query || '%' COLLATE NOCASE
           OR contacts.phone LIKE '%' || :query || '%' COLLATE NOCASE
           OR contacts.relationship LIKE '%' || :query || '%' COLLATE NOCASE
           OR contacts.notes LIKE '%' || :query || '%' COLLATE NOCASE
        GROUP BY contacts.id
        ORDER BY lastInteractionTime DESC, contacts.createdTime DESC, contacts.name COLLATE NOCASE
        """,
    )
    fun observeSummaries(query: String): Flow<List<ContactSummaryRow>>
}
