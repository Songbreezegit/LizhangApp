package com.yangsong.lizhang.domain.contact

import com.yangsong.lizhang.domain.model.DeviceContact

/** 导入预览与事务内去重共用同一套号码规则。 */
object ContactImportRules {
    private val number = Regex("\\+?[0-9]{3,15}")
    private val mainlandMobile = Regex("\\+861[3-9][0-9]{9}")

    fun normalizePhone(value: String?): String? {
        val cleaned = value.orEmpty().filterNot {
            it.isWhitespace() || Character.isSpaceChar(it) || it in "-()."
        }
        if (!number.matches(cleaned)) return null
        return if (mainlandMobile.matches(cleaned)) cleaned.removePrefix("+86") else cleaned
    }

    fun candidates(contacts: List<DeviceContact>): List<DeviceContact> = contacts.asSequence()
        .mapNotNull { contact ->
            val name = contact.name.trim()
            val phone = normalizePhone(contact.phone)
            if (name.isEmpty() || phone == null) null else contact.copy(name = name, phone = phone)
        }
        .distinctBy { it.phone }
        .sortedWith(compareBy({ it.name }, { it.phone }))
        .toList()

    fun maskPhone(phone: String): String = when {
        phone.length <= 4 -> "*".repeat(phone.length)
        phone.length <= 7 -> phone.take(1) + "****" + phone.takeLast(1)
        else -> phone.take(3) + "****" + phone.takeLast(4)
    }
}
