package com.felixbrucker.simklcalendar.receiver.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.felixbrucker.simklcalendar.receiver.alarm.AlarmScheduler
import com.felixbrucker.simklcalendar.receiver.notification.NotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StartupReceiver: BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        // Reschedule all alarms, as they are removed on app update and system reboot
        val pendingResult = goAsync()
        scope.launch {
            try {
                AlarmScheduler.scheduleAllItemsAiredAlarms(context)
                NotificationManager.restoreActiveNotifications(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
