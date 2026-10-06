package com.yangsong.lizhang.domain.onboarding

/** 稳定字符串用于持久化；完成状态也是同一状态机的一部分。 */
enum class FeatureGuideStep(val storedValue: String, val number: Int) {
    ADD_RECORD("add_record", 1),
    CONTACTS("contacts", 2),
    REMINDERS("reminders", 3),
    SETTINGS("settings", 4),
    COMPLETED("completed", 0);

    fun next(): FeatureGuideStep = when (this) {
        ADD_RECORD -> CONTACTS
        CONTACTS -> REMINDERS
        REMINDERS -> SETTINGS
        SETTINGS, COMPLETED -> COMPLETED
    }
}

object FeatureGuidePolicy {
    fun initialStep(savedValue: String?, onboardingCompleted: Boolean): FeatureGuideStep =
        if (savedValue != null) {
            // 已有状态优先于安装痕迹；无法识别的值结束引导，避免升级后反复打扰。
            FeatureGuideStep.entries.firstOrNull { it.storedValue == savedValue } ?: FeatureGuideStep.COMPLETED
        } else if (onboardingCompleted) FeatureGuideStep.COMPLETED else FeatureGuideStep.ADD_RECORD
}
