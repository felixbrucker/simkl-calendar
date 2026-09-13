package com.felixbrucker.simklcalendar.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class WatchlistTableViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SimklRepository(application)
    private val uiPrefs = application.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    val showTv = MutableStateFlow(uiPrefs.getBoolean("filter_show_tv", true))
    val showAnime = MutableStateFlow(uiPrefs.getBoolean("filter_show_anime", true))
    val showMovies = MutableStateFlow(uiPrefs.getBoolean("filter_show_movies", true))
    val showOnlyUnwatchedReleased = MutableStateFlow(uiPrefs.getBoolean("filter_only_unwatched", true))
    val searchQuery = MutableStateFlow("")

    private val _tableSortField = MutableStateFlow(TableSortField.NAME)
    val tableSortField: StateFlow<TableSortField> = _tableSortField.asStateFlow()

    private val _tableSortDirection = MutableStateFlow(SortDirection.ASCENDING)
    val tableSortDirection: StateFlow<SortDirection> = _tableSortDirection.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val watchlistItems: Flow<PagingData<WatchlistTableItem>> = combine(
        combine(showTv, showAnime, showMovies) { tv, anime, movies -> Triple(tv, anime, movies) },
        showOnlyUnwatchedReleased,
        searchQuery,
        combine(tableSortField, tableSortDirection) { field, dir -> field to dir }
    ) { filterTriple, unwatched, query, sortPair ->
        repository.getWatchlistPaged(
            showTv = filterTriple.first,
            showAnime = filterTriple.second,
            showMovies = filterTriple.third,
            onlyUnwatchedReleased = unwatched,
            query = query,
            sortField = sortPair.first.name,
            isDesc = sortPair.second == SortDirection.DESCENDING
        )
    }.flattenConcat()
        .map { pagingData ->
            pagingData.map { stats ->
                WatchlistTableItem(
                    watchlistItem = stats.watchlistItem,
                    hasUnwatched = stats.hasUnwatched,
                    hasUnwatchedReleased = stats.hasUnwatchedReleased,
                    nextEpisodeDate = stats.nextEpisodeDate,
                    lastAiredDate = stats.lastAiredDate,
                    watchedReleasedCount = stats.watchedReleasedCount,
                    totalReleasedCount = stats.totalReleasedCount,
                    downloadedReleasedCount = stats.downloadedReleasedCount,
                    totalDownloadableReleasedCount = stats.totalDownloadableReleasedCount
                )
            }
        }
        .cachedIn(viewModelScope)

    fun toggleTableSort(field: TableSortField) {
        if (_tableSortField.value == field) {
            _tableSortDirection.value = if (_tableSortDirection.value == SortDirection.ASCENDING) SortDirection.DESCENDING else SortDirection.ASCENDING
        } else {
            _tableSortField.value = field
            _tableSortDirection.value = SortDirection.ASCENDING
        }
    }

    val isTorrentServiceInstalled: StateFlow<Boolean> = repository.torrentServiceHelper.isInstalled
}
