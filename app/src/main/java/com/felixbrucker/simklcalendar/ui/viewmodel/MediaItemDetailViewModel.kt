package com.felixbrucker.simklcalendar.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.database.EpisodeType
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.MediaEpisode
import com.felixbrucker.simklcalendar.data.database.MediaItem
import com.felixbrucker.simklcalendar.data.database.MediaItemWithDetails
import com.felixbrucker.simklcalendar.data.database.NotificationSetting
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.WatchedEpisode
import com.felixbrucker.simklcalendar.data.database.WatchlistItem
import com.felixbrucker.simklcalendar.data.database.WatchlistItemDao
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.model.WatchlistStatus
import com.felixbrucker.simklcalendar.data.network.AuthenticatedSimklApiService
import com.felixbrucker.simklcalendar.data.network.SimklIds
import com.felixbrucker.simklcalendar.data.network.SyncAddToListMovieItem
import com.felixbrucker.simklcalendar.data.network.SyncAddToListRequest
import com.felixbrucker.simklcalendar.data.network.SyncAddToListShowItem
import com.felixbrucker.simklcalendar.data.network.SyncHistoryMovieItem
import com.felixbrucker.simklcalendar.data.network.SyncHistoryRequest
import com.felixbrucker.simklcalendar.data.network.SyncHistoryShowItem
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadPreferences
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import com.felixbrucker.simklcalendar.data.preferences.NotificationPreferences
import com.felixbrucker.simklcalendar.data.preferences.NotificationRepository
import com.felixbrucker.simklcalendar.data.preferences.UiPreferences
import com.felixbrucker.simklcalendar.data.preferences.UiRepository
import com.felixbrucker.simklcalendar.data.repository.CalendarRepository
import com.felixbrucker.simklcalendar.data.repository.CustomSearchLinkRepository
import com.felixbrucker.simklcalendar.data.repository.DownloadRepository
import com.felixbrucker.simklcalendar.data.repository.MediaRepository
import com.felixbrucker.simklcalendar.data.repository.NotificationSettingRepository
import com.felixbrucker.simklcalendar.data.repository.SyncRepository
import com.felixbrucker.simklcalendar.data.repository.WatchHistoryRepository
import com.felixbrucker.simklcalendar.data.util.DownloadProgress
import com.felixbrucker.simklcalendar.data.util.MediaFormatter
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import com.felixbrucker.torrent_search_api.SearchResultItem
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
        val totalEpisodes: Int = 0,
        val isFullyWatched: Boolean = false,
        val commonMediaStatus: MediaStatus? = null,
        val hasSearchableEpisode: Boolean = true,
        val hasCalendarItems: Boolean = false,
        val isSeasonUpdatingWatchStatus: Boolean = false,
        val showTitleText: Boolean = true,
        override val key: String = "season_$season",
    ) : EpisodeListItemUiModel

    @Immutable
    data class EpisodeRow(
        val episodeKey: String,
        val season: Int,
        val episodeNumber: Int,
        val title: String,
        val episodeCode: String,
        val date: Instant?,
        val calendarItem: CalendarItemWithWatchlist? = null,
        val isWatched: Boolean = false,
        val isUpdatingWatchStatus: Boolean = false,
        override val key: String = episodeKey,
    ) : EpisodeListItemUiModel
}

@Immutable
data class EpisodeListUiState(
    val totalCount: Int = 0,
    val items: List<EpisodeListItemUiModel> = emptyList(),
    val singleSeasonHeader: EpisodeListItemUiModel.SeasonHeader? = null
)

@Immutable
data class WatchProgressUiState(
    val watchedCount: Int = 0,
    val totalAiredCount: Int = 0,
    val percentage: Float = 0f,
    val notAiredCount: Int = 0,
) {
    val unwatchedCount: Int = (totalAiredCount - watchedCount).coerceAtLeast(0)
}

