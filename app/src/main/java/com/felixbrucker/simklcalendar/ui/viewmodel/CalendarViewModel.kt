package com.felixbrucker.simklcalendar.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import com.felixbrucker.simklcalendar.data.util.DateUtil
import com.felixbrucker.simklcalendar.data.util.DownloadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    val repository = SimklRepository(application)

    val torrentDownloads: StateFlow<Map<String, DownloadProgress>> = repository.torrentServiceHelper.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val isTorrentServiceInstalled: StateFlow<Boolean> = repository.torrentServiceHelper.isInstalled

    private val _pendingDetailKey = MutableStateFlow<String?>(null)
    val pendingDetailKey: StateFlow<String?> = _pendingDetailKey.asStateFlow()

    fun setPendingDetailKey(key: String) {
        _pendingDetailKey.value = key
    }

    fun clearPendingDetailKey() {
        _pendingDetailKey.value = null
    }

    private val _userToken = MutableStateFlow<UserToken?>(null)
    val userToken: StateFlow<UserToken?> = _userToken.asStateFlow()

    private val uiPrefs = application.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    private val _viewMode = MutableStateFlow(MainViewMode.valueOf(uiPrefs.getString("view_mode", MainViewMode.CALENDAR.name) ?: MainViewMode.CALENDAR.name))
    val viewMode: StateFlow<MainViewMode> = _viewMode.asStateFlow()

    fun setViewMode(mode: MainViewMode) {
        _viewMode.value = mode
        uiPrefs.edit { putString("view_mode", mode.name) }
    }

    // Filtering State Flows
    val showTv = MutableStateFlow(uiPrefs.getBoolean("filter_show_tv", true))
    val showAnime = MutableStateFlow(uiPrefs.getBoolean("filter_show_anime", true))
    val showMovies = MutableStateFlow(uiPrefs.getBoolean("filter_show_movies", true))
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

    val hasEarlierReleases: StateFlow<Boolean> = repository.hasEarlierReleases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val calendarItems: Flow<PagingData<CalendarListItem>> = combine(
        showEarlierReleases,
        combine(showTv, showAnime, showMovies) { tv, anime, movies -> Triple(tv, anime, movies) },
        combine(onlySeasonPremieres, onlySeasonFinales, onlyDigitalDvd) { prem, fin, dig -> Triple(prem, fin, dig) },
        searchQuery
    ) { earlier, categoryTriple, filterTriple, query ->
        val hasSubtypeFilter = filterTriple.first || filterTriple.second || filterTriple.third
        repository.getCalendarPaged(
            showEarlier = earlier,
            showTv = categoryTriple.first,
            showAnime = categoryTriple.second,
            showMovies = categoryTriple.third,
            hasSubtypeFilter = hasSubtypeFilter,
            premieres = filterTriple.first,
            finales = filterTriple.second,
            digitalDvd = filterTriple.third,
            query = query
        )
    }.flattenConcat()

        .map { pagingData ->
            val mapped = pagingData.map { CalendarListItem.Item(it) as CalendarListItem }
            mapped.insertSeparators { before, after ->
                if (after is CalendarListItem.Item) {
                    val afterHeader = DateUtil.formatAiringDateHeader(after.item.date)
                    if (before is CalendarListItem.Item) {
                        val beforeHeader = DateUtil.formatAiringDateHeader(before.item.date)
                        if (beforeHeader != afterHeader) {
                            CalendarListItem.DateHeader(afterHeader)
                        } else {
                            null
                        }
                    } else if (before == null) {
                        CalendarListItem.DateHeader(afterHeader)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        }
        .cachedIn(viewModelScope)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isSearchingWantedTorrents = MutableStateFlow(false)
    val isSearchingWantedTorrents: StateFlow<Boolean> = _isSearchingWantedTorrents.asStateFlow()
    private val _autoDownloadStatus = MutableStateFlow("")
    val autoDownloadStatus: StateFlow<String> = _autoDownloadStatus.asStateFlow()
    val shouldShowAutoDownloadStatus: StateFlow<Boolean> = autoDownloadStatus
        .map { it.isNotBlank() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasWantedCalendarItems: StateFlow<Boolean> = repository.calendarItems
        .map { it.any { item -> item.mediaStatus == MediaStatus.WANTED } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun runAutoDownloadManual() {
        viewModelScope.launch {
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

    init {
        viewModelScope.launch {
            repository.activeUserToken.collect { token ->
                _userToken.value = token
                if (token != null && token.accessToken.isNotEmpty()) {
                    syncLocalCalendar()
                }
            }
        }
        repository.torrentServiceHelper.refreshServiceStatus()
    }

    fun syncLocalCalendar() {
        viewModelScope.launch {
            val token = repository.getActiveUserToken()
            if (token == null || token.accessToken.isEmpty()) return@launch
            _isSyncing.value = true
            try {
                repository.syncCalendar()
            } catch (_: Exception) {
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
            if (success) onSuccess() else onFailure()
        }
    }
}
