package com.yangsong.lizhang.data.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.R
import com.yangsong.lizhang.core.common.ReminderNavigationContract
import com.yangsong.lizhang.domain.reminder.*
import com.yangsong.lizhang.domain.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 提醒与原有提醒偏好一样仅保存在应用私有区域；不读取礼金表。 */
class AndroidReminderRepository(private val context: Context) : ReminderRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    override val settings = MutableStateFlow(ReminderSettings(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        advanceDays = preferences.getInt(KEY_ADVANCE_DAYS, 0).takeIf(::isSupportedReminderAdvanceDays) ?: 0,
        hour = preferences.getInt(KEY_HOUR, 9).coerceIn(0, 23),
        minute = preferences.getInt(KEY_MINUTE, 0).coerceIn(0, 59),
    ))
    override val reminders = MutableStateFlow(readReminders())
    init {
        // 升级时取消旧的礼金推导闹钟，不把历史礼金擅自转换为独立提醒。
        preferences.getStringSet("scheduled", emptySet()).orEmpty().forEach { token ->
            val parts = token.split('|')
            if (parts.size == 2) cancelIntent(Uri.parse("lizhang://reminder/${parts[0]}/${parts[1]}"))
        }
        preferences.edit { remove("scheduled") }
        context.getSystemService(NotificationManager::class.java).deleteNotificationChannel("gift_date_reminders")
    }
    override fun setEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ENABLED, enabled) }
        settings.value = settings.value.copy(enabled = enabled)
        if (!enabled) cancelScheduled()
    }
    override fun updateSchedule(advanceDays: Int, hour: Int, minute: Int) {
        val updated = settings.value.copy(advanceDays = advanceDays, hour = hour, minute = minute)
        preferences.edit { putInt(KEY_ADVANCE_DAYS, advanceDays); putInt(KEY_HOUR, hour); putInt(KEY_MINUTE, minute) }
        settings.value = updated
    }
    @Synchronized override fun save(reminder: IndependentReminder) {
        require(reminder.title.trim().length in 1..80)
        require(IndependentReminderPlanner.canSave(reminder.date))
        val id = if (reminder.id > 0) reminder.id else preferences.getLong("next_id", 1)
        require(reminder.id == 0L || reminders.value.any { it.id == reminder.id })
        val value = reminder.copy(id = id, title = reminder.title.trim(), lastNotifiedDate = null)
        persist(reminders.value.filterNot { it.id == id } + value, maxOf(id + 1, preferences.getLong("next_id", 1)))
    }
    @Synchronized override fun delete(id: Long) { persist(reminders.value.filterNot { it.id == id }) }
    @Synchronized override fun setReminderEnabled(id: Long, enabled: Boolean) {
        persist(reminders.value.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }
    @Synchronized override fun markNotified(id: Long, occurrence: LocalDate) {
        persist(reminders.value.map { if (it.id == id) it.copy(lastNotifiedDate = occurrence) else it })
    }
    private fun persist(values: List<IndependentReminder>, nextId: Long = preferences.getLong("next_id", 1)) {
        val array = JSONArray()
        values.forEach { value -> array.put(JSONObject().apply {
            put("id", value.id); put("title", value.title); put("date", value.date.toString())
            put("annually", value.annually); put("enabled", value.enabled)
            value.lastNotifiedDate?.let { put("notified", it.toString()) }
        }) }
        check(preferences.edit().putString("independent_reminders", array.toString()).putLong("next_id", nextId).commit())
        reminders.value = values
    }
    private fun readReminders(): List<IndependentReminder> = runCatching {
        val array = JSONArray(preferences.getString("independent_reminders", "[]"))
        (0 until array.length()).mapNotNull { index -> runCatching {
            val value = array.getJSONObject(index)
            IndependentReminder(value.getLong("id"), value.getString("title"), LocalDate.parse(value.getString("date")),
                value.optBoolean("annually"), value.optBoolean("enabled", true),
                value.optString("notified").takeIf { it.isNotBlank() }?.let(LocalDate::parse))
        }.getOrNull() }.distinctBy { it.id }
    }.getOrDefault(emptyList())
    @Synchronized override fun synchronize() {
        cancelScheduled()
        if (!settings.value.enabled) return
        val localized = ContextCompat.getContextForLanguage(context)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, localized.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = localized.getString(R.string.reminder_channel_description)
            })
        val tokens = reminders.value.mapNotNull { IndependentReminderPlanner.next(it, settings.value) }.map { plan ->
            val intent = Intent(context, ReminderNotificationReceiver::class.java).apply {
                data = reminderUri(plan.reminder.id, plan.triggerAt)
                putExtra("id", plan.reminder.id); putExtra("occurrence", plan.occurrence.toString())
                putExtra("trigger", plan.triggerAt)
            }
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.triggerAt,
                PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            "${plan.reminder.id}|${plan.triggerAt}"
        }.toSet()
        preferences.edit { putStringSet("independent_scheduled", tokens) }
    }
    private fun cancelScheduled() {
        preferences.getStringSet("independent_scheduled", emptySet()).orEmpty().forEach { token ->
            val parts = token.split('|')
            if (parts.size == 2) cancelIntent(Uri.parse("lizhang://independent-reminder/${parts[0]}/${parts[1]}"))
        }
        preferences.edit { remove("independent_scheduled") }
    }
    private fun cancelIntent(uri: Uri) {
        val intent = Intent(context, ReminderNotificationReceiver::class.java).setData(uri)
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            alarmManager.cancel(it); it.cancel()
        }
    }
}

