package com.felixbrucker.simklcalendar.data.repository

import com.felixbrucker.simklcalendar.data.database.AnimeMetadata
import com.felixbrucker.simklcalendar.data.database.AnimeType
import com.felixbrucker.simklcalendar.data.database.MediaItem
import com.felixbrucker.simklcalendar.data.database.MediaItemDao
import com.felixbrucker.simklcalendar.data.database.MovieMetadata
import com.felixbrucker.simklcalendar.data.database.SeriesMetadata
import com.felixbrucker.simklcalendar.data.database.SeriesStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.network.PublicSimklApiService
import com.felixbrucker.simklcalendar.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val mediaItemDao: MediaItemDao,
    private val publicSimklApiService: PublicSimklApiService,
    @ApplicationScope private val externalScope: CoroutineScope
) {

    suspend fun pruneExpiredMediaItems(months: Long = 3): Int = withContext(Dispatchers.IO) {
        val cutoff = Instant.now().minus(months * 30, ChronoUnit.DAYS)
        val deletedCount = mediaItemDao.deleteExpiredMediaItems(cutoff)
        if (deletedCount > 0) {
            Timber.d("Pruned $deletedCount expired MediaItem entities older than $months months")
        }
        deletedCount
    }

    fun getMediaItemStream(simklId: Int, mediaType: MediaType): Flow<MediaItem?> {
        externalScope.launch(Dispatchers.IO) {
            try {
                val cached = mediaItemDao.getMediaItem(simklId)
                val isExpired = cached == null || Duration.between(cached.lastUpdatedAt, Instant.now()).toHours() >= 24
                if (isExpired) {
                    refreshMediaItem(simklId = simklId, mediaType = mediaType)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh MediaItem for simklId=$simklId")
            }
        }
        return mediaItemDao.getMediaItemFlow(simklId)
    }

    suspend fun refreshMediaItem(simklId: Int, mediaType: MediaType): MediaItem = withContext(Dispatchers.IO) {
        val fetchedItem = fetchMediaItemFromApi(simklId = simklId, mediaType = mediaType)
            .copy(lastUpdatedAt = Instant.now())
        mediaItemDao.upsertMediaItem(fetchedItem)
        fetchedItem
    }

    suspend fun fetchMediaItemFromApi(simklId: Int, mediaType: MediaType): MediaItem = withContext(Dispatchers.IO) {
        when (mediaType) {
            MediaType.MOVIE -> fetchMovie(simklId)
            MediaType.TV -> fetchTvSeries(simklId)
            MediaType.ANIME -> fetchAnime(simklId)
        }
    }

    private suspend fun fetchMovie(simklId: Int): MediaItem {
        val details = publicSimklApiService.getMovieDetails(simklId)
        val releaseDate = details.released?.let { parseLocalDate(it) }
        val dvdDateStr = details.extractDigitalOrDvdReleaseDate()
        val dvdDate = dvdDateStr?.let { parseLocalDate(it) }

        val movieMetadata = MovieMetadata(
            releaseDate = releaseDate,
            dvdDate = dvdDate,
            budget = details.budget,
            boxOffice = details.revenue
        )

        return MediaItem(
            simklId = simklId,
            mediaType = MediaType.MOVIE,
            title = details.title,
            year = details.year,
            description = details.overview,
            poster = details.poster,
            genres = details.genres ?: emptyList(),
            rating = details.ratings?.simkl?.rating?.toFloat(),
            movieMetadata = movieMetadata
        )
    }

    private suspend fun fetchTvSeries(simklId: Int): MediaItem {
        val details = publicSimklApiService.getTvDetails(simklId)
        val episodes = publicSimklApiService.getTvEpisodes(simklId)

        val seasonsCount = episodes.mapNotNull { it.season }
            .filter { it > 0 }
            .toSet()
            .size
            .takeIf { it > 0 }

        val endYear = parseEndYear(details.yearStartEnd)

        val regularEpisodesCount = episodes.count { episode ->
            episode.type.equals("episode", ignoreCase = true) && (episode.season == null || episode.season > 0)
        }
        val totalEpisodes = details.totalEpisodes ?: regularEpisodesCount.takeIf { it > 0 }

        val seriesMetadata = SeriesMetadata(
            endYear = endYear,
            seasonsCount = seasonsCount,
            totalEpisodes = totalEpisodes,
            status = SeriesStatus.fromKey(details.status)
        )

        return MediaItem(
            simklId = simklId,
            mediaType = MediaType.TV,
            title = details.title,
            year = details.year,
            description = details.overview,
            poster = details.poster,
            genres = details.genres ?: emptyList(),
            rating = details.ratings?.simkl?.rating?.toFloat(),
            seriesMetadata = seriesMetadata
        )
    }

    private suspend fun fetchAnime(simklId: Int): MediaItem {
        val details = publicSimklApiService.getAnimeDetails(simklId)
        val episodes = publicSimklApiService.getAnimeEpisodes(simklId)

        val endYear = parseEndYear(details.yearStartEnd)
        val (seasonQuarter, seasonYear) = parseSeasonNameYear(details.seasonNameYear)

        val animeTitle = details.enTitle?.ifBlank { null } ?: details.title
        val titleRomaji = if (!details.enTitle.isNullOrBlank()) details.title else null

        val regularEpisodesCount = episodes.count { episode ->
            episode.type.equals("episode", ignoreCase = true) && (episode.season == null || episode.season > 0)
        }
        val totalEpisodes = details.totalEpisodes ?: regularEpisodesCount.takeIf { it > 0 }

        val animeMetadata = AnimeMetadata(
            animeType = AnimeType.fromKey(details.animeType),
            endYear = endYear,
            totalEpisodes = totalEpisodes,
            seasonYear = seasonYear,
            seasonQuarter = seasonQuarter,
            status = SeriesStatus.fromKey(details.status)
        )

        return MediaItem(
            simklId = simklId,
            mediaType = MediaType.ANIME,
            title = animeTitle,
            titleRomaji = titleRomaji,
            year = details.year,
            description = details.overview,
            poster = details.poster,
            genres = details.genres ?: emptyList(),
            rating = details.ratings?.simkl?.rating?.toFloat(),
            animeMetadata = animeMetadata
        )
    }

    private fun parseLocalDate(dateStr: String): LocalDate? {
        return try {
            LocalDate.parse(dateStr.take(10))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseEndYear(yearStartEnd: String?): Int? {
        if (yearStartEnd.isNullOrBlank() || !yearStartEnd.contains("-")) return null
        val parts = yearStartEnd.split("-")
        if (parts.size < 2) return null
        return parts[1].trim().toIntOrNull()
    }

    private fun parseSeasonNameYear(seasonNameYear: String?): Pair<String?, Int?> {
        if (seasonNameYear.isNullOrBlank()) return Pair(null, null)
        val parts = seasonNameYear.trim().split(" ")
        return if (parts.size >= 2) {
            Pair(parts[0], parts[1].toIntOrNull())
        } else {
            Pair(null, seasonNameYear.toIntOrNull())
        }
    }
}
