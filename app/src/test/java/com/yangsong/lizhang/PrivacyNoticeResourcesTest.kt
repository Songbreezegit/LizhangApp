package com.yangsong.lizhang

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class PrivacyNoticeResourcesTest {
    private fun resources(qualifier: String, filename: String = "privacy_notice.xml"): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values$qualifier/$filename")).documentElement
        val nodes = (0 until root.childNodes.length).mapNotNull { root.childNodes.item(it) as? Element }
        assertEquals(nodes.size, nodes.map { it.getAttribute("name") }.toSet().size)
        return nodes.associate { it.getAttribute("name") to it.textContent }
    }

    @Test fun `七语言首次告知关键数据处理说明选择及占位符齐全`() {
        val base = resources("")
        val required = listOf("intro", "local_data", "permissions", "exports", "documents_required", "read_privacy", "read_terms",
            "agree", "decline", "declined_body", "return", "exit", "save_failed", "version")
        required.forEach { assertTrue(base.getValue("privacy_notice_$it").isNotBlank()) }
        for (qualifier in listOf("", "-b+zh+Hant", "-en", "-ja", "-ko", "-es", "-fr")) {
            val translated = resources(qualifier)
            assertEquals(qualifier, base.keys, translated.keys)
            translated.forEach { (name, value) ->
                assertTrue("$qualifier/$name", value.isNotBlank())
                assertEquals("$qualifier/$name", Regex("%[0-9]+\\\$[a-z]").findAll(base.getValue(name)).map { it.value }.toList(),
                    Regex("%[0-9]+\\\$[a-z]").findAll(value).map { it.value }.toList())
            }
        }
    }

    @Test fun `七语言候选提示保留真实草稿状态但正式共享文案不含候选标签`() {
        for (qualifier in listOf("", "-b+zh+Hant", "-en", "-ja", "-ko", "-es", "-fr")) {
            val candidates = resources(qualifier, "candidate_release_strings.xml")
            assertEquals(setOf("legal_candidate_notice", "privacy_notice_candidate"), candidates.keys)
            assertTrue(qualifier, candidates.values.all { it.contains("RC4") && !Regex("RC[123]").containsMatchIn(it) })
            val neutral = resources(qualifier) + resources(qualifier, "legal_strings.xml")
            assertFalse(neutral.containsKey("legal_candidate_notice"))
            assertFalse(neutral.containsKey("privacy_notice_candidate"))
            assertTrue(qualifier, neutral.values.none { Regex("RC[1-4]").containsMatchIn(it) || it.contains("候选") || it.contains("尚未生效") })
        }
        assertTrue(resources("", "legal_strings.xml").getValue("legal_language_fallback").contains("不替代中文法律文本"))
    }
}
