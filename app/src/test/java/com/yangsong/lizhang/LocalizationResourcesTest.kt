package com.yangsong.lizhang

import com.yangsong.lizhang.core.util.CurrencyFormatter
import com.yangsong.lizhang.core.util.DateFormatter
import com.yangsong.lizhang.domain.model.AppLanguage
import com.yangsong.lizhang.ui.mapper.resolveDisplayLocale
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
    fun `七种语言资源键类型占位符完全一致且复数完整`() {
        val base = resources("")
        val placeholder = Regex("%[0-9]+\\\$[0-9]*[ds]")
        for (language in listOf("-b+zh+Hant", "-en", "-ja", "-ko", "-es", "-fr")) {
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
                    if (language in listOf("-en", "-es", "-fr")) assertTrue(key, elements.any { it.getAttribute("quantity") == "one" })
                    if (language in listOf("-es", "-fr")) assertTrue(key, elements.any { it.getAttribute("quantity") == "many" })
                    elements.map { it.textContent }
                } else listOf(target.textContent)
                values.forEach { value ->
                    assertTrue(key, value.isNotBlank())
                    assertEquals("$language/$key", placeholder.findAll(sourceText).map { it.value }.toSet(), placeholder.findAll(value).map { it.value }.toSet())
                }
            }
            listOf("language_chinese", "language_chinese_traditional", "language_english", "language_japanese",
                "language_korean", "language_spanish", "language_french").forEach {
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
    fun `七种语言金额始终使用人民币和两位小数`() {
        for (locale in AppLanguage.entries.filter { it != AppLanguage.SYSTEM }.map { Locale.forLanguageTag(it.localeTag) }) {
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
        assertEquals(AppLanguage.ZH_CN, AppLanguage.fromLanguageTag("zh-Hans"))
        assertEquals(AppLanguage.ZH_CN, AppLanguage.fromLanguageTag("zh-Hans-TW"))
        for (tag in listOf("zh-Hant", "zh-Hant-CN", "zh-TW", "zh-HK", "zh-MO", "zh_hant_tw", "ZH-hant")) {
            assertEquals(tag, AppLanguage.ZH_HANT, AppLanguage.fromLanguageTag(tag))
        }
        assertEquals(AppLanguage.EN, AppLanguage.fromLanguageTag("en-US"))
        assertEquals(AppLanguage.JA, AppLanguage.fromLanguageTag("ja"))
        assertEquals(AppLanguage.KO, AppLanguage.fromLanguageTag("ko"))
        assertEquals(AppLanguage.ES, AppLanguage.fromLanguageTag("es-ES"))
        assertEquals(AppLanguage.ES, AppLanguage.fromLanguageTag("es-MX"))
        assertEquals(AppLanguage.FR, AppLanguage.fromLanguageTag("fr-FR"))
        assertEquals(AppLanguage.FR, AppLanguage.fromLanguageTag("fr-CA"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag("de-DE"))
    }

    @Test
    fun `系统语言声明与应用选项一致`() {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/xml/locales_config.xml")).documentElement
        val locales = root.getElementsByTagName("locale")
        val tags = (0 until locales.length).map { (locales.item(it) as Element).getAttribute("android:name") }
        assertEquals(AppLanguage.entries.filter { it != AppLanguage.SYSTEM }.map { it.localeTag }, tags)
    }

    @Test
    fun `日期金额使用新增语言并保留繁体字形与地区`() {
        for (tag in listOf("zh-Hant", "zh-TW", "zh-HK", "es-ES", "es-MX", "fr-FR", "fr-CA")) {
            val locale = Locale.forLanguageTag(tag)
            assertEquals(tag, locale, resolveDisplayLocale(sequenceOf(locale, Locale.ENGLISH)))
            assertEquals(tag, locale, resolveDisplayLocale(sequenceOf(Locale.GERMAN, locale)))
        }
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolveDisplayLocale(sequenceOf(Locale.GERMAN)))
    }
}
