package com.felixbrucker.simklcalendar.receiver.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.repository.CalendarRepository
import com.felixbrucker.simklcalendar.receiver.notification.NotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class DownloadCompletedReceiver: BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject
    lateinit var calendarRepository: CalendarRepository

    @Inject
    lateinit var calendarItemDao: CalendarItemDao

    @Inject
    lateinit var notificationManager: NotificationManager

    companion object {
        private const val TAG = "DownloadCompletedReceiver"
        const val ACTION_DOWNLOAD_COMPLETED = "com.felixbrucker.simklcalendar.ACTION_DOWNLOAD_COMPLETED"
        const val EXTRA_ITEM_PRIMARY_KEY = "extra_item_primary_key"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != ACTION_DOWNLOAD_COMPLETED) {
            return
        }
        val itemPrimaryKey = intent.getStringExtra(EXTRA_ITEM_PRIMARY_KEY) ?: return
        val pendingResult = goAsync()
        scope.launch {
            try {
                calendarRepository.updateDownloadTaskId(itemPrimaryKey, null, MediaStatus.DOWNLOADED)
                Timber.tag(TAG).d("Updated item $itemPrimaryKey to DOWNLOADED status and cleared taskId")

                val item = calendarItemDao.findItem(itemPrimaryKey) ?: return@launch

                // Update notification for the item that was just downloaded (if active)
                notificationManager.updateNotification(item)

                // If it's a TV show/anime episode, also check if there's an active season finale
                // notification that needs updating to reflect the new aggregate download status.
                val season = item.season
                if (season != null) {
                    val finaleItem = calendarItemDao.getSeasonFinaleItem(item.simklId, season)
                    if (finaleItem != null && finaleItem.primaryKey != item.primaryKey) {
                        notificationManager.updateNotification(finaleItem)
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error handling download completion")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
