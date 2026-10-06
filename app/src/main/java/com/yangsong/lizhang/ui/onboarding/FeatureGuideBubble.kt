package com.yangsong.lizhang.ui.onboarding

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.onboarding.FeatureGuideStep
import com.yangsong.lizhang.ui.component.GlassButton
import com.yangsong.lizhang.ui.component.GlassCard
import com.yangsong.lizhang.ui.component.GlassTextButton
import com.yangsong.lizhang.ui.component.GlassTokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeatureGuideBubble(step: FeatureGuideStep, onNext: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val (title, body) = when (step) {
        FeatureGuideStep.ADD_RECORD -> R.string.feature_guide_add_title to R.string.feature_guide_add_body
        FeatureGuideStep.CONTACTS -> R.string.feature_guide_contacts_title to R.string.feature_guide_contacts_body
        FeatureGuideStep.REMINDERS -> R.string.feature_guide_reminders_title to R.string.feature_guide_reminders_body
        FeatureGuideStep.SETTINGS -> R.string.feature_guide_settings_title to R.string.feature_guide_settings_body
        FeatureGuideStep.COMPLETED -> return
    }
    val progress = stringResource(R.string.feature_guide_progress, step.number, 4)
    GlassCard(modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = GlassTokens.DialogAlpha),
        RoundedCornerShape(GlassTokens.Radius)).testTag("功能引导气泡").semantics {
        liveRegion = LiveRegionMode.Polite
        stateDescription = progress
    }.pointerInput(Unit) {
        // 仅气泡空白处消费触摸；子按钮优先处理，屏幕其余区域保持原有点击。
        detectTapGestures { }
    }) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(body), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
            Text(progress, Modifier.testTag("功能引导进度"), color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge)
        }
        // 窄屏或大字体时按钮自然换行，说明可滚动，操作始终留在气泡内。
        FlowRow(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GlassTextButton(onSkip, Modifier.testTag("功能引导跳过")) { Text(stringResource(R.string.feature_guide_skip)) }
            GlassButton(onNext, Modifier.testTag("功能引导下一步")) {
                Text(stringResource(if (step == FeatureGuideStep.SETTINGS) R.string.feature_guide_finish else R.string.feature_guide_next))
            }
        }
    }
}
