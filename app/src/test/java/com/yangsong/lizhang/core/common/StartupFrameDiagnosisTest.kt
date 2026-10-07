package com.yangsong.lizhang.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupFrameDiagnosisTest {
    private val insufficient = StartupFrameEvidence(1, 0, 0L, 30_000_000L, 16_700_000L)

    @Test fun 长截图但帧回调连续只归因采集不足不豁免画面失败() {
        assertEquals(StartupFrameDiagnosis.COLLECTION_INSUFFICIENT,
            diagnoseStartupFrames(insufficient.copy(maximumCaptureNanos = 176_000_000L)))
        assertEquals(StartupFrameDiagnosis.UNKNOWN,
            diagnoseStartupFrames(insufficient.copy(maximumCaptureNanos = 175_999_999L)))
    }

    @Test fun 截图及回调都长停顿时保留渲染未决结论() {
        assertEquals(StartupFrameDiagnosis.RENDERING_UNRESOLVED,
            diagnoseStartupFrames(insufficient.copy(maximumCaptureNanos = 300_000_000L,
                maximumCallbackGapNanos = 250_000_000L)))
    }

    @Test fun 缺少像素变化或实际跨度不能靠状态回调判定通过() {
        assertEquals(StartupFrameDiagnosis.UNKNOWN,
            diagnoseStartupFrames(insufficient.copy(validFrames = 20)))
        assertEquals(StartupFrameDiagnosis.UNKNOWN,
            diagnoseStartupFrames(insufficient.copy(validFrames = 3, changedPixels = 60,
                conservativeSpanNanos = 140_799_999L)))
        assertEquals(StartupFrameDiagnosis.COMPLETE,
            diagnoseStartupFrames(insufficient.copy(validFrames = 3, changedPixels = 60,
                conservativeSpanNanos = 140_800_000L)))
    }

    @Test fun 启动交接次序异常优先保留即使取得多张画面() {
        assertEquals(StartupFrameDiagnosis.HANDOFF_ORDER,
            diagnoseStartupFrames(insufficient.copy(validFrames = 10, changedPixels = 100,
                conservativeSpanNanos = 500_000_000L, startedBeforeSplashRemoval = true)))
    }
}
