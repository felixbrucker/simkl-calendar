package com.felixbrucker.simklcalendar.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.NotificationSetting
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import com.felixbrucker.simklcalendar.receiver.NotificationReceiver.Companion.ACTION_AIR_DATE_ALERT
import kotlin.math.abs

object NotificationScheduler {

    private const val TAG = "NotificationScheduler"

    /**
     * Schedules or fires notifications for all eligible upcoming or recently aired items across all shows.
     */
    suspend fun scheduleAllNotifications(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val settings = db.notificationSettingDao().getAllSettingsList()
            val settingsMap = settings.associateBy { it.simklId }
            val allItems = db.calendarItemDao().getAllCalendarItemsList()
            val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
            val defaultAiring = prefs.getBoolean("default_notify_airing", false)
            val defaultSeasonFinished = prefs.getBoolean("default_notify_season_finished", true)
            val defaultMovieTheater = prefs.getBoolean("default_notify_movie_theater", false)
            val defaultMovieDigital = prefs.getBoolean("default_notify_movie_digital", true)

            Log.d(TAG, "Scheduling notifications: found ${settings.size} custom show settings and ${allItems.size} calendar items")

            val allEpisodesMap = allItems.groupBy { it.simklId to (if (it.type == MediaType.ANIME) null else it.season) }

            for (item in allItems) {
                // Per-item setting in database always takes precedence over new item defaults
                val isMovie = item.type == MediaType.MOVIE
                val setting = settingsMap[item.simklId] ?: NotificationSetting(
                    simklId = item.simklId,
                    notifyEveryEpisode = if (isMovie) defaultMovieTheater else defaultAiring,
                    notifyAiredLastEpisode = if (isMovie) defaultMovieDigital else defaultSeasonFinished
                )
                val seasonItems = allEpisodesMap[item.simklId to (if (item.type == MediaType.ANIME) null else item.season)]
                val totalEpisodesInSeason = seasonItems?.mapNotNull { it.episodeNumber }?.maxOrNull() ?: item.episodeNumber
                scheduleOrDispatchItem(context, db, item, setting, totalEpisodesInSeason)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while scheduling all notifications", e)
        }
    }

    /**
     * Schedules notifications specifically for a single show when its settings change.
     */
    suspend fun scheduleNotificationsForShow(context: Context, simklId: Int) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val setting = db.notificationSettingDao().getSettingForShow(simklId)
            val showItems = db.calendarItemDao().getItemsForShow(simklId)

            if (setting == null || (!setting.notifyEveryEpisode && !setting.notifyAiredLastEpisode)) {
                // Cancel all alarms for this show
                for (item in showItems) {
                    cancelAlarmForItem(context, item)
                }
                return@withContext
            }

            // Reset notified status for upcoming/today's items for this show so newly enabled alerts fire
            db.calendarItemDao().resetNotifiedForShow(simklId)
            val updatedShowItems = db.calendarItemDao().getItemsForShow(simklId)
            val showEpisodesMap = updatedShowItems.groupBy { if (it.type == MediaType.ANIME) null else it.season }

