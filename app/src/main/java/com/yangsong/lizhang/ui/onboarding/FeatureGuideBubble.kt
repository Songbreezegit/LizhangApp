package com.yangsong.lizhang.ui.onboarding

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.onboarding.FeatureGuideStep
import com.yangsong.lizhang.ui.component.GlassButton
import com.yangsong.lizhang.ui.component.GlassTextButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeatureGuideBubble(step: FeatureGuideStep, onNext: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier,
    decorationScale: Float = 1f, catOnLeft: Boolean = false) {
    val (title, body) = when (step) {
        FeatureGuideStep.ADD_RECORD -> R.string.feature_guide_add_title to R.string.feature_guide_add_body
        FeatureGuideStep.RECORD_CONTACT -> R.string.feature_guide_record_contact_title to R.string.feature_guide_record_contact_body
        FeatureGuideStep.RECORD_AMOUNT -> R.string.feature_guide_record_amount_title to R.string.feature_guide_record_amount_body
        FeatureGuideStep.RECORD_DIRECTION -> R.string.feature_guide_record_direction_title to R.string.feature_guide_record_direction_body
        FeatureGuideStep.RECORD_SAVE -> R.string.feature_guide_record_save_title to R.string.feature_guide_record_save_body
        FeatureGuideStep.CONTACTS -> R.string.feature_guide_contacts_title to R.string.feature_guide_contacts_body
        FeatureGuideStep.REMINDERS -> R.string.feature_guide_reminders_title to R.string.feature_guide_reminders_body
        FeatureGuideStep.SETTINGS -> R.string.feature_guide_settings_title to R.string.feature_guide_settings_body
        FeatureGuideStep.CALENDAR -> R.string.feature_guide_calendar_title to R.string.feature_guide_calendar_body
        FeatureGuideStep.SEARCH -> R.string.feature_guide_search_title to R.string.feature_guide_search_body
        FeatureGuideStep.STATISTICS -> R.string.feature_guide_statistics_title to R.string.feature_guide_statistics_body
        FeatureGuideStep.COMPLETED -> return
    }
    val progress = stringResource(R.string.feature_guide_progress, step.number, step.total)
    val localColors = MaterialTheme.colorScheme.copy(
        primary = GuideCloudPalette.action, onPrimary = GuideCloudPalette.onAction,
        surface = GuideCloudPalette.surface, onSurface = GuideCloudPalette.title,
        onSurfaceVariant = GuideCloudPalette.body, outlineVariant = GuideCloudPalette.outline,
    )
    val decoration = decorationScale.coerceIn(0f, 1f)
    // 浅暖白云和深色文字只覆盖本气泡；外部深色页面继续沿用应用主题。
    MaterialTheme(colorScheme = localColors) {
    Box(modifier.testTag("功能引导气泡").semantics {
        liveRegion = LiveRegionMode.Polite
        // 进度仅供辅助阅读，不显示进度条或步骤数字。
        stateDescription = progress
    }.pointerInput(Unit) {
        // 仅气泡空白处消费触摸；子按钮优先处理，屏幕其余区域保持原有点击。
        detectTapGestures { }
    }) {
        // 顶部装饰区域也参与完整气泡的测量，猫咪趴在云沿并与标题保留间距。
        Column(Modifier.fillMaxWidth().padding(top = 40.dp * decoration)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 28.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(body), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
        // 窄屏或大字体时按钮自然换行，说明可滚动，操作始终留在气泡内。
        FlowRow(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GlassTextButton(onSkip, Modifier.testTag("功能引导跳过")) { Text(stringResource(R.string.feature_guide_skip)) }
            GlassButton(onNext, Modifier.testTag("功能引导下一步")) {
                Text(stringResource(when {
                    step == FeatureGuideStep.ADD_RECORD -> R.string.feature_guide_start_record
                    step == FeatureGuideStep.RECORD_SAVE -> R.string.feature_guide_record_done
                    !step.recordStep -> R.string.feature_guide_understood
                    else -> R.string.feature_guide_next
                }))
            }
        }
        }
        if (decoration > 0f) {
            Image(painterResource(R.drawable.guide_lounging_cat), contentDescription = null,
                modifier = Modifier.align(if (catOnLeft) AbsoluteAlignment.TopLeft else AbsoluteAlignment.TopRight)
                    .padding(horizontal = 18.dp).size(width = 128.dp * decoration, height = 64.dp * decoration)
                    .testTag("功能引导猫咪装饰").clearAndSetSemantics { })
        }
    }
    }
}
