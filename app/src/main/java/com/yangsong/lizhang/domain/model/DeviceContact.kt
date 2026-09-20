package com.yangsong.lizhang.domain.model

/** 仅用于本机导入预览，不持久化系统联系人标识。 */
data class DeviceContact(val deviceContactId: Long, val name: String, val phone: String)

data class ContactImportResult(val imported: Int, val skipped: Int)
