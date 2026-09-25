package com.felixbrucker.simklcalendar.data.repository

import timber.log.Timber
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.WatchedEpisode
import com.felixbrucker.simklcalendar.data.database.WatchedEpisodeDao
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.network.AuthenticatedSimklApiService
import com.felixbrucker.simklcalendar.data.network.SimklIds
import com.felixbrucker.simklcalendar.data.network.SyncHistoryEpisodeItem
import com.felixbrucker.simklcalendar.data.network.SyncHistoryMovieItem
import com.felixbrucker.simklcalendar.data.network.SyncHistoryRequest
import com.felixbrucker.simklcalendar.data.network.SyncHistorySeasonItem
import com.felixbrucker.simklcalendar.data.network.SyncHistoryShowItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchHistoryRepository @Inject constructor(
    private val calendarDao: CalendarItemDao,
    private val watchedDao: WatchedEpisodeDao,
    private val authenticatedSimklApiService: AuthenticatedSimklApiService
) {
    val watchedEpisodes: Flow<List<WatchedEpisode>> = watchedDao.getAllWatchedEpisodesFlow()

    suspend fun markEpisodeWatched(
        simklId: Int,
        season: Int?,
        episodeNumber: Int,
        mediaType: MediaType
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val effectiveSeason = season ?: 1

            val request = if (mediaType == MediaType.ANIME) {
                SyncHistoryRequest(
                    anime = listOf(
                        SyncHistoryShowItem(
                            ids = SimklIds(simkl = simklId),
                            seasons = listOf(
                                SyncHistorySeasonItem(
                                    number = effectiveSeason,
                                    episodes = listOf(
                                        SyncHistoryEpisodeItem(number = episodeNumber)
                                    )
                                )
                            )
                        )
                    )
                )
            } else {
                SyncHistoryRequest(
                    shows = listOf(
                        SyncHistoryShowItem(
                            ids = SimklIds(simkl = simklId),
                            seasons = listOf(
                                SyncHistorySeasonItem(
                                    number = effectiveSeason,
                                    episodes = listOf(
                                        SyncHistoryEpisodeItem(number = episodeNumber)
                                    )
                                )
                            )
                        )
                    )
                )
            }

            authenticatedSimklApiService.markHistoryWatched(
                request = request
            )

            // Update local database immediately
            val now = Instant.now()
            watchedDao.insertWatchedEpisodes(
                listOf(
                    WatchedEpisode(
                        simklId = simklId,
                        season = effectiveSeason,
                        episodeNumber = episodeNumber,
                        watchedAt = now
                    )
                )
            )
            calendarDao.markEpisodeWatched(
                simklId = simklId,
                season = season,
                episodeNumber = episodeNumber,
                watchedAt = now
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag("WatchHistoryRepository").e(e, "Failed to mark episode S${season}E${episodeNumber} as watched for simklId $simklId")
            Result.failure(e)
        }
    }

    suspend fun markSeasonWatched(
        simklId: Int,
        season: Int,
        mediaType: MediaType
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {

            // Determine if show should be marked as "completed"
            val showCalendarItems = calendarDao.getItemsForSimklId(simklId)
            val showWatchedItems = watchedDao.getWatchedEpisodesForShow(simklId)

            val seasonsSet = mutableSetOf<Int>()
            showCalendarItems.forEach { item -> item.season?.let { if (it > 0) seasonsSet.add(it) } }
            showWatchedItems.forEach { w -> if (w.season > 0) seasonsSet.add(w.season) }
            seasonsSet.add(season)
            val sortedSeasons = seasonsSet.sorted()
            val isLastSeason = sortedSeasons.isNotEmpty() && season == sortedSeasons.last()
            val prevSeasons = sortedSeasons.filter { it < season }
            val allPrevWatched = prevSeasons.all { sNum ->
                val epInSeason = showCalendarItems.filter { (it.season ?: 1) == sNum }
                val watchedInSeason = showWatchedItems.filter { it.season == sNum }
                if (epInSeason.isNotEmpty()) {
                    epInSeason.all { it.isWatched }
                } else {
                    watchedInSeason.isNotEmpty()
                }
            }
            val shouldMarkCompleted = isLastSeason && allPrevWatched

            val statusValue = if (shouldMarkCompleted) "completed" else null
            Timber.tag("WatchHistoryRepository").d("Marking season $season as watched for simklId $simklId (isCompleted=$shouldMarkCompleted, status=$statusValue)")

            val request = if (mediaType == MediaType.ANIME) {
                SyncHistoryRequest(
                    anime = listOf(
                        SyncHistoryShowItem(
                            ids = SimklIds(simkl = simklId),
                            status = statusValue,
                            seasons = listOf(
                                SyncHistorySeasonItem(
                                    number = season
                                )
                            )
                        )
                    )
                )
            } else {
                SyncHistoryRequest(
                    shows = listOf(
                        SyncHistoryShowItem(
                            ids = SimklIds(simkl = simklId),
                            status = statusValue,
                            seasons = listOf(
                                SyncHistorySeasonItem(
                                    number = season
                                )
                            )
                        )
                    )
                )
            }

            authenticatedSimklApiService.markHistoryWatched(
                request = request
            )

            // Update local database immediately
            val now = Instant.now()
            val seasonEpisodes = showCalendarItems.filter { (it.season ?: 1) == season }

            if (seasonEpisodes.isNotEmpty()) {
                val newWatched = seasonEpisodes.mapNotNull { item ->
                    item.episodeNumber?.let { epNum ->
                        WatchedEpisode(
                            simklId = simklId,
                            season = season,
                            episodeNumber = epNum,
                            watchedAt = now
                        )
                    }
                }
                if (newWatched.isNotEmpty()) {
                    watchedDao.insertWatchedEpisodes(newWatched)
                }
            }

            calendarDao.markSeasonWatched(
                simklId = simklId,
                season = season,
                watchedAt = now
            )

            Result.success(shouldMarkCompleted)
        } catch (e: Exception) {
            Timber.tag("WatchHistoryRepository").e(e, "Failed to mark season $season as watched for simklId $simklId")
            Result.failure(e)
        }
    }

    suspend fun markMovieWatched(
        simklId: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {

            val request = SyncHistoryRequest(
                movies = listOf(
                    SyncHistoryMovieItem(
                        ids = SimklIds(simkl = simklId)
                    )
                )
            )

            authenticatedSimklApiService.markHistoryWatched(
                request = request
            )

            val now = Instant.now()
            calendarDao.markMovieWatched(simklId = simklId, watchedAt = now)

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag("WatchHistoryRepository").e(e, "Failed to mark movie as watched for simklId $simklId")
            Result.failure(e)
        }
    }

    suspend fun markEpisodeUnwatched(
        simklId: Int,
        season: Int?,
        episodeNumber: Int,
        mediaType: MediaType
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val effectiveSeason = season ?: 1

            val request = if (mediaType == MediaType.ANIME) {
                SyncHistoryRequest(
                    anime = listOf(
                        SyncHistoryShowItem(
                            ids = SimklIds(simkl = simklId),
                            seasons = listOf(
                                SyncHistorySeasonItem(
                                    number = effectiveSeason,
                                    episodes = listOf(
                                        SyncHistoryEpisodeItem(number = episodeNumber)
                                    )
                                )
                            )
                        )
                    )
                )
            } else {
                SyncHistoryRequest(
                    shows = listOf(
                        SyncHistoryShowItem(
                            ids = SimklIds(simkl = simklId),
                            seasons = listOf(
                                SyncHistorySeasonItem(
                                    number = effectiveSeason,
                                    episodes = listOf(
                                        SyncHistoryEpisodeItem(number = episodeNumber)
                                    )
                                )
                            )
                        )
                    )
                )
            }

            authenticatedSimklApiService.markHistoryUnwatched(
                request = request
            )

            // Revert local changes immediately
            watchedDao.deleteWatchedEpisode(
                simklId = simklId,
                season = effectiveSeason,
                episodeNumber = episodeNumber
            )
            calendarDao.markEpisodeWatched(
                simklId = simklId,
                season = season,
                episodeNumber = episodeNumber,
                watchedAt = null
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag("WatchHistoryRepository").e(e, "Failed to mark episode S${season}E${episodeNumber} as unwatched for simklId $simklId")
            Result.failure(e)
        }
    }

    suspend fun markSeasonUnwatched(
        simklId: Int,
        season: Int,
        mediaType: MediaType
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {

            val request = if (mediaType == MediaType.ANIME) {
                SyncHistoryRequest(
                    anime = listOf(
                        SyncHistoryShowItem(
                            ids = SimklIds(simkl = simklId),
                            seasons = listOf(
                                SyncHistorySeasonItem(
                                    number = season
                                )
                            )
                        )
                    )
                )
            } else {
                SyncHistoryRequest(
                    shows = listOf(
                        SyncHistoryShowItem(
                            ids = SimklIds(simkl = simklId),
                            seasons = listOf(
                                SyncHistorySeasonItem(
                                    number = season
                                )
                            )
                        )
                    )
                )
            }

            authenticatedSimklApiService.markHistoryUnwatched(
                request = request
            )

            // Revert local changes immediately
            watchedDao.deleteWatchedSeason(
                simklId = simklId,
                season = season
            )

            calendarDao.markSeasonWatched(
                simklId = simklId,
                season = season,
                watchedAt = null
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag("WatchHistoryRepository").e(e, "Failed to mark season $season as unwatched for simklId $simklId")
            Result.failure(e)
        }
    }

    suspend fun markMovieUnwatched(
        simklId: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {

            val request = SyncHistoryRequest(
                movies = listOf(
                    SyncHistoryMovieItem(
                        ids = SimklIds(simkl = simklId)
                    )
                )
            )

            authenticatedSimklApiService.markHistoryUnwatched(
                request = request
            )

            calendarDao.markMovieWatched(simklId = simklId, watchedAt = null)

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag("WatchHistoryRepository").e(e, "Failed to mark movie as unwatched for simklId $simklId")
            Result.failure(e)
        }
    }
}
