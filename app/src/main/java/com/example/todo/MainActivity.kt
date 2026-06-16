package com.example.todo

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.todo.ui.theme.TodoTheme

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        requestExactAlarmPermissionIfNeeded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val requestedNotificationPermission = requestNotificationPermissionIfNeeded()
        if (!requestedNotificationPermission) {
            requestExactAlarmPermissionIfNeeded()
        }
        setContent {
            TodoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DailyPlannerScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PlannerReminderScheduler.syncFromStorage(applicationContext)
    }

    private fun requestNotificationPermissionIfNeeded(): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return true
        }
        return false
    }

    private fun requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val alarmManager = getSystemService(AlarmManager::class.java) ?: return
        if (alarmManager.canScheduleExactAlarms()) return

        val preferences = getSharedPreferences(ReminderPermissionPreferences, MODE_PRIVATE)
        if (preferences.getBoolean(ExactAlarmPermissionRequestShown, false)) return

        val settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
        }
        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        val intent = listOf(settingsIntent, fallbackIntent)
            .firstOrNull { it.resolveActivity(packageManager) != null }
            ?: return

        val openedSettings = runCatching {
            startActivity(intent)
            true
        }.getOrDefault(false)

        if (openedSettings) {
            preferences.edit()
                .putBoolean(ExactAlarmPermissionRequestShown, true)
                .apply()
        }
    }

    private companion object {
        const val ReminderPermissionPreferences = "reminder_permissions"
        const val ExactAlarmPermissionRequestShown = "exact_alarm_permission_request_shown"
    }
}
