package com.felixbrucker.simklcalendar.data.repository

import android.content.Context
import android.content.Intent
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.network.TorrentSearchManager
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import com.felixbrucker.simklcalendar.extensions.destinationSubdirectory
import com.felixbrucker.simklcalendar.receiver.download.DownloadCompletedReceiver
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

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calendarDao: CalendarItemDao,
    private val itemDownloadSettingsDao: ItemDownloadSettingsDao,
    private val torrentSearchManager: TorrentSearchManager,
    private val torrentServiceHelper: TorrentServiceHelper,
) {
    suspend fun saveItemDownloadSettings(settings: ItemDownloadSettings) = withContext(Dispatchers.IO) {
        itemDownloadSettingsDao.insertOrUpdate(settings)
    }

    fun getItemDownloadSettingsFlow(simklId: Int): Flow<ItemDownloadSettings?> {
        return itemDownloadSettingsDao.getSettingsFlow(simklId)
    }

    suspend fun searchTorrents(item: CalendarItemWithWatchlist): List<SearchResultItem> {
        return torrentSearchManager.search(item)
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

    fun generateCompletionIntentUri(primaryKey: String): String {
        val intent = Intent(DownloadCompletedReceiver.ACTION_DOWNLOAD_COMPLETED).apply {
            setClassName(context.packageName, DownloadCompletedReceiver::class.java.name)
            putExtra(DownloadCompletedReceiver.EXTRA_ITEM_PRIMARY_KEY, primaryKey)
        }
        return intent.toUri(Intent.URI_INTENT_SCHEME)
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
            searchTorrents(item)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        if (results.isEmpty()) {
            return@withContext Result.failure(Exception("No torrent results found for this episode."))
        }

        // 3. Select first result and start download
        val firstResult = results.first()
        val completionUri = generateCompletionIntentUri(item.primaryKey)

        val result = torrentServiceHelper.addTorrent(
            uri = firstResult.uri.toString(),
            name = firstResult.name,
            destinationSubdirectory = item.destinationSubdirectory(),
            createSubfolderByName = false,
            notifyOnCompletion = true,
            fileSelectionMode = "BIGGEST",
            onCompletionIntentUri = completionUri,
        )

        result.onSuccess { taskId ->
            // 4. Update status to DOWNLOADING with taskId
            calendarDao.updateDownloadTaskId(item.primaryKey, taskId, MediaStatus.DOWNLOADING)
        }

        result
    }

    suspend fun searchAndDownloadWantedItems(
        withDelay: Duration = 50.milliseconds,
        onProgress: (current: Int, total: Int, itemTitle: String, success: Boolean) -> Unit = { _, _, _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val items = calendarDao.getAllCalendarItems().first()
        val wantedItems = items.filter { it.mediaStatus == MediaStatus.WANTED }

        if (wantedItems.isEmpty()) return@withContext

        wantedItems.forEachIndexed { index, item ->
            val result = searchAndDownloadEpisode(item)
            onProgress(index + 1, wantedItems.size, item.title, result.isSuccess)
            delay(withDelay) // Artificial delay to prevent flicker and show progress
        }
    }
}
