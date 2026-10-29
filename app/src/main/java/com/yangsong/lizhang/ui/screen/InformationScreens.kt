package com.yangsong.lizhang.ui.screen
import com.yangsong.lizhang.ui.component.gradientHeaderSecondaryColor

import com.yangsong.lizhang.ui.component.LiZhangIcons
import com.yangsong.lizhang.ui.component.AppScaffold
import com.yangsong.lizhang.ui.component.GlassTokens
import com.yangsong.lizhang.ui.component.GlassCard

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.BuildConfig
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.AppTopBar
import com.yangsong.lizhang.ui.component.CenteredSnackbarHost
import com.yangsong.lizhang.ui.component.SecondaryButton
import com.yangsong.lizhang.ui.component.pressClickable
import com.yangsong.lizhang.domain.legal.LegalDocumentType
import com.yangsong.lizhang.ui.viewmodel.LegalDocumentUiState
import com.yangsong.lizhang.ui.viewmodel.LegalDocumentViewModel
import kotlinx.coroutines.launch

@Composable
fun FontGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    InformationPage(
        title = stringResource(R.string.font_guide_title),
        onBack = onBack,
        sections = listOf(
            InformationSection(R.string.font_guide_follow_system_title, R.string.font_guide_follow_system_body),
        ),
        footer = {
            SecondaryButton(
                text = stringResource(R.string.font_guide_open_settings),
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = LiZhangIcons.ExternalLink,
            )
        },
    )
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val filingNumber = BuildConfig.APP_FILING_NUMBER
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val browserFailure = stringResource(R.string.about_filing_browser_failed)
    InformationPage(
        title = stringResource(R.string.about_title),
        onBack = onBack,
        introduction = stringResource(R.string.about_introduction),
        sections = listOf(
            InformationSection(R.string.about_version_title, R.string.settings_version),
            InformationSection(R.string.about_principle_title, R.string.about_principle_body),
        ),
        // 未获本人批准时连同列表项一起省略，不显示占位内容或多余间距。
        footer = if (filingNumber.isNotBlank()) {
            {
                AppFilingCard(filingNumber, onOpenQuery = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://beian.miit.gov.cn/"))
                        .addCategory(Intent.CATEGORY_BROWSABLE)
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        scope.launch { snackbar.showSnackbar(browserFailure) }
                    } catch (_: SecurityException) {
                        scope.launch { snackbar.showSnackbar(browserFailure) }
                    }
                })
            }
        } else null,
        snackbarHost = { CenteredSnackbarHost(snackbar) },
    )
}

@Composable
private fun AppFilingCard(filingNumber: String, onOpenQuery: () -> Unit) {
    val shape = RoundedCornerShape(GlassTokens.Radius)
    val queryLabel = stringResource(R.string.about_filing_query)
    GlassCard(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressClickable(
            role = Role.Button,
            onClickLabel = queryLabel,
            shape = shape,
            onClick = onOpenQuery,
        ),
        shape = shape,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.about_filing_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.about_filing_number, filingNumber),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = true,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(queryLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
                Icon(LiZhangIcons.ExternalLink, contentDescription = null, modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun PrivacyScreen(viewModel: LegalDocumentViewModel, onBack: () -> Unit) {
    LegalDocumentScreen(viewModel, onBack)
}

/** 无仓库的内容入口用于预览和测试；生产页面由 ViewModel 加载完整本地正文。 */
@Composable
fun PrivacyContent(onBack: () -> Unit = {}, state: LegalDocumentUiState = LegalDocumentUiState()) {
    LegalDocumentContent(LegalDocumentType.PRIVACY, state, onBack)
}

private data class InformationSection(
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
)

@Composable
private fun InformationPage(
    title: String,
    onBack: () -> Unit,
    sections: List<InformationSection>,
    introduction: String? = null,
    footer: (@Composable () -> Unit)? = null,
    snackbarHost: @Composable () -> Unit = {},
) {
    AppScaffold(
        topBar = { AppTopBar(title, onBack) },
        snackbarHost = snackbarHost,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            introduction?.let { text ->
                item {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = gradientHeaderSecondaryColor(),
                    )
                }
            }
            sections.forEach { section ->
                item {
                    InformationCard(section)
                }
            }
            footer?.let { content ->
                item { content() }
            }
        }
    }
}

@Composable
private fun InformationCard(section: InformationSection) {
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GlassTokens.Radius),

    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(section.title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (section.body == R.string.settings_version) {
                    stringResource(section.body, com.yangsong.lizhang.BuildConfig.VERSION_NAME)
                } else stringResource(section.body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
