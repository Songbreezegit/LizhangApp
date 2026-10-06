package com.yangsong.lizhang.domain.model

import java.util.Locale

/** 仅用于展示语言；持久化由 Android / AppCompat 负责。 */
enum class AppLanguage(val localeTag: String) {
    SYSTEM(""), ZH_CN("zh-CN"), ZH_HANT("zh-Hant"), EN("en"), JA("ja"), KO("ko"), ES("es"), FR("fr");

    companion object {
        fun fromLanguageTag(tag: String?): AppLanguage {
            val locale = Locale.forLanguageTag(tag.orEmpty().replace('_', '-'))
            return when (locale.language) {
                "zh" -> when {
                    locale.script == "Hant" -> ZH_HANT
                    locale.script == "Hans" -> ZH_CN
                    locale.country in setOf("TW", "HK", "MO") -> ZH_HANT
                    else -> ZH_CN
                }
                "en" -> EN
                "ja" -> JA
                "ko" -> KO
                "es" -> ES
                "fr" -> FR
                else -> SYSTEM
            }
        }
    }
}
