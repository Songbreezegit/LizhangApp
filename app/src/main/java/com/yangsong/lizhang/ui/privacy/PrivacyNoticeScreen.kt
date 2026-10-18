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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
import com.yangsong.lizhang.ui.screen.LegalDocumentScreen
import com.yangsong.lizhang.ui.viewmodel.LegalDocumentViewModel
import com.yangsong.lizhang.ui.viewmodel.PrivacyConsentViewModel

/** 首次告知独立于业务导航；全文只在用户点击链接后于 App 内打开。 */
@Composable
fun PrivacyNoticeGate(
    consentViewModel: PrivacyConsentViewModel,
    legalDocumentRepository: LegalDocumentRepository,
    onAccepted: () -> Unit,
    onExit: () -> Unit,
) {
    var documentName by rememberSaveable { mutableStateOf<String?>(null) }
    val saveFailed by consentViewModel.saveFailed.collectAsStateWithLifecycle()
    val decline = {
        consentViewModel.decline()
        onExit()
    }
    // 全文页系统返回/顶栏返回都回摘要；只有摘要页返回才沿用拒绝退出逻辑。
    BackHandler {
        if (documentName != null) documentName = null else decline()
    }
    val type = documentName?.let { LegalDocumentType.valueOf(it) }
    if (type != null) {
        val documentViewModel: LegalDocumentViewModel = viewModel(
            key = "首次告知全文_${type.name}",
            factory = LegalDocumentViewModel.factory(legalDocumentRepository, type),
        )
        LegalDocumentScreen(documentViewModel, onBack = { documentName = null }, showOnlineLink = false)
    } else {
        PrivacyNoticeScreen(
            saveFailed = saveFailed,
            onAgree = {
                // 读取失败不会替用户同意，也不锁死摘要；只有主动点击且保存成功才能继续。
                if (consentViewModel.accept()) onAccepted()
            },
            onDecline = decline,
            onReadPrivacy = { documentName = LegalDocumentType.PRIVACY.name },
            onReadTerms = { documentName = LegalDocumentType.TERMS.name },
        )
    }
}

@Composable
fun PrivacyNoticeScreen(
    saveFailed: Boolean = false,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onReadPrivacy: () -> Unit = {},
    onReadTerms: () -> Unit = {},
) {
    val summaryScrollState = rememberScrollState()
    LaunchedEffect(saveFailed, summaryScrollState.maxValue) {
        if (saveFailed) summaryScrollState.animateScrollTo(summaryScrollState.maxValue)
    }
    AppScaffold(topBar = { AppTopBar(stringResource(R.string.privacy_notice_title), onBack = onDecline) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp).testTag("首次隐私告知"),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            GlassCard(Modifier.fillMaxWidth().weight(1f).testTag("首次隐私告知阅读卡片")) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(summaryScrollState).padding(20.dp)
                        .testTag("首次隐私告知正文"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(R.string.privacy_notice_intro), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.privacy_notice_local_data), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.privacy_notice_permissions), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.privacy_notice_exports), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.legal_language_fallback), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    if (saveFailed) Text(stringResource(R.string.privacy_notice_save_failed),
                        Modifier.testTag("隐私确认保存失败").semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
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
