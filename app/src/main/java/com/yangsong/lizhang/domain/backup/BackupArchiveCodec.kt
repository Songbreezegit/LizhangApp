package com.yangsong.lizhang.domain.backup

import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

data class BackupArchive(
    val createdTime: Long,
    val contacts: List<Contact>,
    val giftRecords: List<GiftRecord>,
)

class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 礼账专用备份编解码器。
 *
 * 文件只保存领域数据，不绑定 SQLite 物理文件，便于后续数据库迁移。
 */
object BackupArchiveCodec {
    const val FILE_EXTENSION = "lizhangbackup"
    const val MIME_TYPE = "application/octet-stream"
    const val FORMAT_VERSION = 1
    const val MAX_FILE_BYTES = 32 * 1024 * 1024 + 51

    private val magic = "LIZHANG-BACKUP\n".toByteArray(Charsets.US_ASCII)
    private const val digestSize = 32
    private const val maxFileSize = 32 * 1024 * 1024
    private const val maxContacts = 100_000
    private const val maxGiftRecords = 1_000_000
    private const val maxStringBytes = 1024 * 1024

    fun encode(archive: BackupArchive): ByteArray {
        validate(archive)
        val body = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(FORMAT_VERSION)
                output.writeLong(archive.createdTime)
                output.writeInt(archive.contacts.size)
                archive.contacts.forEach { contact ->
                    output.writeLong(contact.id)
                    output.writeString(contact.name)
                    output.writeNullableString(contact.phone)
                    output.writeNullableString(contact.relationship)
                    output.writeNullableString(contact.notes)
                    output.writeLong(contact.createdTime)
                }
                output.writeInt(archive.giftRecords.size)
                archive.giftRecords.forEach { record ->
                    output.writeLong(record.id)
                    output.writeLong(record.contactId)
                    output.writeLong(record.amountInCents)
                    output.writeString(record.eventType.name)
                    output.writeLong(record.eventDate)
                    output.writeString(record.direction.name)
                    output.writeNullableString(record.notes)
                    output.writeLong(record.createdTime)
                }
            }
            buffer.toByteArray()
        }
        require(body.size <= maxFileSize) { "备份数据超过大小限制" }

        return ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.write(magic)
                output.writeInt(body.size)
                output.write(body)
                output.write(body.sha256())
            }
            buffer.toByteArray()
        }
    }

    @Throws(InvalidBackupException::class)
    fun decode(bytes: ByteArray): BackupArchive {
        if (bytes.size > MAX_FILE_BYTES) {
            throw InvalidBackupException("备份文件超过大小限制")
        }
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val actualMagic = ByteArray(magic.size).also(input::readFully)
                if (!actualMagic.contentEquals(magic)) throw InvalidBackupException("不是有效的礼账备份文件")
                val bodySize = input.readInt()
                if (bodySize <= 0 || bodySize > maxFileSize) throw InvalidBackupException("备份文件大小无效")
                val body = ByteArray(bodySize).also(input::readFully)
                val expectedDigest = ByteArray(digestSize).also(input::readFully)
                if (!MessageDigest.isEqual(expectedDigest, body.sha256())) {
                    throw InvalidBackupException("备份文件校验失败，文件可能已损坏")
                }
                if (input.read() != -1) throw InvalidBackupException("备份文件包含未知尾部数据")
                decodeBody(body).also(::validate)
            }
        } catch (error: InvalidBackupException) {
            throw error
        } catch (error: Exception) {
            throw InvalidBackupException("备份文件无法解析", error)
        }
    }

    private fun decodeBody(body: ByteArray): BackupArchive =
        DataInputStream(ByteArrayInputStream(body)).use { input ->
            val version = input.readInt()
            if (version != FORMAT_VERSION) throw InvalidBackupException("暂不支持此备份版本：$version")
            val createdTime = input.readLong()
            val contacts = List(input.readCount(maxContacts, "联系人")) {
                Contact(
                    id = input.readLong(),
                    name = input.readString(),
                    phone = input.readNullableString(),
                    relationship = input.readNullableString(),
                    notes = input.readNullableString(),
                    createdTime = input.readLong(),
                )
            }
            val records = List(input.readCount(maxGiftRecords, "礼金记录")) {
                GiftRecord(
                    id = input.readLong(),
                    contactId = input.readLong(),
                    amountInCents = input.readLong(),
                    eventType = input.readEnum<EventType>("事件类型"),
                    eventDate = input.readLong(),
                    direction = input.readEnum<GiftDirection>("往来方向"),
                    notes = input.readNullableString(),
                    createdTime = input.readLong(),
                )
            }
            if (input.read() != -1) throw InvalidBackupException("备份数据包含未知字段")
            BackupArchive(createdTime, contacts, records)
        }

    private fun validate(archive: BackupArchive) {
        require(archive.createdTime > 0) { "备份生成时间无效" }
        require(archive.contacts.size <= maxContacts) { "联系人数量超过限制" }
        require(archive.giftRecords.size <= maxGiftRecords) { "礼金记录数量超过限制" }
        val contactIds = archive.contacts.map { contact ->
            require(contact.id > 0) { "联系人主键无效" }
            require(contact.name.isNotBlank()) { "联系人姓名不能为空" }
            contact.id
        }.toSet()
        require(contactIds.size == archive.contacts.size) { "联系人主键重复" }
        val recordIds = archive.giftRecords.map { record ->
            require(record.id > 0) { "礼金记录主键无效" }
            require(record.contactId in contactIds) { "礼金记录关联了不存在的联系人" }
            require(record.amountInCents > 0) { "礼金金额必须大于零" }
            record.id
        }.toSet()
        require(recordIds.size == archive.giftRecords.size) { "礼金记录主键重复" }
    }

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= maxStringBytes) { "文本字段超过长度限制" }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        if (value == null) writeInt(-1) else writeString(value)
    }

    private fun DataInputStream.readCount(limit: Int, label: String): Int =
        readInt().also { if (it !in 0..limit) throw InvalidBackupException("$label 数量无效") }

    private fun DataInputStream.readString(): String {
        val size = readInt()
        if (size !in 0..maxStringBytes) throw InvalidBackupException("文本字段长度无效")
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }

    private fun DataInputStream.readNullableString(): String? {
        val size = readInt()
        if (size == -1) return null
        if (size !in 0..maxStringBytes) throw InvalidBackupException("文本字段长度无效")
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }

    private inline fun <reified T : Enum<T>> DataInputStream.readEnum(label: String): T =
        try {
            enumValueOf<T>(readString())
        } catch (error: IllegalArgumentException) {
            throw InvalidBackupException("$label 无效", error)
        }

    private fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)
}
