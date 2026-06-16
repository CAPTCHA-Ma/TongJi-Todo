package com.example.todo

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received broadcast: $action")

        // Use goAsync() to extend the processing window. On boot and package
        // upgrade, loading the store and rescheduling all alarms can take
        // non-trivial time (file I/O + multiple system service calls).
        val pendingResult = goAsync()

        try {
            when (action) {
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                    Log.d(TAG, "Rescheduling alarms due to: $action")
                    PlannerReminderScheduler.syncFromStorage(context.applicationContext)
                }
                else -> {
                    Log.d(TAG, "Unhandled broadcast: $action")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reschedule alarms", e)
        } finally {
            pendingResult.finish()
        }
    }

    companion object {
        private const val TAG = "ReminderRescheduleReceiver"
    }
}
