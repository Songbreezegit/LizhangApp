package com.yangsong.lizhang.domain.model

/** 按联系人保存影响数量，确认后任何数量变化均需重新确认。 */
data class ContactDeletePreview(val recordsPerContact: Map<Long, Int>) {
    val contactCount: Int get() = recordsPerContact.size
    val contactsWithGiftRecords: Int get() = recordsPerContact.count { it.value > 0 }
    val giftRecordCount: Int get() = recordsPerContact.values.sum()
}

data class BulkDeleteResult(val deletedContacts: Int, val deletedGiftRecords: Int)

sealed interface ContactBulkDeleteOutcome {
    data class Deleted(val result: BulkDeleteResult) : ContactBulkDeleteOutcome
    data class Changed(val preview: ContactDeletePreview) : ContactBulkDeleteOutcome
}
