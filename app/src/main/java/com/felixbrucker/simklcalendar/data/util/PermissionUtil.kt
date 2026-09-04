package com.felixbrucker.simklcalendar.data.util

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

object PermissionUtil {

    /**
     * Checks if the app has permission to schedule exact alarms.
     * Always returns true for versions below Android 12 (API 31).
     */
    fun hasExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Returns an intent to the system settings page where the user can grant
     * the schedule exact alarm permission.
     */
    fun getExactAlarmPermissionIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:${context.packageName}".toUri()
            }
        } else {
            throw UnsupportedOperationException("getExactAlarmPermissionIntent is only available for Android 12+ (API 31+)")
        }
    }
}
