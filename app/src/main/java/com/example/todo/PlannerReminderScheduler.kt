package com.example.todo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PlannerReminderScheduler(
    context: Context
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun sync(store: PlannerItemStore) {
        ReminderNotification.ensureChannel(appContext)
        cancelScheduledAlarms()

        val now = LocalDateTime.now()
        val specs = store.reminderAlarmSpecs(appContext, now)
        Log.d(TAG, "Syncing ${specs.size} alarms at $now")
        specs.forEach { spec ->
            Log.d(TAG, "  Alarm: key=${spec.key}, triggerAt=${spec.triggerAt}")
            schedule(spec)
        }

        preferences.edit()
            .putStringSet(ScheduledKeys, specs.map { it.key }.toSet())
            .apply()
    }

    private fun schedule(spec: ReminderAlarmSpec) {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: run {
            Log.e(TAG, "AlarmManager is null, cannot schedule alarm")
            return
        }
        val triggerMillis = spec.triggerAt
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        if (triggerMillis <= System.currentTimeMillis()) {
            Log.w(TAG, "Skipping past alarm: key=${spec.key}, triggerAt=${spec.triggerAt}")
            return
        }

        // Use FLAG_IMMUTABLE only — FLAG_UPDATE_CURRENT is incompatible with
        // FLAG_IMMUTABLE on some Android versions and has no effect anyway
        // (immutable PendingIntents cannot have their extras updated).
        // Since cancelScheduledAlarms() already cancels and invalidates old
        // PendingIntents via pendingIntent.cancel(), a fresh one is created here.
        val pendingIntent = createPendingIntent(
            context = appContext,
            key = spec.key,
            notificationId = spec.notificationId,
            title = spec.title,
            body = spec.body
        ) ?: run {
            Log.e(TAG, "Failed to create PendingIntent for key=${spec.key}")
            return
        }

        val alarmResult = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent
                    )
                    "setExactAndAllowWhileIdle"
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent
                    )
                    "setAndAllowWhileIdle"
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent
                )
                "setExactAndAllowWhileIdle(pre-S)"
            }
        }

        if (alarmResult.isSuccess) {
            Log.d(TAG, "Scheduled alarm via ${alarmResult.getOrDefault("?")}: key=${spec.key}, at=${spec.triggerAt}")
        } else {
            Log.e(TAG, "Failed to schedule exact alarm, trying inexact fallback: key=${spec.key}")
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent
                )
            }.onSuccess {
                Log.d(TAG, "Fallback scheduling succeeded: key=${spec.key}")
            }.onFailure {
                Log.e(TAG, "Fallback scheduling also failed: key=${spec.key}", it)
            }
        }
    }

    private fun cancelScheduledAlarms() {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val keys = preferences.getStringSet(ScheduledKeys, emptySet()).orEmpty()
        Log.d(TAG, "Cancelling ${keys.size} previously scheduled alarms")
        keys.forEach { key ->
            // FLAG_NO_CREATE ensures we only look up existing PendingIntents.
            // Since the intent data URI includes the key and the request code
            // is derived from the key, this should match the original exactly.
            val pendingIntent = createCancelPendingIntent(appContext, key)
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "Cancelled alarm: key=$key")
            } else {
                Log.d(TAG, "No existing PendingIntent found for key=$key (already fired or never created)")
            }
        }
    }

    companion object {
        const val ActionShowReminder = "com.example.todo.action.SHOW_REMINDER"
        const val ExtraReminderKey = "extra_reminder_key"
        const val ExtraNotificationId = "extra_notification_id"
        const val ExtraTitle = "extra_title"
        const val ExtraBody = "extra_body"
        private const val TAG = "PlannerReminderScheduler"
        private const val PreferencesName = "planner_reminders"
        private const val ScheduledKeys = "scheduled_reminder_keys"

        fun syncFromStorage(context: Context) {
            val appContext = context.applicationContext
            val store = PlannerPersistence(appContext).load()
            if (store == null) {
                Log.w(TAG, "No stored data found, skipping alarm sync")
                return
            }
            PlannerReminderScheduler(appContext).sync(store)
        }

        private fun createPendingIntent(
            context: Context,
            key: String,
            notificationId: Int,
            title: String,
            body: String
        ): PendingIntent? {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ActionShowReminder
                data = Uri.parse("todo://reminder/${Uri.encode(key)}")
                putExtra(ExtraReminderKey, key)
                putExtra(ExtraNotificationId, notificationId)
                putExtra(ExtraTitle, title)
                putExtra(ExtraBody, body)
            }
            return runCatching {
                PendingIntent.getBroadcast(
                    context,
                    key.notificationId(),
                    intent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }.getOrNull()
        }

        private fun createCancelPendingIntent(
            context: Context,
            key: String
        ): PendingIntent? {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ActionShowReminder
                data = Uri.parse("todo://reminder/${Uri.encode(key)}")
                // Extras are not part of PendingIntent matching, but we set them
                // to satisfy any paranoid platform checks.
                putExtra(ExtraReminderKey, key)
                putExtra(ExtraNotificationId, key.notificationId())
                putExtra(ExtraTitle, "")
                putExtra(ExtraBody, "")
            }
            return runCatching {
                PendingIntent.getBroadcast(
                    context,
                    key.notificationId(),
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
            }.getOrNull()
        }
    }
}

