package com.yangsong.lizhang

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class PrivacyNoticeResourcesTest {
    private fun resources(qualifier: String): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values$qualifier/privacy_notice.xml")).documentElement
        val nodes = (0 until root.childNodes.length).mapNotNull { root.childNodes.item(it) as? Element }
        assertEquals(nodes.size, nodes.map { it.getAttribute("name") }.toSet().size)
        return nodes.associate { it.getAttribute("name") to it.textContent }
    }

    @Test fun `七语言首次告知关键数据处理说明选择及占位符齐全`() {
        val base = resources("")
        val required = listOf("intro", "local_data", "permissions", "exports", "candidate", "read_privacy", "read_terms",
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
}
