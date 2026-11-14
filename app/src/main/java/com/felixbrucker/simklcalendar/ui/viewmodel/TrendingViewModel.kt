package com.felixbrucker.simklcalendar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.simklcalendar.data.database.TrendingMediaItem
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.preferences.UiPreferences
import com.felixbrucker.simklcalendar.data.preferences.UiRepository
import com.felixbrucker.simklcalendar.data.repository.MediaRepository
import com.felixbrucker.simklcalendar.data.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class TrendingViewModel @Inject constructor(
    private val uiRepo: UiRepository,
    private val mediaRepository: MediaRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    val uiPreferences: StateFlow<UiPreferences> = uiRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiPreferences())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearchQuery() {
        _searchQuery.value = ""
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val trendingMediaItems: StateFlow<List<TrendingMediaItem>> = combine(
        uiPreferences,
        searchQuery
    ) { prefs, query ->
        val allowedTypes = buildList {
            if (prefs.trendingShowTv) add(MediaType.TV)
            if (prefs.trendingShowAnime) add(MediaType.ANIME)
            if (prefs.trendingShowMovies) add(MediaType.MOVIE)
        }
        Triple(allowedTypes, prefs.trendingExcludeOnMyLists, query.trim())
    }.flatMapLatest { (allowedTypes, excludeOnMyLists, query) ->
        mediaRepository.getFilteredTrendingMediaItemsFlow(allowedTypes, excludeOnMyLists, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshTrending()
    }

    fun refreshTrending() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                syncRepository.syncTrending()
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync trending items")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun toggleExcludeOnMyLists() {
        viewModelScope.launch {
            uiRepo.updateFilters { it.copy(trendingExcludeOnMyLists = !it.trendingExcludeOnMyLists) }
        }
    }

    fun toggleShowMovies() {
        viewModelScope.launch {
            uiRepo.updateFilters { it.copy(trendingShowMovies = !it.trendingShowMovies) }
        }
    }

    fun toggleShowTv() {
        viewModelScope.launch {
            uiRepo.updateFilters { it.copy(trendingShowTv = !it.trendingShowTv) }
        }
    }

    fun toggleShowAnime() {
        viewModelScope.launch {
            uiRepo.updateFilters { it.copy(trendingShowAnime = !it.trendingShowAnime) }
        }
    }
}
