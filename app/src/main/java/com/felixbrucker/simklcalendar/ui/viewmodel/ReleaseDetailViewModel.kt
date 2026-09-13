package com.felixbrucker.simklcalendar.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.NotificationSetting
import com.felixbrucker.simklcalendar.data.database.WatchedEpisode
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import com.felixbrucker.simklcalendar.data.util.DirectoryUtils
import com.felixbrucker.simklcalendar.data.util.DownloadProgress
import com.felixbrucker.torrent_search_api.SearchResultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ReleaseDetailViewModel(application: Application) : AndroidViewModel(application), MediaActionViewModel, DownloadConfigViewModel {
    private val repository = SimklRepository(application)
    private val downloadPrefs = application.getSharedPreferences("auto_download_prefs", Context.MODE_PRIVATE)

    override val autoDownloadQuality = MutableStateFlow(downloadPrefs.getString("quality", "1080p") ?: "1080p")
    override val autoDownloadPreferHevc = MutableStateFlow(downloadPrefs.getBoolean("prefer_hevc", true))
    override val autoDownloadUnwatchedTv = MutableStateFlow(downloadPrefs.getBoolean("auto_download_unwatched_tv", false))
    override val autoDownloadUnwatchedAnime = MutableStateFlow(downloadPrefs.getBoolean("auto_download_unwatched_anime", false))
    override val autoDownloadUnwatchedMovie = MutableStateFlow(downloadPrefs.getBoolean("auto_download_unwatched_movie", false))
    override val downloadSubdirectories = MutableStateFlow<List<String>>(emptyList())

    fun refreshDownloadSubdirectories() {
        viewModelScope.launch(Dispatchers.IO) {
            downloadSubdirectories.value = DirectoryUtils.getDownloadSubdirectories()
        }
    }

    val notificationSettings: StateFlow<List<NotificationSetting>> = repository.notificationSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val torrentDownloads: StateFlow<Map<String, DownloadProgress>> = repository.torrentServiceHelper.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun getCalendarItem(primaryKey: String): Flow<CalendarItemWithWatchlist?> {
        return repository.calendarItems.map { list ->
            list.firstOrNull { it.primaryKey == primaryKey }
                ?: list.firstOrNull { it.simklId.toString() == primaryKey }
        }
    }

    fun getCalendarItemsForShow(simklId: Int): Flow<List<CalendarItemWithWatchlist>> {
        return repository.calendarItems.map { list -> list.filter { it.simklId == simklId } }
    }

    fun getWatchedEpisodesForShow(simklId: Int): Flow<List<WatchedEpisode>> {
        return repository.watchedEpisodes.map { list -> list.filter { it.simklId == simklId } }
    }

    val customSearchLinks: StateFlow<List<CustomSearchLink>> = repository.customSearchLinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _updatingWatchStatusKeys = MutableStateFlow<Set<String>>(emptySet())
    val updatingWatchStatusKeys: StateFlow<Set<String>> = _updatingWatchStatusKeys.asStateFlow()

    private val _isMarkingWatched = MutableStateFlow(false)
    val isMarkingWatched: StateFlow<Boolean> = _isMarkingWatched.asStateFlow()

    override fun getItemDownloadSettingsFlow(simklId: Int): Flow<ItemDownloadSettings?> {
        return repository.getItemDownloadSettingsFlow(simklId)
    }

    override fun saveItemDownloadSettings(settings: ItemDownloadSettings) {
        viewModelScope.launch { repository.saveItemDownloadSettings(settings) }
    }

    override fun markEpisodeWatched(
        simklId: Int,
        season: Int?,
        episodeNumber: Int,
        mediaType: MediaType,
        primaryKey: String?,
        showTitle: String?,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            primaryKey?.let { _updatingWatchStatusKeys.update { keys -> keys + it } }
            _isMarkingWatched.value = true
            val result = repository.markEpisodeWatched(simklId, season, episodeNumber, mediaType)
            _isMarkingWatched.value = false
            primaryKey?.let { _updatingWatchStatusKeys.update { keys -> keys - it } }
            if (result.isSuccess) {
                onResult(true, "Marked ${showTitle ?: "episode"} as watched")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Failed to mark as watched")
            }
        }
    }

    override fun markEpisodeUnwatched(
        simklId: Int,
        season: Int?,
        episodeNumber: Int,
        mediaType: MediaType,
        primaryKey: String?,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            primaryKey?.let { _updatingWatchStatusKeys.update { keys -> keys + it } }
            val result = repository.markEpisodeUnwatched(simklId, season, episodeNumber, mediaType)
            primaryKey?.let { _updatingWatchStatusKeys.update { keys -> keys - it } }
            if (result.isSuccess) {
                onResult(true, "Marked as unwatched")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Failed to mark as unwatched")
            }
        }
    }

    override fun markMovieWatched(
        simklId: Int,
        title: String?,
        primaryKey: String?,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            primaryKey?.let { _updatingWatchStatusKeys.update { keys -> keys + it } }
            _isMarkingWatched.value = true
            val result = repository.markMovieWatched(simklId)
            _isMarkingWatched.value = false
            primaryKey?.let { _updatingWatchStatusKeys.update { keys -> keys - it } }
            if (result.isSuccess) {
                onResult(true, "Marked ${title ?: "movie"} as watched")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Failed to mark movie as watched")
            }
        }
    }

    override fun markMovieUnwatched(
        simklId: Int,
        primaryKey: String?,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            primaryKey?.let { _updatingWatchStatusKeys.update { keys -> keys + it } }
            val result = repository.markMovieUnwatched(simklId)
            primaryKey?.let { _updatingWatchStatusKeys.update { keys -> keys - it } }
            if (result.isSuccess) {
                onResult(true, "Marked movie as unwatched")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Failed to mark movie as unwatched")
            }
        }
    }

    override fun updateMediaStatus(primaryKey: String, status: MediaStatus) {
        viewModelScope.launch { repository.updateMediaStatus(primaryKey, status) }
    }

    override fun toggleNotification(simklId: Int, every: Boolean, last: Boolean) {
        viewModelScope.launch { repository.toggleNotificationSetting(simklId, every, last) }
    }

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

    suspend fun searchTorrents(item: CalendarItemWithWatchlist): List<SearchResultItem> {
        return repository.searchTorrents(item)
    }

    fun searchAndDownloadEpisode(item: CalendarItemWithWatchlist, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val result = repository.searchAndDownloadEpisode(item)
            onResult(result)
        }
    }

    val isTorrentServiceBound: StateFlow<Boolean> = repository.torrentServiceHelper.isBound
    override val isTorrentServiceInstalled: StateFlow<Boolean> = repository.torrentServiceHelper.isInstalled

}
