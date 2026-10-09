package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.yangsong.lizhang.domain.repository.PrivacyConsentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PrivacyConsentViewModel(private val repository: PrivacyConsentRepository) : ViewModel() {
    val state = repository.state
    private val mutableSaveFailed = MutableStateFlow(false)
    val saveFailed = mutableSaveFailed.asStateFlow()

    fun accept(): Boolean = repository.acceptCurrentPolicy().also { mutableSaveFailed.value = !it }
    fun decline() { repository.declineCurrentPolicy() }

    companion object {
        fun factory(repository: PrivacyConsentRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) = PrivacyConsentViewModel(repository) as T
        }
    }
}
