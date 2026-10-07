package com.yangsong.lizhang.ui.component

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.doOnDetach
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
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.model.AppLanguage

fun currentAppLanguage(): AppLanguage =
    AppLanguage.fromLanguageTag(AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag())

@Composable
fun AppLanguage.displayName(): String = stringResource(when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.ZH_CN -> R.string.language_chinese
    AppLanguage.ZH_HANT -> R.string.language_chinese_traditional
    AppLanguage.EN -> R.string.language_english
    AppLanguage.JA -> R.string.language_japanese
    AppLanguage.KO -> R.string.language_korean
    AppLanguage.ES -> R.string.language_spanish
    AppLanguage.FR -> R.string.language_french
})

@Composable
fun LanguagePicker(onDismiss: () -> Unit) {
    val currentLanguage = LocalCurrentLanguage.current ?: currentAppLanguage()
    var selected by remember { mutableStateOf(currentLanguage) }
    var requested by remember { mutableStateOf<AppLanguage?>(null) }
    var feedbackDrawn by remember { mutableStateOf(false) }
    val transition = LocalAppearanceActions.current
    // 独立窗口显示前保存原有列表项与偏移，不能让焦点/Insets 的临时派发改写它。
    val originalPosition = remember(transition) {
        (transition as? AppearanceTransitionHost)?.currentListPosition
    }
    val view = LocalView.current
    val popupRoot = remember { arrayOfNulls<android.view.View>(1) }
    // 先绘制选中反馈，再退出窗口；退出确认后才捕获主窗口。
    LaunchedEffect(feedbackDrawn) { if (feedbackDrawn) onDismiss() }
    DisposableEffect(Unit) {
        onDispose {
            requested?.let { target ->
                val submit = {
                    view.post {
                        if (transition is AppearanceTransitionHost) transition.language(target, originalPosition)
                        else transition?.language(target)
                    }
                }
                // 确认独立窗口分离后再捕获，不能把启动 dismiss 当成退出完成。
                popupRoot[0]?.let { popup ->
                    if (popup.isAttachedToWindow) popup.doOnDetach { submit() } else submit()
                } ?: submit()
            }
        }
    }
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            val dialogView = LocalView.current
            SideEffect {
                popupRoot[0] = dialogView.rootView
                // 选中反馈由实际绘制确认；关闭时不留系统窗口退出动画的残影。
                (dialogView.parent as? DialogWindowProvider)?.window?.setWindowAnimations(0)
            }
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup().drawWithContent {
                drawContent()
                if (requested != null && !feedbackDrawn) {
                    val feedback = Runnable { feedbackDrawn = true }
                    if (android.os.Build.VERSION.SDK_INT >= 29 && dialogView.isHardwareAccelerated) {
                        dialogView.viewTreeObserver.registerFrameCommitCallback(feedback)
                        dialogView.postInvalidateOnAnimation()
                    } else dialogView.post(feedback)
                }
            }) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).pressSelectable(
                            selected = selected == language,
                            role = Role.RadioButton,
                            onClick = {
                                if (language != selected) { selected = language; requested = language }
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
