package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface ReminderLaunchResult {
    data class OpenRecord(val recordId: Long) : ReminderLaunchResult
    data object RecordUnavailable : ReminderLaunchResult
}

class ReminderLaunchViewModel(
    private val repository: GiftRecordRepository,
) : ViewModel() {
    private val resultChannel = Channel<ReminderLaunchResult>(Channel.BUFFERED)
    val results = resultChannel.receiveAsFlow()
    private var resolutionJob: Job? = null

    fun resolve(recordId: Long) {
        resolutionJob?.cancel()
        resolutionJob = viewModelScope.launch {
            val exists = runCatching { repository.observeRecord(recordId).first() }
                .getOrNull() != null
            resultChannel.send(
                if (exists) ReminderLaunchResult.OpenRecord(recordId)
                else ReminderLaunchResult.RecordUnavailable,
            )
        }
    }

    companion object {
        fun factory(repository: GiftRecordRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ReminderLaunchViewModel(repository) as T
        }
    }
}
