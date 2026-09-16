package com.yangsong.lizhang.ui.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update

/**
 * 为可观察数据提供显式重试信号。
 *
 * 每次重试都会重新订阅 Repository 数据流；单次订阅失败只结束该次订阅，
 * 不会让后续重试失效。
 */
internal class RetrySignal {
    private val generation = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> flow(
        source: () -> Flow<T>,
        onError: (Throwable) -> T,
    ): Flow<T> = generation.flatMapLatest {
        source().catch { error -> emit(onError(error)) }
    }

    fun retry() {
        generation.update { it + 1L }
    }
}
