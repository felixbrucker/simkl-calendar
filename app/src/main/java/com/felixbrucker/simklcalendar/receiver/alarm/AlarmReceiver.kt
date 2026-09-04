package com.felixbrucker.simklcalendar.receiver.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
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

        // First, ensure the items media status is correctly set after it aired
        repo.updateItemAiredStatus(item)

        // Second, we check if we should post a notification for this item
        val shouldPostNotification = shouldPostNotificationForItem(item, context)
        if (!shouldPostNotification) {
            return
        }
        NotificationManager.showNotification(item, context)
        db.calendarItemDao().markItemAsNotified(itemPrimaryKey)
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
