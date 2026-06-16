package com.example.todo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PlannerReminderScheduler.ActionShowReminder) return

        // Use goAsync() to extend the broadcast processing window.
        // This is critical when the app process is cold-started by AlarmManager:
        // without it, the system may kill the process before the notification
        // is posted and the next alarm is rescheduled.
        val pendingResult = goAsync()

        try {
            val notificationId = intent.getIntExtra(
                PlannerReminderScheduler.ExtraNotificationId,
                System.currentTimeMillis().toInt()
            )
            val title = intent.getStringExtra(PlannerReminderScheduler.ExtraTitle).orEmpty()
            val body = intent.getStringExtra(PlannerReminderScheduler.ExtraBody).orEmpty()

            Log.d(TAG, "Reminder fired: id=$notificationId, title=$title")

            ReminderNotification.show(
                context = context.applicationContext,
                notificationId = notificationId,
                title = title.ifBlank { context.getString(R.string.notification_reminder_title) },
                body = body.ifBlank { context.getString(R.string.notification_reminder_body) }
            )

            // Reschedule the next occurrence of this reminder
            PlannerReminderScheduler.syncFromStorage(context.applicationContext)

            Log.d(TAG, "Reminder handled and rescheduled: id=$notificationId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle reminder", e)
        } finally {
            pendingResult.finish()
        }
    }

    companion object {
        private const val TAG = "ReminderReceiver"
    }
}
