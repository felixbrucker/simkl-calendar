package com.felixbrucker.simklcalendar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.preferences.AppSettingsPreferences
import com.felixbrucker.simklcalendar.data.preferences.AppSettingsRepository
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadPreferences
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import com.felixbrucker.simklcalendar.data.preferences.NotificationPreferences
import com.felixbrucker.simklcalendar.data.preferences.NotificationRepository
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import com.felixbrucker.simklcalendar.receiver.alarm.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SimklRepository,
    val appSettingsRepo: AppSettingsRepository,
    val autoDownloadRepo: AutoDownloadRepository,
    val notificationRepo: NotificationRepository,
    val torrentServiceHelper: TorrentServiceHelper,
    val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val userToken: StateFlow<UserToken?> = repository.activeUserToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val notificationPreferences: StateFlow<NotificationPreferences> = notificationRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationPreferences())

    val appSettingsPreferences: StateFlow<AppSettingsPreferences> = appSettingsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettingsPreferences())

    val autoDownloadPreferences: StateFlow<AutoDownloadPreferences> = autoDownloadRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutoDownloadPreferences())

    val customSearchLinks: StateFlow<List<CustomSearchLink>> = repository.customSearchLinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isForceSyncing = MutableStateFlow(false)
    val isForceSyncing: StateFlow<Boolean> = _isForceSyncing.asStateFlow()

    fun logoutUser() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    fun updateSyncInterval(hours: Int) {
        viewModelScope.launch {
            appSettingsRepo.setSyncIntervalHours(hours)
        }
    }

    fun updateSearchInterval(hours: Int) {
        viewModelScope.launch {
            autoDownloadRepo.setSearchIntervalHours(hours)
        }
    }

    fun updateUseExactAlarms(enabled: Boolean) {
        viewModelScope.launch {
            notificationRepo.setUseExactAlarms(enabled)
        }
    }

    fun updateDefaultNotifyAiring(enabled: Boolean) {
        viewModelScope.launch {
            notificationRepo.setDefaultNotifyAiring(enabled)
        }
    }

    fun updateDefaultNotifySeasonFinished(enabled: Boolean) {
        viewModelScope.launch {
            notificationRepo.setDefaultNotifySeasonFinished(enabled)
        }
    }

    fun updateDefaultNotifyMovieTheater(enabled: Boolean) {
        viewModelScope.launch {
            notificationRepo.setDefaultNotifyMovieTheater(enabled)
        }
    }

    fun updateDefaultNotifyMovieDigital(enabled: Boolean) {
        viewModelScope.launch {
            notificationRepo.setDefaultNotifyMovieDigital(enabled)
        }
    }

    fun scheduleAllItemsAiredAlarms() {
        viewModelScope.launch {
            alarmScheduler.scheduleAllItemsAiredAlarms()
        }
    }

    fun isTorrentServiceInstalled(): Boolean {
        return torrentServiceHelper.isServiceInstalled()
    }

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
}