            for (item in updatedShowItems) {
                val seasonItems = showEpisodesMap[if (item.type == MediaType.ANIME) null else item.season]
                val totalEpisodesInSeason = seasonItems?.mapNotNull { it.episodeNumber }?.maxOrNull() ?: item.episodeNumber
                scheduleOrDispatchItem(context, db, item, setting, totalEpisodesInSeason)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling notifications for show $simklId", e)
        }
    }

    private suspend fun scheduleOrDispatchItem(
        context: Context,
        db: AppDatabase,
        item: CalendarItemWithWatchlist,
        setting: NotificationSetting,
        totalEpisodesInSeason: Int? = null
    ) {
        val isFinale = item.isSeasonFinale
        val shouldNotify = when {
            item.type == MediaType.MOVIE -> if (item.movieReleaseType == MovieReleaseType.THEATER) {
                setting.notifyEveryEpisode
            } else {
                setting.notifyAiredLastEpisode
            }
            isFinale -> setting.notifyAiredLastEpisode || setting.notifyEveryEpisode
            else -> setting.notifyEveryEpisode
        }

        // We ALWAYS schedule/dispatch for items in NOT_AIRED_YET status to ensure
        // they transition to WANTED/IGNORED at the air date, regardless of notification settings.
        if (!shouldNotify && item.mediaStatus != MediaStatus.NOT_AIRED_YET) {
            cancelAlarmForItem(context, item)
            return
        }

        val triggerTime = if (item.type == MediaType.MOVIE) {
            item.date.atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .atTime(9, 0)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } else {
            item.date.toEpochMilli()
        }
        val now = System.currentTimeMillis()
        val (title, message) = NotificationReceiver.formatNotificationContent(item, isFinale, totalEpisodesInSeason)
        val notificationId = abs(item.primaryKey.hashCode())

        if (triggerTime > now) {
            // Future release -> schedule Alarm
            scheduleAlarm(
                context = context,
                itemKey = item.primaryKey,
                triggerAtMillis = triggerTime,
                title = title,
                message = message,
                notificationId = notificationId,
                simklId = item.simklId,
                season = item.season,
                episodeNumber = item.episodeNumber,
                type = item.type,
                isFinale = isFinale,
                showTitle = item.title,
                movieReleaseType = item.movieReleaseType,
                poster = item.poster,
                shouldNotify = shouldNotify
            )
            return
        }

        // Already reached air time -> if recent (within 48 hours or today) and not yet notified in DB, trigger immediately
        val fortyEightHoursMillis = 48 * 60 * 60 * 1000L
        if ((now - triggerTime) >= fortyEightHoursMillis || item.isNotified) {
            return
        }

        if (shouldNotify) {
            NotificationReceiver.showNotification(
                context = context,
                title = title,
                message = message,
                notificationId = notificationId,
                itemKey = item.primaryKey,
                simklId = item.simklId,
                season = item.season,
                episodeNumber = item.episodeNumber,
                type = item.type,
                isFinale = isFinale,
                showTitle = item.title,
                poster = item.poster
            )
        }
        db.calendarItemDao().markItemAsNotified(item.primaryKey)

        // Also perform status transition if needed
        val repo = SimklRepository(context)
        repo.updateItemAiredStatus(item.primaryKey, isTheaterRelease = item.movieReleaseType == MovieReleaseType.THEATER)

        Log.d(TAG, "Dispatched immediate action/notification for recently reached air date: ${item.title}")
    }

    private fun scheduleAlarm(
        context: Context,
        itemKey: String,
        triggerAtMillis: Long,
        title: String,
        message: String,
        notificationId: Int,
        simklId: Int,
        season: Int?,
        episodeNumber: Int?,
        type: MediaType,
        isFinale: Boolean,
        showTitle: String,
        movieReleaseType: MovieReleaseType?,
        poster: String? = null,
        shouldNotify: Boolean
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_AIR_DATE_ALERT
            data = "simkl_alert://$itemKey".toUri()
            putExtra(NotificationReceiver.EXTRA_TITLE, title)
            putExtra(NotificationReceiver.EXTRA_MESSAGE, message)
            putExtra(NotificationReceiver.EXTRA_ID, notificationId)
            putExtra(NotificationReceiver.EXTRA_ITEM_KEY, itemKey)
            putExtra(NotificationReceiver.EXTRA_SIMKL_ID, simklId)
            if (season != null) putExtra(NotificationReceiver.EXTRA_SEASON, season)
            if (episodeNumber != null) putExtra(NotificationReceiver.EXTRA_EPISODE_NUMBER, episodeNumber)
            putExtra(NotificationReceiver.EXTRA_MEDIA_TYPE, type.name)
            putExtra(NotificationReceiver.EXTRA_IS_FINALE, isFinale)
            putExtra(NotificationReceiver.EXTRA_SHOW_TITLE, showTitle)
            if (movieReleaseType != null) putExtra(NotificationReceiver.EXTRA_MOVIE_RELEASE_TYPE, movieReleaseType.name)
            if (poster != null) putExtra(NotificationReceiver.EXTRA_POSTER, poster)
            putExtra(NotificationReceiver.EXTRA_SHOULD_NOTIFY, shouldNotify)
        }

        val requestCode = abs(itemKey.hashCode())
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
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
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

    private fun cancelAlarmForItem(context: Context, item: CalendarItemWithWatchlist) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_AIR_DATE_ALERT
                data = "simkl_alert://${item.primaryKey}".toUri()
            }
            val requestCode = abs(item.primaryKey.hashCode())
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
