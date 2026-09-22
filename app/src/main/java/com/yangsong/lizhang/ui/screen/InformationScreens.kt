package com.yangsong.lizhang.ui.screen
import com.yangsong.lizhang.ui.component.AppScaffold
import com.yangsong.lizhang.ui.component.GlassTokens
import com.yangsong.lizhang.ui.component.GlassCard

import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.AppTopBar
import com.yangsong.lizhang.ui.component.SecondaryButton

@Composable
fun FontGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    InformationPage(
        title = stringResource(R.string.font_guide_title),
        onBack = onBack,
        sections = listOf(
            InformationSection(R.string.font_guide_follow_system_title, R.string.font_guide_follow_system_body),
            InformationSection(R.string.font_guide_adjust_title, R.string.font_guide_adjust_body),
            InformationSection(R.string.font_guide_layout_title, R.string.font_guide_layout_body),
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
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
            )
        },
    )
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    InformationPage(
        title = stringResource(R.string.about_title),
        onBack = onBack,
        introduction = stringResource(R.string.about_introduction),
        sections = listOf(
            InformationSection(R.string.about_version_title, R.string.settings_version),
            InformationSection(R.string.about_principle_title, R.string.about_principle_body),
            InformationSection(R.string.about_scope_title, R.string.about_scope_body),
        ),
    )
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    PrivacyContent(onBack)
}

@Composable
fun PrivacyContent(onBack: () -> Unit = {}) {
    InformationPage(
        title = stringResource(R.string.privacy_title),
        onBack = onBack,
        introduction = stringResource(R.string.privacy_introduction),
        sections = listOf(
            InformationSection(R.string.privacy_storage_title, R.string.privacy_storage_body),
            InformationSection(R.string.privacy_network_title, R.string.privacy_network_body),
            InformationSection(R.string.privacy_permission_title, R.string.privacy_permission_body),
            InformationSection(R.string.privacy_file_title, R.string.privacy_file_body),
            InformationSection(R.string.privacy_delete_title, R.string.privacy_delete_body),
        ),
    )
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
) {
    AppScaffold(
        topBar = { AppTopBar(title, onBack) },
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                stringResource(section.body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
