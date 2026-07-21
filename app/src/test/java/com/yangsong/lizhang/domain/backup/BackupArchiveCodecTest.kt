package com.yangsong.lizhang.domain.backup

import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupArchiveCodecTest {
    @Test
    fun `备份往返保留联系人与礼金记录全部字段`() {
        val archive = sampleArchive()

        val restored = BackupArchiveCodec.decode(BackupArchiveCodec.encode(archive))

        assertEquals(archive, restored)
    }

    @Test
    fun `备份内容被篡改时完整性校验失败`() {
        val bytes = BackupArchiveCodec.encode(sampleArchive())
        bytes[25] = (bytes[25].toInt() xor 1).toByte()

        assertThrows(InvalidBackupException::class.java) {
            BackupArchiveCodec.decode(bytes)
        }
    }

    @Test
    fun `礼金记录引用未知联系人时拒绝生成备份`() {
        val invalid = sampleArchive().copy(
            giftRecords = listOf(sampleArchive().giftRecords.single().copy(contactId = 999)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            BackupArchiveCodec.encode(invalid)
        }
    }

    private fun sampleArchive() = BackupArchive(
        createdTime = 1_752_830_645_000,
        contacts = listOf(
            Contact(
                id = 8,
                name = "王阿姨",
                phone = "13800000000",
                relationship = "亲友",
                notes = "住在杭州",
                createdTime = 1_700_000_000_000,
            ),
        ),
        giftRecords = listOf(
            GiftRecord(
                id = 12,
                contactId = 8,
                amountInCents = 88_800,
                eventType = EventType.WEDDING,
                eventDate = 1_752_787_200_000,
                direction = GiftDirection.RECEIVED,
                notes = "表姐婚礼",
                createdTime = 1_752_787_200_000,
            ),
        ),
    )
}
