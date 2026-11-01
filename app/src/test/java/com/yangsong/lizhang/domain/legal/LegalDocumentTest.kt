package com.yangsong.lizhang.domain.legal

import java.io.File
import java.time.LocalDate
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class LegalDocumentTest {
    private val sourceRoot = File("src/main")

    @Test fun `三份离线完整正文包含关键数据处理说明`() {
        val required = mapOf(
            LegalDocumentType.PRIVACY to listOf("通讯录权限", "通知权限", "开机后恢复提醒", "PBKDF2-HMAC-SHA256",
                "AES-256-GCM", "CSV、Excel 和普通备份文件未加密", "撤回权限不会自动删除", "官网访问补充说明",
                "独立提醒", "替换设备中现有的联系人及礼金记录", "独立密码加密", "依法向有关主管部门投诉", "未成年人"),
            LegalDocumentType.TERMS to listOf("手动添加联系人", "遗失后无法找回或解密", "替换设备中现有的全部联系人及礼金记录", "关联的礼金记录",
                "锁屏", "通知权限", "省电设置", "第三方", "未成年人"),
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

    @Test fun `权限说明保留主动授权字段范围拒绝行为及撤回后的记录`() {
        val document = LegalDocumentParser.parse(LegalDocumentType.PRIVACY,
            File(sourceRoot, "assets/legal/privacy.md").readText())
        val permissions = document.sections.single { it.heading == "二、权限与用途" }
        val contacts = permissions.body.substringAfter("通讯录权限").substringBefore("通知权限")
        listOf("主动选择“通讯录导入”并授权后", "读取通讯录", "拒绝授权后，仍可手动添加联系人",
            "在本机", "有电话号码的条目", "姓名、完整号码和系统联系人标识", "预览、搜索及去重",
            "只保存你选中的姓名和完整号码", "不保存系统联系人标识", "号码遮罩不改变实际保存的完整号码",
            "系统通讯录不会被修改").forEach {
            assertTrue("通讯录说明缺失：$it", contacts.contains(it))
        }
        val notifications = permissions.body.substringAfter("通知权限").substringBefore("开机后恢复提醒")
        listOf("开启提醒通知时", "按系统要求申请通知权限", "拒绝通知权限不影响联系人管理和礼金记账",
            "提醒名称和日期", "Android 系统显示", "可能出现在锁屏上").forEach {
            assertTrue("通知说明缺失：$it", notifications.contains(it))
        }
        val restart = permissions.body.substringAfter("开机后恢复提醒")
        listOf("设备重启后", "重新安排已启用的提醒", "系统设置中撤回通讯录或通知权限",
            "撤回权限不会自动删除已保存到礼账的记录").forEach {
            assertTrue("重启与撤回说明缺失：$it", restart.contains(it))
        }
    }

    @Test fun `公开正文使用自然语言权限名称且开头邮箱去重不影响联系与权利说明`() {
        val introductions = mapOf(
            LegalDocumentType.PRIVACY to "礼账用于记录礼金往来、管理联系人和日期提醒。以下说明这些信息的用途、保存方式及你的管理方式。",
            LegalDocumentType.TERMS to "本协议适用于礼账 Android 应用。使用前请阅读本协议和《礼账隐私说明》。",
        )
        LegalDocumentType.entries.forEach { type ->
            val markdown = File(sourceRoot, "assets/legal/${type.assetName}").readText()
            val displayed = LegalDocumentParser.parse(type, markdown).sections.joinToString("\n") { it.body }
            listOf("READ_CONTACTS", "POST_NOTIFICATIONS", "RECEIVE_BOOT_COMPLETED", "INTERNET").forEach {
                assertFalse("${type.name} 原文不应包含权限常量：$it", markdown.contains(it))
                assertFalse("${type.name} 展示不应包含权限常量：$it", displayed.contains(it))
            }
            introductions[type]?.let { introduction ->
                val opening = markdown.substringAfter("生效日期：{{effective_date}}").substringBefore("\n## ").trim()
                assertEquals("${type.name} 开头不再重复邮箱", introduction, opening)
                val contact = markdown.substringAfterLast("\n## ")
                assertTrue("${type.name} 末尾保留联系邮箱", contact.contains("联系邮箱：**{{contact_email}}**"))
                assertTrue("${type.name} 末尾保留官方网站", contact.contains("官方网站：**https://{{website_domain}}**"))
            }
        }
        val privacy = File(sourceRoot, "assets/legal/privacy.md").readText()
        val mail = privacy.substringAfter("## 六、官网与邮件").substringBefore("## 七、")
        listOf("{{contact_email}}", "发件邮箱、邮件内容", "处理和回复该事项", "邮件服务商", "期限内保存").forEach {
            assertTrue("反馈邮件处理说明缺失：$it", mail.contains(it))
        }
        val rights = privacy.substringAfter("## 七、信息管理与权利").substringBefore("## 八、")
        listOf("查看、更正和删除", "导出或备份取得副本", "撤回权限", "权利请求", "{{contact_email}}",
            "必要核实后依法及时处理", "依法向有关主管部门投诉").forEach {
            assertTrue("信息权利说明缺失：$it", rights.contains(it))
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

    @Test fun `小标题段落及列表保留结构与原编号且显示文本没有Markdown标记`() {
        val document = LegalDocumentParser.parse(LegalDocumentType.PRIVACY, """
            # 隐私说明
            版本：1.0.0
            ## 权限与用途
            ### **通讯录权限**
            仅在主动导入时读取，`预览`保留完整号码。

            拒绝后仍可手动添加联系人。

            - **预览**不修改系统通讯录。
            - 只保存所选姓名和号码。

            3. 核对导入选择。
            4. 确认保存。
        """.trimIndent())
        val section = document.sections.last()
        assertEquals("权限与用途", section.heading)
        assertEquals(listOf(
            LegalDocumentBlock.Heading("通讯录权限", 3),
            LegalDocumentBlock.Paragraph("仅在主动导入时读取，预览保留完整号码。"),
            LegalDocumentBlock.Paragraph("拒绝后仍可手动添加联系人。"),
            LegalDocumentBlock.ListItem("预览不修改系统通讯录。", "•"),
            LegalDocumentBlock.ListItem("只保存所选姓名和号码。", "•"),
            LegalDocumentBlock.ListItem("核对导入选择。", "3."),
            LegalDocumentBlock.ListItem("确认保存。", "4."),
        ), section.blocks)
        listOf("#", "**", "`", "- ").forEach { assertFalse("不应显示 Markdown 标记：$it", section.body.contains(it)) }
        assertTrue(section.body.contains("• 预览不修改系统通讯录。"))
        assertTrue(section.body.contains("3. 核对导入选择。"))
        assertTrue(section.body.contains("4. 确认保存。"))
        assertTrue(section.body.contains("预览保留完整号码。\n\n拒绝后仍可手动添加联系人。"))
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
        val approval = Properties().apply {
            File(sourceRoot, "../../../release/legal-approval.properties").reader(Charsets.UTF_8).use(::load)
        }
        assertEquals(LegalPolicy.CURRENT_VERSION, metadata.getProperty("policy_version"))
        assertEquals(LegalPolicy.CURRENT_VERSION, approval.getProperty("policy_version"))
        val approvalStatus = approval.getProperty("approval_status")
        assertTrue("法律正文只能处于待审阅或已批准状态", approvalStatus in setOf("pending", "approved"))
        assertEquals(approvalStatus, metadata.getProperty("approval_status"))
        val operatorName = metadata.getProperty("operator_name").orEmpty()
        assertTrue("内部登记资料应保留核对来源", operatorName.isNotBlank())
        assertEquals(approval.getProperty("operator_name"), operatorName)
        assertEquals("3173887065@qq.com", metadata.getProperty("contact_email"))
        assertEquals("渝ICP备2026025309号-1", metadata.getProperty("filing_record"))
        assertEquals(approval.getProperty("filing_record"), metadata.getProperty("filing_record"))
        val effectiveDate = metadata.getProperty("effective_date").orEmpty()
        assertEquals(approval.getProperty("effective_date"), effectiveDate)
        assertEquals(approval.getProperty("reviewed_by"), metadata.getProperty("reviewed_by"))
        if (approvalStatus == "approved") {
            assertTrue("生效日期必须为 YYYY-MM-DD", effectiveDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
            assertEquals("生效日期必须是有效公历日期", effectiveDate, LocalDate.parse(effectiveDate).toString())
            assertTrue("已批准内容必须有摘要", approval.getProperty("approved_content_sha256").orEmpty()
                .matches(Regex("[a-fA-F0-9]{64}")))
        } else {
            listOf("effective_date", "reviewed_by", "approved_at", "approved_content_sha256").forEach { key ->
                assertEquals("新版正文不能沿用旧批准：$key", "pending", approval.getProperty(key))
            }
            assertEquals("pending", metadata.getProperty("reviewed_by"))
        }
        assertFalse(metadata.containsKey("operator_address"))
        val unapprovedText = Regex(
            File(sourceRoot, "../../../release/unapproved-content-pattern.txt").readText(Charsets.UTF_8).trim(),
            RegexOption.IGNORE_CASE,
        )
        LegalDocumentType.entries.forEach { type ->
            val text = File(sourceRoot, "assets/legal/${type.assetName}").readText()
            assertFalse(type.name, text.contains(LegalPolicy.CURRENT_VERSION))
            assertFalse(type.name, text.contains("{{publication_status}}"))
            assertFalse(type.name, text.contains("尚未生效"))
            assertFalse("${type.name} 不应出现个人姓名", text.contains(operatorName))
            assertFalse("${type.name} 不应展开个人姓名", text.contains("{{operator_name}}"))
            assertFalse("${type.name} 混入未批准文案", unapprovedText.containsMatchIn(text))
            listOf("approval_status", "source_branch", "code_baseline").forEach { key ->
                assertFalse("${type.name} 混入内部字段：$key", text.contains(key))
            }
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
