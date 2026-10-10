package com.yangsong.lizhang.ui.privacy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.legal.LegalDocumentType
import com.yangsong.lizhang.domain.repository.LegalDocumentRepository
import com.yangsong.lizhang.ui.component.AppScaffold
import com.yangsong.lizhang.ui.component.AppTopBar
import com.yangsong.lizhang.ui.component.GlassButton
import com.yangsong.lizhang.ui.component.GlassCard
import com.yangsong.lizhang.ui.component.GlassTokens
import com.yangsong.lizhang.ui.component.SecondaryButton
import com.yangsong.lizhang.ui.viewmodel.LegalDocumentUiState
import com.yangsong.lizhang.ui.viewmodel.LegalDocumentViewModel
import com.yangsong.lizhang.ui.viewmodel.PrivacyConsentViewModel

/** 完整离线文档在业务导航挂载前展示；未同意时返回或拒绝直接退出。 */
@Composable
fun PrivacyNoticeGate(
    consentViewModel: PrivacyConsentViewModel,
    legalDocumentRepository: LegalDocumentRepository,
    onAccepted: () -> Unit,
    onExit: () -> Unit,
) {
    val privacyViewModel: LegalDocumentViewModel = viewModel(
        key = "首次告知隐私政策",
        factory = LegalDocumentViewModel.factory(legalDocumentRepository, LegalDocumentType.PRIVACY),
    )
    val termsViewModel: LegalDocumentViewModel = viewModel(
        key = "首次告知用户协议",
        factory = LegalDocumentViewModel.factory(legalDocumentRepository, LegalDocumentType.TERMS),
    )
    val privacyState by privacyViewModel.state.collectAsStateWithLifecycle()
    val termsState by termsViewModel.state.collectAsStateWithLifecycle()
    val saveFailed by consentViewModel.saveFailed.collectAsStateWithLifecycle()
    val decline = {
        consentViewModel.decline()
        onExit()
    }
    BackHandler(onBack = decline)
    PrivacyNoticeScreen(
        privacyState = privacyState,
        termsState = termsState,
        saveFailed = saveFailed,
        onAgree = {
            if (privacyState.canConfirm(LegalDocumentType.PRIVACY) &&
                termsState.canConfirm(LegalDocumentType.TERMS) && consentViewModel.accept()) onAccepted()
        },
        onDecline = decline,
        onRetryPrivacy = privacyViewModel::reload,
        onRetryTerms = termsViewModel::reload,
    )
}

@Composable
fun PrivacyNoticeScreen(
    privacyState: LegalDocumentUiState = LegalDocumentUiState(),
    termsState: LegalDocumentUiState = LegalDocumentUiState(),
    saveFailed: Boolean = false,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onRetryPrivacy: () -> Unit = {},
    onRetryTerms: () -> Unit = {},
) {
    val canAgree = privacyState.canConfirm(LegalDocumentType.PRIVACY) && termsState.canConfirm(LegalDocumentType.TERMS)
    val documentScrollState = rememberScrollState()
    LaunchedEffect(saveFailed, documentScrollState.maxValue) {
        // 错误全文随卡片滚动，出现时主动展示，避免大字号挤掉正文阅读空间。
        if (saveFailed) documentScrollState.animateScrollTo(documentScrollState.maxValue)
    }
    AppScaffold(topBar = { AppTopBar(stringResource(R.string.privacy_notice_title), onBack = onDecline) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp).testTag("首次隐私告知"),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            GlassCard(Modifier.fillMaxWidth().weight(1f).testTag("首次隐私告知阅读卡片")) {
                // 文档仅在中间卡片内滚动；Compose 不绘制滚动条，确认与拒绝按钮始终可见。
                Column(
                    Modifier.fillMaxWidth().verticalScroll(documentScrollState).padding(20.dp)
                        .testTag("首次隐私告知正文"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(R.string.privacy_notice_local_data), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.privacy_notice_permissions), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.privacy_notice_exports), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.legal_language_fallback), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NoticeDocument(LegalDocumentType.PRIVACY, privacyState, onRetryPrivacy)
                    NoticeDocument(LegalDocumentType.TERMS, termsState, onRetryTerms)
                    if (saveFailed) Text(stringResource(R.string.privacy_notice_save_failed),
                        Modifier.testTag("隐私确认保存失败").semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                }
            }
            GlassButton(onAgree, Modifier.fillMaxWidth().testTag("隐私告知同意"), enabled = canAgree) {
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
    Column(Modifier.fillMaxWidth().testTag("告知正文_${type.name.lowercase()}"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(if (type == LegalDocumentType.PRIVACY) R.string.privacy_title else R.string.legal_terms_title),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        when {
            state.isLoading -> CircularProgressIndicator()
            !state.canConfirm(type) -> {
                Text(stringResource(R.string.legal_read_failed), style = MaterialTheme.typography.bodyLarge)
                SecondaryButton(stringResource(R.string.legal_retry), onRetry,
                    Modifier.fillMaxWidth().testTag("告知重新读取_${type.name.lowercase()}"))
            }
            else -> state.document?.sections?.forEach { section ->
                section.heading?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                SelectionContainer {
                    Text(section.body, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 完整文档失败、为空或类型不符时，不能以摘要代替正文放行。 */
internal fun LegalDocumentUiState.canConfirm(type: LegalDocumentType): Boolean =
    !isLoading && !failed && document?.let { loaded ->
        loaded.type == type && loaded.sections.isNotEmpty() && loaded.sections.all { it.body.isNotBlank() }
    } == true
