package com.yangsong.lizhang.domain.contact

import com.yangsong.lizhang.domain.model.DeviceContact
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.ContactImportStatus

/** 导入预览与事务内去重共用同一套号码规则。 */
object ContactImportRules {
    class DuplicateIndex(contacts: List<Contact>) {
        private val phones = contacts.mapNotNull { normalizePhone(it.phone) }.toSet()
        private val names = contacts.map { it.name.trim() }.filter { it.isNotEmpty() }.toSet()

        fun status(contact: DeviceContact): ContactImportStatus = when {
            normalizePhone(contact.phone) in phones -> ContactImportStatus.EXISTING
            contact.name.trim() in names -> ContactImportStatus.POSSIBLE_DUPLICATE
            else -> ContactImportStatus.NEW
        }
    }

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
