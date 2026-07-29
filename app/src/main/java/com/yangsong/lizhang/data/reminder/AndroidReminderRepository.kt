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
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.reminder.PlannedReminder
import com.yangsong.lizhang.domain.reminder.ReminderPlanner
import com.yangsong.lizhang.domain.reminder.ReminderSettings
import com.yangsong.lizhang.domain.reminder.isSupportedReminderAdvanceDays
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import com.yangsong.lizhang.domain.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class AndroidReminderRepository(private val context: Context) : ReminderRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val _settings = MutableStateFlow(
        ReminderSettings(
            enabled = preferences.getBoolean(KEY_ENABLED, false),
            advanceDays = preferences.getInt(KEY_ADVANCE_DAYS, 0)
                .takeIf(::isSupportedReminderAdvanceDays) ?: 0,
            hour = preferences.getInt(KEY_HOUR, 9),
            minute = preferences.getInt(KEY_MINUTE, 0),
        ),
    )
    override val settings = _settings

    init {
        createNotificationChannel()
    }

    override fun setEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ENABLED, enabled) }
        _settings.value = _settings.value.copy(enabled = enabled)
        if (!enabled) cancelScheduled()
    }

    override fun updateSchedule(advanceDays: Int, hour: Int, minute: Int) {
        val updated = _settings.value.copy(
            advanceDays = advanceDays,
            hour = hour,
            minute = minute,
        )
        preferences.edit {
            putInt(KEY_ADVANCE_DAYS, updated.advanceDays)
            putInt(KEY_HOUR, updated.hour)
            putInt(KEY_MINUTE, updated.minute)
        }
        _settings.value = updated
    }

    override fun synchronize(records: List<GiftRecordWithContact>) {
        cancelScheduled()
        val currentSettings = settings.value
        if (!currentSettings.enabled) return
        val tokens = ReminderPlanner.plan(
            records = records,
            lookaheadDays = 366,
            advanceDays = currentSettings.advanceDays,
            reminderHour = currentSettings.hour,
            reminderMinute = currentSettings.minute,
        ).map { reminder ->
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerAt,
                reminder.pendingIntent(PendingIntent.FLAG_UPDATE_CURRENT),
            )
            reminder.token()
        }.toSet()
        preferences.edit { putStringSet(KEY_SCHEDULED, tokens) }
    }

    private fun cancelScheduled() {
        preferences.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().forEach { token ->
            val parts = token.split('|')
            val recordId = parts.getOrNull(0)?.toLongOrNull() ?: return@forEach
            val triggerAt = parts.getOrNull(1)?.toLongOrNull() ?: return@forEach
            val reminder = PlannedReminder(recordId, "", EventType.OTHER, triggerAt)
            alarmManager.cancel(reminder.pendingIntent(PendingIntent.FLAG_UPDATE_CURRENT))
        }
        preferences.edit { remove(KEY_SCHEDULED) }
    }

    private fun PlannedReminder.pendingIntent(extraFlags: Int): PendingIntent {
        val intent = Intent(context, ReminderNotificationReceiver::class.java).apply {
            data = reminderUri(recordId, triggerAt)
            putExtra(EXTRA_RECORD_ID, recordId)
            putExtra(EXTRA_CONTACT_NAME, contactName)
            putExtra(EXTRA_EVENT_TYPE, eventType.name)
            putExtra(EXTRA_ADVANCE_DAYS, advanceDays)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            extraFlags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createNotificationChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun PlannedReminder.token() = "$recordId|$triggerAt"
}

class ReminderCoordinator(
    private val giftRecordRepository: GiftRecordRepository,
    private val reminderRepository: ReminderRepository,
    private val scope: CoroutineScope,
) {
    private var synchronizationJob: Job? = null

    fun start() {
        if (synchronizationJob != null) return
        synchronizationJob = combine(
            giftRecordRepository.observeAll(),
            reminderRepository.settings,
        ) { records, _ -> records }
            .onEach(reminderRepository::synchronize)
            .launchIn(scope)
    }

    fun refresh() {
        scope.launch {
            reminderRepository.synchronize(giftRecordRepository.observeAll().first())
        }
    }
}

class ReminderNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(KEY_ENABLED, false)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val recordId = intent.getLongExtra(EXTRA_RECORD_ID, 0)
        val contactName = intent.getStringExtra(EXTRA_CONTACT_NAME).orEmpty()
        val eventType = runCatching {
            enumValueOf<EventType>(intent.getStringExtra(EXTRA_EVENT_TYPE).orEmpty())
        }.getOrDefault(EventType.OTHER)
        val advanceDays = intent.getIntExtra(EXTRA_ADVANCE_DAYS, 0)
        val openApp = PendingIntent.getActivity(
            context,
            recordId.notificationId(),
            ReminderNavigationContract.createOpenRecordIntent(context, recordId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_gift)
            .setContentTitle(context.getString(R.string.reminder_notification_title, contactName))
            .setContentText(
                if (advanceDays == 0) {
                    context.getString(
                        R.string.reminder_notification_text,
                        context.eventTypeName(eventType),
                    )
                } else {
                    context.getString(
                        R.string.reminder_notification_text_advance,
                        context.eventTypeName(eventType),
                        advanceDays,
                    )
                },
            )
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(recordId.notificationId(), notification)
        }
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

private fun reminderUri(recordId: Long, triggerAt: Long): Uri = Uri.Builder()
    .scheme("lizhang")
    .authority("reminder")
    .appendPath(recordId.toString())
    .appendPath(triggerAt.toString())
    .build()

private fun Long.notificationId(): Int = (this xor (this ushr 32)).toInt()

private fun Context.eventTypeName(eventType: EventType): String = getString(
    when (eventType) {
        EventType.WEDDING -> R.string.event_wedding
        EventType.FULL_MONTH -> R.string.event_full_month
        EventType.BIRTHDAY -> R.string.event_birthday
        EventType.HOUSEWARMING -> R.string.event_housewarming
        EventType.FESTIVAL -> R.string.event_festival
        EventType.OTHER -> R.string.event_other
    },
)

private const val PREFERENCES_NAME = "reminder_preferences"
private const val KEY_ENABLED = "enabled"
private const val KEY_ADVANCE_DAYS = "advance_days"
private const val KEY_HOUR = "hour"
private const val KEY_MINUTE = "minute"
private const val KEY_SCHEDULED = "scheduled"
private const val CHANNEL_ID = "gift_date_reminders"
private const val EXTRA_RECORD_ID = "record_id"
private const val EXTRA_CONTACT_NAME = "contact_name"
private const val EXTRA_EVENT_TYPE = "event_type"
private const val EXTRA_ADVANCE_DAYS = "advance_days"

private val rescheduleActions = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    Intent.ACTION_DATE_CHANGED,
)
