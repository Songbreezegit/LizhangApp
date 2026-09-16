package com.yangsong.lizhang.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yangsong.lizhang.data.local.entity.GiftRecordEntity
import com.yangsong.lizhang.data.local.projection.GiftRecordWithContactRow
import com.yangsong.lizhang.data.local.projection.YearlyGiftSummaryRow
import com.yangsong.lizhang.domain.model.GiftDirection
import kotlinx.coroutines.flow.Flow

@Dao
interface GiftRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: GiftRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(records: List<GiftRecordEntity>)

    @Update
    suspend fun update(record: GiftRecordEntity)

    @Delete
    suspend fun delete(record: GiftRecordEntity)

    @Query("SELECT * FROM gift_records ORDER BY id")
    suspend fun getAllForBackup(): List<GiftRecordEntity>

    @Query("DELETE FROM gift_records")
    suspend fun deleteAll()

    @Query("SELECT * FROM gift_records WHERE contactId = :contactId ORDER BY eventDate DESC, createdTime DESC")
    fun observeByContact(contactId: Long): Flow<List<GiftRecordEntity>>

    @Query("SELECT * FROM gift_records WHERE id = :recordId LIMIT 1")
    fun observeById(recordId: Long): Flow<GiftRecordEntity?>

    @Query(
        """
        SELECT gift_records.*, contacts.name AS contactName
        FROM gift_records
        INNER JOIN contacts ON contacts.id = gift_records.contactId
        WHERE gift_records.id = :recordId
        LIMIT 1
        """,
    )
    fun observeWithContactById(recordId: Long): Flow<GiftRecordWithContactRow?>

    @Query(
        """
        SELECT gift_records.*, contacts.name AS contactName
        FROM gift_records
        INNER JOIN contacts ON contacts.id = gift_records.contactId
        ORDER BY gift_records.eventDate DESC, gift_records.createdTime DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<GiftRecordWithContactRow>>

    @Query(
        """
        SELECT gift_records.*, contacts.name AS contactName
        FROM gift_records
        INNER JOIN contacts ON contacts.id = gift_records.contactId
        ORDER BY gift_records.eventDate DESC, gift_records.createdTime DESC
        """,
    )
    fun observeAll(): Flow<List<GiftRecordWithContactRow>>

    @Query(
        """
        SELECT CAST(strftime('%Y', eventDate / 1000, 'unixepoch', 'localtime') AS INTEGER) AS year,
            COALESCE(SUM(CASE WHEN direction = 'RECEIVED' THEN amountInCents ELSE 0 END), 0) AS receivedInCents,
            COALESCE(SUM(CASE WHEN direction = 'GIVEN' THEN amountInCents ELSE 0 END), 0) AS givenInCents
        FROM gift_records
        GROUP BY strftime('%Y', eventDate / 1000, 'unixepoch', 'localtime')
        ORDER BY year DESC
        """,
    )
    fun observeYearlySummaries(): Flow<List<YearlyGiftSummaryRow>>

    @Query(
        """
        SELECT gift_records.*, contacts.name AS contactName
        FROM gift_records
        INNER JOIN contacts ON contacts.id = gift_records.contactId
        WHERE gift_records.eventDate >= :startInclusive
          AND gift_records.eventDate < :endExclusive
        ORDER BY gift_records.eventDate DESC, gift_records.createdTime DESC
        """,
    )
    fun observeBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<GiftRecordWithContactRow>>

    @Query(
        """
        SELECT gift_records.*, contacts.name AS contactName
        FROM gift_records
        INNER JOIN contacts ON contacts.id = gift_records.contactId
        WHERE gift_records.direction = :direction
        ORDER BY gift_records.eventDate DESC, gift_records.createdTime DESC
        """,
    )
    fun observeByDirection(direction: GiftDirection): Flow<List<GiftRecordWithContactRow>>

    @Query(
        """
        SELECT gift_records.*, contacts.name AS contactName
        FROM gift_records
        INNER JOIN contacts ON contacts.id = gift_records.contactId
        WHERE contacts.name LIKE '%' || :query || '%' COLLATE NOCASE
           OR contacts.phone LIKE '%' || :query || '%' COLLATE NOCASE
           OR gift_records.notes LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY gift_records.eventDate DESC, gift_records.createdTime DESC
        """,
    )
    fun observeSearch(query: String): Flow<List<GiftRecordWithContactRow>>
}
