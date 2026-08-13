package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.CalendarItem
import com.example.data.database.NotificationSetting
import com.example.data.database.UserToken
import com.example.data.repository.SimklRepository
import com.example.receiver.NotificationReceiver
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    val repository = SimklRepository(application)

    val userToken: StateFlow<UserToken?> = repository.activeUserToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val notificationSettings: StateFlow<List<NotificationSetting>> = repository.notificationSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtering State Flows
    val showTv = MutableStateFlow(true)
    val showAnime = MutableStateFlow(true)
    val showMovies = MutableStateFlow(true)
    val onlySeasonPremieres = MutableStateFlow(false)
    val onlySeasonFinales = MutableStateFlow(false)

    // Combined filtered calendar list reactive flow
    val filteredCalendarItems: StateFlow<List<CalendarItem>> = combine(
        repository.calendarItems,
        showTv,
        showAnime,
        showMovies,
        onlySeasonPremieres,
        onlySeasonFinales
    ) { flows ->
        val items = flows[0] as List<CalendarItem>
        val tv = flows[1] as Boolean
        val anime = flows[2] as Boolean
        val movies = flows[3] as Boolean
        val premieres = flows[4] as Boolean
        val finales = flows[5] as Boolean

        items.filter { item ->
            // Category filter
            val matchesCategory = when (item.type) {
                "tv" -> tv
                "anime" -> anime
                "movie" -> movies
                else -> true
            }

            // Premiere/Finale filter
            val matchesType = if (premieres && finales) {
                item.isSeasonPremiere || item.isSeasonFinale
            } else if (premieres) {
                item.isSeasonPremiere
            } else if (finales) {
                item.isSeasonFinale
            } else {
                true
            }

            matchesCategory && matchesType
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Calendar sync and status tracking
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    init {
        // Automatically sync calendar on launch (fallbacks to demo if unlogged)
        syncLocalCalendar()
    }

    fun syncLocalCalendar(force: Boolean = false) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            try {
                repository.syncCalendar(force)
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
        showId: Int,
        title: String,
        type: String,
        notifyEp: Boolean,
        notifyLast: Boolean
    ) {
        viewModelScope.launch {
            repository.toggleNotificationSetting(
                showId = showId,
                showTitle = title,
                type = type,
                notifyEveryEpisode = notifyEp,
                notifyAiredLastEpisode = notifyLast
            )
        }
    }

    fun testTriggerNotification(item: CalendarItem) {
        viewModelScope.launch {
            val setting = repository.getSettingForShow(item.id)
            val notifyEveryEp = setting?.notifyEveryEpisode ?: true
            val notifyBinge = setting?.notifyAiredLastEpisode ?: true

            if (item.isLastEpisode) {
                if (notifyBinge) {
                    NotificationReceiver.triggerEpisodeNotification(
                        getApplication(),
                        showTitle = item.title,
                        episodeName = item.episodeTitle,
                        season = item.season,
                        episodeNumber = item.episodeNumber,
                        isLastEpisode = true
                    )
                }
            } else {
                if (notifyEveryEp) {
                    NotificationReceiver.triggerEpisodeNotification(
                        getApplication(),
                        showTitle = item.title,
                        episodeName = item.episodeTitle,
                        season = item.season,
                        episodeNumber = item.episodeNumber,
                        isLastEpisode = false
                    )
                }
            }
        }
    }
}
