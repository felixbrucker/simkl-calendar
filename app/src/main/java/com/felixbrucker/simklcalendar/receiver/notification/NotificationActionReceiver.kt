package com.felixbrucker.simklcalendar.receiver.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.repository.CalendarRepository
import com.felixbrucker.simklcalendar.data.repository.DownloadRepository
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver: BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject
    lateinit var calendarRepository: CalendarRepository

    @Inject
    lateinit var downloadRepository: DownloadRepository

    @Inject
    lateinit var calendarItemDao: CalendarItemDao

    @Inject
    lateinit var torrentServiceHelper: TorrentServiceHelper

    @Inject
    lateinit var notificationManager: NotificationManager

    companion object {
        private const val TAG = "NotificationActionReceiver"
        const val ACTION_MARK_ITEM_WATCHED = "com.felixbrucker.simklcalendar.ACTION_MARK_ITEM_WATCHED"
        const val ACTION_MARK_SEASON_WATCHED = "com.felixbrucker.simklcalendar.ACTION_MARK_SEASON_WATCHED"
        const val ACTION_DOWNLOAD_ITEM = "com.felixbrucker.simklcalendar.ACTION_DOWNLOAD_ITEM"
        const val ACTION_DOWNLOAD_SEASON_MISSING_EPISODES =
            "com.felixbrucker.simklcalendar.ACTION_DOWNLOAD_SEASON_MISSING_EPISODES"
        const val ACTION_NOTIFICATION_DISMISSED =
            "com.felixbrucker.simklcalendar.ACTION_NOTIFICATION_DISMISSED"
        const val EXTRA_ITEM_PRIMARY_KEY = "extra_item_primary_key"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            ACTION_MARK_ITEM_WATCHED -> {
                handleMarkItemWatched(intent)
                return
            }

            ACTION_MARK_SEASON_WATCHED -> {
                handleMarkSeasonWatched(intent)
                return
            }

            ACTION_DOWNLOAD_ITEM -> {
                handleDownloadItem(intent)
                return
            }

            ACTION_DOWNLOAD_SEASON_MISSING_EPISODES -> {
                handleDownloadSeasonMissingEpisodes(intent)
                return
            }

            ACTION_NOTIFICATION_DISMISSED -> {
                handleNotificationDismissed(intent)
                return
            }
        }
    }

    private fun handleMarkItemWatched(intent: Intent) {
        val itemPrimaryKey = intent.getStringExtra(EXTRA_ITEM_PRIMARY_KEY) ?: return
        Timber.tag(TAG).d("Handling mark item watched action for key=$itemPrimaryKey")

        val pendingResult = goAsync()
        scope.launch {
            try {
                val item = calendarItemDao.findItem(itemPrimaryKey) ?: return@launch

                val result = if (item.type == MediaType.MOVIE) {
                    calendarRepository.markMovieWatched(simklId = item.simklId)
                } else {
                    calendarRepository.markEpisodeWatched(
                        simklId = item.simklId,
                        season = item.season,
                        episodeNumber = item.episodeNumber ?: 1,
                        mediaType = item.type,
                    )
                }

                val err = result.exceptionOrNull()
                if (err != null) {
                    Timber.tag(TAG).e(err, "Error marking item as watched from notification action")
                    val updatedItem = calendarItemDao.findItem(itemPrimaryKey) ?: return@launch
                    notificationManager.updateNotification(
                        item = updatedItem
                    )
                } else {
                    notificationManager.dismissNotification(
                        item = item
                    )
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error marking episode as watched from notification action")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleMarkSeasonWatched(intent: Intent) {
        val itemPrimaryKey = intent.getStringExtra(EXTRA_ITEM_PRIMARY_KEY) ?: return
        Timber.tag(TAG).d("Handling mark season watched action for key=$itemPrimaryKey")

        val pendingResult = goAsync()
        scope.launch {
            try {
                val item = calendarItemDao.findItem(itemPrimaryKey) ?: return@launch
                val result = calendarRepository.markSeasonWatched(
                    simklId = item.simklId,
                    season = item.season ?: 1,
                    mediaType = item.type,
                )

                val err = result.exceptionOrNull()
                if (err != null) {
                    Timber.tag(TAG).e(err, "Error marking season as watched from notification action")
                    val updatedItem = calendarItemDao.findItem(itemPrimaryKey) ?: return@launch
                    notificationManager.updateNotification(
                        item = updatedItem
                    )
                } else {
                    notificationManager.dismissNotification(
                        item = item
                    )
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error marking season as watched from notification action")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleDownloadItem(intent: Intent) {
        val itemPrimaryKey = intent.getStringExtra(EXTRA_ITEM_PRIMARY_KEY) ?: return
        Timber.tag(TAG).d("Handling download item action for key=$itemPrimaryKey")

        val pendingResult = goAsync()
        scope.launch {
            try {
                val item = calendarItemDao.findItem(itemPrimaryKey) ?: return@launch

                calendarRepository.updateMediaStatus(item.primaryKey, MediaStatus.WANTED)

                val updatedItem = calendarItemDao.findItem(itemPrimaryKey) ?: return@launch

                try {
                    downloadRepository.searchAndDownloadEpisode(updatedItem)
                } finally {
                    torrentServiceHelper.unbind()
                }

                val finalItem = calendarItemDao.findItem(itemPrimaryKey) ?: return@launch
                notificationManager.updateNotification(
                    item = finalItem
                )
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error starting download from notification action")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleNotificationDismissed(intent: Intent) {
        val itemPrimaryKey = intent.getStringExtra(EXTRA_ITEM_PRIMARY_KEY) ?: return
        Timber.tag(TAG).d("Handling notification dismissed action for key=$itemPrimaryKey")
        val pendingResult = goAsync()
        scope.launch {
            try {
                notificationManager.removeActiveNotification(itemPrimaryKey)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error removing active notification on dismiss")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleDownloadSeasonMissingEpisodes(intent: Intent) {
        val itemPrimaryKey = intent.getStringExtra(EXTRA_ITEM_PRIMARY_KEY) ?: return
        Timber.tag(TAG).d("Handling download season missing episodes action for key=$itemPrimaryKey")

        val pendingResult = goAsync()
        scope.launch {
            try {
                val item = calendarItemDao.findItem(itemPrimaryKey) ?: return@launch
                val season = item.season ?: 1

                val seasonItems =
                    calendarItemDao.getItemsInSeasonOrRelatedItems(item.simklId, season)
                val ignoredItems = seasonItems.filter { it.mediaStatus == MediaStatus.IGNORED }

                for (ignored in ignoredItems) {
                    calendarRepository.updateMediaStatus(ignored.primaryKey, MediaStatus.WANTED)
                }

                try {
                    downloadRepository.searchAndDownloadWantedItems()
                } finally {
                    torrentServiceHelper.unbind()
                }

                val updatedItem = calendarItemDao.findItem(itemPrimaryKey) ?: return@launch
                notificationManager.updateNotification(
                    item = updatedItem
                )
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error starting season download from notification action")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
