package com.example.todo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PlannerReminderScheduler.ActionShowReminder) return

        val notificationId = intent.getIntExtra(
            PlannerReminderScheduler.ExtraNotificationId,
            System.currentTimeMillis().toInt()
        )
        val title = intent.getStringExtra(PlannerReminderScheduler.ExtraTitle).orEmpty()
        val body = intent.getStringExtra(PlannerReminderScheduler.ExtraBody).orEmpty()

        ReminderNotification.show(
            context = context.applicationContext,
            notificationId = notificationId,
            title = title.ifBlank { context.getString(R.string.notification_reminder_title) },
            body = body.ifBlank { context.getString(R.string.notification_reminder_body) }
        )
        PlannerReminderScheduler.syncFromStorage(context.applicationContext)
    }
}
