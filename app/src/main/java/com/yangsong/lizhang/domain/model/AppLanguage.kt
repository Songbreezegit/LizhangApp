package com.yangsong.lizhang.domain.model

/** 仅用于展示语言；持久化由 Android / AppCompat 负责。 */
enum class AppLanguage(val localeTag: String) {
    SYSTEM(""), ZH_CN("zh-CN"), EN("en"), JA("ja"), KO("ko");

    companion object {
        fun fromLanguageTag(tag: String?): AppLanguage = when (tag?.substringBefore('-')) {
            "zh" -> ZH_CN
            "en" -> EN
            "ja" -> JA
            "ko" -> KO
            else -> SYSTEM
        }
    }
}
