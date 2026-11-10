package com.felixbrucker.simklcalendar.data.repository

import com.felixbrucker.simklcalendar.data.database.AnimeMetadata
import com.felixbrucker.simklcalendar.data.database.CachedMediaItem
import com.felixbrucker.simklcalendar.data.database.CachedMediaItemDao
import com.felixbrucker.simklcalendar.data.database.CachedSimilarMediaItem
import com.felixbrucker.simklcalendar.data.database.MovieMetadata
import com.felixbrucker.simklcalendar.data.database.SeriesMetadata
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.TrendingDao
import com.felixbrucker.simklcalendar.data.database.TrendingEntry
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.model.AnimeType
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.SeriesStatus
import com.felixbrucker.simklcalendar.data.model.WatchlistStatus
import com.felixbrucker.simklcalendar.data.network.AuthenticatedSimklApiService
import com.felixbrucker.simklcalendar.data.network.PublicSimklApiService
import com.felixbrucker.simklcalendar.data.network.SearchItemDto
import com.felixbrucker.simklcalendar.data.network.SimklAnimeDetailResponse
import com.felixbrucker.simklcalendar.data.network.SimklEpisodeResponse
import com.felixbrucker.simklcalendar.data.network.SimklIds
import com.felixbrucker.simklcalendar.data.network.SimklMovieDetailResponse
import com.felixbrucker.simklcalendar.data.network.SimklSimilarItem
import com.felixbrucker.simklcalendar.data.network.SimklTvDetailResponse
import com.felixbrucker.simklcalendar.data.network.SyncAddToListMovieItem
import com.felixbrucker.simklcalendar.data.network.SyncAddToListRequest
import com.felixbrucker.simklcalendar.data.network.SyncAddToListShowItem
import com.felixbrucker.simklcalendar.data.network.SyncHistoryMovieItem
import com.felixbrucker.simklcalendar.data.network.SyncHistoryRequest
import com.felixbrucker.simklcalendar.data.network.SyncHistoryShowItem
import com.felixbrucker.simklcalendar.data.network.TrendingItemDto
import com.felixbrucker.simklcalendar.data.preferences.NotificationRepository
import com.felixbrucker.simklcalendar.data.preferences.SyncMetadataRepository
import com.felixbrucker.simklcalendar.data.util.DateUtil
import com.felixbrucker.simklcalendar.receiver.notification.NotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaItemRepository @Inject constructor(
    private val publicSimklApiService: PublicSimklApiService,
    private val authenticatedSimklApiService: AuthenticatedSimklApiService,
    private val cachedMediaItemDao: CachedMediaItemDao,
    private val trendingDao: TrendingDao,
    private val watchlistDao: WatchlistDao,
    private val syncMetadataRepo: SyncMetadataRepository,
    private val notificationRepo: NotificationRepository,
    private val notificationManager: NotificationManager
) {
    companion object {
        private const val TAG = "MediaItemRepository"
        private const val ONE_HOUR_MILLIS = 3600_000L
    }

    private val apiSemaphore = Semaphore(16)

    fun getTrendingItemsFlow(mediaType: MediaType): Flow<List<CachedMediaItem>> {
        return trendingDao.getTrendingItemsFlow(mediaType)
    }

    fun getCachedItemFlow(simklId: Int): Flow<CachedMediaItem?> {
        return cachedMediaItemDao.getCachedMediaItemFlow(simklId)
    }

    suspend fun getCachedItem(simklId: Int): CachedMediaItem? = withContext(Dispatchers.IO) {
        cachedMediaItemDao.getCachedMediaItem(simklId)
    }

    suspend fun syncTrending(force: Boolean = false): List<Int> = withContext(Dispatchers.IO) {
        val nowMillis = System.currentTimeMillis()
        val types = listOf(MediaType.MOVIE, MediaType.TV, MediaType.ANIME)

        // Clean up expired cached items (older than 6 months)
        val sixMonthsAgo = Instant.now().minus(180, ChronoUnit.DAYS)
        cachedMediaItemDao.deleteExpiredItems(sixMonthsAgo)

        val previousTrendingIds = trendingDao.getAllTrendingSimklIds().toSet()
        val trackedWatchlistIds = watchlistDao.getAllTrackedIds().toSet()
        val syncMetadata = syncMetadataRepo.preferencesFlow.first()
        val notifyTrending = notificationRepo.preferencesFlow.first().notifyTrendingItems

        // Parallelize trending fetching across media types
        val results = coroutineScope {
            types.map { type ->
                async {
                    val endpointType = when (type) {
                        MediaType.MOVIE -> "movies"
                        MediaType.TV -> "tv"
                        MediaType.ANIME -> "anime"
                    }
                    val lastModifiedPrefKey = "trending_last_mod_$endpointType"
                    val lastModifiedHeaderKey = "trending_header_$endpointType"

                    val lastModifiedTimestamp = if (force) 0L else syncMetadata.calendarLastModifiedAt[lastModifiedPrefKey] ?: 0L
                    val savedHeader = if (force) null else syncMetadata.calendarLastModifiedHeader[lastModifiedHeaderKey]

                    // Early exit when last trending sync was less than 1h ago
                    val isOverOneHour = (nowMillis - lastModifiedTimestamp) >= ONE_HOUR_MILLIS
                    if (lastModifiedTimestamp > 0L && !isOverOneHour) {
                        Timber.tag(TAG).d("Trending $endpointType synced less than 1h ago, skipping.")
                        return@async null
                    }

                    try {
                        val response = apiSemaphore.withPermit {
                            publicSimklApiService.getTrendingToday(
                                type = endpointType,
                                ifModifiedSince = savedHeader
                            )
                        }

                        if (response.code() == 304) {
                            Timber.tag(TAG).d("Trending $endpointType not modified (HTTP 304)")
                            syncMetadataRepo.setCalendarLastModifiedAt(lastModifiedPrefKey, nowMillis)
                            return@async null
                        }

                        if (!response.isSuccessful) {
                            Timber.tag(TAG).w("HTTP ${response.code()} for trending $endpointType")
                            return@async null
                        }

                        val body = response.body() ?: return@async null
                        val responseLastModifiedHeader = response.headers()["Last-Modified"]
                        val parsedHeaderMillis = DateUtil.parseHttpDateToMillis(responseLastModifiedHeader) ?: nowMillis
                        syncMetadataRepo.setCalendarLastModifiedAt(lastModifiedPrefKey, parsedHeaderMillis)
                        if (!responseLastModifiedHeader.isNullOrBlank()) {
                            syncMetadataRepo.setCalendarLastModifiedHeader(lastModifiedHeaderKey, responseLastModifiedHeader)
                        }

                        val trendingEntries = body.mapIndexed { index, dto ->
                            TrendingEntry(
                                mediaType = type,
                                rank = index + 1,
                                simklId = dto.ids.simkl
                            )
                        }

                        // Collect new items for detail fetching & notifications
                        val newItemsInType = body.mapIndexedNotNull { index, dto ->
                            val simklId = dto.ids.simkl
                            if (simklId !in previousTrendingIds) {
                                Triple(simklId, index + 1, dto)
                            } else null
                        }

                        Triple(type, trendingEntries, newItemsInType)
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error syncing trending for $endpointType")
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val allNewSimklIds = mutableListOf<Int>()

        for ((type, entries, newItems) in results) {
            trendingDao.replaceTrendingEntriesForType(type, entries)

            // For trending items, fetch their full details (in parallel up to 16) to populate cached_media_items
            val itemsToFetch = entries.map { it.simklId }
            coroutineScope {
                itemsToFetch.map { simklId ->
                    async {
                        apiSemaphore.withPermit {
                            getOrFetchItemDetails(simklId, type, forceRefresh = false)
                        }
                    }
                }.awaitAll()
            }

            // Dispatch individual notification per new trending item with complete details (only if not on our list)
            if (notifyTrending) {
                for ((simklId, rank, dto) in newItems) {
                    allNewSimklIds.add(simklId)
                    if (simklId !in trackedWatchlistIds) {
                        try {
                            notificationManager.showTrendingItemNotification(
                                simklId = simklId,
                                title = dto.title,
                                mediaType = type,
                                poster = dto.poster,
                                rank = rank
                            )
                        } catch (e: Exception) {
                            Timber.tag(TAG).e(e, "Error sending trending notification for $simklId")
                        }
                    }
                }
            } else {
                allNewSimklIds.addAll(newItems.map { it.first })
            }
        }

        allNewSimklIds
    }

    suspend fun search(
        query: String,
        mediaType: MediaType? = null,
        page: Int = 1,
        limit: Int = 20
    ): List<SearchItemDto> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val typeParam = mediaType?.let {
            when (it) {
                MediaType.MOVIE -> "movie"
                MediaType.TV -> "tv"
                MediaType.ANIME -> "anime"
            }
        } ?: "all"

        try {
            apiSemaphore.withPermit {
                publicSimklApiService.search(
                    type = typeParam,
                    query = query.trim(),
                    extended = "full",
                    page = page,
                    limit = limit
                )
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error searching Simkl for '$query'")
            emptyList()
        }
    }

    suspend fun getOrFetchItemDetails(
        simklId: Int,
        mediaType: MediaType,
        forceRefresh: Boolean = false
    ): CachedMediaItem? = withContext(Dispatchers.IO) {
        val existing = cachedMediaItemDao.getCachedMediaItem(simklId)
        val oneDayAgo = Instant.now().minus(24, ChronoUnit.HOURS)
        val shouldUpdate = existing == null || forceRefresh || existing.lastUpdatedAt.isBefore(oneDayAgo)

        if (!shouldUpdate && existing?.description != null) {
            return@withContext existing
        }

        try {
            when (mediaType) {
                MediaType.MOVIE -> {
                    val detail = apiSemaphore.withPermit {
                        publicSimklApiService.getMovieDetails(simklId)
                    }
                    val relDate = detail.released?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    val dvdStr = detail.extractDigitalOrDvdReleaseDate()
                    val dvdDate = dvdStr?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    val similars = detail.similar?.map {
                        CachedSimilarMediaItem(
                            simklId = it.ids.simkl,
                            title = it.title,
                            year = it.year,
                            poster = it.poster,
                            mediaType = MediaType.fromKey(it.type)
                        )
                    } ?: existing?.similarItems ?: emptyList()

                    val item = CachedMediaItem(
                        simklId = simklId,
                        mediaType = MediaType.MOVIE,
                        title = detail.title,
                        year = detail.year,
                        description = detail.overview,
                        poster = detail.poster,
                        genres = detail.genres ?: emptyList(),
                        rating = detail.ratings?.simkl?.rating?.toFloat(),
                        similarItems = similars,
                        lastUpdatedAt = Instant.now(),
                        movieMetadata = MovieMetadata(
                            releaseDate = relDate,
                            dvdDate = dvdDate,
                            budget = detail.budget,
                            boxOffice = detail.revenue
                        )
                    )
                    cachedMediaItemDao.upsert(item)
                    item
                }
                MediaType.TV -> {
                    val detail = apiSemaphore.withPermit {
                        publicSimklApiService.getTvDetails(simklId)
                    }
                    val endYr = parseEndYear(detail.yearStartEnd)
                    val similars = detail.similar?.map {
                        CachedSimilarMediaItem(
                            simklId = it.ids.simkl,
                            title = it.title,
                            year = it.year,
                            poster = it.poster,
                            mediaType = MediaType.fromKey(it.type)
                        )
                    } ?: existing?.similarItems ?: emptyList()

                    val item = CachedMediaItem(
                        simklId = simklId,
                        mediaType = MediaType.TV,
                        title = detail.title,
                        year = detail.year,
                        description = detail.overview,
                        poster = detail.poster,
                        genres = detail.genres ?: emptyList(),
                        rating = detail.ratings?.simkl?.rating?.toFloat(),
                        similarItems = similars,
                        lastUpdatedAt = Instant.now(),
                        seriesMetadata = SeriesMetadata(
                            endYear = endYr,
                            totalEpisodes = detail.totalEpisodes,
                            seriesStatus = SeriesStatus.fromKey(detail.status)
                        )
                    )
                    cachedMediaItemDao.upsert(item)
                    item
                }
                MediaType.ANIME -> {
                    val detail = apiSemaphore.withPermit {
                        publicSimklApiService.getAnimeDetails(simklId)
                    }
                    val endYr = parseEndYear(detail.yearStartEnd)
                    val (seasonYr, seasonQtr) = parseSeasonNameYear(detail.seasonNameYear)
                    val similars = detail.similar?.map {
                        CachedSimilarMediaItem(
                            simklId = it.ids.simkl,
                            title = it.title,
                            year = it.year,
                            poster = it.poster,
                            mediaType = MediaType.fromKey(it.type)
                        )
                    } ?: existing?.similarItems ?: emptyList()

                    val item = CachedMediaItem(
                        simklId = simklId,
                        mediaType = MediaType.ANIME,
                        title = detail.title,
                        year = detail.year,
                        description = detail.overview,
                        poster = detail.poster,
                        genres = detail.genres ?: emptyList(),
                        rating = detail.ratings?.simkl?.rating?.toFloat(),
                        similarItems = similars,
                        lastUpdatedAt = Instant.now(),
                        animeMetadata = AnimeMetadata(
                            animeType = AnimeType.fromKey(detail.animeType),
                            endYear = endYr,
                            totalEpisodes = detail.totalEpisodes,
                            seasonYear = seasonYr,
                            seasonQuarter = seasonQtr
                        )
                    )
                    cachedMediaItemDao.upsert(item)
                    item
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error fetching details for $mediaType $simklId")
            existing
        }
    }

    suspend fun getEpisodes(simklId: Int, mediaType: MediaType): List<SimklEpisodeResponse> = withContext(Dispatchers.IO) {
        try {
            apiSemaphore.withPermit {
                when (mediaType) {
                    MediaType.TV -> publicSimklApiService.getTvEpisodes(simklId)
                    MediaType.ANIME -> publicSimklApiService.getAnimeEpisodes(simklId)
                    MediaType.MOVIE -> emptyList()
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error fetching episodes for $simklId")
            emptyList()
        }
    }

    suspend fun updateWatchlistStatus(
        item: CachedMediaItem,
        newStatus: WatchlistStatus
    ): Boolean = withContext(Dispatchers.IO) {
        val existingTracked = watchlistDao.getItem(item.simklId)
        val simklIds = SimklIds(simkl = item.simklId)

        try {
            if (newStatus == WatchlistStatus.COMPLETED && existingTracked != null) {
                val historyRequest = when (item.mediaType) {
                    MediaType.MOVIE -> SyncHistoryRequest(movies = listOf(SyncHistoryMovieItem(ids = simklIds)))
                    MediaType.TV -> SyncHistoryRequest(shows = listOf(SyncHistoryShowItem(ids = simklIds)))
                    MediaType.ANIME -> SyncHistoryRequest(anime = listOf(SyncHistoryShowItem(ids = simklIds)))
                }
                apiSemaphore.withPermit {
                    authenticatedSimklApiService.markHistoryWatched(historyRequest)
                }
            } else {
                val addRequest = when (item.mediaType) {
                    MediaType.MOVIE -> SyncAddToListRequest(
                        movies = listOf(SyncAddToListMovieItem(to = newStatus.key, ids = simklIds))
                    )
                    MediaType.TV -> SyncAddToListRequest(
                        shows = listOf(SyncAddToListShowItem(to = newStatus.key, ids = simklIds))
                    )
                    MediaType.ANIME -> SyncAddToListRequest(
                        anime = listOf(SyncAddToListShowItem(to = newStatus.key, ids = simklIds))
                    )
                }
                apiSemaphore.withPermit {
                    authenticatedSimklApiService.addToList(addRequest)
                }
            }

            // Update local DB
            if (existingTracked != null) {
                watchlistDao.updateItems(listOf(existingTracked.copy(status = newStatus)))
            } else {
                watchlistDao.insertItems(
                    listOf(
                        TrackedWatchlistItem(
                            simklId = item.simklId,
                            type = item.mediaType,
                            title = item.title,
                            poster = item.poster,
                            rating = item.rating?.toDouble(),
                            status = newStatus
                        )
                    )
                )
            }
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error updating watchlist status for ${item.simklId}")
            false
        }
    }

    private fun parseEndYear(yearStartEnd: String?): Int? {
        if (yearStartEnd.isNullOrBlank()) return null
        val parts = yearStartEnd.split("-")
        if (parts.size >= 2) {
            return parts[1].trim().toIntOrNull()
        }
        return null
    }

    private fun parseSeasonNameYear(seasonNameYear: String?): Pair<Int?, String?> {
        if (seasonNameYear.isNullOrBlank()) return Pair(null, null)
        val parts = seasonNameYear.split(" ")
        return if (parts.size >= 2) {
            val qtr = parts[0]
            val yr = parts[1].toIntOrNull()
            Pair(yr, qtr)
        } else {
            Pair(null, null)
        }
    }
}
