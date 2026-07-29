package com.yangsong.lizhang.performance

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yangsong.lizhang.data.local.LiZhangDatabase
import com.yangsong.lizhang.data.local.entity.ContactEntity
import com.yangsong.lizhang.data.local.entity.GiftRecordEntity
import com.yangsong.lizhang.data.repository.RoomContactRepository
import com.yangsong.lizhang.data.repository.RoomGiftRecordRepository
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.ui.viewmodel.aggregateStatistics
import java.util.Calendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 独立内存数据库性能探针，不接触用户数据库。
 *
 * 耗时只用于同一台测试设备上的优化前后对比，不设置易受设备负载影响的硬阈值。
 */
@RunWith(AndroidJUnit4::class)
class LargeDatasetPerformanceInstrumentedTest {
    private lateinit var database: LiZhangDatabase
    private lateinit var contactRepository: RoomContactRepository
    private lateinit var giftRepository: RoomGiftRecordRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LiZhangDatabase::class.java).build()
        contactRepository = RoomContactRepository(database.contactDao())
        giftRepository = RoomGiftRecordRepository(database.giftRecordDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun 测量一百一千和五千条记录的主要读取链路() = runBlocking {
        listOf(100, 1_000, 5_000).forEach { count ->
            database.clearAllTables()
            seed(count)

            val allResult = measure {
                giftRepository.observeAll().first()
            }
            val searchResult = measure {
                giftRepository.observeSearch("共同备注").first()
            }
            val contactResult = measure {
                contactRepository.observeContactSummaries("联系人").first()
            }
            val statisticsResult = measure {
                aggregateStatistics(
                    records = allResult.value,
                    year = TEST_YEAR,
                    currentYear = TEST_YEAR,
                )
            }

            assertEquals(count, allResult.value.size)
            assertEquals(count, searchResult.value.size)
            assertEquals(count, contactResult.value.size)
            assertEquals(count * AMOUNT_IN_CENTS, statisticsResult.value.received + statisticsResult.value.given)

            Log.i(
                LOG_TAG,
                "数量=$count；全量读取=${allResult.elapsedMillis}ms；" +
                    "搜索=${searchResult.elapsedMillis}ms；联系人汇总=${contactResult.elapsedMillis}ms；" +
                    "内存统计=${statisticsResult.elapsedMillis}ms",
            )
        }
    }

    private suspend fun seed(count: Int) {
        val contacts = (1..count).map { sequence ->
            ContactEntity(
                id = sequence.toLong(),
                name = "性能联系人${sequence.toString().padStart(4, '0')}",
                phone = "138${sequence.toString().padStart(8, '0')}",
                relationship = "测试关系",
                notes = "独立测试数据",
                createdTime = sequence.toLong(),
            )
        }
        val yearStart = Calendar.getInstance().apply {
            clear()
            set(TEST_YEAR, Calendar.JANUARY, 1)
        }.timeInMillis
        val records = (1..count).map { sequence ->
            GiftRecordEntity(
                id = sequence.toLong(),
                contactId = sequence.toLong(),
                amountInCents = AMOUNT_IN_CENTS,
                eventType = EventType.entries[sequence % EventType.entries.size],
                eventDate = yearStart + (sequence % 365) * DAY_MILLIS,
                direction = if (sequence % 2 == 0) {
                    GiftDirection.RECEIVED
                } else {
                    GiftDirection.GIVEN
                },
                notes = "共同备注${sequence % 20}",
                createdTime = sequence.toLong(),
            )
        }
        database.withTransaction {
            database.contactDao().insertAll(contacts)
            database.giftRecordDao().insertAll(records)
        }
    }

    private inline fun <T> measure(block: () -> T): TimedResult<T> {
        val startedAt = SystemClock.elapsedRealtimeNanos()
        val value = block()
        val elapsedMillis = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
        return TimedResult(value, elapsedMillis)
    }

    private data class TimedResult<T>(
        val value: T,
        val elapsedMillis: Double,
    )

    private companion object {
        const val LOG_TAG = "LizhangPerformance"
        const val TEST_YEAR = 2026
        const val AMOUNT_IN_CENTS = 10_000L
        const val DAY_MILLIS = 86_400_000L
    }
}
