package com.yangsong.lizhang.domain.legal

import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class LegalDocumentTest {
    private val sourceRoot = File("src/main")

    @Test fun `三份离线完整正文包含关键数据处理说明`() {
        val required = mapOf(
            LegalDocumentType.PRIVACY to listOf("READ_CONTACTS", "POST_NOTIFICATIONS", "PBKDF2-HMAC-SHA256",
                "AES-256-GCM", "CSV、Excel 和普通备份文件未加密", "撤回权限不会自动删除", "官网访问补充说明",
                "独立提醒", "替换设备中现有的联系人及礼金记录", "独立密码加密", "依法向有关主管部门投诉", "未成年人"),
            LegalDocumentType.TERMS to listOf("手动添加联系人", "遗失后我无法替你找回或解密", "替换设备中现有的全部联系人及礼金记录", "关联的礼金记录",
                "锁屏", "不能保证", "第三方", "未成年人"),
            LegalDocumentType.HELP to listOf("拒绝通知权限不影响记账", "礼金备份不包含独立提醒", "替换当前联系人和礼金记录",
                "不能通过礼账恢复", "完整正文为简体中文", "数据备份与恢复"),
        )
        required.forEach { (type, phrases) ->
            val text = File(sourceRoot, "assets/legal/${type.assetName}").readText()
            val document = LegalDocumentParser.parse(type, text)
            val displayed = document.sections.joinToString("\n") { "${it.heading.orEmpty()}\n${it.body}" }
            phrases.forEach { assertTrue("${type.name} 缺失完整说明：$it", displayed.contains(it)) }
            assertTrue(type.name, document.sections.size >= 8)
        }
    }

    @Test fun `表格逐行展开与强调链接处理不丢失完整内容`() {
        val document = LegalDocumentParser.parse(LegalDocumentType.PRIVACY, """
            # 隐私政策
            **版本：1.0.0**
            ## 处理与保存
            | 数据 | 用途 | 保留 |
            | --- | --- | --- |
            | 联系人号码 | **选择导入** | 至你删除 |
            详情见[隐私政策](/privacy/)和[官方说明](https://example.invalid/privacy/)。
        """.trimIndent())
        assertEquals("版本：1.0.0", document.sections.first().body)
        val body = document.sections.last().body
        assertTrue(body.contains("联系人号码：选择导入：至你删除"))
        assertTrue(body.contains("https://example.invalid/privacy/"))
        assertFalse(body.contains("**"))
        assertFalse(body.contains("| ---"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `空正文不能被当作可阅读文档`() {
        LegalDocumentParser.parse(LegalDocumentType.PRIVACY, "# 隐私政策\n")
    }

    @Test fun `所有应用语言共用完整中文正文且七语言提示键占位符完整`() {
        assertEquals(setOf("zh-CN", "zh-Hant", "en", "ja", "ko", "es", "fr"), LegalDocumentLanguage.supportedUiLanguages)
        assertEquals("zh-CN", LegalDocumentLanguage.CONTENT_LANGUAGE)
        val placeholder = Regex("%[0-9]+\\\$[0-9]*[ds]")
        val base = legalStrings("")
        for (qualifier in listOf("-b+zh+Hant", "-en", "-ja", "-ko", "-es", "-fr")) {
            val translated = legalStrings(qualifier)
            assertEquals(qualifier, base.keys, translated.keys)
            assertTrue(qualifier, translated.values.all { it.isNotBlank() })
            base.forEach { (key, value) ->
                assertEquals("$qualifier/$key", placeholder.findAll(value).map { it.value }.toList(),
                    placeholder.findAll(translated.getValue(key)).map { it.value }.toList())
            }
        }
    }

    @Test fun `内部审批和告知版本不混入公开法律正文`() {
        val metadata = Properties().apply {
            File(sourceRoot, "assets/legal/metadata.properties").reader(Charsets.UTF_8).use(::load)
        }
        assertEquals(LegalPolicy.CURRENT_VERSION, metadata.getProperty("policy_version"))
        assertEquals("pending", metadata.getProperty("approval_status"))
        assertEquals("杨松", metadata.getProperty("operator_name"))
        assertEquals("3173887065@qq.com", metadata.getProperty("contact_email"))
        assertEquals("渝ICP备2026025309号-1", metadata.getProperty("filing_record"))
        assertEquals("pending", metadata.getProperty("effective_date"))
        assertFalse(metadata.containsKey("operator_address"))
        LegalDocumentType.entries.forEach { type ->
            val text = File(sourceRoot, "assets/legal/${type.assetName}").readText()
            assertFalse(type.name, text.contains(LegalPolicy.CURRENT_VERSION))
            assertFalse(type.name, text.contains("{{publication_status}}"))
            assertFalse(type.name, text.contains("尚未生效"))
            if (type != LegalDocumentType.HELP) {
                assertTrue(type.name, text.contains("版本：1.0.0"))
                assertTrue(type.name, text.contains("{{effective_date}}"))
            }
        }
    }

    @Test fun `官网链接只预留固定HTTPS路径且App无联网权限`() {
        assertEquals(listOf("https://lizhang.songisle.xyz/privacy/", "https://lizhang.songisle.xyz/terms/",
            "https://lizhang.songisle.xyz/help/"), LegalDocumentType.entries.map { it.officialUrl })
        assertFalse(File(sourceRoot, "AndroidManifest.xml").readText().contains("android.permission.INTERNET"))
    }

    private fun legalStrings(qualifier: String): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(sourceRoot, "res/values$qualifier/legal_strings.xml")).documentElement
        val entries = (0 until root.childNodes.length).mapNotNull { root.childNodes.item(it) as? Element }
        assertEquals(entries.size, entries.map { it.getAttribute("name") }.toSet().size)
        return entries.associate { it.getAttribute("name") to it.textContent }
    }
}
