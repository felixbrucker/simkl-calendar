package com.felixbrucker.simklcalendar.receiver.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import com.felixbrucker.simklcalendar.receiver.notification.NotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


class AlarmReceiver: BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        const val EXTRA_ITEM_PRIMARY_KEY = "extra_item_primary_key"
        const val ACTION_ITEM_AIRED_ALARM = "com.felixbrucker.simklcalendar.ACTION_ITEM_AIRED_ALARM"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != ACTION_ITEM_AIRED_ALARM) {
            return
        }
        val itemPrimaryKey = intent.getStringExtra(EXTRA_ITEM_PRIMARY_KEY) ?: return

        val pendingResult = goAsync()
        scope.launch {
            try {
                onItemAired(itemPrimaryKey, context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun onItemAired(itemPrimaryKey: String, context: Context) {
        val db = AppDatabase.getDatabase(context)
        val item = db.calendarItemDao().findItem(itemPrimaryKey) ?: return
        val repo = SimklRepository(context)

        try {
            // First, ensure the item's media status is correctly set after it aired
            repo.updateItemAiredStatus(item)

            // Second, check if we should post a notification for this item
            val shouldPostNotification = shouldPostNotificationForItem(item, context)
            if (shouldPostNotification) {
                NotificationManager.showNotification(item, context)
                db.calendarItemDao().markItemAsNotified(itemPrimaryKey)
            }

            // Lastly, search and download torrents if configured
            val updatedItem = db.calendarItemDao().findItem(itemPrimaryKey) ?: return
            if (updatedItem.mediaStatus == MediaStatus.WANTED) {
                repo.searchAndDownloadEpisode(updatedItem)
            }

            val calendarItem = item.calendarItem
            if (calendarItem.isSeasonFinale && calendarItem.season != null && item.type != MediaType.MOVIE) {
                val settings = db.itemDownloadSettingsDao().getSettings(item.simklId)
                val downloadPrefs = context.getSharedPreferences("auto_download_prefs", Context.MODE_PRIVATE)
                val isDownloadSeasonUnwatchedEnabled = settings?.downloadSeasonUnwatched ?: when (item.type) {
                    MediaType.TV -> downloadPrefs.getBoolean("auto_download_season_unwatched_tv", false)
                    MediaType.ANIME -> downloadPrefs.getBoolean("auto_download_season_unwatched_anime", false)
                    MediaType.MOVIE -> false
                }
                if (isDownloadSeasonUnwatchedEnabled) {
                    repo.searchAndDownloadSeason(item.simklId, calendarItem.season)
                }
            }
        } finally {
            repo.torrentServiceHelper.unbind()
        }
    }

    private suspend fun shouldPostNotificationForItem(item: CalendarItemWithWatchlist, context: Context): Boolean {
        if (item.isNotified) {
            return false
        }

        val db = AppDatabase.getDatabase(context)
        val setting = db.notificationSettingDao().getSettingForShow(item.simklId) ?: DefaultNotificationSettings.fromContext(context).makeNotificationSettings(item)

        return when {
            item.type == MediaType.MOVIE -> if (item.movieReleaseType == MovieReleaseType.THEATER) {
                setting.notifyEveryEpisode
            } else {
                setting.notifyAiredLastEpisode
            }
            item.isSeasonFinale -> setting.notifyAiredLastEpisode || setting.notifyEveryEpisode
            else -> setting.notifyEveryEpisode
        }
    }
}
