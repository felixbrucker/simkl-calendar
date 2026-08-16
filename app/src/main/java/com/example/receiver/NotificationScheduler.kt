package com.example.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.CalendarItem
import com.example.data.database.NotificationSetting
import com.example.data.util.DateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object NotificationScheduler {

    private const val TAG = "NotificationScheduler"
    const val ACTION_AIR_DATE_ALERT = "com.example.ACTION_AIR_DATE_ALERT"

    /**
     * Formats the notification title and message body for a calendar item.
     */
    fun formatNotificationContent(item: CalendarItem, isFinale: Boolean): Pair<String, String> {
        return if (item.type == "movie") {
            val title = "Movie Released Today"
            val message = "${item.title} is now available!"
            title to message
        } else if (isFinale) {
            val title = "Season Finale Released"
            val epLabel = if (item.season != null && item.episodeNumber != null) {
                String.format(Locale.US, " (S%02dE%02d)", item.season, item.episodeNumber)
            } else if (item.episodeNumber != null) {
                " (Episode ${item.episodeNumber})"
            } else ""
            val message = "${item.title}$epLabel - Ready to binge!"
            title to message
        } else {
            val title = "New Episode Released"
            val epLabel = if (item.season != null && item.episodeNumber != null) {
                String.format(Locale.US, " (S%02dE%02d)", item.season, item.episodeNumber)
            } else if (item.episodeNumber != null) {
                " (Episode ${item.episodeNumber})"
            } else ""
            val epName = if (!item.episodeTitle.isNullOrBlank()) " \"${item.episodeTitle}\"" else ""
            val message = "${item.title}$epLabel$epName is now airing."
            title to message
        }
    }

    /**
     * Schedules or fires notifications for all eligible upcoming or recently aired items across all shows.
     */
    suspend fun scheduleAllNotifications(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val settings = db.notificationSettingDao().getAllSettingsList()
            val settingsMap = settings.associateBy { it.showId }
            val allItems = db.calendarItemDao().getAllCalendarItemsList()
            val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
            val defaultAiring = prefs.getBoolean("default_notify_airing", prefs.getBoolean("global_airing_alerts", false))
            val defaultBinge = prefs.getBoolean("default_notify_binge", prefs.getBoolean("global_binge_alerts", true))

            Log.d(TAG, "Scheduling notifications: found ${settings.size} custom show settings and ${allItems.size} calendar items")

            for (item in allItems) {
                // Per-item setting in database always takes precedence over new item defaults
                val setting = settingsMap[item.id] ?: NotificationSetting(
                    showId = item.id,
                    showTitle = item.title,
                    type = item.type,
                    notifyEveryEpisode = defaultAiring,
                    notifyAiredLastEpisode = defaultBinge
                )
                scheduleOrDispatchItem(context, db, item, setting)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while scheduling all notifications", e)
        }
    }

    /**
     * Schedules notifications specifically for a single show when its settings change.
     */
    suspend fun scheduleNotificationsForShow(context: Context, showId: Int) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val setting = db.notificationSettingDao().getSettingForShow(showId)
            val showItems = db.calendarItemDao().getItemsForShow(showId)

            if (setting == null || (!setting.notifyEveryEpisode && !setting.notifyAiredLastEpisode)) {
                // Cancel all alarms for this show
                for (item in showItems) {
                    cancelAlarmForItem(context, item)
                }
                return@withContext
            }

            // Reset notified status for upcoming/today's items for this show so newly enabled alerts fire
            db.calendarItemDao().resetNotifiedForShow(showId)
            val updatedShowItems = db.calendarItemDao().getItemsForShow(showId)

            for (item in updatedShowItems) {
                scheduleOrDispatchItem(context, db, item, setting)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling notifications for show $showId", e)
        }
    }

    private suspend fun scheduleOrDispatchItem(
        context: Context,
        db: AppDatabase,
        item: CalendarItem,
        setting: NotificationSetting
    ) {
        val isFinale = item.isSeasonFinale || item.isLastEpisode
        val isEnabled = if (item.type == "movie") {
            setting.notifyEveryEpisode
        } else if (isFinale) {
            setting.notifyAiredLastEpisode || setting.notifyEveryEpisode
        } else {
            setting.notifyEveryEpisode
        }

        if (!isEnabled) {
            cancelAlarmForItem(context, item)
            return
        }

        val triggerTime = DateUtil.parseToEpochMillis(item.date) ?: return
        val now = System.currentTimeMillis()
        val (title, message) = formatNotificationContent(item, isFinale)
        val notificationId = Math.abs(item.primaryKey.hashCode())

        if (triggerTime > now) {
            // Future release -> schedule Alarm
            scheduleAlarm(
                context = context,
                itemKey = item.primaryKey,
                triggerAtMillis = triggerTime,
                title = title,
                message = message,
                notificationId = notificationId
            )
        } else {
            // Already reached air time -> if recent (within 48 hours or today) and not yet notified in DB, trigger immediately
            val fortyEightHoursMillis = 48 * 60 * 60 * 1000L
            if ((now - triggerTime) < fortyEightHoursMillis && !item.isNotified) {
                NotificationReceiver.showNotification(context, title, message, notificationId)
                db.calendarItemDao().markItemAsNotified(item.primaryKey)
                Log.d(TAG, "Dispatched immediate notification for recently reached air date: ${item.title}")
            }
        }
    }

    private fun scheduleAlarm(
        context: Context,
        itemKey: String,
        triggerAtMillis: Long,
        title: String,
        message: String,
        notificationId: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_AIR_DATE_ALERT
            data = android.net.Uri.parse("simkl_alert://$itemKey")
            putExtra(NotificationReceiver.EXTRA_TITLE, title)
            putExtra(NotificationReceiver.EXTRA_MESSAGE, message)
            putExtra(NotificationReceiver.EXTRA_ID, notificationId)
            putExtra(NotificationReceiver.EXTRA_ITEM_KEY, itemKey)
        }

        val requestCode = Math.abs(itemKey.hashCode())
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            Log.d(TAG, "Scheduled air date alarm for '$title' at timestamp $triggerAtMillis (key=$itemKey)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed scheduling alarm, falling back to setAndAllowWhileIdle", e)
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Could not schedule fallback alarm", fallbackEx)
            }
        }
    }

    private fun cancelAlarmForItem(context: Context, item: CalendarItem) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_AIR_DATE_ALERT
                data = android.net.Uri.parse("simkl_alert://${item.primaryKey}")
            }
            val requestCode = Math.abs(item.primaryKey.hashCode())
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling alarm for item ${item.primaryKey}", e)
        }
    }
}
