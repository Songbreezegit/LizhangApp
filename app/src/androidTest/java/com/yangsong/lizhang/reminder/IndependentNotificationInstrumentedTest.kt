package com.yangsong.lizhang.reminder

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.data.reminder.*
import com.yangsong.lizhang.domain.reminder.IndependentReminder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.*
import org.junit.Assert.*

class IndependentNotificationInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = ApplicationProvider.getApplicationContext<LiZhangApplication>()
    private val repository get() = app.appContainer.reminderRepository
    private val manager get() = app.getSystemService(NotificationManager::class.java)
    @Before fun 准备私有合成提醒() {
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        instrumentation.runOnMainSync {
            repository.reminders.value.forEach { repository.delete(it.id) }
            repository.setEnabled(true)
            val time = LocalTime.now()
            repository.updateSchedule(1, time.hour, time.minute)
        }
    }
    @After fun 清理通知和提醒() {
        instrumentation.runOnMainSync {
            repository.setEnabled(false)
            repository.reminders.value.forEach { repository.delete(it.id) }
            manager.cancelAll()
        }
    }
    private fun broadcast(id: Long, date: LocalDate): Intent {
        val settings = repository.settings.value
        val trigger = date.minusDays(settings.advanceDays.toLong()).atTime(settings.hour, settings.minute)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Intent(app, ReminderNotificationReceiver::class.java).setData(Uri.parse("lizhang://independent-reminder/$id/$trigger"))
            .putExtra("id", id).putExtra("occurrence", date.toString()).putExtra("trigger", trigger)
    }
    @Test fun 系统通知成功消费一次且重复广播不重复通知() {
        var id = 0L
        var sent: Intent? = null
        instrumentation.runOnMainSync {
            val date = LocalDate.now().plusDays(1)
            repository.save(IndependentReminder(title = "测试安排", date = date))
            id = repository.reminders.value.single().id
            val intent = broadcast(id, date)
            sent = intent
            ReminderNotificationReceiver().onReceive(app, intent)
            assertEquals(date, repository.reminders.value.single().lastNotifiedDate)
        }
        fun awaitNotification(present: Boolean) {
            val deadline = android.os.SystemClock.uptimeMillis() + 3000
            while ((manager.activeNotifications.any { it.id == id.toInt() }) != present &&
                android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(30)
            assertEquals("系统通知队列已确认状态", present, manager.activeNotifications.any { it.id == id.toInt() })
        }
        awaitNotification(true)
        manager.cancel(id.toInt())
        // API 26 的系统服务异步取消，需要确认旧通知移除后再检查重复广播。
        awaitNotification(false)
        instrumentation.runOnMainSync { ReminderNotificationReceiver().onReceive(app, sent!!) }
        android.os.SystemClock.sleep(200)
        assertNull(manager.activeNotifications.firstOrNull { it.id == id.toInt() })
    }
    @Test fun 删除停用旧设置和旧礼金广播不能消费提醒() {
        instrumentation.runOnMainSync {
            val date = LocalDate.now().plusDays(1)
            repository.save(IndependentReminder(title = "测试安排", date = date))
            val value = repository.reminders.value.single()
            val intent = broadcast(value.id, date)
            repository.setReminderEnabled(value.id, false)
            ReminderNotificationReceiver().onReceive(app, intent)
            assertNull(repository.reminders.value.single().lastNotifiedDate)
            repository.setReminderEnabled(value.id, true)
            repository.updateSchedule(0, 9, 0)
            ReminderNotificationReceiver().onReceive(app, intent)
            assertNull(repository.reminders.value.single().lastNotifiedDate)
            ReminderNotificationReceiver().onReceive(app, Intent(intent).setData(Uri.parse("lizhang://reminder/1/1")))
            assertNull(repository.reminders.value.single().lastNotifiedDate)
            repository.delete(value.id)
            ReminderNotificationReceiver().onReceive(app, intent)
            assertNull(manager.activeNotifications.firstOrNull { it.id == value.id.toInt() })
        }
    }
    @Test fun 升级撤销旧礼金闹钟且不创建独立提醒() {
        instrumentation.runOnMainSync {
            val uri = Uri.parse("lizhang://reminder/998/123456")
            val intent = Intent(app, ReminderNotificationReceiver::class.java).setData(uri)
            PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            app.getSharedPreferences("reminder_preferences", 0).edit().putStringSet("scheduled", setOf("998|123456")).commit()
            val upgraded = AndroidReminderRepository(app)
            assertNull(PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
            assertTrue(upgraded.reminders.value.isEmpty())
        }
    }
}
