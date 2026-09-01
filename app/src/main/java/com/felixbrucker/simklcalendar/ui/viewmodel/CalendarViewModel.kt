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
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.database.WatchedEpisode
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import com.felixbrucker.simklcalendar.data.util.DownloadProgress
import com.felixbrucker.simklcalendar.data.util.MediaFormatter
import com.felixbrucker.simklcalendar.receiver.NotificationReceiver
import com.felixbrucker.simklcalendar.receiver.NotificationScheduler
import com.felixbrucker.torrent_search_api.SearchResultItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val nextEpisodeDate: Instant?,
    val lastAiredDate: Instant?,
    val watchedReleasedCount: Int = 0,
    val totalReleasedCount: Int = 0,
    val downloadedReleasedCount: Int = 0,
    val totalDownloadableReleasedCount: Int = 0,
    val status: String? = null // e.g. "Watching", "Plan to watch"
)

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    val repository = SimklRepository(application)

    val userToken: StateFlow<UserToken?> = repository.activeUserToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val notificationSettings: StateFlow<List<NotificationSetting>> = repository.notificationSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCalendarItems: StateFlow<List<CalendarItemWithWatchlist>> = repository.calendarItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchedEpisodes: StateFlow<List<WatchedEpisode>> = repository.watchedEpisodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customSearchLinks: StateFlow<List<CustomSearchLink>> = repository.customSearchLinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val itemDownloadSettings: StateFlow<List<ItemDownloadSettings>> = repository.itemDownloadSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val torrentDownloads: StateFlow<Map<String, DownloadProgress>> = repository.torrentServiceHelper.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val isTorrentServiceBound: StateFlow<Boolean> = repository.torrentServiceHelper.isBound
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isTorrentServiceInstalled: StateFlow<Boolean> = repository.torrentServiceHelper.isInstalled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _viewMode = MutableStateFlow(MainViewMode.CALENDAR)
    val viewMode: StateFlow<MainViewMode> = _viewMode.asStateFlow()

    fun setViewMode(mode: MainViewMode) {
        _viewMode.value = mode
    }

    private val _tableSortField = MutableStateFlow(TableSortField.NAME)
    val tableSortField: StateFlow<TableSortField> = _tableSortField.asStateFlow()

    private val _tableSortDirection = MutableStateFlow(SortDirection.ASCENDING)
    val tableSortDirection: StateFlow<SortDirection> = _tableSortDirection.asStateFlow()

    fun toggleTableSort(field: TableSortField) {
        if (_tableSortField.value == field) {
            _tableSortDirection.value = if (_tableSortDirection.value == SortDirection.ASCENDING) SortDirection.DESCENDING else SortDirection.ASCENDING
        } else {
            _tableSortField.value = field
            _tableSortDirection.value = SortDirection.ASCENDING
        }
    }

    // Filtering State Flows
    val showTv = MutableStateFlow(true)
    val showAnime = MutableStateFlow(true)
    val showMovies = MutableStateFlow(true)
    val onlySeasonPremieres = MutableStateFlow(false)
    val onlySeasonFinales = MutableStateFlow(false)
    val onlyDigitalDvd = MutableStateFlow(false)
    val showEarlierReleases = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun clearSearchQuery() {
        searchQuery.value = ""
    }

    private val downloadPrefs = application.getSharedPreferences("auto_download_prefs", Context.MODE_PRIVATE)

    val autoDownloadQuality = MutableStateFlow(downloadPrefs.getString("quality", "1080p") ?: "1080p")
    val autoDownloadPreferHevc = MutableStateFlow(downloadPrefs.getBoolean("prefer_hevc", true))
    val autoDownloadUnwatchedDefault = MutableStateFlow(downloadPrefs.getBoolean("unwatched_default", false))
    val autoDownloadPreferredKeywords = MutableStateFlow(downloadPrefs.getStringSet("preferred_keywords", setOf("erai", "subsplease", "megusta", "PSA"))?.toList() ?: listOf("erai", "subsplease", "megusta", "PSA"))
    val autoDownloadIgnoreKeywords = MutableStateFlow(downloadPrefs.getStringSet("ignore_keywords", setOf("ita"))?.toList() ?: listOf("ita"))

    fun updateAutoDownloadQuality(quality: String) {
        autoDownloadQuality.value = quality
        downloadPrefs.edit().putString("quality", quality).apply()
    }

    fun updateAutoDownloadPreferHevc(prefer: Boolean) {
        autoDownloadPreferHevc.value = prefer
        downloadPrefs.edit().putBoolean("prefer_hevc", prefer).apply()
    }

    fun updateAutoDownloadUnwatchedDefault(default: Boolean) {
        autoDownloadUnwatchedDefault.value = default
        downloadPrefs.edit().putBoolean("unwatched_default", default).apply()
    }

    fun addPreferredKeyword(keyword: String) {
        val current = autoDownloadPreferredKeywords.value.toMutableList()
        if (!current.contains(keyword)) {
            current.add(keyword)
            autoDownloadPreferredKeywords.value = current
            downloadPrefs.edit().putStringSet("preferred_keywords", current.toSet()).apply()
        }
    }

    fun removePreferredKeyword(keyword: String) {
        val current = autoDownloadPreferredKeywords.value.toMutableList()
        if (current.remove(keyword)) {
            autoDownloadPreferredKeywords.value = current
            downloadPrefs.edit().putStringSet("preferred_keywords", current.toSet()).apply()
        }
    }

    fun addIgnoreKeyword(keyword: String) {
        val current = autoDownloadIgnoreKeywords.value.toMutableList()
        if (!current.contains(keyword)) {
            current.add(keyword)
            autoDownloadIgnoreKeywords.value = current
            downloadPrefs.edit().putStringSet("ignore_keywords", current.toSet()).apply()
        }
    }

    fun removeIgnoreKeyword(keyword: String) {
        val current = autoDownloadIgnoreKeywords.value.toMutableList()
        if (current.remove(keyword)) {
            autoDownloadIgnoreKeywords.value = current
            downloadPrefs.edit().putStringSet("ignore_keywords", current.toSet()).apply()
        }
    }

    val watchlistTableItems: StateFlow<List<WatchlistTableItem>> = combine(
        repository.watchlistItems,
        repository.calendarEntities,
        searchQuery,
        showTv,
        showAnime,
        showMovies,
        tableSortField,
        tableSortDirection
    ) { flows ->
        val watchlist = flows[0] as List<TrackedWatchlistItem>
        val calendar = flows[1] as List<com.felixbrucker.simklcalendar.data.database.CalendarItem>
        val query = flows[2] as String
        val tv = flows[3] as Boolean
        val anime = flows[4] as Boolean
        val movies = flows[5] as Boolean
        val sortField = flows[6] as TableSortField
        val sortDirection = flows[7] as SortDirection

        watchlist.map { item ->
            val itemCalendar = calendar.filter { it.simklId == item.simklId }
            val now = Instant.now()
            val releasedItems = itemCalendar.filter { it.date.isBefore(now) }

            // Check for unwatched episodes in calendar
            val hasUnwatched = itemCalendar.any { !it.isWatched }

            val nextEp = itemCalendar.filter { it.date.isAfter(now) }
                .minByOrNull { it.date }?.date

            val lastAired = releasedItems.maxByOrNull { it.date }?.date

            val watchedReleasedCount = releasedItems.count { it.isWatched }
            val totalReleasedCount = releasedItems.size

            val downloadedReleasedCount = releasedItems.count {
                it.mediaStatus == MediaStatus.DOWNLOADED || it.mediaStatus == MediaStatus.ARCHIVED
            }
            val totalDownloadableReleasedCount = releasedItems.count {
                it.mediaStatus == MediaStatus.WANTED ||
                it.mediaStatus == MediaStatus.DOWNLOADING ||
                it.mediaStatus == MediaStatus.DOWNLOADED ||
                it.mediaStatus == MediaStatus.ARCHIVED
            }

            WatchlistTableItem(
                watchlistItem = item,
                hasUnwatched = hasUnwatched,
                nextEpisodeDate = nextEp,
                lastAiredDate = lastAired,
                watchedReleasedCount = watchedReleasedCount,
                totalReleasedCount = totalReleasedCount,
                downloadedReleasedCount = downloadedReleasedCount,
                totalDownloadableReleasedCount = totalDownloadableReleasedCount
            )
        }.filter {
            // Category filter
            val matchesCategory = when (it.watchlistItem.type) {
                MediaType.TV -> tv
                MediaType.ANIME -> anime
                MediaType.MOVIE -> movies
            }

            // Search query filter
            val matchesQuery = if (query.isBlank()) true
            else {
                it.watchlistItem.title.contains(query, ignoreCase = true) ||
                it.watchlistItem.titleRomaji?.contains(query, ignoreCase = true) == true
            }

            matchesCategory && it.hasUnwatched && matchesQuery
        }.let { list ->
            val comparator = when (sortField) {
                TableSortField.NAME -> compareBy<WatchlistTableItem> { it.watchlistItem.title.lowercase() }
                TableSortField.LAST_EP -> compareBy<WatchlistTableItem> { it.lastAiredDate ?: Instant.MIN }
                TableSortField.NEXT_EP -> compareBy<WatchlistTableItem> { it.nextEpisodeDate ?: Instant.MAX }
                TableSortField.WATCHED -> compareBy<WatchlistTableItem> {
                    if (it.totalReleasedCount == 0) 1.0 else it.watchedReleasedCount.toDouble() / it.totalReleasedCount
                }
                TableSortField.DOWNLOADED -> compareBy<WatchlistTableItem> {
                    if (it.totalDownloadableReleasedCount == 0) 1.0 else it.downloadedReleasedCount.toDouble() / it.totalDownloadableReleasedCount
                }
            }
            if (sortDirection == SortDirection.ASCENDING) list.sortedWith(comparator) else list.sortedWith(comparator.reversed())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveItemDownloadSettings(settings: ItemDownloadSettings) {
        viewModelScope.launch {
            repository.saveItemDownloadSettings(settings)
        }
    }

    fun getItemDownloadSettingsFlow(simklId: Int): Flow<ItemDownloadSettings?> {
        return repository.getItemDownloadSettingsFlow(simklId)
    }

    private val _updatingWatchStatusKeys = MutableStateFlow<Set<String>>(emptySet())
    val updatingWatchStatusKeys: StateFlow<Set<String>> = _updatingWatchStatusKeys.asStateFlow()

    private fun addUpdatingKey(key: String) {
        _updatingWatchStatusKeys.update { it + key }
    }

    private fun removeUpdatingKey(key: String) {
        _updatingWatchStatusKeys.update { it - key }
    }

    private val _isMarkingWatched = MutableStateFlow(false)
    val isMarkingWatched: StateFlow<Boolean> = _isMarkingWatched.asStateFlow()

    private val _torrentResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val torrentResults: StateFlow<List<SearchResultItem>> = _torrentResults.asStateFlow()

    private val _isSearchingTorrents = MutableStateFlow(false)
    val isSearchingTorrents: StateFlow<Boolean> = _isSearchingTorrents.asStateFlow()

    private val _pendingDetailKey = MutableStateFlow<String?>(null)
    val pendingDetailKey: StateFlow<String?> = _pendingDetailKey.asStateFlow()

    fun setPendingDetailKey(key: String) {
        _pendingDetailKey.value = key
    }

    fun clearPendingDetailKey() {
        _pendingDetailKey.value = null
    }

    fun markEpisodeWatched(
        simklId: Int,
        season: Int?,
        episodeNumber: Int,
        mediaType: MediaType,
        primaryKey: String? = null,
        showTitle: String? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            primaryKey?.let { addUpdatingKey(it) }
            _isMarkingWatched.value = true
            val result = repository.markEpisodeWatched(
                simklId = simklId,
                season = season,
                episodeNumber = episodeNumber,
                mediaType = mediaType
            )
            _isMarkingWatched.value = false
            primaryKey?.let { removeUpdatingKey(it) }
            if (result.isSuccess) {
                val message = MediaFormatter.formatEpisodeWatchedToast(
                    showTitle = showTitle,
                    mediaType = mediaType,
                    season = season,
                    episodeNumber = episodeNumber
                )
                onResult(true, message)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to mark episode as watched"
                onResult(false, errorMsg)
            }
        }
    }

    fun markSeasonWatched(
        simklId: Int,
        season: Int,
        mediaType: MediaType,
        showTitle: String? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isMarkingWatched.value = true
            val result = repository.markSeasonWatched(
                simklId = simklId,
                season = season,
                mediaType = mediaType
            )
            _isMarkingWatched.value = false
            if (result.isSuccess) {
                val isCompleted = result.getOrDefault(false)
                val message = MediaFormatter.formatSeasonWatchedToast(
                    showTitle = showTitle,
                    mediaType = mediaType,
                    season = season,
                    isCompleted = isCompleted
                )
                onResult(true, message)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to mark season as watched"
                onResult(false, errorMsg)
            }
        }
    }

    fun markMovieWatched(
        simklId: Int,
        primaryKey: String? = null,
        showTitle: String? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            primaryKey?.let { addUpdatingKey(it) }
            _isMarkingWatched.value = true
            val result = repository.markMovieWatched(simklId = simklId)
            _isMarkingWatched.value = false
            primaryKey?.let { removeUpdatingKey(it) }
            if (result.isSuccess) {
                val message = MediaFormatter.formatMovieWatchedToast(showTitle)
                onResult(true, message)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to mark movie as watched"
                onResult(false, errorMsg)
            }
        }
    }

    fun markEpisodeUnwatched(
        simklId: Int,
        season: Int?,
        episodeNumber: Int,
        mediaType: MediaType,
        primaryKey: String? = null,
        showTitle: String? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            primaryKey?.let { addUpdatingKey(it) }
            _isMarkingWatched.value = true
            val result = repository.markEpisodeUnwatched(
                simklId = simklId,
                season = season,
                episodeNumber = episodeNumber,
                mediaType = mediaType
            )
            _isMarkingWatched.value = false
            primaryKey?.let { removeUpdatingKey(it) }
            if (result.isSuccess) {
                val message = MediaFormatter.formatEpisodeUnwatchedToast(
                    showTitle = showTitle,
                    mediaType = mediaType,
                    season = season,
                    episodeNumber = episodeNumber
                )
                onResult(true, message)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to mark episode as unwatched"
                onResult(false, errorMsg)
            }
        }
    }

    fun markSeasonUnwatched(
        simklId: Int,
        season: Int,
        mediaType: MediaType,
        showTitle: String? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isMarkingWatched.value = true
            val result = repository.markSeasonUnwatched(
                simklId = simklId,
                season = season,
                mediaType = mediaType
            )
            _isMarkingWatched.value = false
            if (result.isSuccess) {
                val message = MediaFormatter.formatSeasonUnwatchedToast(
                    showTitle = showTitle,
                    mediaType = mediaType,
                    season = season
                )
                onResult(true, message)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to mark season as unwatched"
                onResult(false, errorMsg)
            }
        }
    }

    fun markMovieUnwatched(
        simklId: Int,
        primaryKey: String? = null,
        showTitle: String? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            primaryKey?.let { addUpdatingKey(it) }
            _isMarkingWatched.value = true
            val result = repository.markMovieUnwatched(simklId = simklId)
            _isMarkingWatched.value = false
            primaryKey?.let { removeUpdatingKey(it) }
            if (result.isSuccess) {
                val message = MediaFormatter.formatMovieUnwatchedToast(showTitle)
                onResult(true, message)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to mark movie as unwatched"
                onResult(false, errorMsg)
            }
        }
    }

    // Combined filtered calendar list reactive flow
    @Suppress("UNCHECKED_CAST")
    val filteredCalendarItems: StateFlow<List<CalendarItemWithWatchlist>> = combine(
        repository.calendarItems,
        showTv,
        showAnime,
        showMovies,
        onlySeasonPremieres,
        onlySeasonFinales,
        onlyDigitalDvd,
        searchQuery
    ) { flows ->
        val items = flows[0] as List<CalendarItemWithWatchlist>
        val tv = flows[1] as Boolean
        val anime = flows[2] as Boolean
        val movies = flows[3] as Boolean
        val premieres = flows[4] as Boolean
        val finales = flows[5] as Boolean
        val digitalDvd = flows[6] as Boolean
        val query = (flows[7] as String).trim()

        items.filter { item ->
            // Category filter
            val matchesCategory = when (item.type) {
                MediaType.TV -> tv
                MediaType.ANIME -> anime
                MediaType.MOVIE -> movies
            }

            // Premiere / Finale / Digital-DVD Subtype filter
            val hasSubtypeFilter = premieres || finales || digitalDvd
            val matchesType = if (!hasSubtypeFilter) {
                true
            } else {
                (premieres && item.isSeasonPremiere) ||
                (finales && item.isSeasonFinale) ||
                (digitalDvd && item.type == MediaType.MOVIE && item.movieReleaseType == MovieReleaseType.DIGITAL)
            }

            // Always exclude watched episodes / releases
            val matchesWatched = !item.isWatched

            // Search query filter matching show/movie title, romaji title, or episode title
            val matchesQuery = if (query.isEmpty()) {
                true
            } else {
                item.title.contains(query, ignoreCase = true) ||
                (item.titleRomaji?.contains(query, ignoreCase = true) == true) ||
                (item.episodeTitle?.contains(query, ignoreCase = true) == true)
            }

            matchesCategory && matchesType && matchesWatched && matchesQuery
        }.sortedWith(compareBy<CalendarItemWithWatchlist> { it.date }.thenBy { it.title })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Calendar sync and status tracking
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    init {
        // Automatically sync calendar on launch only if user is logged in
        viewModelScope.launch {
            val token = repository.getActiveUserToken()
            if (token != null && !token.accessToken.isNullOrEmpty()) {
                syncLocalCalendar()
            }
        }
        // Refresh torrent service status
        repository.torrentServiceHelper.refreshServiceStatus()
    }

    fun syncLocalCalendar() {
        viewModelScope.launch {
            val token = repository.getActiveUserToken()
            if (token == null || token.accessToken.isNullOrEmpty()) {
                return@launch
            }
            _isSyncing.value = true
            _syncError.value = null
            try {
                repository.syncCalendar()
            } catch (e: Exception) {
                _syncError.value = e.message ?: "Failed to sync calendar"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun createAuthorizationUrl(redirectUri: String = "simklcalendar://auth"): String? {
        return repository.createAuthorizationUrl(redirectUri)
    }

    fun exchangeOAuthCode(
        code: String,
        state: String? = null,
        redirectUri: String? = null,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        viewModelScope.launch {
            _isSyncing.value = true
            val success = repository.exchangeOAuthCode(code = code, state = state, redirectUri = redirectUri)
            _isSyncing.value = false
            if (success) {
                onSuccess()
            } else {
                onFailure()
            }
        }
    }

    fun logoutUser() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    fun toggleNotification(
        simklId: Int,
        notifyEpisode: Boolean,
        notifySeasonFinished: Boolean
    ) {
        viewModelScope.launch {
            repository.toggleNotificationSetting(
                simklId = simklId,
                notifyEveryEpisode = notifyEpisode,
                notifyAiredLastEpisode = notifySeasonFinished
            )
        }
    }

    fun rescheduleAllNotifications() {
        viewModelScope.launch {
            NotificationScheduler.scheduleAllNotifications(getApplication())
        }
    }

    private val _isForceSyncing = MutableStateFlow(false)
    val isForceSyncing: StateFlow<Boolean> = _isForceSyncing.asStateFlow()

    fun forceWatchlistResync(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val token = repository.getActiveUserToken()
            if (token == null || token.accessToken.isEmpty()) {
                onComplete(false, "User is not logged in")
                return@launch
            }
            _isForceSyncing.value = true
            try {
                repository.forceWatchlistResync()
                _isForceSyncing.value = false
                onComplete(true, "Watchlist re-synced successfully")
            } catch (e: Exception) {
                _isForceSyncing.value = false
                onComplete(false, e.message ?: "Failed to re-sync watchlist")
            }
        }
    }

    fun saveCustomSearchLink(link: CustomSearchLink, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            if (link.id == 0L) {
                val currentLinks = customSearchLinks.value
                val nextPos = (currentLinks.maxOfOrNull { it.position } ?: -1) + 1
                repository.insertSearchLink(link.copy(position = nextPos))
            } else {
                repository.updateSearchLink(link)
            }
            onComplete()
        }
    }

    fun updateSearchLinksOrder(reorderedLinks: List<CustomSearchLink>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val updated = reorderedLinks.mapIndexed { index, link ->
                link.copy(position = index)
            }
            repository.updateSearchLinks(updated)
            onComplete()
        }
    }

    fun deleteCustomSearchLink(link: CustomSearchLink, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteSearchLink(link)
            onComplete()
        }
    }

    fun deleteCustomSearchLinkById(id: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteSearchLinkById(id)
            onComplete()
        }
    }

    fun searchTorrents(item: CalendarItemWithWatchlist) {
        viewModelScope.launch {
            _isSearchingTorrents.value = true
            _torrentResults.value = emptyList()
            try {
                _torrentResults.value = repository.searchTorrents(item)
            } catch (e: Exception) {
                // Error handling
            } finally {
                _isSearchingTorrents.value = false
            }
        }
    }

    fun clearTorrentResults() {
        _torrentResults.value = emptyList()
    }

    fun updateMediaStatus(primaryKey: String, status: MediaStatus) {
        viewModelScope.launch {
            repository.updateMediaStatus(primaryKey, status)
        }
    }

    fun searchAndDownloadEpisode(
        item: CalendarItemWithWatchlist,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            // 1. Set status to WANTED
            repository.updateMediaStatus(item.primaryKey, MediaStatus.WANTED)

            // 2. Search torrents
            _isSearchingTorrents.value = true
            try {
                val results = repository.searchTorrents(item)
                if (results.isEmpty()) {
                    onResult(false, "No torrent results found for this episode.")
                    return@launch
                }

                // 3. Select first result and start download
                val firstResult = results.first()
                repository.torrentServiceHelper.addTorrent(
                    uri = firstResult.uri.toString(),
                    name = firstResult.name,
                    destinationSubdirectory = item.destinationSubdirectory(),
                    createSubfolderByName = false,
                    fileSelectionMode = "BIGGEST",
                ) { success, taskId ->
                    if (success && taskId != null) {
                        // 4. Update status to DOWNLOADING with taskId
                        viewModelScope.launch {
                            repository.updateDownloadTaskId(item.primaryKey, taskId, MediaStatus.DOWNLOADING)
                        }
                        onResult(true, "Download started: ${firstResult.name}")
                    } else {
                        onResult(false, "Failed to start download.")
                    }
                }
            } catch (e: Exception) {
                onResult(false, "Error searching torrents: ${e.message}")
            } finally {
                _isSearchingTorrents.value = false
            }
        }
    }

    fun removeTorrent(taskId: String, deleteFiles: Boolean = false, deleteTorrentFile: Boolean = true, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        repository.torrentServiceHelper.removeTorrent(taskId, deleteFiles, deleteTorrentFile, onResult)
    }

    fun isTorrentServiceInstalled(): Boolean {
        return repository.torrentServiceHelper.isServiceInstalled()
    }

    override fun onCleared() {
        super.onCleared()
    }
}

fun MediaType.subdirectoryName(): String {
    return when(this) {
        MediaType.MOVIE -> "movies"
        MediaType.TV -> "series"
        MediaType.ANIME -> "anime"
    }
}

fun CalendarItemWithWatchlist.destinationSubdirectory(): String {
    val baseSubdirectory = type.subdirectoryName()
    if (type == MediaType.MOVIE) {
        return baseSubdirectory
    }
    val title = titleRomaji ?: title

    return "$baseSubdirectory/$title"
}
