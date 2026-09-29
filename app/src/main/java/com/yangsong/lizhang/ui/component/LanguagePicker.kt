package com.yangsong.lizhang.ui.component

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.model.AppLanguage

fun currentAppLanguage(): AppLanguage =
    AppLanguage.fromLanguageTag(AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag())

@Composable
fun AppLanguage.displayName(): String = stringResource(when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.ZH_CN -> R.string.language_chinese
    AppLanguage.EN -> R.string.language_english
    AppLanguage.JA -> R.string.language_japanese
    AppLanguage.KO -> R.string.language_korean
})

@Composable
fun LanguagePicker(onDismiss: () -> Unit) {
    val selected = currentAppLanguage()
    val transition = LocalAppearanceTransition.current
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                            selected = selected == language,
                            role = Role.RadioButton,
                            onClick = {
                                onDismiss()
                                if (language != selected) transition {
                                    AppCompatDelegate.setApplicationLocales(
                                        LocaleListCompat.forLanguageTags(language.localeTag),
                                    )
                                }
                            },
                        ).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == language, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(language.displayName(), Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            GlassTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
