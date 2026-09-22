package com.felixbrucker.simklcalendar.receiver.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
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
    lateinit var repo: SimklRepository

    @Inject
    lateinit var db: AppDatabase

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
                handleMarkItemWatched(context, intent)
                return
            }

            ACTION_MARK_SEASON_WATCHED -> {
                handleMarkSeasonWatched(context, intent)
                return
            }

            ACTION_DOWNLOAD_ITEM -> {
                handleDownloadItem(context, intent)
                return
            }

            ACTION_DOWNLOAD_SEASON_MISSING_EPISODES -> {
                handleDownloadSeasonMissingEpisodes(context, intent)
                return
            }

            ACTION_NOTIFICATION_DISMISSED -> {
                handleNotificationDismissed(context, intent)
                return
            }
        }
    }

    private fun handleMarkItemWatched(context: Context, intent: Intent) {
        val itemPrimaryKey = intent.getStringExtra(EXTRA_ITEM_PRIMARY_KEY) ?: return
        Timber.tag(TAG).d("Handling mark item watched action for key=$itemPrimaryKey")
        val effectiveRepo = if (::repo.isInitialized) repo else SimklRepository(context)
        val effectiveDb = if (::db.isInitialized) db else AppDatabase.getDatabase(context)

        val pendingResult = goAsync()
        scope.launch {
            try {
                val item = effectiveDb.calendarItemDao().findItem(itemPrimaryKey) ?: return@launch

                val result = if (item.type == MediaType.MOVIE) {
                    effectiveRepo.markMovieWatched(simklId = item.simklId)
                } else {
                    effectiveRepo.markEpisodeWatched(
                        simklId = item.simklId,
                        season = item.season,
                        episodeNumber = item.episodeNumber ?: 1,
                        mediaType = item.type,
                    )
                }

                val err = result.exceptionOrNull()
                if (err != null) {
                    Timber.tag(TAG).e(err, "Error marking item as watched from notification action")
                    val updatedItem = effectiveDb.calendarItemDao().findItem(itemPrimaryKey) ?: return@launch
                    NotificationManager.updateNotification(
                        item = updatedItem,
                        context = context
                    )
                } else {
                    NotificationManager.dismissNotification(
                        item = item,
                        context = context
                    )
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error marking episode as watched from notification action")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleMarkSeasonWatched(context: Context, intent: Intent) {
        val itemPrimaryKey = intent.getStringExtra(EXTRA_ITEM_PRIMARY_KEY) ?: return
        Timber.tag(TAG).d("Handling mark season watched action for key=$itemPrimaryKey")
        val effectiveRepo = if (::repo.isInitialized) repo else SimklRepository(context)
        val effectiveDb = if (::db.isInitialized) db else AppDatabase.getDatabase(context)

        val pendingResult = goAsync()
        scope.launch {
            try {
                val item = effectiveDb.calendarItemDao().findItem(itemPrimaryKey) ?: return@launch
                val result = effectiveRepo.markSeasonWatched(
                    simklId = item.simklId,
                    season = item.season ?: 1,
                    mediaType = item.type,
                )

                val err = result.exceptionOrNull()
                if (err != null) {
                    Timber.tag(TAG).e(err, "Error marking season as watched from notification action")
                    val updatedItem = effectiveDb.calendarItemDao().findItem(itemPrimaryKey) ?: return@launch
                    NotificationManager.updateNotification(
                        item = updatedItem,
                        context = context
                    )
                } else {
                    NotificationManager.dismissNotification(
                        item = item,
                        context = context
                    )
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error marking season as watched from notification action")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleDownloadItem(context: Context, intent: Intent) {
        val itemPrimaryKey = intent.getStringExtra(EXTRA_ITEM_PRIMARY_KEY) ?: return
        Timber.tag(TAG).d("Handling download item action for key=$itemPrimaryKey")
        val effectiveRepo = if (::repo.isInitialized) repo else SimklRepository(context)
        val effectiveDb = if (::db.isInitialized) db else AppDatabase.getDatabase(context)

        val pendingResult = goAsync()
        scope.launch {
            try {
                val item = effectiveDb.calendarItemDao().findItem(itemPrimaryKey) ?: return@launch

                // Update status to WANTED first
                effectiveRepo.updateMediaStatus(item.primaryKey, MediaStatus.WANTED)

                // Refresh item from DB
                val updatedItem = effectiveDb.calendarItemDao().findItem(itemPrimaryKey) ?: return@launch

                // Trigger search and download
                try {
                    effectiveRepo.searchAndDownloadEpisode(updatedItem)
                } finally {
                    effectiveRepo.torrentServiceHelper.unbind()
                }

                // Refetch again to reflect intermediate state change (WANTED -> DOWNLOADING / IGNORED)
                val finalItem = effectiveDb.calendarItemDao().findItem(itemPrimaryKey) ?: return@launch
                NotificationManager.updateNotification(
                    item = finalItem,
                    context = context
                )
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error starting download from notification action")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleNotificationDismissed(context: Context, intent: Intent) {
        val itemPrimaryKey = intent.getStringExtra(EXTRA_ITEM_PRIMARY_KEY) ?: return
        Timber.tag(TAG).d("Handling notification dismissed action for key=$itemPrimaryKey")
        val pendingResult = goAsync()
        scope.launch {
            try {
                NotificationManager.removeActiveNotification(context, itemPrimaryKey)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error removing active notification on dismiss")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleDownloadSeasonMissingEpisodes(context: Context, intent: Intent) {
        val itemPrimaryKey = intent.getStringExtra(EXTRA_ITEM_PRIMARY_KEY) ?: return
        Timber.tag(TAG).d("Handling download season missing episodes action for key=$itemPrimaryKey")
        val effectiveRepo = if (::repo.isInitialized) repo else SimklRepository(context)
        val effectiveDb = if (::db.isInitialized) db else AppDatabase.getDatabase(context)

        val pendingResult = goAsync()
        scope.launch {
            try {
                val item = effectiveDb.calendarItemDao().findItem(itemPrimaryKey) ?: return@launch
                val season = item.season ?: 1

                val seasonItems =
                    effectiveDb.calendarItemDao().getItemsInSeasonOrRelatedItems(item.simklId, season)
                val ignoredItems = seasonItems.filter { it.mediaStatus == MediaStatus.IGNORED }

                for (ignored in ignoredItems) {
                    effectiveRepo.updateMediaStatus(ignored.primaryKey, MediaStatus.WANTED)
                }

                // Trigger batch search and download for all WANTED items
                try {
                    effectiveRepo.searchAndDownloadWantedItems()
                } finally {
                    effectiveRepo.torrentServiceHelper.unbind()
                }

                // Update the notification that triggered this to reflect new season aggregate status
                val updatedItem = effectiveDb.calendarItemDao().findItem(itemPrimaryKey) ?: return@launch
                NotificationManager.updateNotification(
                    item = updatedItem,
                    context = context
                )
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error starting season download from notification action")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
