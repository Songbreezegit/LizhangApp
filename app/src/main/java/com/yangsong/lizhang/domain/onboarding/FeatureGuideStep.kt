package com.yangsong.lizhang.domain.onboarding

/** 稳定字符串用于持久化；完成状态也是同一状态机的一部分。 */
enum class FeatureGuideStep(val storedValue: String, val number: Int) {
    ADD_RECORD("add_record", 1),
    RECORD_CONTACT("record_contact", 2),
    RECORD_AMOUNT("record_amount", 3),
    RECORD_DIRECTION("record_direction", 4),
    RECORD_SAVE("record_save", 5),
    CONTACTS("contacts", 1),
    REMINDERS("reminders", 1),
    SETTINGS("settings", 1),
    CALENDAR("calendar", 1),
    SEARCH("search", 1),
    STATISTICS("statistics", 1),
    COMPLETED("completed", 0);

    val recordStep get() = this in recordSteps
    val total get() = if (recordStep) 5 else 1

    fun next(): FeatureGuideStep = when (this) {
        ADD_RECORD -> RECORD_CONTACT
        RECORD_CONTACT -> RECORD_AMOUNT
        RECORD_AMOUNT -> RECORD_DIRECTION
        RECORD_DIRECTION -> RECORD_SAVE
        else -> COMPLETED
    }

    companion object {
        val recordSteps = setOf(ADD_RECORD, RECORD_CONTACT, RECORD_AMOUNT, RECORD_DIRECTION, RECORD_SAVE)
    }
}

/** 各页面独立记录首次介绍，不依赖访问顺序，也不进入记账流程的进度。 */
enum class FeatureGuidePage(val step: FeatureGuideStep) {
    CONTACTS(FeatureGuideStep.CONTACTS),
    REMINDERS(FeatureGuideStep.REMINDERS),
    SETTINGS(FeatureGuideStep.SETTINGS),
    CALENDAR(FeatureGuideStep.CALENDAR),
    SEARCH(FeatureGuideStep.SEARCH),
    STATISTICS(FeatureGuideStep.STATISTICS),
}

object FeatureGuidePolicy {
    fun initialStep(savedValue: String?, onboardingCompleted: Boolean): FeatureGuideStep =
        if (savedValue != null) {
            // 已有状态优先于安装痕迹；无法识别的值结束引导，避免升级后反复打扰。
            FeatureGuideStep.entries.firstOrNull { it.storedValue == savedValue && it.recordStep }
                ?: FeatureGuideStep.COMPLETED
        } else if (onboardingCompleted) FeatureGuideStep.COMPLETED else FeatureGuideStep.ADD_RECORD

    fun initialSeenPages(saved: Set<String>?, savedStep: String?, onboardingCompleted: Boolean): Set<FeatureGuidePage> {
        if (saved != null) return FeatureGuidePage.entries.filterTo(mutableSetOf()) { it.name in saved }
        // 已完成的升级用户和未知未来状态不强制重播；未完成用户按访问页面继续了解功能。
        val knownPending = FeatureGuideStep.entries.any { it.storedValue == savedStep && it != FeatureGuideStep.COMPLETED }
        return if (onboardingCompleted && !knownPending) FeatureGuidePage.entries.toSet() else emptySet()
    }
}
