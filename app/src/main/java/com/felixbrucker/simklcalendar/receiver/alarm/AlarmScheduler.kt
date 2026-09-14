package com.felixbrucker.simklcalendar.receiver.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import java.time.Instant.now
import java.time.ZoneId


class AlarmScheduler {
    companion object {
        private const val TAG = "AlarmScheduler"

        /**
         * Schedules item aired alarms for all eligible upcoming items. Intended to be called after each
         * sync to schedule any new items and reschedule changed items.
         */
        suspend fun scheduleAllItemsAiredAlarms(context: Context) = withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val calendarItems = db.calendarItemDao().getCalendarItemsForAiredAlarm(now())
                for (item in calendarItems) {
                    scheduleItemAiredAlarmForItem(item, context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error while scheduling all notifications", e)
            }
        }

        private fun scheduleItemAiredAlarmForItem(item: CalendarItemWithWatchlist, context: Context) {
            val triggerAt = if (item.type == MediaType.MOVIE) {
                item.date.atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .atTime(9, 0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            } else {
                item.date
            }
            val alarmManager = getAlarmManager(context) ?: return

            // Cancel any existing pending alarm
            cancelItemAiredAlarmForItem(item, context)

            val pendingIntent = item.makeItemAiredAlarmIntent(
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                context
            )
            val triggerAtMillis = triggerAt.toEpochMilli()
            try {
                val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
                val useExactAlarms = prefs.getBoolean("use_exact_alarms", false)

                if (useExactAlarms) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                        } else {
                            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                        }
                    } else {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    }
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
                Log.d(TAG, "Scheduled item aired alarm for '${item.title}' at timestamp $triggerAtMillis (key=${item.primaryKey})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed scheduling alarm", e)
            }
        }

        private fun cancelItemAiredAlarmForItem(item: CalendarItemWithWatchlist, context: Context) {
            val alarmManager = getAlarmManager(context) ?: return
            try {
                val pendingIntent = item.makeItemAiredAlarmIntent(
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                    context,
                )
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling alarm for item ${item.primaryKey}", e)
            }
        }

        private fun getAlarmManager(context: Context): AlarmManager? {
            return context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        }
    }
}


fun CalendarItemWithWatchlist.makeItemAiredAlarmIntent(flags: Int, context: Context): PendingIntent {
    val intent = Intent(context, AlarmReceiver::class.java).apply {
        action = AlarmReceiver.ACTION_ITEM_AIRED_ALARM
        data = "simklcalendar://item_aired_alarm/$primaryKey".toUri()
        putExtra(AlarmReceiver.EXTRA_ITEM_PRIMARY_KEY, primaryKey)
    }

    return PendingIntent.getBroadcast(
        context,
        notificationId,
        intent,
        flags,
    )
}
