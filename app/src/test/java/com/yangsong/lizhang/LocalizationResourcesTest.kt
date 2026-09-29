package com.yangsong.lizhang

import com.yangsong.lizhang.core.util.CurrencyFormatter
import com.yangsong.lizhang.core.util.DateFormatter
import com.yangsong.lizhang.domain.model.AppLanguage
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class LocalizationResourcesTest {
    private fun resources(qualifier: String): Map<String, Element> {
        val file = File("src/main/res/values$qualifier/strings.xml")
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val entries = (0 until root.childNodes.length).mapNotNull { root.childNodes.item(it) as? Element }
        assertEquals("资源键不得重复", entries.size, entries.map { it.getAttribute("name") }.toSet().size)
        return entries.associateBy { it.getAttribute("name") }
    }

    @Test
    fun `四种语言资源键类型占位符完全一致且英文复数完整`() {
        val base = resources("")
        val placeholder = Regex("%[0-9]+\\\$[0-9]*[ds]")
        for (language in listOf("-en", "-ja", "-ko")) {
            val translated = resources(language)
            assertEquals(language, base.keys, translated.keys)
            base.forEach { (key, node) ->
                val target = translated.getValue(key)
                assertEquals(key, node.tagName, target.tagName)
                val sourceText = if (node.tagName == "plurals") node.getElementsByTagName("item").item(0).textContent else node.textContent
                val values = if (target.tagName == "plurals") {
                    val items = target.getElementsByTagName("item")
                    val elements = (0 until items.length).map { items.item(it) as Element }
                    assertTrue(elements.any { it.getAttribute("quantity") == "other" })
                    if (language == "-en") assertTrue(key, elements.any { it.getAttribute("quantity") == "one" })
                    elements.map { it.textContent }
                } else listOf(target.textContent)
                values.forEach { value ->
                    assertTrue(key, value.isNotBlank())
                    assertEquals("$language/$key", placeholder.findAll(sourceText).map { it.value }.toSet(), placeholder.findAll(value).map { it.value }.toSet())
                }
            }
            listOf("language_chinese", "language_english", "language_japanese", "language_korean").forEach {
                assertEquals(it, base.getValue(it).textContent, translated.getValue(it).textContent)
            }
        }
    }

    @Test
    fun `日期切换语言不复用旧格式且导出日期稳定`() {
        val date = LocalDate.of(2026, 9, 29).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val english = DateFormatter.format(date, locale = Locale.ENGLISH)
        assertTrue(english, english.contains("Sep"))
        assertNotEquals(english, DateFormatter.format(date, locale = Locale.JAPANESE))
        assertNotEquals(english, DateFormatter.format(date, locale = Locale.KOREAN))
        assertEquals(english, DateFormatter.format(date, locale = Locale.ENGLISH))
        assertEquals("2026-09-29", DateFormatter.format(date, "yyyy-MM-dd", Locale.KOREAN))
    }

    @Test
    fun `四种语言金额始终使用人民币和两位小数`() {
        for (locale in listOf(Locale.SIMPLIFIED_CHINESE, Locale.ENGLISH, Locale.JAPANESE, Locale.KOREAN)) {
            val expected = NumberFormat.getCurrencyInstance(locale).apply {
                currency = Currency.getInstance("CNY")
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }.format(java.math.BigDecimal("12345.67"))
            assertEquals(expected, CurrencyFormatter.formatCents(1234567, locale))
        }
    }

    @Test
    fun `语言映射区分跟随系统与显式中文`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag(""))
        assertEquals(AppLanguage.ZH_CN, AppLanguage.fromLanguageTag("zh-CN"))
        assertEquals(AppLanguage.EN, AppLanguage.fromLanguageTag("en-US"))
        assertEquals(AppLanguage.JA, AppLanguage.fromLanguageTag("ja"))
        assertEquals(AppLanguage.KO, AppLanguage.fromLanguageTag("ko"))
    }
}
