package com.yangsong.lizhang.domain.legal

import com.yangsong.lizhang.BuildConfig
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
                "AES-256-GCM", "专用扩展名和校验不等于加密", "此前已确认导入的礼账联系人不会自动删除", "同一全文",
                "独立提醒", "替换当前联系人和礼金", "未设置礼账自身的密码加密", "IP 地址", "未成年人"),
            LegalDocumentType.TERMS to listOf("手动添加联系人", "密码遗失", "替换当前联系人和礼金", "关联的礼金记录",
                "锁屏", "不承诺", "第三方", "未成年人"),
            LegalDocumentType.HELP to listOf("拒绝通知权限不影响记账", "当前礼金备份不包含独立提醒", "替换当前全部联系人",
                "不能通过礼账的恢复入口导入", "数据备份与恢复"),
        )
        required.forEach { (type, phrases) ->
            val text = File(sourceRoot, "assets/legal/${type.assetName}").readText()
            val document = LegalDocumentParser.parse(type, text)
            val displayed = document.sections.joinToString("\n") { "${it.heading.orEmpty()}\n${it.body}" }
            phrases.forEach { assertTrue("${type.name} 缺失完整说明：$it", displayed.contains(it)) }
            assertTrue(type.name, document.sections.size >= 8)
            if (!BuildConfig.IS_OFFICIAL_RELEASE && type == LegalDocumentType.HELP)
                assertTrue("候选帮助仍明确完整译文状态", displayed.contains("完整译文待审核"))
        }
    }

    @Test fun `表格逐行展开与强调链接处理不丢失完整内容`() {
        val document = LegalDocumentParser.parse(LegalDocumentType.PRIVACY, """
            # 隐私政策
            **尚未生效**
            ## 处理与保存
            | 数据 | 用途 | 保留 |
            | --- | --- | --- |
            | 联系人号码 | **选择导入** | 至你删除 |
            详情见[隐私政策](/privacy/)和[官方说明](https://example.invalid/privacy/)。
        """.trimIndent())
        assertEquals("尚未生效", document.sections.first().body)
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

    @Test fun `元数据与再次告知标识一致且候选与正式状态区分`() {
        val metadata = Properties().apply {
            File(sourceRoot, "assets/legal/metadata.properties").reader(Charsets.UTF_8).use(::load)
        }
        assertEquals(LegalPolicy.CURRENT_VERSION, metadata.getProperty("policy_version"))
        assertEquals(if (BuildConfig.IS_OFFICIAL_RELEASE) "approved" else "待批准", metadata.getProperty("approval_status"))
        for (key in listOf("operator_name", "contact_email", "effective_date", "filing_record", "reviewed_by", "minors_arrangement")) {
            if (BuildConfig.IS_OFFICIAL_RELEASE) {
                assertTrue(key, metadata.getProperty(key).isNotBlank())
                assertFalse(key, metadata.getProperty(key).contains("待本人确认"))
            } else assertEquals(key, "待本人确认", metadata.getProperty(key))
        }
        LegalDocumentType.entries.forEach { type ->
            val text = File(sourceRoot, "assets/legal/${type.assetName}").readText()
            assertTrue(type.name, text.contains(LegalPolicy.CURRENT_VERSION))
            assertEquals(type.name, !BuildConfig.IS_OFFICIAL_RELEASE, text.contains("尚未生效"))
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
