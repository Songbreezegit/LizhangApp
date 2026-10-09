package com.yangsong.lizhang.ui.privacy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.legal.LegalDocumentType
import com.yangsong.lizhang.domain.legal.LegalPolicy
import com.yangsong.lizhang.domain.repository.LegalDocumentRepository
import com.yangsong.lizhang.ui.component.AppScaffold
import com.yangsong.lizhang.ui.component.AppTopBar
import com.yangsong.lizhang.ui.component.GlassButton
import com.yangsong.lizhang.ui.component.GlassCard
import com.yangsong.lizhang.ui.component.GlassTextButton
import com.yangsong.lizhang.ui.screen.LegalDocumentScreen
import com.yangsong.lizhang.ui.viewmodel.LegalDocumentViewModel
import com.yangsong.lizhang.ui.viewmodel.PrivacyConsentViewModel

/** 告知和离线文档在业务导航挂载之前展示；拒绝不会请求权限或消费功能引导。 */
@Composable
fun PrivacyNoticeGate(
    consentViewModel: PrivacyConsentViewModel,
    legalDocumentRepository: LegalDocumentRepository,
    onAccepted: () -> Unit,
    onExit: () -> Unit,
) {
    var documentName by rememberSaveable { mutableStateOf<String?>(null) }
    var declined by rememberSaveable { mutableStateOf(false) }
    val saveFailed by consentViewModel.saveFailed.collectAsStateWithLifecycle()
    val documentType = documentName?.let { name -> LegalDocumentType.entries.firstOrNull { it.name == name } }
    if (documentType != null) {
        val documentViewModel: LegalDocumentViewModel = viewModel(
            key = "隐私告知文档-${documentType.name}",
            factory = LegalDocumentViewModel.factory(legalDocumentRepository, documentType),
        )
        BackHandler { documentName = null }
        LegalDocumentScreen(documentViewModel, onBack = { documentName = null })
    } else if (declined) {
        BackHandler { declined = false }
        PrivacyDeclinedScreen(onReturn = { declined = false }, onExit = onExit)
    } else {
        val decline = {
            consentViewModel.decline()
            declined = true
        }
        BackHandler(onBack = decline)
        PrivacyNoticeScreen(
            saveFailed = saveFailed,
            onAgree = { if (consentViewModel.accept()) onAccepted() },
            onDecline = decline,
            onPrivacy = { documentName = LegalDocumentType.PRIVACY.name },
            onTerms = { documentName = LegalDocumentType.TERMS.name },
        )
    }
}

@Composable
fun PrivacyNoticeScreen(
    saveFailed: Boolean = false,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
) {
    AppScaffold(topBar = { AppTopBar(stringResource(R.string.privacy_notice_title), onBack = onDecline) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(24.dp).testTag("首次隐私告知"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.privacy_notice_intro), style = MaterialTheme.typography.bodyLarge)
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.privacy_notice_local_data), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.privacy_notice_permissions), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.privacy_notice_exports), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(stringResource(R.string.privacy_notice_candidate), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.privacy_notice_version, LegalPolicy.CURRENT_VERSION),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GlassTextButton(onPrivacy, Modifier.fillMaxWidth().testTag("告知隐私政策")) {
                Text(stringResource(R.string.privacy_notice_read_privacy))
            }
            GlassTextButton(onTerms, Modifier.fillMaxWidth().testTag("告知用户协议")) {
                Text(stringResource(R.string.privacy_notice_read_terms))
            }
            if (saveFailed) Text(stringResource(R.string.privacy_notice_save_failed),
                Modifier.testTag("隐私确认保存失败"), color = MaterialTheme.colorScheme.error)
            GlassButton(onAgree, Modifier.fillMaxWidth().testTag("隐私告知同意")) {
                Text(stringResource(R.string.privacy_notice_agree))
            }
            GlassTextButton(onDecline, Modifier.fillMaxWidth().testTag("隐私告知拒绝")) {
                Text(stringResource(R.string.privacy_notice_decline))
            }
        }
    }
}

@Composable
private fun PrivacyDeclinedScreen(onReturn: () -> Unit, onExit: () -> Unit) {
    AppScaffold(topBar = { AppTopBar(stringResource(R.string.privacy_notice_declined_title), onBack = onReturn) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(24.dp).testTag("隐私拒绝说明"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.privacy_notice_declined_body), Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyLarge)
            }
            GlassButton(onReturn, Modifier.fillMaxWidth().testTag("返回隐私告知")) {
                Text(stringResource(R.string.privacy_notice_return))
            }
            GlassTextButton(onExit, Modifier.fillMaxWidth().testTag("拒绝退出应用")) {
                Text(stringResource(R.string.privacy_notice_exit))
            }
        }
    }
}
