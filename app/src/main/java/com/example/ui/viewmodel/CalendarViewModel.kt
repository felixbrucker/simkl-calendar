package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.CalendarItem
import com.example.data.database.NotificationSetting
import com.example.data.database.UserToken
import com.example.data.model.MediaType
import com.example.data.model.MovieReleaseType
import com.example.data.repository.SimklRepository
import com.example.data.util.MediaFormatter
import com.example.receiver.NotificationReceiver
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

    val watchedEpisodes: StateFlow<List<com.example.data.database.WatchedEpisode>> = repository.watchedEpisodes
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
    val excludeWatched = MutableStateFlow(false)
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
        excludeWatched,
        searchQuery
    ) { flows ->
        val items = flows[0] as List<CalendarItem>
        val tv = flows[1] as Boolean
        val anime = flows[2] as Boolean
        val movies = flows[3] as Boolean
        val premieres = flows[4] as Boolean
        val finales = flows[5] as Boolean
        val digitalDvd = flows[6] as Boolean
        val excludeWatchedOnly = flows[7] as Boolean
        val query = (flows[8] as String).trim()

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

            // Exclude watched episodes / releases filter
            val matchesWatched = if (excludeWatchedOnly) {
                !item.isWatched
            } else {
                true
            }

            // Search query filter matching show/movie title or episode title
            val matchesQuery = if (query.isEmpty()) {
                true
            } else {
                item.title.contains(query, ignoreCase = true) ||
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
            com.example.receiver.NotificationScheduler.scheduleAllNotifications(getApplication())
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
}
