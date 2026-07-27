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
    suspend fun createBackup(password: String? = null): BackupDocument
    fun requiresPassword(bytes: ByteArray): Boolean
    fun inspectBackup(bytes: ByteArray, password: String? = null): BackupSummary
    suspend fun restoreBackup(bytes: ByteArray, password: String? = null): BackupSummary
}
