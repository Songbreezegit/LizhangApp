package com.yangsong.lizhang.domain.repository

data class BackupSummary(
    val createdTime: Long,
    val contactCount: Int,
    val giftRecordCount: Int,
)

data class BackupDocument(
    val bytes: ByteArray,
    val summary: BackupSummary,
)

interface BackupRepository {
    suspend fun createBackup(): BackupDocument
    fun inspectBackup(bytes: ByteArray): BackupSummary
    suspend fun restoreBackup(bytes: ByteArray): BackupSummary
}
