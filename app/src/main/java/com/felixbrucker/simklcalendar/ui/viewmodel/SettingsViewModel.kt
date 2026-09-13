package com.felixbrucker.simklcalendar.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import com.felixbrucker.simklcalendar.data.util.getStringListWithMigration
import com.felixbrucker.simklcalendar.data.util.putStringList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    val repository = SimklRepository(application)
    private val downloadPrefs = application.getSharedPreferences("auto_download_prefs", Context.MODE_PRIVATE)

    val userToken: StateFlow<UserToken?> = repository.activeUserToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isTorrentServiceInstalled: StateFlow<Boolean> = repository.torrentServiceHelper.isInstalled

    val customSearchLinks: StateFlow<List<CustomSearchLink>> = repository.customSearchLinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val autoDownloadQuality = MutableStateFlow(downloadPrefs.getString("quality", "1080p") ?: "1080p")
    val autoDownloadPreferHevc = MutableStateFlow(downloadPrefs.getBoolean("prefer_hevc", true))
    val autoDownloadUnwatchedTv = MutableStateFlow(downloadPrefs.getBoolean("auto_download_unwatched_tv", false))
    val autoDownloadUnwatchedAnime = MutableStateFlow(downloadPrefs.getBoolean("auto_download_unwatched_anime", false))
    val autoDownloadUnwatchedMovie = MutableStateFlow(downloadPrefs.getBoolean("auto_download_unwatched_movie", false))
    val autoDownloadPreferredKeywords = MutableStateFlow(downloadPrefs.getStringListWithMigration("preferred_keywords"))
    val autoDownloadIgnoreKeywords = MutableStateFlow(downloadPrefs.getStringListWithMigration("ignore_keywords"))

    fun updateAutoDownloadQuality(quality: String) {
        autoDownloadQuality.value = quality
        downloadPrefs.edit { putString("quality", quality) }
    }

    fun updateAutoDownloadPreferHevc(prefer: Boolean) {
        autoDownloadPreferHevc.value = prefer
        downloadPrefs.edit { putBoolean("prefer_hevc", prefer) }
    }

    fun updateAutoDownloadUnwatchedTv(default: Boolean) {
        autoDownloadUnwatchedTv.value = default
        downloadPrefs.edit { putBoolean("auto_download_unwatched_tv", default) }
    }

    fun updateAutoDownloadUnwatchedAnime(default: Boolean) {
        autoDownloadUnwatchedAnime.value = default
        downloadPrefs.edit { putBoolean("auto_download_unwatched_anime", default) }
    }

    fun updateAutoDownloadUnwatchedMovie(default: Boolean) {
        autoDownloadUnwatchedMovie.value = default
        downloadPrefs.edit { putBoolean("auto_download_unwatched_movie", default) }
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
            downloadPrefs.edit { putStringList("ignore_keywords_list", current) }
        }
    }

    fun updateIgnoreKeywordsOrder(reordered: List<String>) {
        autoDownloadIgnoreKeywords.value = reordered
        downloadPrefs.edit { putStringList("ignore_keywords_list", reordered) }
    }

    private val _isForceSyncing = MutableStateFlow(false)
    val isForceSyncing: StateFlow<Boolean> = _isForceSyncing.asStateFlow()

    private val _isSearchingWantedTorrents = MutableStateFlow(false)
    val isSearchingWantedTorrents: StateFlow<Boolean> = _isSearchingWantedTorrents.asStateFlow()
    private val _autoDownloadStatus = MutableStateFlow("")
    val autoDownloadStatus: StateFlow<String> = _autoDownloadStatus.asStateFlow()
    val shouldShowAutoDownloadStatus: StateFlow<Boolean> = autoDownloadStatus
        .map { it.isNotBlank() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun forceWatchlistResync(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _isForceSyncing.value = true
            try {
                repository.syncCalendar(force = true)
                onComplete("Full re-sync completed successfully.")
            } catch (e: Exception) {
                onComplete("Sync failed: ${e.message}")
            } finally {
                _isForceSyncing.value = false
            }
        }
    }

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

    fun logout(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.logout()
            onComplete()
        }
    }

    fun insertSearchLink(link: CustomSearchLink) {
        viewModelScope.launch { repository.insertSearchLink(link) }
    }

    fun updateSearchLink(link: CustomSearchLink) {
        viewModelScope.launch { repository.updateSearchLink(link) }
    }

    fun updateSearchLinks(links: List<CustomSearchLink>) {
        viewModelScope.launch { repository.updateSearchLinks(links) }
    }

    fun deleteSearchLink(link: CustomSearchLink) {
        viewModelScope.launch { repository.deleteSearchLink(link) }
    }
}
