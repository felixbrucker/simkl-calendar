package com.felixbrucker.simklcalendar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.felixbrucker.simklcalendar.data.model.WatchlistStatus
import com.felixbrucker.simklcalendar.data.network.AuthenticatedSimklApiService
import com.felixbrucker.simklcalendar.data.network.SimklSearchItem
import com.felixbrucker.simklcalendar.data.paging.SimklSearchPagingSource
import com.felixbrucker.simklcalendar.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class SimklSearchViewModel @Inject constructor(
    private val apiService: AuthenticatedSimklApiService,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _displayedSimklIds = MutableStateFlow<List<Int>>(emptyList())

    fun updateDisplayedSimklIds(ids: List<Int>) {
        if (_displayedSimklIds.value != ids) {
            _displayedSimklIds.value = ids
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val watchlistStatusMap: StateFlow<Map<Int, WatchlistStatus>> = _displayedSimklIds
        .flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(emptyMap())
            } else {
                mediaRepository.getWatchlistItemsFlow(ids).map { items ->
                    items.mapNotNull { item ->
                        item.status?.let { item.simklId to it }
                    }.toMap()
                }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearchQuery() {
        _searchQuery.value = ""
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val searchResults: Flow<PagingData<SimklSearchItem>> = searchQuery
        .debounce(500.milliseconds)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(PagingData.empty())
            } else {
                Pager(
                    config = PagingConfig(
                        pageSize = 20,
                        prefetchDistance = 5,
                        enablePlaceholders = true,
                        initialLoadSize = 20
                    ),
                    pagingSourceFactory = { SimklSearchPagingSource(apiService, query) }
                ).flow
            }
        }
        .flowOn(Dispatchers.IO)
        .cachedIn(viewModelScope)
}
