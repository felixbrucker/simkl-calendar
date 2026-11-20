package com.felixbrucker.simklcalendar.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.database.MediaItem
import com.felixbrucker.simklcalendar.data.database.MediaItemWithDetails
import com.felixbrucker.simklcalendar.data.database.WatchlistItem
import com.felixbrucker.simklcalendar.data.database.WatchlistItemDao
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.WatchlistStatus
import com.felixbrucker.simklcalendar.data.preferences.UiPreferences
import com.felixbrucker.simklcalendar.data.preferences.UiRepository
import com.felixbrucker.simklcalendar.data.network.AuthenticatedSimklApiService
import com.felixbrucker.simklcalendar.data.network.SimklIds
import com.felixbrucker.simklcalendar.data.network.SyncAddToListMovieItem
import com.felixbrucker.simklcalendar.data.network.SyncAddToListRequest
import com.felixbrucker.simklcalendar.data.network.SyncAddToListShowItem
import com.felixbrucker.simklcalendar.data.network.SyncHistoryMovieItem
import com.felixbrucker.simklcalendar.data.network.SyncHistoryRequest
import com.felixbrucker.simklcalendar.data.network.SyncHistoryShowItem
import com.felixbrucker.simklcalendar.data.repository.CalendarRepository
import com.felixbrucker.simklcalendar.data.repository.CustomSearchLinkRepository
import com.felixbrucker.simklcalendar.data.repository.MediaRepository
import com.felixbrucker.simklcalendar.data.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.compose.runtime.Immutable
import com.felixbrucker.simklcalendar.data.database.EpisodeType
import com.felixbrucker.simklcalendar.data.util.MediaFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

object MediaItemHolder {
    private var cachedItem: MediaItem? = null

    fun set(item: MediaItem?) {
        cachedItem = item
    }

    fun getAndClear(): MediaItem? {
        val item = cachedItem
        cachedItem = null
        return item
    }

    fun get(): MediaItem? = cachedItem
}

@Immutable
sealed interface EpisodeListItemUiModel {
    val key: String

    @Immutable
    data class SeasonHeader(
        val season: Int,
        override val key: String = "season_$season",
    ) : EpisodeListItemUiModel

    @Immutable
    data class EpisodeRow(
        val episodeKey: String,
        val title: String,
        val episodeCode: String,
        val date: Instant?,
        override val key: String = episodeKey,
    ) : EpisodeListItemUiModel
}

@Immutable
data class EpisodeListUiState(
    val totalCount: Int = 0,
    val items: List<EpisodeListItemUiModel> = emptyList()
)

