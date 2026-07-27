package com.yangsong.lizhang.data.repository

import androidx.room.withTransaction
import com.yangsong.lizhang.data.local.LiZhangDatabase
import com.yangsong.lizhang.data.mapper.toDomain
import com.yangsong.lizhang.data.mapper.toEntity
import com.yangsong.lizhang.domain.backup.BackupArchive
import com.yangsong.lizhang.domain.backup.BackupArchiveCodec
import com.yangsong.lizhang.domain.backup.BackupEncryptionCodec
import com.yangsong.lizhang.domain.backup.BackupPasswordRequiredException
import com.yangsong.lizhang.domain.repository.BackupDocument
import com.yangsong.lizhang.domain.repository.BackupRepository
import com.yangsong.lizhang.domain.repository.BackupSummary

class RoomBackupRepository(
    private val database: LiZhangDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : BackupRepository {
    override suspend fun createBackup(password: String?): BackupDocument {
        val archive = database.withTransaction {
            BackupArchive(
                createdTime = now(),
                contacts = database.contactDao().getAllForBackup().map { it.toDomain() },
                giftRecords = database.giftRecordDao().getAllForBackup().map { it.toDomain() },
            )
        }
        val plainBytes = BackupArchiveCodec.encode(archive)
        val documentBytes = password
            ?.takeIf { it.isNotEmpty() }
            ?.let { BackupEncryptionCodec.encrypt(plainBytes, it) }
            ?: plainBytes
        return BackupDocument(documentBytes, archive.toSummary())
    }

    override fun requiresPassword(bytes: ByteArray): Boolean = BackupEncryptionCodec.isEncrypted(bytes)

    override fun inspectBackup(bytes: ByteArray, password: String?): BackupSummary =
        decode(bytes, password).toSummary()

    override suspend fun restoreBackup(bytes: ByteArray, password: String?): BackupSummary {
        val archive = decode(bytes, password)
        database.withTransaction {
            database.giftRecordDao().deleteAll()
            database.contactDao().deleteAll()
            database.contactDao().insertAll(archive.contacts.map { it.toEntity() })
            database.giftRecordDao().insertAll(archive.giftRecords.map { it.toEntity() })
        }
        return archive.toSummary()
    }

    private fun decode(bytes: ByteArray, password: String?): BackupArchive {
        val plainBytes = if (BackupEncryptionCodec.isEncrypted(bytes)) {
            val actualPassword = password ?: throw BackupPasswordRequiredException()
            BackupEncryptionCodec.decrypt(bytes, actualPassword)
        } else {
            bytes
        }
        return BackupArchiveCodec.decode(plainBytes)
    }

    private fun BackupArchive.toSummary() = BackupSummary(
        createdTime = createdTime,
        contactCount = contacts.size,
        giftRecordCount = giftRecords.size,
    )
}
