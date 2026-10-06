package com.yangsong.lizhang.ui.viewmodel

import com.yangsong.lizhang.domain.contact.ContactImportRules
import com.yangsong.lizhang.domain.model.DeviceContact
import org.junit.Assert.*
import org.junit.Test

class ContactImportRulesTest {
    @Test fun `号码匹配优先于姓名匹配且空号码同名也为可能重复`() {
        val index = ContactImportRules.DuplicateIndex(listOf(
            com.yangsong.lizhang.domain.model.Contact(name = "测试同名", phone = "+86 138 0000 0000"),
            com.yangsong.lizhang.domain.model.Contact(name = "  测试无号码  ", phone = null),
        ))
        assertEquals(com.yangsong.lizhang.domain.model.ContactImportStatus.EXISTING, index.status(DeviceContact(1, "另一姓名", "138-0000-0000")))
        assertEquals(com.yangsong.lizhang.domain.model.ContactImportStatus.POSSIBLE_DUPLICATE, index.status(DeviceContact(2, "测试同名", "13900000000")))
        assertEquals(com.yangsong.lizhang.domain.model.ContactImportStatus.POSSIBLE_DUPLICATE, index.status(DeviceContact(3, "测试无号码", "13700000000")))
        assertEquals(com.yangsong.lizhang.domain.model.ContactImportStatus.NEW, index.status(DeviceContact(4, "测试新增", "+12025550123")))
    }
    @Test fun `大陆手机归一化但不剥离其他中国号码国家码`() {
        listOf("138 0000 0000", "138-0000-0000", "13800000000", "+86 (138) 0000-0000")
            .forEach { assertEquals("13800000000", ContactImportRules.normalizePhone(it)) }
        assertEquals("+861012345678", ContactImportRules.normalizePhone("+86 10 1234 5678"))
    }
    @Test fun `国际号码保留国家码`() {
        assertEquals("+12025550123", ContactImportRules.normalizePhone("+1 (202) 555-0123"))
        assertEquals("+442079460000", ContactImportRules.normalizePhone("+44 20 7946 0000"))
        assertEquals("02079460000", ContactImportRules.normalizePhone("020 7946 0000"))
    }
    @Test fun `空号码及格式错误号码排除`() {
        listOf(null, "", "   ", "--()", "abc", "+", "12", "1+234", "123#456")
            .forEach { assertNull(ContactImportRules.normalizePhone(it)) }
    }
    @Test fun `过滤空姓名按号码去重并保留同一联系人的多个号码`() {
        val result = ContactImportRules.candidates(listOf(
            DeviceContact(1, "  测试甲  ", "+86 13800000000"),
            DeviceContact(1, "测试甲", "+1 202 555 0123"),
            DeviceContact(2, "测试重复", "138-0000-0000"),
            DeviceContact(3, "   ", "13900000000"),
            DeviceContact(4, "无号码", ""),
        ))
        assertEquals(2, result.size)
        assertTrue(result.all { it.name == "测试甲" })
        assertEquals(setOf("13800000000", "+12025550123"), result.map { it.phone }.toSet())
    }
    @Test fun `短号码和国际号码也不会完整展示`() {
        assertEquals("138****0000", ContactImportRules.maskPhone("13800000000"))
        assertEquals("+12****0123", ContactImportRules.maskPhone("+12025550123"))
        assertEquals("***", ContactImportRules.maskPhone("123"))
        assertEquals("1****5", ContactImportRules.maskPhone("12345"))
    }
}