/** 只观察独立提醒及其设置；保存礼金不会触发调度。 */
class ReminderCoordinator(private val repository: ReminderRepository, private val scope: CoroutineScope) {
    private var synchronizationJob: Job? = null
    fun start() {
        if (synchronizationJob != null) return
        synchronizationJob = combine(repository.settings, repository.reminders) { _, _ -> Unit }
            .onEach { repository.synchronize() }.launchIn(scope)
    }
    fun refresh() { repository.synchronize() }
}

class ReminderNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 旧礼金闹钟及未知来源不再发送通知。
        if (intent.data?.authority != "independent-reminder") return
        val app = context.applicationContext as? LiZhangApplication ?: return
        val repository = app.appContainer.reminderRepository
        if (!repository.settings.value.enabled) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val id = intent.getLongExtra("id", 0)
        val reminder = repository.reminders.value.firstOrNull { it.id == id && it.enabled } ?: return
        val occurrence = runCatching { LocalDate.parse(intent.getStringExtra("occurrence")) }.getOrNull() ?: return
        if ((reminder.lastNotifiedDate != null && occurrence <= reminder.lastNotifiedDate) || occurrence < LocalDate.now()) return
        val trigger = intent.getLongExtra("trigger", 0)
        if (trigger <= 0 || trigger > System.currentTimeMillis()) return
        // 修改设置后到达的旧广播不能消费新提醒。
        val expected = IndependentReminderPlanner.next(reminder, repository.settings.value, trigger - 1)
        if (expected?.occurrence != occurrence || expected.triggerAt != trigger) return
        val localized = ContextCompat.getContextForLanguage(context)
        val open = PendingIntent.getActivity(context, id.toInt(), ReminderNavigationContract.createOpenRemindersIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_notification_gift)
            .setContentTitle(reminder.title).setContentText(localized.getString(R.string.independent_notification_text, occurrence.toString()))
            .setContentIntent(open).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER).build()
        runCatching { NotificationManagerCompat.from(context).notify(id.toInt(), notification) }
            .onSuccess { repository.markNotified(id, occurrence) }
    }
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in rescheduleActions) return
        val application = context.applicationContext as? LiZhangApplication ?: return
        application.appContainer.startReminderCoordination()
        application.appContainer.refreshReminderSchedules()
    }
}
private fun reminderUri(id: Long, trigger: Long): Uri = Uri.parse("lizhang://independent-reminder/$id/$trigger")
private const val PREFERENCES_NAME = "reminder_preferences"
private const val KEY_ENABLED = "enabled"
private const val KEY_ADVANCE_DAYS = "advance_days"
private const val KEY_HOUR = "hour"
private const val KEY_MINUTE = "minute"
private const val CHANNEL_ID = "independent_date_reminders"
private val rescheduleActions = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED)
