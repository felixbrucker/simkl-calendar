package com.felixbrucker.simklcalendar.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.NotificationSetting
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import com.felixbrucker.simklcalendar.data.util.DownloadProgress
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant

class WatchlistItemDetailViewModel(application: Application) : AndroidViewModel(application), MediaActionViewModel, DownloadConfigViewModel {
    private val repository = SimklRepository(application)
    private val downloadPrefs = application.getSharedPreferences("auto_download_prefs", Context.MODE_PRIVATE)

    override val autoDownloadQuality = MutableStateFlow(downloadPrefs.getString("quality", "1080p") ?: "1080p")
    override val autoDownloadPreferHevc = MutableStateFlow(downloadPrefs.getBoolean("prefer_hevc", true))
    override val autoDownloadUnwatchedTv = MutableStateFlow(downloadPrefs.getBoolean("auto_download_unwatched_tv", false))
    override val autoDownloadUnwatchedAnime = MutableStateFlow(downloadPrefs.getBoolean("auto_download_unwatched_anime", false))
    override val autoDownloadUnwatchedMovie = MutableStateFlow(downloadPrefs.getBoolean("auto_download_unwatched_movie", false))
    override val isTorrentServiceInstalled = repository.torrentServiceHelper.isInstalled
    override val downloadSubdirectories = MutableStateFlow<List<String>>(emptyList())

    val torrentDownloads: StateFlow<Map<String, DownloadProgress>> = repository.torrentServiceHelper.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    override fun getItemDownloadSettingsFlow(simklId: Int): Flow<ItemDownloadSettings?> {
        return repository.getItemDownloadSettingsFlow(simklId)
    }

    override fun saveItemDownloadSettings(settings: ItemDownloadSettings) {
        viewModelScope.launch { repository.saveItemDownloadSettings(settings) }
    }

    fun getWatchlistItem(simklId: Int): Flow<TrackedWatchlistItem?> {
        return repository.watchlistItems.map { list -> list.find { it.simklId == simklId } }
    }

    fun getCalendarItemsForShow(simklId: Int): Flow<List<CalendarItemWithWatchlist>> {
        return repository.calendarItems.map { list -> list.filter { it.simklId == simklId } }
    }

    fun getWatchlistTableItem(simklId: Int): Flow<WatchlistTableItem?> {
        return repository.watchlistItems.combine(repository.calendarItems) { watchlist, calendar ->
            val item = watchlist.find { it.simklId == simklId } ?: return@combine null
            val itemCalendar = calendar.filter { it.simklId == simklId }
            val now = Instant.now()
            val releasedItems = itemCalendar.filter { it.date.isBefore(now) }

            WatchlistTableItem(
                watchlistItem = item,
                hasUnwatched = itemCalendar.any { !it.isWatched },
                hasUnwatchedReleased = releasedItems.any { !it.isWatched },
                nextEpisodeDate = itemCalendar.filter { it.date.isAfter(now) }.minByOrNull { it.date }?.date,
                lastAiredDate = releasedItems.maxByOrNull { it.date }?.date,
                watchedReleasedCount = releasedItems.count { it.isWatched },
                totalReleasedCount = releasedItems.size,
                downloadedReleasedCount = releasedItems.count { it.mediaStatus == MediaStatus.DOWNLOADED },
                totalDownloadableReleasedCount = releasedItems.count {
                    it.mediaStatus == MediaStatus.WANTED ||
                            it.mediaStatus == MediaStatus.DOWNLOADING ||
                    it.mediaStatus == MediaStatus.DOWNLOADED
                }
            )
        }
    }

    override fun markEpisodeWatched(simklId: Int, season: Int?, episodeNumber: Int, mediaType: MediaType, primaryKey: String?, showTitle: String?, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.markEpisodeWatched(simklId, season, episodeNumber, mediaType)
            if (result.isSuccess) onResult(true, "Marked episode as watched")
            else onResult(false, result.exceptionOrNull()?.message ?: "Failed")
        }
    }

    override fun markEpisodeUnwatched(simklId: Int, season: Int?, episodeNumber: Int, mediaType: MediaType, primaryKey: String?, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.markEpisodeUnwatched(simklId, season, episodeNumber, mediaType)
            if (result.isSuccess) onResult(true, "Marked as unwatched")
            else onResult(false, result.exceptionOrNull()?.message ?: "Failed")
        }
    }

    override fun markMovieWatched(simklId: Int, title: String?, primaryKey: String?, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.markMovieWatched(simklId)
            if (result.isSuccess) onResult(true, "Marked movie as watched")
            else onResult(false, result.exceptionOrNull()?.message ?: "Failed")
        }
    }

    override fun markMovieUnwatched(simklId: Int, primaryKey: String?, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.markMovieUnwatched(simklId)
            if (result.isSuccess) onResult(true, "Marked movie as unwatched")
            else onResult(false, result.exceptionOrNull()?.message ?: "Failed")
        }
    }

    override fun updateMediaStatus(primaryKey: String, status: MediaStatus) {
        viewModelScope.launch { repository.updateMediaStatus(primaryKey, status) }
    }

    private val _updatingWatchStatusKeys = MutableStateFlow<Set<String>>(emptySet())
    val updatingWatchStatusKeys: StateFlow<Set<String>> = _updatingWatchStatusKeys.asStateFlow()

    override fun toggleNotification(simklId: Int, every: Boolean, last: Boolean) {
        viewModelScope.launch { repository.toggleNotificationSetting(simklId, every, last) }
    }

    val notificationSettings: StateFlow<List<NotificationSetting>> = repository.notificationSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customSearchLinks: StateFlow<List<CustomSearchLink>> = repository.customSearchLinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    override fun markSeasonWatched(simklId: Int, season: Int, mediaType: MediaType, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.markSeasonWatched(simklId, season, mediaType)
            if (result.isSuccess) onResult(true, "Marked season as watched")
            else onResult(false, result.exceptionOrNull()?.message ?: "Failed")
        }
    }

    override fun markSeasonUnwatched(simklId: Int, season: Int, mediaType: MediaType, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.markSeasonUnwatched(simklId, season, mediaType)
            if (result.isSuccess) onResult(true, "Marked season as unwatched")
            else onResult(false, result.exceptionOrNull()?.message ?: "Failed")
        }
    }

    override fun updateSeasonMediaStatus(simklId: Int, season: Int, status: MediaStatus) {
        viewModelScope.launch { repository.updateSeasonMediaStatus(simklId, season, status) }
    }
}
