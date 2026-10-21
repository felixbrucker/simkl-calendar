package com.felixbrucker.simklcalendar.receiver.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
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
        const val EXTRA_SIMKL_ID = "extra_simkl_id"
        const val EXTRA_SEASON = "extra_season"
        const val EXTRA_FILE_PATHS = "EXTRA_FILE_PATHS"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return
        if (action != ACTION_DOWNLOAD_COMPLETED && action != ACTION_SEASON_DOWNLOAD_COMPLETED) {
            return
        }

        val pendingResult = try {
            goAsync()
        } catch (_: Exception) {
            null
        }
        scope.launch {
            try {
                if (action == ACTION_SEASON_DOWNLOAD_COMPLETED) {
                    handleSeasonDownloadCompleted(intent)
                } else {
                    handleSingleDownloadCompleted(intent)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error handling download completion")
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private suspend fun handleSingleDownloadCompleted(intent: Intent) {
        val itemPrimaryKey = intent.getStringExtra(EXTRA_ITEM_PRIMARY_KEY) ?: return
        val filePaths = intent.getStringArrayListExtra(EXTRA_FILE_PATHS) ?: emptyList<String>()
        val primaryItem = calendarItemDao.findItem(itemPrimaryKey)
        val taskId = primaryItem?.downloadTaskId

        val itemsToProcess = if (!taskId.isNullOrEmpty()) {
            val taskItems = calendarItemDao.getItemsForDownloadTaskId(taskId)
            if (taskItems.isNotEmpty()) taskItems else listOfNotNull(primaryItem)
        } else {
            listOfNotNull(primaryItem)
        }

        if (itemsToProcess.isEmpty()) {
            calendarRepository.updateDownloadCompleted(
                itemPrimaryKey,
                null,
                MediaStatus.DOWNLOADED,
                filePaths.firstOrNull()
            )
            return
        }

        for (epItem in itemsToProcess) {
            processCompletedEpisodeItem(epItem, itemsToProcess.size, filePaths)
        }
    }

    private suspend fun handleSeasonDownloadCompleted(intent: Intent) {
        val simklId = intent.getIntExtra(EXTRA_SIMKL_ID, -1)
        val season = intent.getIntExtra(EXTRA_SEASON, -1)
        val filePaths = intent.getStringArrayListExtra(EXTRA_FILE_PATHS) ?: emptyList<String>()

        if (simklId == -1 || season == -1) return

        val seasonEpisodes = calendarItemDao.getItemsInSeasonOrRelatedItems(simklId, season)
            .filter { (it.season ?: 1) == season }

        for (epItem in seasonEpisodes) {
            processCompletedEpisodeItem(epItem, seasonEpisodes.size, filePaths)
        }
    }

    private suspend fun processCompletedEpisodeItem(
        epItem: CalendarItemWithWatchlist,
        totalItemsCount: Int,
        filePaths: List<String>
    ) {
        val settings = itemDownloadSettingsDao.getSettings(epItem.simklId)
        val searchStyle = settings?.episodeSearchStyle ?: epItem.type.defaultEpisodeSearchStyle
        val season = settings?.seasonOverrides?.get(epItem.season ?: 1) ?: (epItem.season ?: 1)
        val episode = epItem.episodeNumber ?: 1

        val matchedFilePath = matchFilePathForEpisode(
            filePaths = filePaths,
            season = season,
            episode = episode,
            searchStyle = searchStyle
        ) ?: if (totalItemsCount == 1) filePaths.firstOrNull() else null

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
            else -> findByEpisodeVariants(filePaths, formatTwoDigits(episode))
        }
    }

    private fun findByEpisodeVariants(filePaths: List<String>, episodeTerm: String): String? {
        val variants = listOf(
            " $episodeTerm ", " $episodeTerm.", " $episodeTerm-", " ${episodeTerm}_",
            " ${episodeTerm}v", "E$episodeTerm", "e$episodeTerm", "[$episodeTerm]", "($episodeTerm)"
        )
        return filePaths.find { path ->
            val fileName = path.substringAfterLast('/').substringAfterLast('\\')
            variants.any { variant -> fileName.contains(variant, ignoreCase = true) }
        }
    }
}
