package com.yangsong.lizhang.data.repository

import androidx.room.withTransaction
import com.yangsong.lizhang.data.local.LiZhangDatabase
import com.yangsong.lizhang.data.mapper.toDomain
import com.yangsong.lizhang.data.mapper.toEntity
import com.yangsong.lizhang.domain.backup.BackupArchive
import com.yangsong.lizhang.domain.backup.BackupArchiveCodec
import com.yangsong.lizhang.domain.repository.BackupDocument
import com.yangsong.lizhang.domain.repository.BackupRepository
import com.yangsong.lizhang.domain.repository.BackupSummary

class RoomBackupRepository(
    private val database: LiZhangDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : BackupRepository {
    override suspend fun createBackup(): BackupDocument {
        val archive = database.withTransaction {
            BackupArchive(
                createdTime = now(),
                contacts = database.contactDao().getAllForBackup().map { it.toDomain() },
                giftRecords = database.giftRecordDao().getAllForBackup().map { it.toDomain() },
            )
        }
        return BackupDocument(BackupArchiveCodec.encode(archive), archive.toSummary())
    }

    override fun inspectBackup(bytes: ByteArray): BackupSummary = BackupArchiveCodec.decode(bytes).toSummary()

    override suspend fun restoreBackup(bytes: ByteArray): BackupSummary {
        val archive = BackupArchiveCodec.decode(bytes)
        database.withTransaction {
            database.giftRecordDao().deleteAll()
            database.contactDao().deleteAll()
            database.contactDao().insertAll(archive.contacts.map { it.toEntity() })
            database.giftRecordDao().insertAll(archive.giftRecords.map { it.toEntity() })
        }
        return archive.toSummary()
    }

    private fun BackupArchive.toSummary() = BackupSummary(
        createdTime = createdTime,
        contactCount = contacts.size,
        giftRecordCount = giftRecords.size,
    )
}
