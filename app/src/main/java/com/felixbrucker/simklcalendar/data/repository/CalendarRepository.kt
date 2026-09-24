package com.felixbrucker.simklcalendar.data.repository

import timber.log.Timber
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.WatchedEpisode
import com.felixbrucker.simklcalendar.data.database.WatchedEpisodeDao
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.network.AuthenticatedSimklApiService
import com.felixbrucker.simklcalendar.data.network.SimklIds
import com.felixbrucker.simklcalendar.data.network.SyncHistoryEpisodeItem
import com.felixbrucker.simklcalendar.data.network.SyncHistoryMovieItem
import com.felixbrucker.simklcalendar.data.network.SyncHistoryRequest
import com.felixbrucker.simklcalendar.data.network.SyncHistorySeasonItem
import com.felixbrucker.simklcalendar.data.network.SyncHistoryShowItem
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRepository @Inject constructor(
    private val calendarDao: CalendarItemDao,
    private val watchedDao: WatchedEpisodeDao,
    private val itemDownloadSettingsDao: ItemDownloadSettingsDao,
    private val authenticatedSimklApiService: AuthenticatedSimklApiService,
    private val autoDownloadRepo: AutoDownloadRepository
) {
    val calendarItems: Flow<List<CalendarItemWithWatchlist>> = calendarDao.getAllCalendarItems()
    val watchedEpisodes: Flow<List<WatchedEpisode>> = watchedDao.getAllWatchedEpisodesFlow()

    suspend fun updateMediaStatus(primaryKey: String, status: MediaStatus) = withContext(Dispatchers.IO) {
        calendarDao.updateMediaStatus(primaryKey, status)
    }

    suspend fun updateSeasonMediaStatus(simklId: Int, season: Int, status: MediaStatus) = withContext(Dispatchers.IO) {
        calendarDao.updateSeasonMediaStatus(simklId, season, status, Instant.now())
    }

    suspend fun updateDownloadTaskId(primaryKey: String, taskId: String?, status: MediaStatus) = withContext(Dispatchers.IO) {
        calendarDao.updateDownloadTaskId(primaryKey, taskId, status)
    }

    suspend fun updateItemAiredStatus(item: CalendarItemWithWatchlist) = withContext(Dispatchers.IO) {
        val calendarItem = item.calendarItem
        if (item.mediaStatus != MediaStatus.NOT_AIRED_YET) return@withContext

        val settings = itemDownloadSettingsDao.getSettings(item.simklId)

        val newStatus = determineStatus(
            airDate = calendarItem.date,
            settings = settings,
            mediaType = item.type,
            isTheaterRelease = calendarItem.movieReleaseType == MovieReleaseType.THEATER,
            isWatched = item.isWatched,
        )
        updateMediaStatus(calendarItem.primaryKey, newStatus)
    }

    suspend fun determineStatus(
        airDate: Instant,
        settings: ItemDownloadSettings?,
        mediaType: MediaType,
        isTheaterRelease: Boolean,
        isWatched: Boolean,
    ): MediaStatus {
        if (airDate.isAfter(Instant.now())) return MediaStatus.NOT_AIRED_YET
        if (isTheaterRelease || isWatched) return MediaStatus.IGNORED

        val autoDownloadSettings = autoDownloadRepo.preferencesFlow.first()
        val globalIsAutoDownloadUnwatched = when (mediaType) {
            MediaType.TV -> autoDownloadSettings.autoDownloadUnwatchedTv
            MediaType.ANIME -> autoDownloadSettings.autoDownloadUnwatchedAnime
            MediaType.MOVIE -> autoDownloadSettings.autoDownloadUnwatchedMovie
        }

        val isAutoDownloadUnwatched = settings?.downloadUnwatched ?: globalIsAutoDownloadUnwatched
        return if (isAutoDownloadUnwatched) MediaStatus.WANTED else MediaStatus.IGNORED
    }

    suspend fun cleanupOldWatchedCalendarItems(cutoffDays: Long = 30): Int = withContext(Dispatchers.IO) {
        try {
            val cutoff = Instant.now().minus(cutoffDays, ChronoUnit.DAYS)
            val deletedCount = calendarDao.deleteWatchedItemsOlderThan(cutoff)
            if (deletedCount > 0) {
                Timber.tag("CalendarRepository").d("Cleaned up $deletedCount old watched calendar items (watched over $cutoffDays days ago)")
            }
            deletedCount
        } catch (e: Exception) {
            Timber.tag("CalendarRepository").e(e, "Error cleaning up old watched calendar items")
            0
        }
    }

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
            Timber.tag("CalendarRepository").e(e, "Failed to mark episode S${season}E${episodeNumber} as watched for simklId $simklId")
            Result.failure(e)
        }
    }

    suspend fun markSeasonWatched(
        simklId: Int,
        season: Int,
        mediaType: MediaType
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
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
            Timber.tag("CalendarRepository").d("Marking season $season as watched for simklId $simklId (isCompleted=$shouldMarkCompleted, status=$statusValue)")

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
            Timber.tag("CalendarRepository").e(e, "Failed to mark season $season as watched for simklId $simklId")
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
            Timber.tag("CalendarRepository").e(e, "Failed to mark movie as watched for simklId $simklId")
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
            Timber.tag("CalendarRepository").e(e, "Failed to mark episode S${season}E${episodeNumber} as unwatched for simklId $simklId")
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
            Timber.tag("CalendarRepository").e(e, "Failed to mark season $season as unwatched for simklId $simklId")
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
            Timber.tag("CalendarRepository").e(e, "Failed to mark movie as unwatched for simklId $simklId")
            Result.failure(e)
        }
    }
}
