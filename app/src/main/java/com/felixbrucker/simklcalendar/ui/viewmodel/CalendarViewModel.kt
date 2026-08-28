package com.felixbrucker.simklcalendar.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.database.NotificationSetting
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.database.WatchedEpisode
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import com.felixbrucker.simklcalendar.data.util.MediaFormatter
import com.felixbrucker.simklcalendar.receiver.NotificationReceiver
import com.felixbrucker.simklcalendar.receiver.NotificationScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    val repository = SimklRepository(application)

    val userToken: StateFlow<UserToken?> = repository.activeUserToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val notificationSettings: StateFlow<List<NotificationSetting>> = repository.notificationSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCalendarItems: StateFlow<List<CalendarItem>> = repository.calendarItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchedEpisodes: StateFlow<List<WatchedEpisode>> = repository.watchedEpisodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customSearchLinks: StateFlow<List<CustomSearchLink>> = repository.customSearchLinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isMarkingWatched = MutableStateFlow(false)
    val isMarkingWatched: StateFlow<Boolean> = _isMarkingWatched.asStateFlow()

    private val _pendingDetailKey = MutableStateFlow<String?>(null)
    val pendingDetailKey: StateFlow<String?> = _pendingDetailKey.asStateFlow()

    fun setPendingDetailKey(key: String) {
        _pendingDetailKey.value = key
    }

    fun clearPendingDetailKey() {
        _pendingDetailKey.value = null
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

    // Combined filtered calendar list reactive flow
    @Suppress("UNCHECKED_CAST")
    val filteredCalendarItems: StateFlow<List<CalendarItem>> = combine(
        repository.calendarItems,
        showTv,
        showAnime,
        showMovies,
        onlySeasonPremieres,
        onlySeasonFinales,
        onlyDigitalDvd,
        searchQuery
    ) { flows ->
        val items = flows[0] as List<CalendarItem>
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
        }.sortedWith(compareBy<CalendarItem> { it.date }.thenBy { it.title })
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

    fun markEpisodeWatched(
        simklId: Int,
        season: Int?,
        episodeNumber: Int,
        mediaType: MediaType,
        showTitle: String? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isMarkingWatched.value = true
            val result = repository.markEpisodeWatched(
                simklId = simklId,
                season = season,
                episodeNumber = episodeNumber,
                mediaType = mediaType
            )
            _isMarkingWatched.value = false
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
        showTitle: String? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isMarkingWatched.value = true
            val result = repository.markMovieWatched(simklId = simklId)
            _isMarkingWatched.value = false
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
        showTitle: String? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isMarkingWatched.value = true
            val result = repository.markEpisodeUnwatched(
                simklId = simklId,
                season = season,
                episodeNumber = episodeNumber,
                mediaType = mediaType
            )
            _isMarkingWatched.value = false
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
        showTitle: String? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isMarkingWatched.value = true
            val result = repository.markMovieUnwatched(simklId = simklId)
            _isMarkingWatched.value = false
            if (result.isSuccess) {
                val message = MediaFormatter.formatMovieUnwatchedToast(showTitle)
                onResult(true, message)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to mark movie as unwatched"
                onResult(false, errorMsg)
            }
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
                repository.insertSearchLink(link)
            } else {
                repository.updateSearchLink(link)
            }
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
}

