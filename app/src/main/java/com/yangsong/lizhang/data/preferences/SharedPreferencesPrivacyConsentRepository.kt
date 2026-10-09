package com.yangsong.lizhang.data.preferences

import android.content.Context
import com.yangsong.lizhang.domain.legal.LegalPolicy
import com.yangsong.lizhang.domain.privacy.PrivacyConsentState
import com.yangsong.lizhang.domain.repository.PrivacyConsentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 隐私确认独立于业务数据库与旧功能引导，不迁移或删除任何账本及引导偏好。 */
class SharedPreferencesPrivacyConsentRepository(context: Context) : PrivacyConsentRepository {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(PrivacyConsentState(
        acceptedVersion = preferences.getString(ACCEPTED_VERSION, null),
        declinedVersion = preferences.getString(DECLINED_VERSION, null),
    ))
    override val state = mutableState.asStateFlow()

    @Synchronized override fun acceptCurrentPolicy(): Boolean {
        val saved = preferences.edit().putString(ACCEPTED_VERSION, LegalPolicy.CURRENT_VERSION)
            .remove(DECLINED_VERSION).commit()
        if (saved) mutableState.value = PrivacyConsentState(acceptedVersion = LegalPolicy.CURRENT_VERSION)
        return saved
    }

    @Synchronized override fun declineCurrentPolicy(): Boolean {
        // 拒绝新版本不删除旧确认记录，保留账本并阻止当前版本业务处理。
        val saved = preferences.edit().putString(DECLINED_VERSION, LegalPolicy.CURRENT_VERSION).commit()
        if (saved) mutableState.value = mutableState.value.copy(declinedVersion = LegalPolicy.CURRENT_VERSION)
        return saved
    }

    companion object {
        const val FILE_NAME = "privacy_consent_preferences"
        const val ACCEPTED_VERSION = "accepted_policy_version"
        const val DECLINED_VERSION = "declined_policy_version"
    }
}
