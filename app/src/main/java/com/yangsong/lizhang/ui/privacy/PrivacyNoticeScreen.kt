package com.yangsong.lizhang.ui.privacy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.legal.LegalDocumentType
import com.yangsong.lizhang.domain.model.AppLanguage
import com.yangsong.lizhang.domain.repository.LegalDocumentRepository
import com.yangsong.lizhang.ui.component.AppScaffold
import com.yangsong.lizhang.ui.component.AppTopBar
import com.yangsong.lizhang.ui.component.GlassButton
import com.yangsong.lizhang.ui.component.GlassCard
import com.yangsong.lizhang.ui.component.GlassTokens
import com.yangsong.lizhang.ui.component.SecondaryButton
import com.yangsong.lizhang.ui.screen.LegalDocumentScreen
import com.yangsong.lizhang.ui.viewmodel.LegalDocumentViewModel
import com.yangsong.lizhang.ui.viewmodel.LegalDocumentUiState
import com.yangsong.lizhang.ui.viewmodel.PrivacyConsentViewModel

/** 首次告知卡片内滚动阅读两份完整离线文档；末尾链接可进入独立全文页。 */
@Composable
fun PrivacyNoticeGate(
    consentViewModel: PrivacyConsentViewModel,
    legalDocumentRepository: LegalDocumentRepository,
    onAccepted: () -> Unit,
    onExit: () -> Unit,
) {
    var documentName by rememberSaveable { mutableStateOf<String?>(null) }
    val noticeScrollState = rememberScrollState()
    val privacyViewModel: LegalDocumentViewModel = viewModel(
        key = "首次告知全文_PRIVACY",
        factory = LegalDocumentViewModel.factory(legalDocumentRepository, LegalDocumentType.PRIVACY),
    )
    val termsViewModel: LegalDocumentViewModel = viewModel(
        key = "首次告知全文_TERMS",
        factory = LegalDocumentViewModel.factory(legalDocumentRepository, LegalDocumentType.TERMS),
    )
    val privacyState by privacyViewModel.state.collectAsStateWithLifecycle()
    val termsState by termsViewModel.state.collectAsStateWithLifecycle()
    val saveFailed by consentViewModel.saveFailed.collectAsStateWithLifecycle()
    val decline = {
        consentViewModel.decline()
        onExit()
    }
    // 独立全文页返回告知卡片；只有告知页返回才沿用拒绝退出逻辑。
    BackHandler {
        if (documentName != null) documentName = null else decline()
    }
    val type = documentName?.let { LegalDocumentType.valueOf(it) }
    if (type != null) {
        val documentViewModel = if (type == LegalDocumentType.PRIVACY) privacyViewModel else termsViewModel
        LegalDocumentScreen(documentViewModel, onBack = { documentName = null }, showOnlineLink = false)
    } else {
        PrivacyNoticeScreen(
            privacyState = privacyState,
            termsState = termsState,
            documentScrollState = noticeScrollState,
            saveFailed = saveFailed,
            onAgree = {
                // 全文状态不锁死选择；只有主动点击且确认记录保存成功才能继续。
                if (consentViewModel.accept()) onAccepted()
            },
            onDecline = decline,
            onReadPrivacy = { documentName = LegalDocumentType.PRIVACY.name },
            onReadTerms = { documentName = LegalDocumentType.TERMS.name },
            onRetryPrivacy = privacyViewModel::reload,
            onRetryTerms = termsViewModel::reload,
        )
    }
}

@Composable
fun PrivacyNoticeScreen(
    privacyState: LegalDocumentUiState = LegalDocumentUiState(),
    termsState: LegalDocumentUiState = LegalDocumentUiState(),
    saveFailed: Boolean = false,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onReadPrivacy: () -> Unit = {},
    onReadTerms: () -> Unit = {},
    onRetryPrivacy: () -> Unit = {},
    onRetryTerms: () -> Unit = {},
    documentScrollState: ScrollState = rememberScrollState(),
) {
    val showLanguageNotice = AppLanguage.fromLanguageTag(
        LocalConfiguration.current.locales[0]?.toLanguageTag(),
    ) != AppLanguage.ZH_CN
    // Gate 持有滚动状态，打开独立全文页再返回时仍停在原位置。
    LaunchedEffect(saveFailed, documentScrollState.maxValue) {
        if (saveFailed) documentScrollState.animateScrollTo(documentScrollState.maxValue)
    }
    AppScaffold(topBar = { AppTopBar(stringResource(R.string.privacy_notice_title), onBack = onDecline) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp).testTag("首次隐私告知"),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            GlassCard(Modifier.fillMaxWidth().weight(1f).testTag("首次隐私告知阅读卡片")) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(documentScrollState).padding(20.dp)
                        .testTag("首次隐私告知正文"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (showLanguageNotice) Text(stringResource(R.string.legal_language_fallback),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NoticeDocument(LegalDocumentType.PRIVACY, privacyState, onRetryPrivacy)
                    NoticeDocument(LegalDocumentType.TERMS, termsState, onRetryTerms)
                    if (saveFailed) Text(stringResource(R.string.privacy_notice_save_failed),
                        Modifier.testTag("隐私确认保存失败").semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    // 两个链接属于同一滚动容器，严格排在两份完整正文及错误提示之后。
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onReadPrivacy, Modifier.weight(1f).heightIn(min = 48.dp).testTag("告知阅读隐私政策")) {
                            Text(stringResource(R.string.privacy_title), textDecoration = TextDecoration.Underline,
                                textAlign = TextAlign.Center)
                        }
                        TextButton(onReadTerms, Modifier.weight(1f).heightIn(min = 48.dp).testTag("告知阅读用户协议")) {
                            Text(stringResource(R.string.legal_terms_title), textDecoration = TextDecoration.Underline,
                                textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            GlassButton(onAgree, Modifier.fillMaxWidth().testTag("隐私告知同意")) {
                Text(stringResource(R.string.privacy_notice_agree))
            }
            Button(
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("隐私告知拒绝"),
                shape = RoundedCornerShape(GlassTokens.ControlRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(stringResource(R.string.privacy_notice_decline))
            }
        }
    }
}

@Composable
private fun NoticeDocument(type: LegalDocumentType, state: LegalDocumentUiState, onRetry: () -> Unit) {
    val document = state.document
    Column(Modifier.fillMaxWidth().testTag("告知正文_${type.name.lowercase()}"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(if (type == LegalDocumentType.PRIVACY) R.string.privacy_title else R.string.legal_terms_title),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        when {
            state.isLoading -> CircularProgressIndicator()
            state.failed || document == null || document.type != type || document.sections.isEmpty() ||
                document.sections.any { it.body.isBlank() } -> {
                Text(stringResource(R.string.legal_read_failed), style = MaterialTheme.typography.bodyLarge)
                state.debugFailure?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                SecondaryButton(stringResource(R.string.legal_retry), onRetry,
                    Modifier.fillMaxWidth().testTag("告知重新读取_${type.name.lowercase()}"))
            }
            else -> document.sections.forEach { section ->
                section.heading?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                SelectionContainer {
                    Text(section.body, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
