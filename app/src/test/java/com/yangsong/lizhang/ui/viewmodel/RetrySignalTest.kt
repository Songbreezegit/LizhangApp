package com.yangsong.lizhang.ui.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetrySignalTest {
    @Test
    fun `数据流失败后点击重试会重新订阅`() = runTest {
        val signal = RetrySignal()
        var subscriptions = 0
        val values = mutableListOf<Int>()
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            signal.flow(
                source = {
                    flow {
                        subscriptions += 1
                        if (subscriptions == 1) error("首次加载失败")
                        emit(42)
                    }
                },
                onError = { -1 },
            ).take(2).toList(values)
        }

        advanceUntilIdle()
        assertEquals(listOf(-1), values)

        signal.retry()
        advanceUntilIdle()

        assertEquals(listOf(-1, 42), values)
        assertEquals(2, subscriptions)
        collection.cancel()
    }
}
