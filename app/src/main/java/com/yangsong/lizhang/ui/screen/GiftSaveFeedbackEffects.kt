package com.yangsong.lizhang.ui.screen

import android.os.SystemClock
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.yangsong.lizhang.ui.component.GlassSnackbarVisuals
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val GIFT_SAVE_RETURN_DELAY_MILLIS = 450L

/** 成功反馈与返回并行；返回标记和截止时间跨重建保存，避免重复返回或重新等待。 */
@Composable
fun GiftSaveFeedbackEffects(
    isSaved: Boolean,
    operationFailed: Boolean,
    snackbar: SnackbarHostState,
    savedMessage: String,
    failedMessage: String,
    onFailureConsumed: () -> Unit,
    onBack: () -> Unit,
) {
    val latestOnBack by rememberUpdatedState(onBack)
    val latestOnFailureConsumed by rememberUpdatedState(onFailureConsumed)
    val scope = rememberCoroutineScope()
    var returned by rememberSaveable { mutableStateOf(false) }
    var returnAt by rememberSaveable { mutableLongStateOf(0L) }

    LaunchedEffect(isSaved) {
        if (isSaved && !returned) {
            snackbar.currentSnackbarData?.dismiss()
            val feedback = launch(start = CoroutineStart.UNDISPATCHED) {
                snackbar.showSnackbar(GlassSnackbarVisuals(savedMessage))
            }
            if (returnAt == 0L) returnAt = SystemClock.uptimeMillis() + GIFT_SAVE_RETURN_DELAY_MILLIS
            try {
                delay((returnAt - SystemClock.uptimeMillis()).coerceAtLeast(0L))
                returned = true
                latestOnBack()
            } finally {
                feedback.cancel()
            }
        }
    }
    LaunchedEffect(operationFailed) {
        if (operationFailed) {
            // 消费事件后仍由页面作用域展示，重组或恢复不会再次弹出同一次失败。
            latestOnFailureConsumed()
            snackbar.currentSnackbarData?.dismiss()
            scope.launch { snackbar.showSnackbar(GlassSnackbarVisuals(failedMessage, isError = true)) }
        }
    }
}
