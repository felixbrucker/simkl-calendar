package com.felixbrucker.simklcalendar.receiver.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.model.EpisodeSearchStyle
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.repository.CalendarRepository
import com.felixbrucker.simklcalendar.data.util.formatTwoDigits
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
    lateinit var itemDownloadSettingsDao: ItemDownloadSettingsDao

    @Inject
    lateinit var notificationManager: NotificationManager

    companion object {
        private const val TAG = "DownloadCompletedReceiver"
        const val ACTION_DOWNLOAD_COMPLETED = "com.felixbrucker.simklcalendar.ACTION_DOWNLOAD_COMPLETED"
        const val ACTION_SEASON_DOWNLOAD_COMPLETED = "com.felixbrucker.simklcalendar.ACTION_SEASON_DOWNLOAD_COMPLETED"
        const val EXTRA_ITEM_PRIMARY_KEY = "extra_item_primary_key"
        const val EXTRA_ITEM_PRIMARY_KEYS = "extra_item_primary_keys"
        const val EXTRA_SIMKL_ID = "extra_simkl_id"
        const val EXTRA_SEASON = "extra_season"
        const val EXTRA_FILE_PATHS = "EXTRA_FILE_PATHS"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val pendingResult = try {
            goAsync()
        } catch (_: Exception) {
            null
        }

        if (intent.action == ACTION_DOWNLOAD_COMPLETED) {
            scope.launch {
                try {
                    handleSingleDownloadCompleted(intent)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error handling download completion")
                } finally {
                    pendingResult?.finish()
                }
            }
        } else if (intent.action == ACTION_SEASON_DOWNLOAD_COMPLETED) {
            scope.launch {
                try {
                    handleSeasonDownloadCompleted(intent)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error handling season download completion")
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }

    private suspend fun handleSingleDownloadCompleted(intent: Intent) {
        val itemPrimaryKey = intent.getStringExtra(EXTRA_ITEM_PRIMARY_KEY) ?: return
        val firstFilePath = intent.getStringArrayListExtra(EXTRA_FILE_PATHS)?.firstOrNull()

        calendarRepository.updateDownloadCompleted(
            itemPrimaryKey,
            null,
            MediaStatus.DOWNLOADED,
            firstFilePath
        )
        Timber.tag(TAG).d(
            "Updated item $itemPrimaryKey to DOWNLOADED status, " +
                "cleared taskId, and persisted filePath=$firstFilePath"
        )

        val item = calendarItemDao.findItem(itemPrimaryKey) ?: return

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
    }

    private suspend fun handleSeasonDownloadCompleted(intent: Intent) {
        val primaryKeys = intent.getStringArrayListExtra(EXTRA_ITEM_PRIMARY_KEYS) ?: emptyList<String>()
        val filePaths = intent.getStringArrayListExtra(EXTRA_FILE_PATHS) ?: emptyList<String>()

        if (primaryKeys.isEmpty()) return

        val itemsToProcess = primaryKeys.mapNotNull { calendarItemDao.findItem(it) }

        for (epItem in itemsToProcess) {
            val settings = itemDownloadSettingsDao.getSettings(epItem.simklId)
            val searchStyle = settings?.episodeSearchStyle ?: epItem.type.defaultEpisodeSearchStyle
            val season = settings?.seasonOverrides?.get(epItem.season ?: 1) ?: (epItem.season ?: 1)
            val episode = epItem.episodeNumber ?: 1

            val matchedFilePath = matchFilePathForEpisode(
                filePaths = filePaths,
                season = season,
                episode = episode,
                searchStyle = searchStyle
            )

            calendarRepository.updateDownloadCompleted(
                epItem.primaryKey,
                null,
                MediaStatus.DOWNLOADED,
                matchedFilePath
            )
            Timber.tag(TAG).d(
                "Updated item %s to DOWNLOADED status, filePath=%s",
                epItem.primaryKey,
                matchedFilePath
            )

            val updatedItem = calendarItemDao.findItem(epItem.primaryKey) ?: epItem
            notificationManager.updateNotification(updatedItem)

            val itemSeason = updatedItem.season
            if (itemSeason != null) {
                val finaleItem = calendarItemDao.getSeasonFinaleItem(updatedItem.simklId, itemSeason)
                if (finaleItem != null && finaleItem.primaryKey != updatedItem.primaryKey) {
                    notificationManager.updateNotification(finaleItem)
                }
            }
        }
    }

    internal fun matchFilePathForEpisode(
        filePaths: List<String>,
        season: Int,
        episode: Int,
        searchStyle: EpisodeSearchStyle
    ): String? {
        if (filePaths.isEmpty()) {
            return null
        }

        val seasonAndEpisodeTerm = "S${formatTwoDigits(season)}E${formatTwoDigits(episode)}"
        val exactMatch = filePaths.find { it.contains(seasonAndEpisodeTerm, ignoreCase = true) }

        return when {
            exactMatch != null -> exactMatch
            searchStyle == EpisodeSearchStyle.SeasonAndEpisode -> null
            else -> findByAnimeEpisodeVariant(filePaths, formatTwoDigits(episode))
        }
    }

    private fun findByAnimeEpisodeVariant(filePaths: List<String>, episodeTerm: String): String? {
        val variants = listOf(" $episodeTerm ", " ${episodeTerm}v")
        return filePaths.find { path ->
            val fileName = path.substringAfterLast('/').substringAfterLast('\\')
            variants.any { variant -> fileName.contains(variant, ignoreCase = true) }
        }
    }
}
