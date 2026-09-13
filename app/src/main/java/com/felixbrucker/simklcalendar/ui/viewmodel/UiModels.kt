package com.felixbrucker.simklcalendar.ui.viewmodel

import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

enum class MainViewMode {
    CALENDAR,
    TABLE
}

enum class TableSortField {
    NAME,
    LAST_EP,
    NEXT_EP,
    WATCHED,
    DOWNLOADED
}

enum class SortDirection {
    ASCENDING,
    DESCENDING
}

data class WatchlistTableItem(
    val watchlistItem: TrackedWatchlistItem,
    val hasUnwatched: Boolean,
    val hasUnwatchedReleased: Boolean = false,
    val nextEpisodeDate: Instant?,
    val lastAiredDate: Instant?,
    val watchedReleasedCount: Int = 0,
    val totalReleasedCount: Int = 0,
    val downloadedReleasedCount: Int = 0,
    val totalDownloadableReleasedCount: Int = 0,
    val status: String? = null
) {
    val watchedProgress: Double = if (totalReleasedCount > 0) watchedReleasedCount.toDouble() / totalReleasedCount else 0.0
    val downloadedProgress: Double = if (totalDownloadableReleasedCount > 0) downloadedReleasedCount.toDouble() / totalDownloadableReleasedCount else 0.0
}

sealed class CalendarListItem {
    data class DateHeader(val dateText: String) : CalendarListItem()
    data class Item(val item: CalendarItemWithWatchlist) : CalendarListItem()
}

interface MediaActionViewModel {
    fun markEpisodeWatched(simklId: Int, season: Int?, episodeNumber: Int, mediaType: MediaType, primaryKey: String? = null, showTitle: String? = null, onResult: (Boolean, String) -> Unit = { _, _ -> })
    fun markEpisodeUnwatched(simklId: Int, season: Int?, episodeNumber: Int, mediaType: MediaType, primaryKey: String? = null, onResult: (Boolean, String) -> Unit = { _, _ -> })
    fun markMovieWatched(simklId: Int, title: String? = null, primaryKey: String? = null, onResult: (Boolean, String) -> Unit = { _, _ -> })
    fun markMovieUnwatched(simklId: Int, primaryKey: String? = null, onResult: (Boolean, String) -> Unit = { _, _ -> })
    fun updateMediaStatus(primaryKey: String, status: MediaStatus)
    fun toggleNotification(simklId: Int, every: Boolean, last: Boolean)
    fun markSeasonWatched(simklId: Int, season: Int, mediaType: MediaType, onResult: (Boolean, String) -> Unit = { _, _ -> })
    fun markSeasonUnwatched(simklId: Int, season: Int, mediaType: MediaType, onResult: (Boolean, String) -> Unit = { _, _ -> })
    fun updateSeasonMediaStatus(simklId: Int, season: Int, status: MediaStatus)
}

interface DownloadConfigViewModel {
    val autoDownloadQuality: StateFlow<String>
    val autoDownloadPreferHevc: StateFlow<Boolean>
    val autoDownloadUnwatchedTv: StateFlow<Boolean>
    val autoDownloadUnwatchedAnime: StateFlow<Boolean>
    val autoDownloadUnwatchedMovie: StateFlow<Boolean>
    val isTorrentServiceInstalled: StateFlow<Boolean>
    val downloadSubdirectories: StateFlow<List<String>>
    fun getItemDownloadSettingsFlow(simklId: Int): Flow<ItemDownloadSettings?>
    fun saveItemDownloadSettings(settings: ItemDownloadSettings)
}
