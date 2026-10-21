package com.felixbrucker.simklcalendar.data.repository

import android.content.Context
import android.content.Intent
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.network.TorrentSearchManager
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import com.felixbrucker.simklcalendar.data.util.formattedAutoDownloadLabel
import com.felixbrucker.simklcalendar.extensions.destinationSubdirectory
import com.felixbrucker.simklcalendar.receiver.download.DownloadCompletedReceiver
import com.felixbrucker.simklcalendar.receiver.notification.NotificationManager
import com.felixbrucker.torrent_search_api.SearchResultItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Suppress("TooManyFunctions")
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calendarDao: CalendarItemDao,
    private val watchlistDao: WatchlistDao,
    private val itemDownloadSettingsDao: ItemDownloadSettingsDao,
    private val torrentSearchManager: TorrentSearchManager,
    private val torrentServiceHelper: TorrentServiceHelper,
    private val notificationManager: NotificationManager,
) {
    suspend fun saveItemDownloadSettings(settings: ItemDownloadSettings) = withContext(Dispatchers.IO) {
        itemDownloadSettingsDao.insertOrUpdate(settings)
    }

    fun getItemDownloadSettingsFlow(simklId: Int): Flow<ItemDownloadSettings?> {
        return itemDownloadSettingsDao.getSettingsFlow(simklId)
    }

    suspend fun detectTorrents(
        simklId: Int,
        customTitle: String,
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        torrentSearchManager.detectTorrents(simklId, customTitle)
    }

    suspend fun searchEpisode(
        item: CalendarItemWithWatchlist,
        customTitle: String? = null,
        includeSeasonToken: Boolean = false
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        torrentSearchManager.searchEpisode(item, customTitle, includeSeasonToken)
    }

    suspend fun searchSeason(
        simklId: Int,
        season: Int,
        customTitle: String? = null,
        includeSeasonToken: Boolean = false
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        torrentSearchManager.searchSeason(simklId, season, customTitle, includeSeasonToken)
    }

    suspend fun downloadEpisodeResult(
        item: CalendarItemWithWatchlist,
        resultItem: SearchResultItem
    ): Result<String> = withContext(Dispatchers.IO) {
        if (item.mediaStatus != MediaStatus.WANTED) {
            calendarDao.updateMediaStatus(item.primaryKey, MediaStatus.WANTED)
        }

        val completionUri = generateCompletionIntentUri(item.primaryKey)
        val result = torrentServiceHelper.addTorrent(
            uri = resultItem.uri.toString(),
            name = resultItem.name,
            destinationSubdirectory = item.destinationSubdirectory(),
            createSubfolderByName = false,
            notifyOnCompletion = true,
            fileSelectionMode = "BIGGEST",
            onCompletionIntentUri = completionUri,
        )

        result.onSuccess { taskId ->
            calendarDao.updateDownloadTaskId(item.primaryKey, taskId, MediaStatus.DOWNLOADING)
        }

        result
    }

    suspend fun downloadSeasonResult(
        simklId: Int,
        season: Int,
        resultItem: SearchResultItem
    ): Result<String> = withContext(Dispatchers.IO) {
        val targetItems = calendarDao.getUnwatchedDownloadableSeasonItems(simklId, season)

        if (targetItems.isEmpty()) {
            return@withContext Result.failure(Exception("No downloadable items found in season $season."))
        }

        targetItems.forEach { item ->
            if (item.mediaStatus != MediaStatus.WANTED) {
                calendarDao.updateMediaStatus(item.primaryKey, MediaStatus.WANTED)
            }
        }

        val watchlistItem = watchlistDao.getItem(simklId)
            ?: return@withContext Result.failure(Exception("Watchlist item not found for simklId $simklId"))
        val itemSettings = itemDownloadSettingsDao.getSettings(simklId)
        val destinationSubdirectory = watchlistItem.destinationSubdirectory(itemSettings)
        val primaryKeys = targetItems.map { it.primaryKey }
        val completionUri = generateSeasonCompletionIntentUri(simklId, season, primaryKeys)

        val result = torrentServiceHelper.addTorrent(
            uri = resultItem.uri.toString(),
            name = resultItem.name,
            destinationSubdirectory = destinationSubdirectory,
            createSubfolderByName = false,
            notifyOnCompletion = true,
            fileSelectionMode = "ALL",
            onCompletionIntentUri = completionUri,
        )

        result.onSuccess { taskId ->
            targetItems.forEach { item ->
                calendarDao.updateDownloadTaskId(item.primaryKey, taskId, MediaStatus.DOWNLOADING)
            }
        }

        result
    }

    suspend fun searchAndDownloadSeason(simklId: Int, season: Int) = withContext(Dispatchers.IO) {
        val unwatchedItems = calendarDao.getUnwatchedDownloadableSeasonItems(simklId, season)
        unwatchedItems.forEach { item ->
            calendarDao.updateMediaStatus(item.primaryKey, MediaStatus.WANTED)
        }
        unwatchedItems.forEach { item ->
            val updatedItem = calendarDao.findItem(item.primaryKey) ?: item
            searchAndDownloadEpisode(updatedItem)
        }
    }

    suspend fun searchAndDownloadEpisode(
        item: CalendarItemWithWatchlist
    ): Result<String> = withContext(Dispatchers.IO) {
        // 1. Set status to WANTED (if not already)
        if (item.mediaStatus != MediaStatus.WANTED) {
            calendarDao.updateMediaStatus(item.primaryKey, MediaStatus.WANTED)
        }

        // 2. Search torrents
        val results = try {
            torrentSearchManager.search(item)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        if (results.isEmpty()) {
            return@withContext Result.failure(Exception("No torrent results found for this episode."))
        }

        // 3. Select first result and start download
        downloadEpisodeResult(item, results.first())
    }

    suspend fun searchAndDownloadWantedItems(
        withDelay: Duration = 50.milliseconds,
        onProgress: (current: Int, total: Int, itemTitle: String, success: Boolean) -> Unit = { _, _, _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val wantedItems = calendarDao.getWantedCalendarItems().first()

        if (wantedItems.isEmpty()) return@withContext

        wantedItems.forEachIndexed { index, item ->
            val result = searchAndDownloadEpisode(item)

            onProgress(index + 1, wantedItems.size, item.formattedAutoDownloadLabel, result.isSuccess)

            // Update the notification for this item, if it has one
            val updatedItem = calendarDao.findItem(item.primaryKey) ?: return@forEachIndexed
            notificationManager.updateNotification(item = updatedItem)

            delay(withDelay) // Artificial delay to prevent flicker and show progress
        }
    }

    private fun generateCompletionIntentUri(primaryKey: String): String {
        val intent = Intent(DownloadCompletedReceiver.ACTION_DOWNLOAD_COMPLETED).apply {
            setClassName(context.packageName, DownloadCompletedReceiver::class.java.name)
            putExtra(DownloadCompletedReceiver.EXTRA_ITEM_PRIMARY_KEY, primaryKey)
        }
        return intent.toUri(Intent.URI_INTENT_SCHEME)
    }

    private fun generateSeasonCompletionIntentUri(simklId: Int, season: Int, primaryKeys: List<String>): String {
        val intent = Intent(DownloadCompletedReceiver.ACTION_SEASON_DOWNLOAD_COMPLETED).apply {
            setClassName(context.packageName, DownloadCompletedReceiver::class.java.name)
            putExtra(DownloadCompletedReceiver.EXTRA_SIMKL_ID, simklId)
            putExtra(DownloadCompletedReceiver.EXTRA_SEASON, season)
            putStringArrayListExtra(DownloadCompletedReceiver.EXTRA_ITEM_PRIMARY_KEYS, ArrayList(primaryKeys))
        }
        return intent.toUri(Intent.URI_INTENT_SCHEME)
    }
}
