package com.yangsong.lizhang.core.common

/** 只归类证据的充分性，不以状态回调或调度计数代替实际合成画面验收。 */
internal data class StartupFrameEvidence(
    val validFrames: Int,
    val changedPixels: Int,
    val conservativeSpanNanos: Long,
    val maximumCaptureNanos: Long,
    val maximumCallbackGapNanos: Long,
    val startedBeforeContentCommit: Boolean = false,
    val startedBeforeSplashRemoval: Boolean = false,
)

internal enum class StartupFrameDiagnosis(val description: String) {
    HANDOFF_ORDER("动画先于内容提交或系统启动层移除；启动交接次序异常，需要核对系统画面"),
    COMPLETE("系统画面确有跨时间中间变化，当前采集覆盖充分"),
    COLLECTION_INSUFFICIENT("截图调用吞吐不足，动画帧回调仍连续；画面覆盖不足不能判为生产动画已通过"),
    RENDERING_UNRESOLVED("动画帧回调存在长间隔；采集不足与实际渲染停顿仍须连续系统录制裁决"),
    UNKNOWN("当前真实画面不足以归因；保留失败，需连续系统录制补足画面证据"),
}

internal fun diagnoseStartupFrames(evidence: StartupFrameEvidence): StartupFrameDiagnosis = when {
    evidence.startedBeforeContentCommit || evidence.startedBeforeSplashRemoval -> StartupFrameDiagnosis.HANDOFF_ORDER
    evidence.validFrames >= 3 && evidence.changedPixels >= 60 && evidence.conservativeSpanNanos >= 140_800_000L -> StartupFrameDiagnosis.COMPLETE
    // 176ms 是设计时长的 20%，仅用于指出采集调用跨越较大时间；不改变任何验收阈值。
    evidence.maximumCaptureNanos >= 176_000_000L && evidence.maximumCallbackGapNanos < 100_000_000L -> StartupFrameDiagnosis.COLLECTION_INSUFFICIENT
    evidence.maximumCallbackGapNanos >= 176_000_000L -> StartupFrameDiagnosis.RENDERING_UNRESOLVED
    else -> StartupFrameDiagnosis.UNKNOWN
}