@HiltViewModel
class MediaItemDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val syncRepository: SyncRepository,
    private val authenticatedSimklApiService: AuthenticatedSimklApiService,
    private val calendarRepository: CalendarRepository,
    customSearchLinkRepository: CustomSearchLinkRepository,
    private val watchlistItemDao: WatchlistItemDao,
    private val uiRepository: UiRepository,
    private val downloadRepository: DownloadRepository,
    private val notificationSettingRepository: NotificationSettingRepository,
    autoDownloadRepo: AutoDownloadRepository,
    notificationRepo: NotificationRepository,
    torrentServiceHelper: TorrentServiceHelper,
    private val watchHistoryRepository: WatchHistoryRepository,
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
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = initialItem
        )

    val episodesForSeasons: StateFlow<List<MediaEpisode>> = mediaItem
        .map { details ->
            details?.episodes?.filter { it.type != EpisodeType.SPECIAL && it.season > 0 } ?: emptyList()
        }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isMovie: StateFlow<Boolean> = mediaItem
        .map { it?.mediaType == MediaType.MOVIE }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val seasons: StateFlow<Map<Int, List<MediaEpisode>>> = episodesForSeasons
        .map { eps -> eps.groupBy { it.season } }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _manualSearchSeasonNum = MutableStateFlow<Int?>(null)
    val manualSearchSeasonNum: StateFlow<Int?> = _manualSearchSeasonNum.asStateFlow()

    private val _isMovieManualSearchOpen = MutableStateFlow(false)
    val isMovieManualSearchOpen: StateFlow<Boolean> = _isMovieManualSearchOpen.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val watchlistItem: StateFlow<WatchlistItem?> = simklIdFlow
        .flatMapLatest { id ->
            calendarRepository.getWatchlistItemFlow(id)
        }
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val calendarItemsFlow: Flow<List<CalendarItemWithWatchlist>> = simklIdFlow
        .flatMapLatest { id ->
            calendarRepository.getSortedCalendarItemsForSimklIdFlow(id)
        }
        .flowOn(defaultDispatcher)

    @OptIn(ExperimentalCoroutinesApi::class)
    val watchedEpisodesFlow: Flow<List<WatchedEpisode>> = simklIdFlow
        .flatMapLatest { id ->
            watchHistoryRepository.getWatchedEpisodesForSimklIdFlow(id)
        }
        .flowOn(defaultDispatcher)

    val watchedSetFlow: StateFlow<Set<Pair<Int, Int>>> = combine(
        watchlistItem,
        episodesForSeasons,
        watchedEpisodesFlow
    ) { item, episodes, watchedEpisodes ->
        if (item?.status == WatchlistStatus.COMPLETED) {
            episodes.mapTo(HashSet()) { Pair(it.season, it.episodeNumber) }
        } else {
            watchedEpisodes.mapTo(HashSet()) { Pair(it.season, it.episodeNumber) }
        }
    }
    .flowOn(defaultDispatcher)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val targetMovieCalendarItem: StateFlow<CalendarItemWithWatchlist?> = combine(
        isMovie,
        calendarItemsFlow
    ) { isMovie, calendarItems ->
        if (!isMovie || calendarItems.isEmpty()) null
        else {
            calendarItems.find { it.movieReleaseType == MovieReleaseType.DIGITAL }
                ?: calendarItems.firstOrNull()
        }
    }
    .flowOn(defaultDispatcher)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val targetSeason: StateFlow<Int> = combine(
        seasons,
        watchedSetFlow
    ) { seasonMap, watchedSet ->
        if (seasonMap.isEmpty()) 1
        else {
            val sortedSeasons = seasonMap.keys.sorted()
            sortedSeasons.firstOrNull { s ->
                val epList = seasonMap[s] ?: emptyList()
                epList.any { ep -> !watchedSet.contains(Pair(ep.season, ep.episodeNumber)) }
            } ?: sortedSeasons.last()
        }
    }
    .flowOn(defaultDispatcher)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val isTargetSeasonFullyWatched: StateFlow<Boolean> = combine(
        targetSeason,
        seasons,
        watchedSetFlow
    ) { tSeason, seasonMap, watchedSet ->
        val epList = seasonMap[tSeason] ?: emptyList()
        epList.isNotEmpty() && epList.all { ep -> watchedSet.contains(Pair(ep.season, ep.episodeNumber)) }
    }
    .flowOn(defaultDispatcher)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isTargetSeasonOrMovieSearchable: StateFlow<Boolean> = combine(
        isMovie,
        targetMovieCalendarItem,
        targetSeason,
        seasons,
        calendarItemsFlow
    ) { isMovie, movieItem, tSeason, seasonMap, calendarItems ->
        if (isMovie) {
            movieItem != null && movieItem.mediaStatus.isSearchable
        } else {
            val calendarItemMap = calendarItems.associateBy { Pair(it.season ?: 1, it.episodeNumber ?: 1) }
            val targetEpisodes = seasonMap[tSeason] ?: emptyList()
            targetEpisodes.isNotEmpty() && targetEpisodes.any { ep ->
                val calItem = calendarItemMap[Pair(ep.season, ep.episodeNumber)]
                calItem != null && calItem.mediaStatus.isSearchable
            }
        }
    }
    .flowOn(defaultDispatcher)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _updatingWatchStatusKeys = MutableStateFlow<Set<String>>(emptySet())
    val updatingWatchStatusKeys: StateFlow<Set<String>> = _updatingWatchStatusKeys.asStateFlow()

    val isTargetSeasonUpdatingWatchStatus: StateFlow<Boolean> = combine(
        isMovie,
        simklIdFlow,
        targetSeason,
        updatingWatchStatusKeys
    ) { isMovie, sId, tSeason, updatingKeys ->
        if (isMovie) {
            updatingKeys.contains("${sId}_movie")
        } else {
            updatingKeys.contains("${sId}_season_${tSeason}")
        }
    }
    .flowOn(defaultDispatcher)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val watchProgressState: StateFlow<WatchProgressUiState?> = combine(
        mediaItem,
        episodesForSeasons,
        watchedSetFlow
    ) { details, episodes, watchedSet ->
        if (episodes.isEmpty() || details == null) null
        else {
            val now = Instant.now()
            var watchedCount = 0
            var airedCount = 0
            var notAiredCount = 0

            for (ep in episodes) {
                val isAired = ep.date != null && ep.date.isBefore(now)
                if (isAired) {
                    airedCount++
                } else {
                    notAiredCount++
                }

                val isWatched = watchedSet.contains(Pair(ep.season, ep.episodeNumber))
                if (isWatched) {
                    watchedCount++
                }
            }

            val percentage = if (airedCount > 0) (watchedCount.toFloat() / airedCount).coerceAtMost(1f) else 0f
            WatchProgressUiState(
                watchedCount = watchedCount,
                totalAiredCount = airedCount,
                percentage = percentage,
                notAiredCount = notAiredCount,
            )
        }
    }
    .flowOn(defaultDispatcher)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val watchlistStatusMap: StateFlow<Map<Int, WatchlistStatus>> = mediaItem
        .map { details ->
            if (details == null) {
                emptyList()
            } else {
                buildList {
                    addAll(details.relations.map { it.relatedSimklId })
                    addAll(details.similarItems.map { it.similarSimklId })
                    addAll(details.recommendedItems.map { it.recommendedSimklId })
                }
            }
        }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(emptyMap())
            } else {
                watchlistItemDao.getWatchlistItemsFlow(ids)
                    .map { list ->
                        list.associateBy(
                            keySelector = { it.simklId },
                            valueTransform = { it.status ?: WatchlistStatus.PLAN_TO_WATCH }
                        )
                    }
            }
        }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val uiPreferences: StateFlow<UiPreferences> = uiRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiPreferences())

    val customSearchLinks: StateFlow<List<CustomSearchLink>> = customSearchLinkRepository.customSearchLinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isUpdatingStatus = MutableStateFlow(false)
    val isUpdatingStatus: StateFlow<Boolean> = _isUpdatingStatus.asStateFlow()

    val autoDownloadPreferences = autoDownloadRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutoDownloadPreferences())

    val notificationPreferences = notificationRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationPreferences())

    val isTorrentServiceInstalled = torrentServiceHelper.isInstalled

    val downloadSubdirectories: StateFlow<List<String>> = downloadRepository.downloadSubdirectories

    val downloads: StateFlow<Map<String, DownloadProgress>> = torrentServiceHelper.downloads

    val episodeListUiState: StateFlow<EpisodeListUiState> = combine(
        mediaItem,
        episodesForSeasons,
        calendarItemsFlow,
        watchedSetFlow,
        updatingWatchStatusKeys
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val details = args[0] as MediaItemWithDetails?
        @Suppress("UNCHECKED_CAST")
        val episodes = args[1] as List<MediaEpisode>
        @Suppress("UNCHECKED_CAST")
        val calendarItems = args[2] as List<CalendarItemWithWatchlist>
        @Suppress("UNCHECKED_CAST")
        val watchedSet = args[3] as Set<Pair<Int, Int>>
        @Suppress("UNCHECKED_CAST")
        val updatingKeys = args[4] as Set<String>

        if (details == null) {
            EpisodeListUiState()
        } else {
            val mediaType = details.mediaType
            val calendarItemMap = calendarItems.associateBy { Pair(it.season ?: 1, it.episodeNumber ?: 1) }

            val grouped = episodes.groupBy { it.season }
            val sortedSeasons = grouped.keys.sortedDescending()
            val items = ArrayList<EpisodeListItemUiModel>()
            val isSingleSeason = sortedSeasons.size == 1
            var singleSeasonHeader: EpisodeListItemUiModel.SeasonHeader? = null

            for (season in sortedSeasons) {
                val seasonEpisodes = grouped[season] ?: continue
                val sortedEpisodes = seasonEpisodes.sortedByDescending { it.episodeNumber }

                var isSeasonFullyWatched = sortedEpisodes.isNotEmpty()
                var firstMediaStatus: MediaStatus? = null
                var hasDifferentMediaStatus = false
                var hasSearchableEpisode = false
                var hasCalendarItems = false
                val isSeasonUpdatingWatchStatus = updatingKeys.contains("${details.mediaItem.simklId}_season_${season}")
                val seasonRows = ArrayList<EpisodeListItemUiModel.EpisodeRow>(sortedEpisodes.size)

                for (ep in sortedEpisodes) {
                    val epKey = Pair(ep.season, ep.episodeNumber)
                    val calItem = calendarItemMap[epKey]
                    val isEpWatched = watchedSet.contains(epKey)
                    if (!isEpWatched) {
                        isSeasonFullyWatched = false
                    }

                    if (calItem != null) {
                        hasCalendarItems = true
                        val status = calItem.mediaStatus
                        if (status.isSearchable) {
                            hasSearchableEpisode = true
                        }
                        if (firstMediaStatus == null) {
                            firstMediaStatus = status
                        } else if (status != firstMediaStatus) {
                            hasDifferentMediaStatus = true
                        }
                    }

                    val episodeCode = MediaFormatter.formatEpisodeCode(mediaType, ep.season, ep.episodeNumber)
                    val title = ep.title.takeIf { it.isNotBlank() } ?: "TBA"
                    val key = "${ep.simklId}_${ep.season}_${ep.episodeNumber}"
                    val isUpdatingWatchStatus = updatingKeys.contains(key)

                    seasonRows.add(
                        EpisodeListItemUiModel.EpisodeRow(
                            episodeKey = key,
                            season = ep.season,
                            episodeNumber = ep.episodeNumber,
                            title = title,
                            episodeCode = episodeCode,
                            date = ep.date,
                            calendarItem = calItem,
                            isWatched = isEpWatched,
                            isUpdatingWatchStatus = isUpdatingWatchStatus
                        )
                    )
                }

                val commonMediaStatus = if (hasCalendarItems && !hasDifferentMediaStatus) firstMediaStatus else null

                val headerModel = EpisodeListItemUiModel.SeasonHeader(
                    season = season,
                    totalEpisodes = sortedEpisodes.size,
                    isFullyWatched = isSeasonFullyWatched,
                    commonMediaStatus = commonMediaStatus,
                    hasSearchableEpisode = hasSearchableEpisode,
                    hasCalendarItems = hasCalendarItems,
                    isSeasonUpdatingWatchStatus = isSeasonUpdatingWatchStatus,
                    showTitleText = !isSingleSeason
                )

                if (isSingleSeason) {
                    singleSeasonHeader = headerModel
                } else {
                    items.add(headerModel)
                }
                items.addAll(seasonRows)
            }
            EpisodeListUiState(
                totalCount = episodes.size,
                items = items,
                singleSeasonHeader = singleSeasonHeader
            )
        }
    }
    .flowOn(defaultDispatcher)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EpisodeListUiState()
    )

    fun setManualSearchSeason(season: Int?) {
        _manualSearchSeasonNum.value = season
    }

    fun setMovieManualSearchOpen(open: Boolean) {
        _isMovieManualSearchOpen.value = open
    }

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

    fun refresh(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val currentId = simklIdFlow.value
                val typeStr = savedStateHandle.get<String>("mediaType")
                val type = MediaType.fromKey(typeStr)
                mediaRepository.refreshMediaItem(currentId, type)
                onResult(true, "Refreshed successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh media item")
                onResult(false, "Failed to refresh: ${e.localizedMessage ?: "Unknown error"}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

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

    private fun addUpdatingKey(key: String) {
        _updatingWatchStatusKeys.update { it + key }
    }

    private fun removeUpdatingKey(key: String) {
        _updatingWatchStatusKeys.update { it - key }
    }

    fun getTrackedWatchlistItemFlow(simklId: Int): Flow<TrackedWatchlistItem?> {
        return calendarRepository.getTrackedWatchlistItemFlow(simklId).flowOn(Dispatchers.IO)
    }

    fun getSettingForSimklIdFlow(simklId: Int): Flow<NotificationSetting?> {
        return notificationSettingRepository.getSettingForSimklIdFlow(simklId).flowOn(Dispatchers.IO)
    }

    fun getItemDownloadSettingsFlow(simklId: Int): Flow<ItemDownloadSettings?> {
        return downloadRepository.getItemDownloadSettingsFlow(simklId)
    }

    fun saveItemDownloadSettings(settings: ItemDownloadSettings) {
        viewModelScope.launch {
            downloadRepository.saveItemDownloadSettings(settings)
        }
    }

    suspend fun detectTorrents(simklId: Int, customTitle: String): List<SearchResultItem> {
        return downloadRepository.detectTorrents(simklId, customTitle)
    }

    suspend fun searchSeasonTorrents(simklId: Int, season: Int, customTitle: String? = null): List<SearchResultItem> {
        return downloadRepository.searchSeason(simklId, season, customTitle)
    }

    fun downloadSeasonResult(
        simklId: Int,
        season: Int,
        resultItem: SearchResultItem,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val result = downloadRepository.downloadSeasonResult(simklId, season, resultItem)
            result.onSuccess {
                onResult(true, "Season download started")
            }.onFailure {
                onResult(false, it.message ?: "Error starting season download")
            }
        }
    }

    fun downloadMovieResult(
        resultItem: SearchResultItem,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val movieItem = targetMovieCalendarItem.value
            if (movieItem == null) {
                onResult(false, "No calendar item available for this movie")
                return@launch
            }
            val dlResult = downloadRepository.downloadItemResult(movieItem, resultItem)
            dlResult.onSuccess {
                onResult(true, "Movie download started")
            }.onFailure {
                onResult(false, it.message ?: "Error starting movie download")
            }
        }
    }

    fun markEpisodeWatched(
        simklId: Int,
        season: Int?,
        episodeNumber: Int,
        mediaType: MediaType,
        showTitle: String? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val updateKey = "${simklId}_${season}_${episodeNumber}"
            addUpdatingKey(updateKey)
            val result = watchHistoryRepository.markEpisodeWatched(
                simklId = simklId,
                season = season,
                episodeNumber = episodeNumber,
                mediaType = mediaType
            )
            removeUpdatingKey(updateKey)
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

    fun markEpisodeUnwatched(
        simklId: Int,
        season: Int?,
        episodeNumber: Int,
        mediaType: MediaType,
        showTitle: String? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val updateKey = "${simklId}_${season}_${episodeNumber}"
            addUpdatingKey(updateKey)
            val result = watchHistoryRepository.markEpisodeUnwatched(
                simklId = simklId,
                season = season,
                episodeNumber = episodeNumber,
                mediaType = mediaType
            )
            removeUpdatingKey(updateKey)
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

    fun markSeasonWatched(
        simklId: Int,
        season: Int,
        mediaType: MediaType,
        showTitle: String? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val updateKey = "${simklId}_season_${season}"
            addUpdatingKey(updateKey)
            val result = watchHistoryRepository.markSeasonWatched(
                simklId = simklId,
                season = season,
                mediaType = mediaType
            )
            removeUpdatingKey(updateKey)
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

    fun markSeasonUnwatched(
        simklId: Int,
        season: Int,
        mediaType: MediaType,
        showTitle: String? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val updateKey = "${simklId}_season_${season}"
            addUpdatingKey(updateKey)
            val result = watchHistoryRepository.markSeasonUnwatched(
                simklId = simklId,
                season = season,
                mediaType = mediaType
            )
            removeUpdatingKey(updateKey)
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

    fun markMovieWatched(
        simklId: Int,
        showTitle: String? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val updateKey = "${simklId}_movie"
            addUpdatingKey(updateKey)
            val result = watchHistoryRepository.markMovieWatched(simklId = simklId)
            removeUpdatingKey(updateKey)
            if (result.isSuccess) {
                val message = MediaFormatter.formatMovieWatchedToast(showTitle)
                onResult(true, message)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to mark movie as watched"
                onResult(false, errorMsg)
            }
        }
    }

    fun markMovieUnwatched(
        simklId: Int,
        showTitle: String? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val updateKey = "${simklId}_movie"
            addUpdatingKey(updateKey)
            val result = watchHistoryRepository.markMovieUnwatched(simklId = simklId)
            removeUpdatingKey(updateKey)
            if (result.isSuccess) {
                val message = MediaFormatter.formatMovieUnwatchedToast(showTitle)
                onResult(true, message)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to mark movie as unwatched"
                onResult(false, errorMsg)
            }
        }
    }

    fun toggleNotification(simklId: Int, notifyEpisode: Boolean, notifySeasonFinished: Boolean) {
        viewModelScope.launch {
            notificationSettingRepository.toggleNotificationSetting(
                simklId = simklId,
                notifyEveryEpisode = notifyEpisode,
                notifyAiredLastEpisode = notifySeasonFinished
            )
        }
    }

    fun updateMediaStatus(primaryKey: String, status: MediaStatus) {
        viewModelScope.launch {
            calendarRepository.updateMediaStatus(primaryKey, status)
        }
    }

    fun updateSeasonMediaStatus(simklId: Int, season: Int, status: MediaStatus) {
        viewModelScope.launch {
            calendarRepository.updateSeasonMediaStatus(simklId, season, status)
        }
    }
}
