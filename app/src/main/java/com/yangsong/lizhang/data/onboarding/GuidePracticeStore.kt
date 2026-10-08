package com.yangsong.lizhang.data.onboarding

import com.yangsong.lizhang.domain.model.BulkDeleteResult
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.ContactBulkDeleteOutcome
import com.yangsong.lizhang.domain.model.ContactDeletePreview
import com.yangsong.lizhang.domain.model.ContactImportResult
import com.yangsong.lizhang.domain.model.ContactImportSelection
import com.yangsong.lizhang.domain.model.ContactLedgerSummary
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.model.YearlyGiftSummary
import com.yangsong.lizhang.domain.repository.ContactRepository
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import com.yangsong.lizhang.domain.repository.GuidePracticeRepository
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** 联系人与记录共用不可变快照，关联查询不会混合保存前后的数据。 */
data class GuidePracticeSnapshot(
    val contacts: List<Contact> = emptyList(),
    val records: List<GiftRecord> = emptyList(),
)

/**
 * 每次演示独立创建的内存仓储。
 * 只接受本会话专用的负数标识；不持有数据库、文件、偏好设置或真实仓储。
 */
class GuidePracticeStore : GuidePracticeRepository {
    override val contactId = -101L
    override val recordId = -201L

    private val mutationLock = Any()
    private val mutableSnapshot = MutableStateFlow(GuidePracticeSnapshot())
    val snapshot: StateFlow<GuidePracticeSnapshot> = mutableSnapshot.asStateFlow()

    override val contactRepository: ContactRepository = object : ContactRepository {
        override fun observeContacts(query: String): Flow<List<Contact>> = snapshot.map { current ->
            current.contacts.filter { it.matches(query.trim()) }.sortedWith(contactOrder)
        }.distinctUntilChanged()

        override fun observeContactSummaries(query: String): Flow<List<ContactLedgerSummary>> =
            snapshot.map { current ->
                current.contacts.filter { it.matches(query.trim()) }.map { contact ->
                    val records = current.records.filter { it.contactId == contact.id }
                    ContactLedgerSummary(
                        contact = contact,
                        receivedInCents = records.filter { it.direction == GiftDirection.RECEIVED }
                            .sumOf(GiftRecord::amountInCents),
                        givenInCents = records.filter { it.direction == GiftDirection.GIVEN }
                            .sumOf(GiftRecord::amountInCents),
                        lastInteractionTime = records.maxOfOrNull(GiftRecord::eventDate)
                            ?: contact.createdTime,
                    )
                }.sortedWith(
                    compareByDescending<ContactLedgerSummary> { it.lastInteractionTime }
                        .thenByDescending { it.contact.createdTime }
                        .thenBy { it.contact.name.lowercase(Locale.ROOT) },
                )
            }.distinctUntilChanged()

        override fun observeContact(contactId: Long): Flow<Contact?> = snapshot.map { current ->
            current.contacts.firstOrNull { it.id == contactId }
        }.distinctUntilChanged()

        override suspend fun create(contact: Contact): Long = synchronized(mutationLock) {
            require(contact.id == 0L || contact.id == contactId) { "演示不能创建真实联系人标识" }
            check(mutableSnapshot.value.contacts.isEmpty()) { "演示联系人已经创建" }
            mutableSnapshot.value = mutableSnapshot.value.copy(
                contacts = listOf(contact.copy(id = contactId)),
            )
            contactId
        }

        override suspend fun createAll(contacts: List<Contact>): ContactImportResult =
            throw UnsupportedOperationException("演示不支持批量导入联系人")

        override suspend fun importDeviceContacts(
            selections: List<ContactImportSelection>,
        ): ContactImportResult = throw UnsupportedOperationException("演示不读取或导入设备联系人")

        override suspend fun update(contact: Contact) {
            synchronized(mutationLock) {
                requireOwnContactId(contact.id)
                val current = mutableSnapshot.value
                check(current.contacts.any { it.id == contact.id }) { "演示联系人不存在" }
                mutableSnapshot.value = current.copy(
                    contacts = current.contacts.map { if (it.id == contact.id) contact else it },
                )
            }
        }

        override suspend fun delete(contact: Contact) {
            synchronized(mutationLock) {
                requireOwnContactId(contact.id)
                val current = mutableSnapshot.value
                mutableSnapshot.value = current.copy(
                    contacts = current.contacts.filterNot { it.id == contact.id },
                    records = current.records.filterNot { it.contactId == contact.id },
                )
            }
        }

        override suspend fun previewDelete(ids: Set<Long>): ContactDeletePreview =
            synchronized(mutationLock) {
                ids.forEach(::requireOwnContactId)
                mutableSnapshot.value.deletePreview(ids)
            }

        override suspend fun deleteContacts(
            ids: Set<Long>,
            confirmed: ContactDeletePreview,
        ): ContactBulkDeleteOutcome = synchronized(mutationLock) {
            ids.forEach(::requireOwnContactId)
            val current = mutableSnapshot.value
            val actual = current.deletePreview(ids)
            if (actual != confirmed) {
                ContactBulkDeleteOutcome.Changed(actual)
            } else {
                val deletedIds = actual.recordsPerContact.keys
                mutableSnapshot.value = current.copy(
                    contacts = current.contacts.filterNot { it.id in deletedIds },
                    records = current.records.filterNot { it.contactId in deletedIds },
                )
                ContactBulkDeleteOutcome.Deleted(
                    BulkDeleteResult(actual.contactCount, actual.giftRecordCount),
                )
            }
        }
    }

