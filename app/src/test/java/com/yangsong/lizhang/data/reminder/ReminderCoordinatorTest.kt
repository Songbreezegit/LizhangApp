package com.yangsong.lizhang.data.reminder

import com.yangsong.lizhang.domain.reminder.*
import com.yangsong.lizhang.domain.repository.ReminderRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReminderCoordinatorTest {
    @Test fun 启动幂等且设置和独立提醒变化才触发同步() = runTest {
        val repository = FakeReminderRepository()
        val coordinator = ReminderCoordinator(repository, backgroundScope)
        coordinator.start()
        coordinator.start()
        runCurrent()
        assertEquals(1, repository.calls)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(2, repository.calls)
        repository.save(IndependentReminder(1, "测试安排", LocalDate.now().plusDays(1)))
        runCurrent()
        assertEquals(3, repository.calls)
        repository.delete(1)
        runCurrent()
        assertEquals(4, repository.calls)
        coordinator.refresh()
        assertEquals(5, repository.calls)
    }
}
private class FakeReminderRepository : ReminderRepository {
    override val settings = MutableStateFlow(ReminderSettings())
    override val reminders = MutableStateFlow<List<IndependentReminder>>(emptyList())
    var calls = 0
    override fun setEnabled(enabled: Boolean) { settings.value = settings.value.copy(enabled = enabled) }
    override fun updateSchedule(advanceDays: Int, hour: Int, minute: Int) { settings.value = settings.value.copy(advanceDays = advanceDays, hour = hour, minute = minute) }
    override fun save(reminder: IndependentReminder) { reminders.value = reminders.value + reminder }
    override fun delete(id: Long) { reminders.value = reminders.value.filterNot { it.id == id } }
    override fun setReminderEnabled(id: Long, enabled: Boolean) = Unit
    override fun markNotified(id: Long, occurrence: LocalDate) = Unit
    override fun synchronize() { calls++ }
}