@HiltViewModel
class MediaItemDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val syncRepository: SyncRepository,
    private val authenticatedSimklApiService: AuthenticatedSimklApiService,
    calendarRepository: CalendarRepository,
    customSearchLinkRepository: CustomSearchLinkRepository,
    private val watchlistItemDao: WatchlistItemDao,
    private val uiRepository: UiRepository,
    private val savedStateHandle: SavedStateHandle,
    defaultDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val simklIdFlow: StateFlow<Int> = savedStateHandle.getStateFlow("simklId", 0)

    private val initialMediaItem = MediaItemHolder.getAndClear()
    private val initialItem = initialMediaItem?.let { MediaItemWithDetails(mediaItem = it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val mediaItem: StateFlow<MediaItemWithDetails?> = simklIdFlow
        .flatMapLatest { id ->
            val typeStr = savedStateHandle.get<String>("mediaType")
            val type = MediaType.fromKey(typeStr)
            mediaRepository.getMediaItemStream(id, type)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = initialItem
        )

    val episodeListUiState: StateFlow<EpisodeListUiState> = mediaItem
        .map { details ->
            if (details == null) {
                EpisodeListUiState()
            } else {
                val episodes = details.episodes
                val mediaType = details.mediaType
                val filteredEpisodes = episodes.filter { (it.type != EpisodeType.SPECIAL) && (it.season > 0) }
                val grouped = filteredEpisodes.groupBy { it.season }
                val sortedSeasons = grouped.keys.sortedDescending()
                val items = ArrayList<EpisodeListItemUiModel>()
                val showSeasonHeaders = sortedSeasons.size > 1
                for (season in sortedSeasons) {
                    val seasonEpisodes = grouped[season] ?: continue
                    if ((season > 0) && showSeasonHeaders) {
                        items.add(EpisodeListItemUiModel.SeasonHeader(season = season))
                    }
                    for (ep in seasonEpisodes.sortedByDescending { it.episodeNumber }) {
                        val episodeCode = if (mediaType == MediaType.MOVIE) {
                            "MOVIE"
                        } else {
                            MediaFormatter.formatEpisodeCode(mediaType, ep.season, ep.episodeNumber)
                        }
                        val title = ep.title.takeIf { it.isNotBlank() } ?: "TBA"
                        items.add(
                            EpisodeListItemUiModel.EpisodeRow(
                                episodeKey = "${ep.simklId}_${ep.season}_${ep.episodeNumber}",
                                title = title,
                                episodeCode = episodeCode,
                                date = ep.date
                            )
                        )
                    }
                }
                EpisodeListUiState(
                    totalCount = filteredEpisodes.size,
                    items = items
                )
            }
        }
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = EpisodeListUiState()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val watchlistItem: StateFlow<WatchlistItem?> = simklIdFlow
        .flatMapLatest { id ->
            calendarRepository.getWatchlistItemFlow(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val watchlistStatusMap: StateFlow<Map<Int, WatchlistStatus>> = mediaItem
        .flatMapLatest { details ->
            if (details == null) {
                flowOf(emptyMap())
            } else {
                val ids = buildList {
                    addAll(details.relations.map { it.relatedSimklId })
                    addAll(details.similarItems.map { it.similarSimklId })
                    addAll(details.recommendedItems.map { it.recommendedSimklId })
                }
                if (ids.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    watchlistItemDao.getWatchlistItemsFlow(ids)
                        .map { list -> list.associate { it.simklId to (it.status ?: WatchlistStatus.PLAN_TO_WATCH) } }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val uiPreferences: StateFlow<UiPreferences> = uiRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiPreferences())

    fun toggleHideWatchlistItemsInSimilar(hide: Boolean) {
        viewModelScope.launch {
            uiRepository.updateFilters { it.copy(hideWatchlistItemsInSimilar = hide) }
        }
    }

    fun toggleHideWatchlistItemsInRecommended(hide: Boolean) {
        viewModelScope.launch {
            uiRepository.updateFilters { it.copy(hideWatchlistItemsInRecommended = hide) }
        }
    }

    val customSearchLinks: StateFlow<List<CustomSearchLink>> = customSearchLinkRepository.customSearchLinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refresh(onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val currentId = simklIdFlow.value
                val typeStr = savedStateHandle.get<String>("mediaType")
                val type = MediaType.fromKey(typeStr)
                mediaRepository.refreshMediaItem(currentId, type)
                onResult?.invoke(true, "Refreshed successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh media item")
                onResult?.invoke(false, "Failed to refresh: ${e.localizedMessage ?: "Unknown error"}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private val _isUpdatingStatus = MutableStateFlow(false)
    val isUpdatingStatus: StateFlow<Boolean> = _isUpdatingStatus.asStateFlow()

    fun updateWatchlistStatus(
        simklId: Int,
        mediaType: MediaType,
        status: WatchlistStatus?,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isUpdatingStatus.value = true
            try {
                val simklIds = SimklIds(simkl = simklId)
                when (status) {
                    null -> {
                        val request = when (mediaType) {
                            MediaType.MOVIE -> SyncHistoryRequest(movies = listOf(SyncHistoryMovieItem(ids = simklIds)))
                            MediaType.TV -> SyncHistoryRequest(shows = listOf(SyncHistoryShowItem(ids = simklIds)))
                            MediaType.ANIME -> SyncHistoryRequest(anime = listOf(SyncHistoryShowItem(ids = simklIds)))
                        }
                        authenticatedSimklApiService.markHistoryUnwatched(request)
                        syncRepository.removeWatchlistItem(simklId)
                    }
                    WatchlistStatus.COMPLETED -> {
                        val statusKey = status.key
                        val request = when (mediaType) {
                            MediaType.MOVIE -> SyncHistoryRequest(
                                movies = listOf(
                                    SyncHistoryMovieItem(
                                        ids = simklIds,
                                        status = statusKey
                                    )
                                )
                            )

                            MediaType.TV -> SyncHistoryRequest(
                                shows = listOf(
                                    SyncHistoryShowItem(
                                        ids = simklIds,
                                        status = statusKey
                                    )
                                )
                            )

                            MediaType.ANIME -> SyncHistoryRequest(
                                anime = listOf(
                                    SyncHistoryShowItem(
                                        ids = simklIds,
                                        status = statusKey
                                    )
                                )
                            )
                        }
                        authenticatedSimklApiService.markHistoryWatched(request)
                    }
                    else -> {
                        val statusKey = status.key
                        val request = when (mediaType) {
                            MediaType.MOVIE -> SyncAddToListRequest(
                                movies = listOf(
                                    SyncAddToListMovieItem(
                                        to = statusKey,
                                        ids = simklIds
                                    )
                                )
                            )

                            MediaType.TV -> SyncAddToListRequest(
                                shows = listOf(
                                    SyncAddToListShowItem(
                                        to = statusKey,
                                        ids = simklIds
                                    )
                                )
                            )

                            MediaType.ANIME -> SyncAddToListRequest(
                                anime = listOf(
                                    SyncAddToListShowItem(
                                        to = statusKey,
                                        ids = simklIds
                                    )
                                )
                            )
                        }
                        authenticatedSimklApiService.addToList(request)
                    }
                }
                syncRepository.syncCalendar()
                val message = if (status == null) "Removed from watchlist" else "Updated list to ${status.displayName}"
                onResult(true, message)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update watchlist status on SIMKL")
                onResult(false, "Failed to update watchlist: ${e.localizedMessage ?: "Unknown error"}")
            } finally {
                _isUpdatingStatus.value = false
            }
        }
    }
}
