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
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


class AlarmReceiver: BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val TAG = "AlarmReceiver"
        const val EXTRA_ITEM_PRIMARY_KEY = "extra_item_primary_key"
        const val ACTION_ITEM_AIRED_ALARM = "com.felixbrucker.simklcalendar.ACTION_ITEM_AIRED_ALARM"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != ACTION_ITEM_AIRED_ALARM) {
            return
        }
        val itemPrimaryKey = intent.getStringExtra(EXTRA_ITEM_PRIMARY_KEY) ?: return
        Timber.tag(TAG).d("Received item aired alarm for key=$itemPrimaryKey")

        val pendingResult = goAsync()
        scope.launch {
            try {
                onItemAired(itemPrimaryKey, context)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error processing item aired alarm for key=$itemPrimaryKey")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun onItemAired(itemPrimaryKey: String, context: Context) {
        val db = AppDatabase.getDatabase(context)
        val item = db.calendarItemDao().findItem(itemPrimaryKey)
        if (item == null) {
            Timber.tag(TAG).w("Item for key=$itemPrimaryKey not found in database")
            return
        }
        val repo = SimklRepository(context)

        Timber.tag(TAG).d("Processing item aired for '${item.title}' (key=$itemPrimaryKey)")

        // First, ensure the item's media status is correctly set after it aired
        repo.updateItemAiredStatus(item)

        // Second, check if we should post a notification for this item
        val shouldPostNotification = shouldPostNotificationForItem(item, context)
        if (shouldPostNotification) {
            Timber.tag(TAG).d("Posting notification for '${item.title}'")
            NotificationManager.showNotification(item, context)
            db.calendarItemDao().markItemAsNotified(itemPrimaryKey)
        } else {
            Timber.tag(TAG).d("Skipping notification for '${item.title}' based on user preferences or notification state")
        }

        var didSearchAndDownload = false
        // Lastly, search and download torrents if configured
        try {
            val updatedItem = db.calendarItemDao().findItem(itemPrimaryKey) ?: return
            if (updatedItem.mediaStatus == MediaStatus.WANTED) {
                Timber.tag(TAG).d("Searching and downloading WANTED episode for '${item.title}'")
                repo.searchAndDownloadEpisode(updatedItem)
                didSearchAndDownload = true
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
                    Timber.tag(TAG).d("Season finale aired for '${item.title}', downloading unwatched season ${calendarItem.season}")
                    repo.searchAndDownloadSeason(item.simklId, calendarItem.season)
                    didSearchAndDownload = true
                }
            }
        } finally {
            repo.torrentServiceHelper.unbind()
        }

        if (didSearchAndDownload) {
            val finalItem = db.calendarItemDao().findItem(itemPrimaryKey)
            if (finalItem != null) {
                NotificationManager.updateNotification(finalItem, context)
            }
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
