package com.felixbrucker.simklcalendar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.simklcalendar.data.database.TrendingMediaItem
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.TrendingPeriod
import com.felixbrucker.simklcalendar.data.model.WatchlistStatus
import com.felixbrucker.simklcalendar.data.preferences.AppSettingsPreferences
import com.felixbrucker.simklcalendar.data.preferences.AppSettingsRepository
import com.felixbrucker.simklcalendar.data.preferences.UiPreferences
import com.felixbrucker.simklcalendar.data.preferences.UiRepository
import com.felixbrucker.simklcalendar.data.repository.MediaRepository
import com.felixbrucker.simklcalendar.data.repository.SyncRepository
import com.felixbrucker.simklcalendar.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TrendingViewModel @Inject constructor(
    private val uiRepo: UiRepository,
    private val mediaRepository: MediaRepository,
    private val syncRepository: SyncRepository,
    private val appSettingsRepo: AppSettingsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    defaultDispatcher: CoroutineDispatcher
) : ViewModel() {

    val uiPreferences: StateFlow<UiPreferences> = uiRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiPreferences())

    val trendingPeriod: StateFlow<TrendingPeriod> = appSettingsRepo.preferencesFlow
        .map { TrendingPeriod.fromKey(it.trendingPeriod) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrendingPeriod.DAILY)

    private val _isTrendingPeriodSyncing = MutableStateFlow(false)
    val isTrendingPeriodSyncing: StateFlow<Boolean> = _isTrendingPeriodSyncing.asStateFlow()

    companion object {
        private const val TAG = "TrendingViewModel"
    }

    fun updateTrendingPeriod(period: TrendingPeriod) {
        appScope.launch {
            _isTrendingPeriodSyncing.value = true
            appSettingsRepo.setTrendingPeriod(period.key)
            try {
                syncRepository.syncTrending(force = true)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to sync trending items after period change")
            } finally {
                _isTrendingPeriodSyncing.value = false
            }
        }
    }

    val minTrendingYear: StateFlow<Int> = mediaRepository.minTrendingYearFlow
        .map { it ?: 1950 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1950)

    val maxTrendingYear: StateFlow<Int> = mediaRepository.maxTrendingYearFlow
        .map { it ?: LocalDate.now().year }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalDate.now().year)

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

    data class TrendingFilterState(
        val allowedTypes: List<MediaType>,
        val excludeOnMyLists: Boolean,
        val minRating: Float,
        val minYear: Int,
        val hasDvd: Boolean
    )

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
        val filterState = TrendingFilterState(
            allowedTypes = allowedTypes,
            excludeOnMyLists = prefs.trendingExcludeOnMyLists,
            minRating = prefs.trendingMinRating,
            minYear = prefs.trendingMinYear,
            hasDvd = prefs.trendingHasDvd
        )
        Pair(filterState, query.trim())
    }.flatMapLatest { (filterState, query) ->
        mediaRepository.getFilteredTrendingMediaItemsFlow(
            allowedTypes = filterState.allowedTypes,
            excludeOnMyLists = filterState.excludeOnMyLists,
            minRating = filterState.minRating,
            minYear = filterState.minYear,
            hasDvd = filterState.hasDvd,
            searchQuery = query
        )
    }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val watchlistStatusMap: StateFlow<Map<Int, WatchlistStatus>> = trendingMediaItems
        .map { items -> items.map { it.trendingEntry.simklId } }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(emptyMap())
            } else {
                mediaRepository.getWatchlistItemsFlow(ids).map { list ->
                    list.mapNotNull { item ->
                        item.status?.let { item.simklId to it }
                    }.toMap()
                }
            }
        }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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

    fun setTrendingMinRating(rating: Float) {
        viewModelScope.launch {
            uiRepo.updateFilters { it.copy(trendingMinRating = rating) }
        }
    }

    fun setTrendingMinYear(year: Int) {
        viewModelScope.launch {
            uiRepo.updateFilters { it.copy(trendingMinYear = year) }
        }
    }

    fun toggleTrendingHasDvd() {
        viewModelScope.launch {
            uiRepo.updateFilters { it.copy(trendingHasDvd = !it.trendingHasDvd) }
        }
    }

    fun toggleTrendingFilterSidebarVisible() {
        viewModelScope.launch {
            uiRepo.updateFilters { it.copy(trendingFilterSidebarVisible = !it.trendingFilterSidebarVisible) }
        }
    }
}
