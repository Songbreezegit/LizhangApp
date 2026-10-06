package com.yangsong.lizhang.ui.onboarding

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.onboarding.ContextualHint
import com.yangsong.lizhang.ui.component.GlassCard
import com.yangsong.lizhang.ui.viewmodel.OnboardingViewModel

@Composable
fun rememberContextualHint(viewModel: OnboardingViewModel?, hint: ContextualHint, loaded: Boolean, empty: Boolean): MutableState<Boolean> {
    val visible = remember { mutableStateOf(false) }
    LaunchedEffect(loaded, empty) {
        if (!loaded || !empty) visible.value = false
        else if (viewModel?.claimHint(hint, loaded, empty) == true) visible.value = true
    }
    return visible
}

/** 只观察触摸，不消费事件；空白点击、原有按钮和滚动均保持正常操作。 */
fun Modifier.dismissContextualHintOnTouch(visible: Boolean, onDismiss: () -> Unit): Modifier =
    if (!visible) this else pointerInput(onDismiss) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onDismiss()
        }
    }

@Composable
fun ContextualHintBubble(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier.testTag("首次操作提示")) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            IconButton(onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.onboarding_hint_close)) }
        }
    }
}