private data class ReminderAlarmSpec(
    val key: String,
    val notificationId: Int,
    val title: String,
    val body: String,
    val triggerAt: LocalDateTime
)

private fun PlannerItemStore.reminderAlarmSpecs(context: Context, now: LocalDateTime): List<ReminderAlarmSpec> {
    val textContext = context
        .applicationContext
        .localizedContext(AppLanguageStore.load(context.applicationContext))
    val scheduleReminders = storedSchedules().flatMap { schedule ->
        schedule.reminders.mapIndexedNotNull { index, reminder ->
            reminder.toAlarmSpec(
                context = textContext,
                itemType = "schedule",
                itemId = schedule.id,
                reminderIndex = index,
                fallbackTitle = textContext.getString(R.string.notification_schedule_reminder),
                itemTitle = schedule.title,
                now = now
            )
        }
    }

    val taskReminders = storedTasks()
        .filterNot { it.isCompleted }
        .flatMap { task ->
            task.reminders.mapIndexedNotNull { index, reminder ->
                reminder.toAlarmSpec(
                    context = textContext,
                    itemType = "task",
                    itemId = task.id,
                    reminderIndex = index,
                    fallbackTitle = textContext.getString(R.string.notification_task_reminder),
                    itemTitle = task.title,
                    now = now
                )
            }
        }

    return (scheduleReminders + taskReminders).sortedBy { it.triggerAt }
}

private fun Reminder.toAlarmSpec(
    context: Context,
    itemType: String,
    itemId: String,
    reminderIndex: Int,
    fallbackTitle: String,
    itemTitle: String,
    now: LocalDateTime
): ReminderAlarmSpec? {
    if (!enabled) return null

    val triggerAt = time.nextReminderOccurrenceAfter(now) ?: return null
    val key = "$itemType:$itemId:$reminderIndex"
    val displayTime = ReminderTimeFormatter.format(triggerAt)

    return ReminderAlarmSpec(
        key = key,
        notificationId = key.notificationId(),
        title = itemTitle.ifBlank { fallbackTitle },
        body = context.getString(R.string.notification_body_template, fallbackTitle, displayTime),
        triggerAt = triggerAt
    )
}

internal fun FlexibleDateTime.nextReminderOccurrenceAfter(
    now: LocalDateTime = LocalDateTime.now()
): LocalDateTime? {
    val reminderHour = hour ?: return null
    val reminderMinute = minute ?: return null
    val reminderTime = runCatching { LocalTime.of(reminderHour, reminderMinute) }.getOrNull() ?: return null
    val startDate = now.toLocalDate()
    val endDate = year?.let { fixedYear ->
        if (fixedYear < startDate.year) return null
        LocalDate.of(fixedYear, 12, 31)
    } ?: startDate.plusYears(5)

    var date = startDate
    while (!date.isAfter(endDate)) {
        if (matchesReminderDate(date)) {
            val candidate = LocalDateTime.of(date, reminderTime)
            if (candidate.isAfter(now)) return candidate
        }
        date = date.plusDays(1)
    }

    return null
}

private fun FlexibleDateTime.matchesReminderDate(date: LocalDate): Boolean =
    (year == null || year == date.year) &&
        (month == null || month == date.monthValue) &&
        (day == null || day == date.dayOfMonth)

private fun String.notificationId(): Int =
    hashCode()


private val ReminderTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
