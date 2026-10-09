package com.yangsong.lizhang.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.BuildConfig
import com.yangsong.lizhang.domain.legal.LegalDocumentType
import com.yangsong.lizhang.domain.legal.LegalPolicy
import com.yangsong.lizhang.ui.component.AppScaffold
import com.yangsong.lizhang.ui.component.AppTopBar
import com.yangsong.lizhang.ui.component.CenteredSnackbarHost
import com.yangsong.lizhang.ui.component.GlassCard
import com.yangsong.lizhang.ui.component.GlassTokens
import com.yangsong.lizhang.ui.component.SecondaryButton
import com.yangsong.lizhang.ui.viewmodel.LegalDocumentUiState
import com.yangsong.lizhang.ui.viewmodel.LegalDocumentViewModel
import kotlinx.coroutines.launch

@Composable
fun LegalDocumentScreen(viewModel: LegalDocumentViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LegalDocumentContent(viewModel.type, state, onBack, viewModel::reload)
}

@Composable
fun LegalDocumentContent(
    type: LegalDocumentType,
    state: LegalDocumentUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val browserFailure = stringResource(R.string.legal_browser_failed)
    val title = stringResource(when (type) {
        LegalDocumentType.PRIVACY -> R.string.privacy_title
        LegalDocumentType.TERMS -> R.string.legal_terms_title
        LegalDocumentType.HELP -> R.string.legal_help_title
    })
    AppScaffold(
        topBar = { AppTopBar(title, onBack) },
        snackbarHost = { CenteredSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("legal_document_${type.name.lowercase()}"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "status") {
                LegalCard {
                    if (!BuildConfig.IS_OFFICIAL_RELEASE) {
                        Text(stringResource(R.string.legal_candidate_notice), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                    }
                    Text(stringResource(R.string.legal_language_fallback),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.legal_content_version, LegalPolicy.CURRENT_VERSION),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.isLoading) item { CircularProgressIndicator(Modifier.testTag("legal_loading")) }
            if (state.failed) item {
                LegalCard {
                    Text(stringResource(R.string.legal_read_failed), Modifier.testTag("legal_read_failed"),
                        style = MaterialTheme.typography.bodyLarge)
                    SecondaryButton(stringResource(R.string.legal_retry), onRetry,
                        Modifier.fillMaxWidth().testTag("legal_retry"))
                }
            }
            state.document?.sections?.forEachIndexed { index, section ->
                item(key = "section_$index") {
                    LegalCard {
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
            item(key = "online") {
                LegalCard {
                    Text(stringResource(R.string.legal_online_explanation),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(type.officialUrl, style = MaterialTheme.typography.bodyMedium)
                    SecondaryButton(
                        stringResource(R.string.legal_open_official_site),
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(type.officialUrl))
                                .addCategory(Intent.CATEGORY_BROWSABLE)
                            if (runCatching { context.startActivity(intent) }.isFailure) {
                                scope.launch { snackbar.showSnackbar(browserFailure) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("legal_open_browser"),
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegalCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(GlassTokens.Radius)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
