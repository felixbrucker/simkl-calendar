package com.felixbrucker.simklcalendar.ui.viewmodel

import timber.log.Timber
import androidx.lifecycle.ViewModel
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
import com.felixbrucker.simklcalendar.data.repository.CalendarRepository
import com.felixbrucker.simklcalendar.data.repository.CustomSearchLinkRepository
import com.felixbrucker.simklcalendar.data.repository.DownloadRepository
import com.felixbrucker.simklcalendar.data.repository.NotificationSettingRepository
import com.felixbrucker.simklcalendar.data.repository.SyncRepository
import com.felixbrucker.simklcalendar.data.repository.UserRepository
import com.felixbrucker.simklcalendar.data.repository.WatchHistoryRepository
import com.felixbrucker.simklcalendar.data.util.DownloadProgress
import com.felixbrucker.simklcalendar.data.util.MediaFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import androidx.compose.runtime.Immutable
import com.felixbrucker.simklcalendar.data.util.DirectoryUtils
import com.felixbrucker.simklcalendar.data.preferences.*
import kotlin.time.Duration.Companion.milliseconds

import com.felixbrucker.simklcalendar.data.preferences.ViewMode
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

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

@Immutable
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
    val status: String? = null // e.g. "Watching", "Plan to watch"
) {
    val watchedProgress: Double = if (totalReleasedCount > 0) watchedReleasedCount.toDouble() / totalReleasedCount else 0.0
    val downloadedProgress: Double = if (totalDownloadableReleasedCount > 0) downloadedReleasedCount.toDouble() / totalDownloadableReleasedCount else 0.0
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val calendarRepository: CalendarRepository,
    private val syncRepository: SyncRepository,
    private val watchHistoryRepository: WatchHistoryRepository,
    private val downloadRepository: DownloadRepository,
    customSearchLinkRepository: CustomSearchLinkRepository,
    private val notificationSettingRepository: NotificationSettingRepository,
    private val autoDownloadRepo: AutoDownloadRepository,
    notificationRepo: NotificationRepository,
    private val uiRepo: UiRepository,
    private val torrentServiceHelper: TorrentServiceHelper
) : ViewModel() {

    val watchlistItems: Flow<List<TrackedWatchlistItem>> = calendarRepository.watchlistItems

    val notificationSettings: StateFlow<List<NotificationSetting>> = notificationSettingRepository.notificationSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCalendarItems: StateFlow<List<CalendarItemWithWatchlist>> = calendarRepository.calendarItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchedEpisodes: StateFlow<List<WatchedEpisode>> = watchHistoryRepository.watchedEpisodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customSearchLinks: StateFlow<List<CustomSearchLink>> = customSearchLinkRepository.customSearchLinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val torrentDownloads: StateFlow<Map<String, DownloadProgress>> = torrentServiceHelper.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val isTorrentServiceBound: StateFlow<Boolean> = torrentServiceHelper.isBound
    val isTorrentServiceInstalled: StateFlow<Boolean> = torrentServiceHelper.isInstalled
    val hasWantedCalendarItems: StateFlow<Boolean> = allCalendarItems
        .map { it.any { item -> item.mediaStatus == MediaStatus.WANTED } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val userToken: StateFlow<UserToken?> = userRepository.activeUserToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    data class AuthState(val isReady: Boolean, val token: UserToken?)
    val authState: StateFlow<AuthState> = userRepository.activeUserToken
        .distinctUntilChanged()
        .map { token -> AuthState(true, token) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState(false, null))

    val uiPreferences: StateFlow<UiPreferences> = uiRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiPreferences())

    val notificationPreferences: StateFlow<NotificationPreferences> = notificationRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationPreferences())

    val viewMode: StateFlow<ViewMode> = uiPreferences
        .map { it.viewMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ViewMode.CALENDAR)

    private var pollingJob: Job? = null
    private val downloadingItems = allCalendarItems
        .map { items -> items.filter { it.mediaStatus == MediaStatus.DOWNLOADING } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setViewMode(mode: ViewMode) {
        viewModelScope.launch {
            uiRepo.setViewMode(mode)
        }
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
    val searchQuery = MutableStateFlow("")

    fun toggleShowTv() {
        viewModelScope.launch {
            uiRepo.updateFilters { it.copy(filterShowTv = !it.filterShowTv) }
        }
    }

    fun toggleShowAnime() {
        viewModelScope.launch {
            uiRepo.updateFilters { it.copy(filterShowAnime = !it.filterShowAnime) }
        }
    }

    fun toggleShowMovies() {
        viewModelScope.launch {
            uiRepo.updateFilters { it.copy(filterShowMovies = !it.filterShowMovies) }
        }
    }

    fun toggleShowOnlyUnwatchedReleased() {
        viewModelScope.launch {
            uiRepo.updateFilters { it.copy(filterOnlyUnwatched = !it.filterOnlyUnwatched) }
        }
    }

    fun toggleOnlySeasonPremieres() {
        viewModelScope.launch {
            uiRepo.updateFilters { it.copy(filterOnlyPremieres = !it.filterOnlyPremieres) }
        }
    }

    fun toggleOnlySeasonFinales() {
        viewModelScope.launch {
            uiRepo.updateFilters { it.copy(filterOnlyFinales = !it.filterOnlyFinales) }
        }
    }

    fun toggleOnlyDigitalDvd() {
        viewModelScope.launch {
            uiRepo.updateFilters { it.copy(filterOnlyDigitalDvd = !it.filterOnlyDigitalDvd) }
        }
    }

    fun toggleShowEarlierReleases() {
        viewModelScope.launch {
            uiRepo.updateFilters { it.copy(filterShowEarlier = !it.filterShowEarlier) }
        }
    }

    fun setShowEarlierReleases(show: Boolean) {
        viewModelScope.launch {
            uiRepo.updateFilters { it.copy(filterShowEarlier = show) }
        }
    }

    fun resetFilters() {
        viewModelScope.launch {
            uiRepo.updateFilters {
                it.copy(
                    filterShowTv = true,
                    filterShowAnime = true,
                    filterShowMovies = true,
                    filterOnlyUnwatched = true,
                    filterOnlyPremieres = false,
                    filterOnlyFinales = false,
                    filterOnlyDigitalDvd = false,
                    filterShowEarlier = false
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun clearSearchQuery() {
        searchQuery.value = ""
    }

    val autoDownloadPreferences: StateFlow<AutoDownloadPreferences> = autoDownloadRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutoDownloadPreferences())

    fun updateAutoDownloadQuality(quality: String) {
        viewModelScope.launch {
            autoDownloadRepo.setQuality(quality)
        }
    }

    fun updateAutoDownloadPreferHevc(prefer: Boolean) {
        viewModelScope.launch {
            autoDownloadRepo.setPreferHevc(prefer)
        }
    }

    fun updateAutoDownloadUnwatchedTv(default: Boolean) {
        viewModelScope.launch {
            autoDownloadRepo.setAutoDownloadUnwatchedTv(default)
        }
    }

    fun updateAutoDownloadUnwatchedAnime(default: Boolean) {
        viewModelScope.launch {
            autoDownloadRepo.setAutoDownloadUnwatchedAnime(default)
        }
    }

    fun updateAutoDownloadUnwatchedMovie(default: Boolean) {
        viewModelScope.launch {
            autoDownloadRepo.setAutoDownloadUnwatchedMovie(default)
        }
    }

    fun updateAutoDownloadSeasonUnwatchedTv(default: Boolean) {
        viewModelScope.launch {
            autoDownloadRepo.setAutoDownloadSeasonUnwatchedTv(default)
        }
    }

    fun updateAutoDownloadSeasonUnwatchedAnime(default: Boolean) {
        viewModelScope.launch {
            autoDownloadRepo.setAutoDownloadSeasonUnwatchedAnime(default)
        }
    }

    fun addPreferredKeyword(keyword: String) {
        viewModelScope.launch {
            val current = autoDownloadPreferences.value.preferredKeywords.toMutableList()
            if (!current.contains(keyword)) {
                current.add(keyword)
                autoDownloadRepo.setPreferredKeywords(current)
            }
        }
    }

    fun removePreferredKeyword(keyword: String) {
        viewModelScope.launch {
            val current = autoDownloadPreferences.value.preferredKeywords.toMutableList()
            if (current.remove(keyword)) {
                autoDownloadRepo.setPreferredKeywords(current)
            }
        }
    }

    fun updatePreferredKeywordsOrder(reordered: List<String>) {
        viewModelScope.launch {
            autoDownloadRepo.setPreferredKeywords(reordered)
        }
    }

    fun addIgnoreKeyword(keyword: String) {
        viewModelScope.launch {
            val current = autoDownloadPreferences.value.ignoreKeywords.toMutableList()
            if (!current.contains(keyword)) {
                current.add(keyword)
                autoDownloadRepo.setIgnoreKeywords(current)
            }
        }
    }

    fun removeIgnoreKeyword(keyword: String) {
        viewModelScope.launch {
            val current = autoDownloadPreferences.value.ignoreKeywords.toMutableList()
            if (current.remove(keyword)) {
                autoDownloadRepo.setIgnoreKeywords(current)
            }
        }
    }

    fun updateIgnoreKeywordsOrder(reordered: List<String>) {
        viewModelScope.launch {
            autoDownloadRepo.setIgnoreKeywords(reordered)
        }
    }

    @Suppress("UNCHECKED_CAST")
    val watchlistTableItems: StateFlow<List<WatchlistTableItem>> = combine(
        calendarRepository.watchlistItems,
        calendarRepository.calendarItems,
        searchQuery,
        uiPreferences,
        tableSortField,
        tableSortDirection
    ) { flows ->
        val watchlist = flows[0] as List<TrackedWatchlistItem>
        val calendar = flows[1] as List<CalendarItemWithWatchlist>
        val query = flows[2] as String
        val ui = flows[3] as UiPreferences
        val sortField = flows[4] as TableSortField
        val sortDirection = flows[5] as SortDirection

        val tv = ui.filterShowTv
        val anime = ui.filterShowAnime
        val movies = ui.filterShowMovies
        val onlyUnwatchedReleased = ui.filterOnlyUnwatched

        // Pre-group calendar items by simklId upfront to convert lookup complexity from O(N*M) to O(N+M).
        val calendarBySimklId = calendar.groupBy { it.simklId }
        val now = Instant.now()

        // Filter watchlist items by category & search query BEFORE running calendar episode loops and object allocations.
        watchlist.filter { item ->
            val matchesCategory = when (item.type) {
                MediaType.TV -> tv
                MediaType.ANIME -> anime
                MediaType.MOVIE -> movies
            }
            if (!matchesCategory) return@filter false

            if (query.isBlank()) true
            else {
                item.title.contains(query, ignoreCase = true) ||
                item.titleRomaji?.contains(query, ignoreCase = true) == true
            }
        }.mapNotNull { item ->
            val itemCalendar = calendarBySimklId[item.simklId] ?: emptyList()

            // Single pass over itemCalendar without intermediate list allocations
            var hasUnwatched = false
            var hasUnwatchedReleased = false
            var nextEp: Instant? = null
            var lastAired: Instant? = null
            var watchedReleasedCount = 0
            var totalReleasedCount = 0
            var downloadedReleasedCount = 0
            var totalDownloadableReleasedCount = 0

            for (calItem in itemCalendar) {
                val calDate = calItem.date
                val isReleased = calDate.isBefore(now)

                if (!calItem.isWatched) {
                    hasUnwatched = true
                    if (isReleased) {
                        hasUnwatchedReleased = true
                    }
                }

                if (isReleased) {
                    totalReleasedCount++
                    if (calItem.isWatched) {
                        watchedReleasedCount++
                    }
                    if (calItem.mediaStatus == MediaStatus.DOWNLOADED) {
                        downloadedReleasedCount++
                    }
                    if (calItem.mediaStatus == MediaStatus.WANTED ||
                        calItem.mediaStatus == MediaStatus.DOWNLOADING ||
                        calItem.mediaStatus == MediaStatus.DOWNLOADED
                    ) {
                        totalDownloadableReleasedCount++
                    }
                    if (lastAired == null || calDate.isAfter(lastAired)) {
                        lastAired = calDate
                    }
                } else if (calDate.isAfter(now)) {
                    if (nextEp == null || calDate.isBefore(nextEp)) {
                        nextEp = calDate
                    }
                }
            }

            if (onlyUnwatchedReleased && !hasUnwatchedReleased) {
                return@mapNotNull null
            }

            WatchlistTableItem(
                watchlistItem = item,
                hasUnwatched = hasUnwatched,
                hasUnwatchedReleased = hasUnwatchedReleased,
                nextEpisodeDate = nextEp,
                lastAiredDate = lastAired,
                watchedReleasedCount = watchedReleasedCount,
                totalReleasedCount = totalReleasedCount,
                downloadedReleasedCount = downloadedReleasedCount,
                totalDownloadableReleasedCount = totalDownloadableReleasedCount
            )
        }.let { list ->
            val comparator = when (sortField) {
                TableSortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.watchlistItem.title }
                TableSortField.LAST_EP -> compareBy { it.lastAiredDate ?: Instant.MIN }
                TableSortField.NEXT_EP -> compareBy { it.nextEpisodeDate ?: Instant.MAX }
                TableSortField.WATCHED -> compareBy<WatchlistTableItem> { it.watchedProgress }
                    .thenBy { it.totalReleasedCount }
                TableSortField.DOWNLOADED -> compareBy<WatchlistTableItem> { it.downloadedProgress }
                    .thenBy { it.totalDownloadableReleasedCount }
            }
            if (sortDirection == SortDirection.ASCENDING) list.sortedWith(comparator) else list.sortedWith(comparator.reversed())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveItemDownloadSettings(settings: ItemDownloadSettings) {
        viewModelScope.launch {
            downloadRepository.saveItemDownloadSettings(settings)
        }
    }

    fun getItemDownloadSettingsFlow(simklId: Int): Flow<ItemDownloadSettings?> {
        return downloadRepository.getItemDownloadSettingsFlow(simklId)
    }

    private val _downloadSubdirectories = MutableStateFlow<List<String>>(emptyList())
    val downloadSubdirectories: StateFlow<List<String>> = _downloadSubdirectories.asStateFlow()

    fun refreshDownloadSubdirectories() {
        viewModelScope.launch(Dispatchers.IO) {
            _downloadSubdirectories.value = DirectoryUtils.getDownloadSubdirectories()
        }
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

    private val _isSearchingTorrents = MutableStateFlow(false)

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
            val result = watchHistoryRepository.markEpisodeWatched(
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
            val result = watchHistoryRepository.markSeasonWatched(
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
            val result = watchHistoryRepository.markMovieWatched(simklId = simklId)
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
            val result = watchHistoryRepository.markEpisodeUnwatched(
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
            val result = watchHistoryRepository.markSeasonUnwatched(
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
            val result = watchHistoryRepository.markMovieUnwatched(simklId = simklId)
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
        calendarRepository.calendarItems,
        uiPreferences,
        searchQuery
    ) { flows ->
        val items = flows[0] as List<CalendarItemWithWatchlist>
        val ui = flows[1] as UiPreferences
        val query = (flows[2] as String).trim()

        val tv = ui.filterShowTv
        val anime = ui.filterShowAnime
        val movies = ui.filterShowMovies
        val premieres = ui.filterOnlyPremieres
        val finales = ui.filterOnlyFinales
        val digitalDvd = ui.filterOnlyDigitalDvd

        items.filter { item ->
            // Always exclude watched episodes / releases first (fastest short-circuit)
            if (item.isWatched) return@filter false

            // Category filter
            val matchesCategory = when (item.type) {
                MediaType.TV -> tv
                MediaType.ANIME -> anime
                MediaType.MOVIE -> movies
            }
            if (!matchesCategory) return@filter false

            // Premiere / Finale / Digital-DVD Subtype filter
            val hasSubtypeFilter = premieres || finales || digitalDvd
            if (hasSubtypeFilter) {
                val matchesType = (premieres && item.isSeasonPremiere) ||
                        (finales && item.isSeasonFinale) ||
                        (digitalDvd && item.type == MediaType.MOVIE && item.movieReleaseType == MovieReleaseType.DIGITAL)
                if (!matchesType) return@filter false
            }

            // Search query filter matching show/movie title, romaji title, or episode title
            if (query.isNotEmpty()) {
                val matchesQuery = item.title.contains(query, ignoreCase = true) ||
                        (item.titleRomaji?.contains(query, ignoreCase = true) == true) ||
                        (item.episodeTitle?.contains(query, ignoreCase = true) == true)
                if (!matchesQuery) return@filter false
            }

            true
        }.sortedWith(compareBy<CalendarItemWithWatchlist> { it.date }.thenBy { it.title })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Calendar sync and status tracking
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)

    init {
        refreshDownloadSubdirectories()
        viewModelScope.launch {
            userRepository.resetAuthIfNeeded()
            // Automatically sync calendar on startup and after logging
            userToken
                .map { it != null }
                .distinctUntilChanged()
                .filter { it }
                .collect { syncLocalCalendar() }
        }
        // Refresh torrent service status
        torrentServiceHelper.refreshServiceStatus()

        // Start polling for downloading items
        viewModelScope.launch {
            downloadingItems.collect { items ->
                updatePolling(items)
            }
        }
    }

    fun syncLocalCalendar() {
        viewModelScope.launch {
            val token = userRepository.getActiveUserToken()
            if (token == null || token.accessToken.isEmpty()) {
                return@launch
            }
            _isSyncing.value = true
            _syncError.value = null
            try {
                syncRepository.syncCalendar()
            } catch (e: Exception) {
                _syncError.value = e.message ?: "Failed to sync calendar"
            } finally {
                _isSyncing.value = false
            }
        }
    }
    private val _isSearchingWantedTorrents = MutableStateFlow(false)
    val isSearchingWantedTorrents: StateFlow<Boolean> = _isSearchingWantedTorrents.asStateFlow()
    private val _autoDownloadStatus = MutableStateFlow("")
    val autoDownloadStatus: StateFlow<String> = _autoDownloadStatus.asStateFlow()
    val shouldShowAutoDownloadStatus: StateFlow<Boolean> = autoDownloadStatus
        .map { it.isNotBlank() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun runAutoDownloadManual() {
        viewModelScope.launch {
            val items = calendarRepository.calendarItems.first()
            val wantedItems = items.filter { it.mediaStatus == MediaStatus.WANTED }

            if (wantedItems.isEmpty()) {
                _autoDownloadStatus.value = "No wanted episodes found"
                delay(2.seconds)
                _autoDownloadStatus.value = ""
                return@launch
            }

            _isSearchingWantedTorrents.value = true
            _autoDownloadStatus.value = "Starting search..."
            delay(800.milliseconds)

            downloadRepository.searchAndDownloadWantedItems(withDelay = 1500.milliseconds) { current, total, title, _ ->
                _autoDownloadStatus.value = "Searching ($current/$total): $title"
            }

            _isSearchingWantedTorrents.value = false
            _autoDownloadStatus.value = "Search completed"
            delay(2.seconds)
            _autoDownloadStatus.value = ""
        }
    }

    fun toggleNotification(
        simklId: Int,
        notifyEpisode: Boolean,
        notifySeasonFinished: Boolean
    ) {
        viewModelScope.launch {
            notificationSettingRepository.toggleNotificationSetting(
                simklId = simklId,
                notifyEveryEpisode = notifyEpisode,
                notifyAiredLastEpisode = notifySeasonFinished
            )
        }
    }

    fun updateMediaStatus(primaryKey: String, status: MediaStatus) {
        viewModelScope.launch {
            calendarRepository.updateMediaStatus(primaryKey, status)
            if (status == MediaStatus.WANTED) {
                val item = calendarRepository.calendarItems.first().find { it.primaryKey == primaryKey }
                if (item != null && item.date.isBefore(Instant.now())) {
                    searchAndDownloadEpisode(item) { _, _ -> }
                }
            }
        }
    }

    fun updateSeasonMediaStatus(simklId: Int, season: Int, status: MediaStatus) {
        viewModelScope.launch {
            calendarRepository.updateSeasonMediaStatus(simklId, season, status)
            if (status == MediaStatus.WANTED) {
                searchAndDownloadSeason(simklId, season)
            }
        }
    }

    fun searchAndDownloadEpisode(
        item: CalendarItemWithWatchlist,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isSearchingTorrents.value = true
            val result = downloadRepository.searchAndDownloadEpisode(item)
            result.onSuccess {
                onResult(true, "Download started")
            }.onFailure {
                onResult(false, it.message ?: "Error searching torrents")
            }
            _isSearchingTorrents.value = false
        }
    }

    fun searchAndDownloadSeason(simklId: Int, season: Int) {
        viewModelScope.launch {
            _isSearchingTorrents.value = true
            val items = calendarRepository.calendarItems.first()
            val seasonEpisodes = items.filter {
                it.simklId == simklId && (it.season == season || (season == 1 && it.season == null))
            }

            val now = Instant.now()
            val airedEpisodes = seasonEpisodes.filter { it.date.isBefore(now) }

            airedEpisodes.forEach { item ->
                downloadRepository.searchAndDownloadEpisode(item)
            }
            _isSearchingTorrents.value = false
        }
    }

    fun isTorrentServiceInstalled(): Boolean {
        return torrentServiceHelper.isServiceInstalled()
    }

    private fun updatePolling(items: List<CalendarItemWithWatchlist>) {
        if (items.isEmpty()) {
            pollingJob?.cancel()
            pollingJob = null
            torrentServiceHelper.unbind()
            return
        }

        if (pollingJob == null || pollingJob?.isActive == false) {
            torrentServiceHelper.bind()
            pollingJob = viewModelScope.launch {
                while (true) {
                    if (!isTorrentServiceBound.value) {
                        delay(1.seconds)
                        continue
                    }

                    val currentDownloading = downloadingItems.value
                    if (currentDownloading.isEmpty()) break

                    coroutineScope {
                        currentDownloading.map { item ->
                            launch(Dispatchers.IO) {
                                val taskId = item.downloadTaskId ?: return@launch
                                try {
                                    val stats = torrentServiceHelper.getProgress(taskId)
                                    if (stats != null) {
                                        torrentServiceHelper.updateDownloadProgress(taskId, stats)
                                    } else {
                                        // Task was removed from downloader
                                        // Wait a few seconds to allow completion intent to be processed
                                        delay(3.seconds)
                                        // Fetch current items from repository flow
                                        val currentEntity = calendarRepository.calendarItems.first().find { it.primaryKey == item.primaryKey }
                                        if (currentEntity?.mediaStatus == MediaStatus.DOWNLOADING && currentEntity.downloadTaskId == taskId) {
                                            Timber.tag("CalendarViewModel").d("Task $taskId still not found after 3s and status is still DOWNLOADING with same taskId, reverting for ${item.primaryKey}")
                                            calendarRepository.updateDownloadTaskId(item.primaryKey, null, MediaStatus.WANTED)
                                            torrentServiceHelper.clearDownload(taskId)
                                        } else {
                                            Timber.tag("CalendarViewModel").d("Task $taskId not found, but status is now ${currentEntity?.mediaStatus} or taskId changed, skipping revert")
                                            torrentServiceHelper.clearDownload(taskId)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Timber.tag("CalendarViewModel").e(e, "Error polling progress for $taskId")
                                }
                            }
                        }
                    }
                    delay(1.seconds)
                }
            }
        }
    }
}
