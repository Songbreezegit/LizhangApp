package com.yangsong.lizhang.domain.repository

import com.yangsong.lizhang.domain.model.AppThemeMode
import kotlinx.coroutines.flow.StateFlow

interface ThemeRepository {
    val themeMode: StateFlow<AppThemeMode>
    fun setThemeMode(mode: AppThemeMode)
}
