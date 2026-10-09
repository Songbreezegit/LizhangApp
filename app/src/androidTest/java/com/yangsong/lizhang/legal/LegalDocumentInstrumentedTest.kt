package com.yangsong.lizhang.legal

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.yangsong.lizhang.R
import com.yangsong.lizhang.data.legal.AssetLegalDocumentRepository
import com.yangsong.lizhang.domain.legal.LegalDocumentType
import com.yangsong.lizhang.ui.screen.LegalDocumentContent
import com.yangsong.lizhang.ui.screen.SettingsContent
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.LegalDocumentUiState
import com.yangsong.lizhang.ui.viewmodel.SettingsUiState
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 只读安装包 assets；使用无业务仓库的页面状态，不获取设备通讯录或真实账本。 */
class LegalDocumentInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = AssetLegalDocumentRepository(context)

    @Test fun 隐私政策与用户协议及帮助可读取完整打包正文() = runBlocking {
        LegalDocumentType.entries.forEach { type ->
            val document = repository.load(type)
            assertEquals(type, document.type)
            assertTrue(document.sections.size >= 8)
            assertTrue(document.sections.first().body.contains("尚未生效"))
        }
    }

    @Test fun 隐私页能访问末尾说明且返回不会自动打开浏览器() {
        val document = runBlocking { repository.load(LegalDocumentType.PRIVACY) }
        var returned = false
        compose.setContent { LiZhangTheme {
            LegalDocumentContent(LegalDocumentType.PRIVACY,
                LegalDocumentUiState(isLoading = false, document = document), { returned = true })
        } }
        compose.onNodeWithTag("legal_document_privacy")
            .performScrollToNode(hasText("10. 文档网站说明"))
        compose.onNodeWithText("10. 文档网站说明").assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_back)).performClick()
        assertTrue(returned)
    }

    @Test fun 我的三入口可返回且大字号深浅色保留完整正文() {
        val selected = mutableStateOf<LegalDocumentType?>(null)
        val dark = mutableStateOf(false)
        val documents = runBlocking { LegalDocumentType.entries.associateWith { repository.load(it) } }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                LiZhangTheme(darkTheme = dark.value) {
                    selected.value?.let { type ->
                        LegalDocumentContent(type, LegalDocumentUiState(isLoading = false, document = documents.getValue(type)),
                            onBack = { selected.value = null })
                    } ?: SettingsContent(SettingsUiState(), {}, {}, {}, {},
                        onPrivacy = { selected.value = LegalDocumentType.PRIVACY },
                        onTerms = { selected.value = LegalDocumentType.TERMS },
                        onHelp = { selected.value = LegalDocumentType.HELP })
                }
            }
        }
        val entries = listOf(
            R.string.settings_privacy to LegalDocumentType.PRIVACY,
            R.string.legal_terms_title to LegalDocumentType.TERMS,
            R.string.legal_help_title to LegalDocumentType.HELP,
        )
        entries.forEach { (stringId, type) ->
            compose.onNodeWithTag("设置列表").performScrollToNode(hasText(compose.activity.getString(stringId)))
            compose.onNodeWithText(compose.activity.getString(stringId)).performScrollTo().performClick()
            compose.onNodeWithTag("legal_document_${type.name.lowercase()}").assertIsDisplayed()
            compose.runOnIdle { dark.value = !dark.value }
            compose.onNodeWithText(compose.activity.getString(R.string.legal_candidate_notice)).assertIsDisplayed()
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_back)).performClick()
            compose.onNodeWithTag("设置列表").performScrollToNode(hasText(compose.activity.getString(stringId)))
            compose.onNodeWithText(compose.activity.getString(stringId)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun 七语言都有回退提示而正文始终完整一致() = runBlocking {
        val original = repository.load(LegalDocumentType.PRIVACY)
        for (tag in listOf("zh-CN", "zh-Hant", "en", "ja", "ko", "es", "fr")) {
            val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            val localized = context.createConfigurationContext(configuration)
            assertTrue(tag, localized.getString(R.string.legal_language_fallback).isNotBlank())
            assertEquals(tag, original, AssetLegalDocumentRepository(localized).load(LegalDocumentType.PRIVACY))
        }
    }
}
