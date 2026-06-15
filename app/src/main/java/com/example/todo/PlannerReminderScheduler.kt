package com.example.todo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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

        val specs = store.reminderAlarmSpecs(appContext, LocalDateTime.now())
        specs.forEach(::schedule)

        preferences.edit()
            .putStringSet(ScheduledKeys, specs.map { it.key }.toSet())
            .apply()
    }

    private fun schedule(spec: ReminderAlarmSpec) {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val triggerMillis = spec.triggerAt
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        if (triggerMillis <= System.currentTimeMillis()) return

        val pendingIntent = reminderPendingIntent(
            context = appContext,
            key = spec.key,
            notificationId = spec.notificationId,
            title = spec.title,
            body = spec.body,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) ?: return

        val scheduledExactly = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        }.isSuccess

        if (!scheduledExactly) {
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        }
    }

    private fun cancelScheduledAlarms() {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        preferences.getStringSet(ScheduledKeys, emptySet()).orEmpty().forEach { key ->
            val pendingIntent = reminderPendingIntent(
                context = appContext,
                key = key,
                notificationId = key.notificationId(),
                title = "",
                body = "",
                flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    companion object {
        const val ActionShowReminder = "com.example.todo.action.SHOW_REMINDER"
        const val ExtraReminderKey = "extra_reminder_key"
        const val ExtraNotificationId = "extra_notification_id"
        const val ExtraTitle = "extra_title"
        const val ExtraBody = "extra_body"
        private const val PreferencesName = "planner_reminders"
        private const val ScheduledKeys = "scheduled_reminder_keys"

        fun syncFromStorage(context: Context) {
            val appContext = context.applicationContext
            val store = PlannerPersistence(appContext).load() ?: return
            PlannerReminderScheduler(appContext).sync(store)
        }

        private fun reminderPendingIntent(
            context: Context,
            key: String,
            notificationId: Int,
            title: String,
            body: String,
            flags: Int
        ): PendingIntent? {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ActionShowReminder
                data = Uri.parse("todo://reminder/${Uri.encode(key)}")
                putExtra(ExtraReminderKey, key)
                putExtra(ExtraNotificationId, notificationId)
                putExtra(ExtraTitle, title)
                putExtra(ExtraBody, body)
            }
            return PendingIntent.getBroadcast(context, key.notificationId(), intent, flags)
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
    val scheduleReminders = storedSchedules().flatMap { schedule ->
        schedule.reminders.mapIndexedNotNull { index, reminder ->
            reminder.toAlarmSpec(
                context = context,
                itemType = "schedule",
                itemId = schedule.id,
                reminderIndex = index,
                fallbackTitle = context.getString(R.string.notification_schedule_reminder),
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
                    context = context,
                    itemType = "task",
                    itemId = task.id,
                    reminderIndex = index,
                    fallbackTitle = context.getString(R.string.notification_task_reminder),
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
