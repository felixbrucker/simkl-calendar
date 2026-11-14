package com.felixbrucker.simklcalendar.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.database.MediaItem
import com.felixbrucker.simklcalendar.data.database.WatchlistItem
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.WatchlistStatus
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
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

@HiltViewModel
class MediaItemDetailViewModel @Inject constructor(
    mediaRepository: MediaRepository,
    private val syncRepository: SyncRepository,
    private val authenticatedSimklApiService: AuthenticatedSimklApiService,
    calendarRepository: CalendarRepository,
    customSearchLinkRepository: CustomSearchLinkRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val simklId: Int = savedStateHandle.get<Int>("simklId") ?: 0
    private val mediaTypeStr: String? = savedStateHandle.get<String>("mediaType")
    private val mediaType: MediaType = MediaType.fromKey(mediaTypeStr)

    private val initialItem = MediaItemHolder.getAndClear()

    val mediaItem: StateFlow<MediaItem?> = mediaRepository.getMediaItemStream(simklId, mediaType)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = initialItem
        )

    val watchlistItem: StateFlow<WatchlistItem?> = calendarRepository.getWatchlistItemFlow(simklId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val customSearchLinks: StateFlow<List<CustomSearchLink>> = customSearchLinkRepository.customSearchLinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
