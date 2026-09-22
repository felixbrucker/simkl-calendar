package com.felixbrucker.simklcalendar.receiver.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.felixbrucker.simklcalendar.receiver.alarm.AlarmScheduler
import com.felixbrucker.simklcalendar.receiver.notification.NotificationManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import javax.inject.Inject

@AndroidEntryPoint
class StartupReceiver: BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject
    lateinit var notificationManager: NotificationManager

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    companion object {
        private const val TAG = "StartupReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        Timber.tag(TAG).d("StartupReceiver triggered for action ${intent.action}")

        // Reschedule all alarms, as they are removed on app update and system reboot
        val pendingResult = goAsync()
        scope.launch {
            try {
                alarmScheduler.scheduleAllItemsAiredAlarms()
                notificationManager.restoreActiveNotifications()
                Timber.tag(TAG).d("Rescheduled all item aired alarms and restored active notifications after startup/update")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error rescheduling alarms and notifications on startup")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