    override val giftRecordRepository: GiftRecordRepository = object : GiftRecordRepository {
        override fun observeRecent(limit: Int): Flow<List<GiftRecordWithContact>> =
            snapshot.map { it.joinedRecords().take(limit.coerceAtLeast(0)) }.distinctUntilChanged()

        override fun observeAll(): Flow<List<GiftRecordWithContact>> =
            snapshot.map { it.joinedRecords() }.distinctUntilChanged()

        override fun observeYearlySummaries(): Flow<List<YearlyGiftSummary>> = snapshot.map { current ->
            val calendar = Calendar.getInstance()
            current.records.groupBy { record ->
                calendar.timeInMillis = record.eventDate
                calendar.get(Calendar.YEAR)
            }.map { (year, records) ->
                YearlyGiftSummary(
                    year,
                    records.filter { it.direction == GiftDirection.RECEIVED }.sumOf(GiftRecord::amountInCents),
                    records.filter { it.direction == GiftDirection.GIVEN }.sumOf(GiftRecord::amountInCents),
                )
            }.sortedByDescending(YearlyGiftSummary::year)
        }.distinctUntilChanged()

        override fun observeBetween(
            startInclusive: Long,
            endExclusive: Long,
        ): Flow<List<GiftRecordWithContact>> = snapshot.map { current ->
            current.joinedRecords().filter {
                it.record.eventDate >= startInclusive && it.record.eventDate < endExclusive
            }
        }.distinctUntilChanged()

        override fun observeByDirection(direction: GiftDirection): Flow<List<GiftRecordWithContact>> =
            snapshot.map { current -> current.joinedRecords().filter { it.record.direction == direction } }
                .distinctUntilChanged()

        override fun observeRecord(recordId: Long): Flow<GiftRecord?> = snapshot.map { current ->
            current.records.firstOrNull { it.id == recordId }
        }.distinctUntilChanged()

        override fun observeRecordWithContact(recordId: Long): Flow<GiftRecordWithContact?> =
            snapshot.map { current -> current.joinedRecords().firstOrNull { it.record.id == recordId } }
                .distinctUntilChanged()

        override fun observeByContact(contactId: Long): Flow<List<GiftRecord>> = snapshot.map { current ->
            current.records.filter { it.contactId == contactId }.sortedWith(recordOrder)
        }.distinctUntilChanged()

        override fun observeSearch(query: String): Flow<List<GiftRecordWithContact>> = snapshot.map { current ->
            val normalized = query.trim()
            val phones = current.contacts.associate { it.id to it.phone.orEmpty() }
            current.joinedRecords().filter { item ->
                item.contactName.contains(normalized, ignoreCase = true) ||
                    phones[item.record.contactId].orEmpty().contains(normalized, ignoreCase = true) ||
                    item.record.notes.orEmpty().contains(normalized, ignoreCase = true)
            }
        }.distinctUntilChanged()

        override suspend fun create(record: GiftRecord): Long = synchronized(mutationLock) {
            require(record.id == 0L || record.id == recordId) { "演示不能创建真实礼金标识" }
            requireOwnContactId(record.contactId)
            val current = mutableSnapshot.value
            check(current.contacts.any { it.id == record.contactId }) { "请先创建演示联系人" }
            check(current.records.isEmpty()) { "演示礼金已经创建" }
            mutableSnapshot.value = current.copy(records = listOf(record.copy(id = recordId)))
            recordId
        }

        override suspend fun update(record: GiftRecord) {
            synchronized(mutationLock) {
                requireOwnRecordId(record.id)
                requireOwnContactId(record.contactId)
                val current = mutableSnapshot.value
                check(current.contacts.any { it.id == record.contactId }) { "演示联系人不存在" }
                check(current.records.any { it.id == record.id }) { "演示礼金不存在" }
                mutableSnapshot.value = current.copy(
                    records = current.records.map { if (it.id == record.id) record else it },
                )
            }
        }

        override suspend fun delete(record: GiftRecord) {
            synchronized(mutationLock) {
                requireOwnRecordId(record.id)
                requireOwnContactId(record.contactId)
                val current = mutableSnapshot.value
                mutableSnapshot.value = current.copy(records = current.records.filterNot { it.id == record.id })
            }
        }
    }

    override fun clear() {
        synchronized(mutationLock) { mutableSnapshot.value = GuidePracticeSnapshot() }
    }

    private fun requireOwnContactId(id: Long) {
        require(id == contactId) { "演示只可操作本会话的联系人" }
    }

    private fun requireOwnRecordId(id: Long) {
        require(id == recordId) { "演示只可操作本会话的礼金" }
    }

    private fun Contact.matches(query: String): Boolean =
        listOf(name, phone.orEmpty(), relationship.orEmpty(), notes.orEmpty())
            .any { it.contains(query, ignoreCase = true) }

    private fun GuidePracticeSnapshot.deletePreview(ids: Set<Long>): ContactDeletePreview =
        ContactDeletePreview(
            contacts.filter { it.id in ids }.associate { contact ->
                contact.id to records.count { it.contactId == contact.id }
            },
        )

    private fun GuidePracticeSnapshot.joinedRecords(): List<GiftRecordWithContact> {
        val names = contacts.associate { it.id to it.name }
        return records.sortedWith(recordOrder).mapNotNull { record ->
            names[record.contactId]?.let { GiftRecordWithContact(record, it) }
        }
    }

    private val contactOrder = compareBy<Contact> { it.name.lowercase(Locale.ROOT) }
        .thenByDescending(Contact::createdTime)
    private val recordOrder = compareByDescending<GiftRecord> { it.eventDate }
        .thenByDescending(GiftRecord::createdTime)
}
