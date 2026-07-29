package com.yangsong.lizhang.data.preferences

import android.content.Context
import androidx.core.content.edit
import com.yangsong.lizhang.domain.model.AppThemeMode
import com.yangsong.lizhang.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 小型同步偏好使用 SharedPreferences；业务数据仍由 Room 管理。 */
class SharedPreferencesThemeRepository(context: Context) : ThemeRepository {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(readThemeMode())

    override val themeMode = _themeMode.asStateFlow()

    override fun setThemeMode(mode: AppThemeMode) {
        if (_themeMode.value == mode) return
        preferences.edit { putString(KEY_THEME_MODE, mode.name) }
        _themeMode.value = mode
    }

    private fun readThemeMode(): AppThemeMode = runCatching {
        AppThemeMode.valueOf(preferences.getString(KEY_THEME_MODE, null).orEmpty())
    }.getOrDefault(AppThemeMode.SYSTEM)

    private companion object {
        const val FILE_NAME = "礼账显示设置"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
