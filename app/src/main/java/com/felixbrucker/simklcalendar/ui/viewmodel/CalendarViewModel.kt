package com.felixbrucker.simklcalendar.ui.viewmodel

import android.app.Application
import android.content.Context
import timber.log.Timber
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import com.felixbrucker.simklcalendar.data.util.DirectoryUtils
import com.felixbrucker.simklcalendar.data.util.getStringListWithMigration
import com.felixbrucker.simklcalendar.data.util.putStringList
import kotlin.time.Duration.Companion.milliseconds

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

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    val repository = SimklRepository(application)

    val notificationSettings: StateFlow<List<NotificationSetting>> = repository.notificationSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCalendarItems: StateFlow<List<CalendarItemWithWatchlist>> = repository.calendarItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchedEpisodes: StateFlow<List<WatchedEpisode>> = repository.watchedEpisodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customSearchLinks: StateFlow<List<CustomSearchLink>> = repository.customSearchLinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val torrentDownloads: StateFlow<Map<String, DownloadProgress>> = repository.torrentServiceHelper.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val isTorrentServiceBound: StateFlow<Boolean> = repository.torrentServiceHelper.isBound
    val isTorrentServiceInstalled: StateFlow<Boolean> = repository.torrentServiceHelper.isInstalled
    val hasWantedCalendarItems: StateFlow<Boolean> = allCalendarItems
        .map { it.any { item -> item.mediaStatus == MediaStatus.WANTED } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isAuthReady = MutableStateFlow(false)

    private val _userToken = MutableStateFlow<UserToken?>(null)
    val userToken: StateFlow<UserToken?> = _userToken.asStateFlow()

    private val _isAuthV2UpgradeHint = MutableStateFlow(false)
    val isAuthV2UpgradeHint: StateFlow<Boolean> = _isAuthV2UpgradeHint.asStateFlow()

    data class AuthState(val isReady: Boolean, val token: UserToken?)
    val authState: StateFlow<AuthState> = combine(_isAuthReady, _userToken) { ready, token ->
        AuthState(ready, token)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AuthState(false, null))

    private val uiPrefs = application.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    private val _viewMode = MutableStateFlow(MainViewMode.valueOf(uiPrefs.getString("view_mode", MainViewMode.CALENDAR.name) ?: MainViewMode.CALENDAR.name))
    val viewMode: StateFlow<MainViewMode> = _viewMode.asStateFlow()

    private var pollingJob: Job? = null
    private val downloadingItems = allCalendarItems
        .map { items -> items.filter { it.mediaStatus == MediaStatus.DOWNLOADING } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setViewMode(mode: MainViewMode) {
        _viewMode.value = mode
        uiPrefs.edit { putString("view_mode", mode.name) }
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
    val showTv = MutableStateFlow(uiPrefs.getBoolean("filter_show_tv", true))
    val showAnime = MutableStateFlow(uiPrefs.getBoolean("filter_show_anime", true))
    val showMovies = MutableStateFlow(uiPrefs.getBoolean("filter_show_movies", true))
    val showOnlyUnwatchedReleased = MutableStateFlow(uiPrefs.getBoolean("filter_only_unwatched", true))
    val onlySeasonPremieres = MutableStateFlow(uiPrefs.getBoolean("filter_only_premieres", false))
    val onlySeasonFinales = MutableStateFlow(uiPrefs.getBoolean("filter_only_finales", false))
    val onlyDigitalDvd = MutableStateFlow(uiPrefs.getBoolean("filter_only_digital_dvd", false))
    val showEarlierReleases = MutableStateFlow(uiPrefs.getBoolean("filter_show_earlier", false))
    val searchQuery = MutableStateFlow("")

    fun toggleShowTv() {
        showTv.value = !showTv.value
        uiPrefs.edit { putBoolean("filter_show_tv", showTv.value) }
    }

    fun toggleShowAnime() {
        showAnime.value = !showAnime.value
        uiPrefs.edit { putBoolean("filter_show_anime", showAnime.value) }
    }

    fun toggleShowMovies() {
        showMovies.value = !showMovies.value
        uiPrefs.edit { putBoolean("filter_show_movies", showMovies.value) }
    }

    fun toggleShowOnlyUnwatchedReleased() {
        showOnlyUnwatchedReleased.value = !showOnlyUnwatchedReleased.value
        uiPrefs.edit { putBoolean("filter_only_unwatched", showOnlyUnwatchedReleased.value) }
    }

    fun toggleOnlySeasonPremieres() {
        onlySeasonPremieres.value = !onlySeasonPremieres.value
        uiPrefs.edit { putBoolean("filter_only_premieres", onlySeasonPremieres.value) }
    }

    fun toggleOnlySeasonFinales() {
        onlySeasonFinales.value = !onlySeasonFinales.value
        uiPrefs.edit { putBoolean("filter_only_finales", onlySeasonFinales.value) }
    }

    fun toggleOnlyDigitalDvd() {
        onlyDigitalDvd.value = !onlyDigitalDvd.value
        uiPrefs.edit { putBoolean("filter_only_digital_dvd", onlyDigitalDvd.value) }
    }

    fun toggleShowEarlierReleases() {
        showEarlierReleases.value = !showEarlierReleases.value
        uiPrefs.edit { putBoolean("filter_show_earlier", showEarlierReleases.value) }
    }

    fun setShowEarlierReleases(show: Boolean) {
        showEarlierReleases.value = show
        uiPrefs.edit { putBoolean("filter_show_earlier", show) }
    }

    fun resetFilters() {
        showTv.value = true
        showAnime.value = true
        showMovies.value = true
        onlySeasonPremieres.value = false
        onlySeasonFinales.value = false
        onlyDigitalDvd.value = false
        uiPrefs.edit {
            putBoolean("filter_show_tv", true)
            putBoolean("filter_show_anime", true)
            putBoolean("filter_show_movies", true)
            putBoolean("filter_only_premieres", false)
            putBoolean("filter_only_finales", false)
            putBoolean("filter_only_digital_dvd", false)
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun clearSearchQuery() {
        searchQuery.value = ""
    }

    private val downloadPrefs = application.getSharedPreferences("auto_download_prefs", Context.MODE_PRIVATE)

    val autoDownloadQuality = MutableStateFlow(downloadPrefs.getString("quality", "1080p") ?: "1080p")
    val autoDownloadPreferHevc = MutableStateFlow(downloadPrefs.getBoolean("prefer_hevc", true))
    val autoDownloadUnwatchedTv = MutableStateFlow(downloadPrefs.getBoolean("auto_download_unwatched_tv", false))
    val autoDownloadUnwatchedAnime = MutableStateFlow(downloadPrefs.getBoolean("auto_download_unwatched_anime", false))
    val autoDownloadUnwatchedMovie = MutableStateFlow(downloadPrefs.getBoolean("auto_download_unwatched_movie", false))
    val autoDownloadSeasonUnwatchedTv = MutableStateFlow(downloadPrefs.getBoolean("auto_download_season_unwatched_tv", false))
    val autoDownloadSeasonUnwatchedAnime = MutableStateFlow(downloadPrefs.getBoolean("auto_download_season_unwatched_anime", false))
    val autoDownloadPreferredKeywords = MutableStateFlow(downloadPrefs.getStringListWithMigration("preferred_keywords"))
    val autoDownloadIgnoreKeywords = MutableStateFlow(downloadPrefs.getStringListWithMigration("ignore_keywords"))

    fun updateAutoDownloadQuality(quality: String) {
        autoDownloadQuality.value = quality
        downloadPrefs.edit {putString("quality", quality)}
    }

    fun updateAutoDownloadPreferHevc(prefer: Boolean) {
        autoDownloadPreferHevc.value = prefer
        downloadPrefs.edit {putBoolean("prefer_hevc", prefer)}
    }

    fun updateAutoDownloadUnwatchedTv(default: Boolean) {
        autoDownloadUnwatchedTv.value = default
        downloadPrefs.edit { putBoolean("auto_download_unwatched_tv", default)}
    }

    fun updateAutoDownloadUnwatchedAnime(default: Boolean) {
        autoDownloadUnwatchedAnime.value = default
        downloadPrefs.edit { putBoolean("auto_download_unwatched_anime", default)}
    }

    fun updateAutoDownloadUnwatchedMovie(default: Boolean) {
        autoDownloadUnwatchedMovie.value = default
        downloadPrefs.edit { putBoolean("auto_download_unwatched_movie", default)}
    }

    fun updateAutoDownloadSeasonUnwatchedTv(default: Boolean) {
        autoDownloadSeasonUnwatchedTv.value = default
        downloadPrefs.edit { putBoolean("auto_download_season_unwatched_tv", default)}
    }

    fun updateAutoDownloadSeasonUnwatchedAnime(default: Boolean) {
        autoDownloadSeasonUnwatchedAnime.value = default
        downloadPrefs.edit { putBoolean("auto_download_season_unwatched_anime", default)}
    }

    fun addPreferredKeyword(keyword: String) {
        val current = autoDownloadPreferredKeywords.value.toMutableList()
        if (!current.contains(keyword)) {
            current.add(keyword)
            autoDownloadPreferredKeywords.value = current
            downloadPrefs.edit { putStringList("preferred_keywords", current) }
        }
    }

    fun removePreferredKeyword(keyword: String) {
        val current = autoDownloadPreferredKeywords.value.toMutableList()
        if (current.remove(keyword)) {
            autoDownloadPreferredKeywords.value = current
            downloadPrefs.edit { putStringList("preferred_keywords", current) }
        }
    }

    fun updatePreferredKeywordsOrder(reordered: List<String>) {
        autoDownloadPreferredKeywords.value = reordered
        downloadPrefs.edit { putStringList("preferred_keywords", reordered) }
    }

    fun addIgnoreKeyword(keyword: String) {
        val current = autoDownloadIgnoreKeywords.value.toMutableList()
        if (!current.contains(keyword)) {
            current.add(keyword)
            autoDownloadIgnoreKeywords.value = current
            downloadPrefs.edit { putStringList("ignore_keywords", current) }
        }
    }

    fun removeIgnoreKeyword(keyword: String) {
        val current = autoDownloadIgnoreKeywords.value.toMutableList()
        if (current.remove(keyword)) {
            autoDownloadIgnoreKeywords.value = current
            downloadPrefs.edit { putStringList("ignore_keywords", current) }
        }
    }

    fun updateIgnoreKeywordsOrder(reordered: List<String>) {
        autoDownloadIgnoreKeywords.value = reordered
        downloadPrefs.edit { putStringList("ignore_keywords", reordered) }
    }

    @Suppress("UNCHECKED_CAST")
    val watchlistTableItems: StateFlow<List<WatchlistTableItem>> = combine(
        repository.watchlistItems,
        repository.calendarItems,
        searchQuery,
        showTv,
        showAnime,
        showMovies,
        showOnlyUnwatchedReleased,
        tableSortField,
        tableSortDirection
    ) { flows ->
        val watchlist = flows[0] as List<TrackedWatchlistItem>
        val calendar = flows[1] as List<CalendarItemWithWatchlist>
        val query = flows[2] as String
        val tv = flows[3] as Boolean
        val anime = flows[4] as Boolean
        val movies = flows[5] as Boolean
        val onlyUnwatchedReleased = flows[6] as Boolean
        val sortField = flows[7] as TableSortField
        val sortDirection = flows[8] as SortDirection

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

            for (i in 0 until itemCalendar.size) {
                val calItem = itemCalendar[i]
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
            repository.saveItemDownloadSettings(settings)
        }
    }

    fun getItemDownloadSettingsFlow(simklId: Int): Flow<ItemDownloadSettings?> {
        return repository.getItemDownloadSettingsFlow(simklId)
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
        // Automatically sync calendar on launch only if user is logged in
        viewModelScope.launch {
            val migrated = repository.checkAndMigrateAuthV2()
            if (migrated) {
                _isAuthV2UpgradeHint.value = true
            } else {
                _isAuthV2UpgradeHint.value = repository.isAuthV2UpgradeHint()
            }
            repository.activeUserToken.collect { token ->
                _userToken.value = token
                _isAuthReady.value = true
                if (token != null && token.accessToken.isNotEmpty()) {
                    _isAuthV2UpgradeHint.value = false
                    repository.clearAuthV2UpgradeHint()
                    syncLocalCalendar()
                } else if (repository.isAuthV2UpgradeHint()) {
                    _isAuthV2UpgradeHint.value = true
                }
            }
        }
        // Refresh torrent service status
        repository.torrentServiceHelper.refreshServiceStatus()

        // Start polling for downloading items
        viewModelScope.launch {
            downloadingItems.collect { items ->
                updatePolling(items)
            }
        }
    }

    fun syncLocalCalendar() {
        viewModelScope.launch {
            val token = repository.getActiveUserToken()
            if (token == null || token.accessToken.isEmpty()) {
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
            val items = repository.calendarItems.first()
            val wantedItems = items.filter { it.mediaStatus == MediaStatus.WANTED }

            if (wantedItems.isEmpty()) {
                _autoDownloadStatus.value = "No wanted episodes found"
                delay(2.seconds)
                _autoDownloadStatus.value = ""
                return@launch
            }

            _isSearchingWantedTorrents.value = true
            _autoDownloadStatus.value = "Starting search..."
            delay(500.milliseconds)

            repository.searchAndDownloadWantedItems(withDelay = 800.milliseconds) { current, total, title, _ ->
                _autoDownloadStatus.value = "Searching ($current/$total): $title"
            }

            _isSearchingWantedTorrents.value = false
            _autoDownloadStatus.value = "Search completed"
            delay(2.seconds)
            _autoDownloadStatus.value = ""
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
                repository.syncCalendar(force = true)
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

    fun updateMediaStatus(primaryKey: String, status: MediaStatus) {
        viewModelScope.launch {
            repository.updateMediaStatus(primaryKey, status)
            if (status == MediaStatus.WANTED) {
                val item = repository.calendarItems.first().find { it.primaryKey == primaryKey }
                if (item != null && item.date.isBefore(Instant.now())) {
                    searchAndDownloadEpisode(item) { _, _ -> }
                }
            }
        }
    }

    fun updateSeasonMediaStatus(simklId: Int, season: Int, status: MediaStatus) {
        viewModelScope.launch {
            repository.updateSeasonMediaStatus(simklId, season, status)
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
            val result = repository.searchAndDownloadEpisode(item)
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
            val items = repository.calendarItems.first()
            val seasonEpisodes = items.filter {
                it.simklId == simklId && (it.season == season || (season == 1 && it.season == null))
            }

            val now = Instant.now()
            val airedEpisodes = seasonEpisodes.filter { it.date.isBefore(now) }

            airedEpisodes.forEach { item ->
                repository.searchAndDownloadEpisode(item)
            }
            _isSearchingTorrents.value = false
        }
    }

    fun isTorrentServiceInstalled(): Boolean {
        return repository.torrentServiceHelper.isServiceInstalled()
    }

    private fun updatePolling(items: List<CalendarItemWithWatchlist>) {
        if (items.isEmpty()) {
            pollingJob?.cancel()
            pollingJob = null
            repository.torrentServiceHelper.unbind()
            return
        }

        if (pollingJob == null || pollingJob?.isActive == false) {
            repository.torrentServiceHelper.bind()
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
                                    val stats = repository.torrentServiceHelper.getProgress(taskId)
                                    if (stats != null) {
                                        repository.torrentServiceHelper.updateDownloadProgress(taskId, stats)
                                    } else {
                                        // Task was removed from downloader
                                        // Wait a few seconds to allow completion intent to be processed
                                        delay(3.seconds)
                                        // Fetch current items from repository flow
                                        val currentEntity = repository.calendarItems.first().find { it.primaryKey == item.primaryKey }
                                        if (currentEntity?.mediaStatus == MediaStatus.DOWNLOADING && currentEntity.downloadTaskId == taskId) {
                                            Timber.tag("CalendarViewModel").d("Task $taskId still not found after 3s and status is still DOWNLOADING with same taskId, reverting for ${item.primaryKey}")
                                            repository.updateDownloadTaskId(item.primaryKey, null, MediaStatus.WANTED)
                                            repository.torrentServiceHelper.clearDownload(taskId)
                                        } else {
                                            Timber.tag("CalendarViewModel").d("Task $taskId not found, but status is now ${currentEntity?.mediaStatus} or taskId changed, skipping revert")
                                            repository.torrentServiceHelper.clearDownload(taskId)
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
