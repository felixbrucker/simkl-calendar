package com.felixbrucker.simklcalendar.data.repository

import com.felixbrucker.simklcalendar.data.database.AnimeMetadata
import com.felixbrucker.simklcalendar.data.database.AnimeType
import com.felixbrucker.simklcalendar.data.database.EpisodeType
import com.felixbrucker.simklcalendar.data.database.MediaEpisode
import com.felixbrucker.simklcalendar.data.database.MediaItem
import com.felixbrucker.simklcalendar.data.database.MediaItemDao
import com.felixbrucker.simklcalendar.data.database.MediaItemWithDetails
import com.felixbrucker.simklcalendar.data.database.MovieMetadata
import com.felixbrucker.simklcalendar.data.database.RecommendedMediaItem
import com.felixbrucker.simklcalendar.data.database.RelatedMediaItem
import com.felixbrucker.simklcalendar.data.database.RelationType
import com.felixbrucker.simklcalendar.data.database.SeriesMetadata
import com.felixbrucker.simklcalendar.data.database.SeriesStatus
import com.felixbrucker.simklcalendar.data.database.SimilarMediaItem
import com.felixbrucker.simklcalendar.data.database.TrendingEntryDao
import com.felixbrucker.simklcalendar.data.database.TrendingMediaItem
import com.felixbrucker.simklcalendar.data.database.WatchlistItem
import com.felixbrucker.simklcalendar.data.database.WatchlistItemDao
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.network.PublicSimklApiService
import com.felixbrucker.simklcalendar.data.util.DateUtil
import com.felixbrucker.simklcalendar.di.ApplicationScope
import com.felixbrucker.simklcalendar.extensions.unescapeHtml
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
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
    private val trendingEntryDao: TrendingEntryDao,
    private val watchlistItemDao: WatchlistItemDao,
    private val publicSimklApiService: PublicSimklApiService,
    @ApplicationScope private val externalScope: CoroutineScope
) {

    fun getWatchlistItemsFlow(simklIds: List<Int>): Flow<List<WatchlistItem>> {
        return watchlistItemDao.getWatchlistItemsFlow(simklIds).flowOn(Dispatchers.IO)
    }

    val minTrendingYearFlow: Flow<Int?> = trendingEntryDao.getMinTrendingYearFlow().flowOn(Dispatchers.IO)
    val maxTrendingYearFlow: Flow<Int?> = trendingEntryDao.getMaxTrendingYearFlow().flowOn(Dispatchers.IO)

    fun getFilteredTrendingMediaItemsFlow(
        allowedTypes: List<MediaType>,
        excludeOnMyLists: Boolean,
        minRating: Float,
        minYear: Int,
        hasDvd: Boolean,
        searchQuery: String
    ): Flow<List<TrendingMediaItem>> {
        return trendingEntryDao.getFilteredTrendingMediaItemsFlow(
            allowedTypes = allowedTypes,
            excludeOnMyLists = excludeOnMyLists,
            minRating = minRating,
            minYear = minYear,
            hasDvd = hasDvd,
            searchQuery = searchQuery
        ).flowOn(Dispatchers.IO)
    }

    suspend fun pruneExpiredMediaItems(months: Long = 3): Int = withContext(Dispatchers.IO) {
        val cutoff = Instant.now().minus(months * 30, ChronoUnit.DAYS)
        val deletedCount = mediaItemDao.deleteExpiredMediaItems(cutoff)
        if (deletedCount > 0) {
            Timber.d("Pruned $deletedCount expired MediaItem entities older than $months months")
        }
        deletedCount
    }

    fun getMediaItemStream(simklId: Int, mediaType: MediaType): Flow<MediaItemWithDetails?> {
        externalScope.launch(Dispatchers.IO) {
            try {
                val cached = mediaItemDao.getMediaItem(simklId)
                if (cached.isExpired()) {
                    refreshMediaItem(simklId = simklId, mediaType = mediaType)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh MediaItem for simklId=$simklId")
            }
        }
        return mediaItemDao.getMediaItemWithDetailsStream(simklId)
    }

    suspend fun refreshMediaItem(simklId: Int, mediaType: MediaType): MediaItemWithDetails = withContext(Dispatchers.IO) {
        val fetchedDetails = fetchMediaItemFromApi(simklId = simklId, mediaType = mediaType)
        val updatedMediaItem = fetchedDetails.mediaItem.copy(lastUpdatedAt = Instant.now())
        val updatedDetails = fetchedDetails.copy(mediaItem = updatedMediaItem)

        mediaItemDao.upsertMediaItem(updatedDetails.mediaItem)

        if (updatedDetails.episodes.isNotEmpty()) {
            mediaItemDao.insertEpisodes(updatedDetails.episodes)
            mediaItemDao.deleteEpisodesNotIn(simklId, updatedDetails.episodes.map { "${it.season}_${it.episodeNumber}" })
        } else {
            mediaItemDao.deleteAllEpisodes(simklId)
        }

        if (updatedDetails.relations.isNotEmpty()) {
            mediaItemDao.insertRelations(updatedDetails.relations)
            mediaItemDao.deleteRelationsNotIn(simklId, updatedDetails.relations.map { it.relatedSimklId })
        } else {
            mediaItemDao.deleteAllRelations(simklId)
        }

        if (updatedDetails.similarItems.isNotEmpty()) {
            mediaItemDao.insertSimilarItems(updatedDetails.similarItems)
            mediaItemDao.deleteSimilarNotIn(simklId, updatedDetails.similarItems.map { it.similarSimklId })
        } else {
            mediaItemDao.deleteAllSimilar(simklId)
        }

        if (updatedDetails.recommendedItems.isNotEmpty()) {
            mediaItemDao.insertRecommendedItems(updatedDetails.recommendedItems)
            mediaItemDao.deleteRecommendedNotIn(simklId, updatedDetails.recommendedItems.map { it.recommendedSimklId })
        } else {
            mediaItemDao.deleteAllRecommended(simklId)
        }

        updatedDetails
    }

    suspend fun fetchMediaItemFromApi(simklId: Int, mediaType: MediaType): MediaItemWithDetails = withContext(Dispatchers.IO) {
        when (mediaType) {
            MediaType.MOVIE -> fetchMovie(simklId)
            MediaType.TV -> fetchTvSeries(simklId)
            MediaType.ANIME -> fetchAnime(simklId)
        }
    }

    private suspend fun fetchMovie(simklId: Int): MediaItemWithDetails {
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

        val (title, titleRomaji) = parseTitles(details.title, null)

        val item = MediaItem(
            simklId = simklId,
            mediaType = MediaType.MOVIE,
            title = title,
            titleRomaji = titleRomaji,
            year = details.year,
            description = details.overview?.unescapeHtml(),
            poster = details.poster,
            genres = details.genres ?: emptyList(),
            rating = details.ratings?.simkl?.rating?.toFloat(),
            movieMetadata = movieMetadata
        )

        val similarItems = details.similar?.map { sim ->
            val (simTitle, simRomaji) = parseTitles(sim.title, sim.enTitle)
            SimilarMediaItem(
                simklId = simklId,
                similarSimklId = sim.ids.simkl,
                mediaType = MediaType.fromKey(sim.type ?: "movie"),
                title = simTitle,
                titleRomaji = simRomaji,
                year = sim.year,
                poster = sim.poster
            )
        } ?: emptyList()

        val recommendedItems = details.usersRecommendations?.map { rec ->
            val (recTitle, recRomaji) = parseTitles(rec.title, rec.enTitle)
            RecommendedMediaItem(
                simklId = simklId,
                recommendedSimklId = rec.ids.simkl,
                mediaType = MediaType.fromKey(rec.type ?: "movie"),
                title = recTitle,
                titleRomaji = recRomaji,
                year = rec.year,
                poster = rec.poster,
                usersPercent = rec.usersPercent,
                usersCount = rec.usersCount
            )
        } ?: emptyList()

        return MediaItemWithDetails(
            mediaItem = item,
            similarItems = similarItems,
            recommendedItems = recommendedItems
        )
    }

    private suspend fun fetchTvSeries(simklId: Int): MediaItemWithDetails {
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

        val (title, titleRomaji) = parseTitles(details.title, null)

        val item = MediaItem(
            simklId = simklId,
            mediaType = MediaType.TV,
            title = title,
            titleRomaji = titleRomaji,
            year = details.year,
            description = details.overview?.unescapeHtml(),
            poster = details.poster,
            genres = details.genres ?: emptyList(),
            rating = details.ratings?.simkl?.rating?.toFloat(),
            seriesMetadata = seriesMetadata
        )

        val mediaEpisodes = episodes.map { ep ->
            MediaEpisode(
                simklId = simklId,
                season = ep.season ?: 1,
                episodeNumber = ep.episode ?: 0,
                title = ep.title.unescapeHtml(),
                type = EpisodeType.fromKey(ep.type),
                aired = ep.aired,
                date = DateUtil.parseToInstant(ep.date)
            )
        }

        val similarItems = details.similar?.map { sim ->
            val (simTitle, simRomaji) = parseTitles(sim.title, sim.enTitle)
            SimilarMediaItem(
                simklId = simklId,
                similarSimklId = sim.ids.simkl,
                mediaType = MediaType.fromKey(sim.type ?: "tv"),
                title = simTitle,
                titleRomaji = simRomaji,
                year = sim.year,
                poster = sim.poster
            )
        } ?: emptyList()

        val recommendedItems = details.usersRecommendations?.map { rec ->
            val (recTitle, recRomaji) = parseTitles(rec.title, rec.enTitle)
            RecommendedMediaItem(
                simklId = simklId,
                recommendedSimklId = rec.ids.simkl,
                mediaType = MediaType.fromKey(rec.type ?: "tv"),
                title = recTitle,
                titleRomaji = recRomaji,
                year = rec.year,
                poster = rec.poster,
                usersPercent = rec.usersPercent,
                usersCount = rec.usersCount
            )
        } ?: emptyList()

        return MediaItemWithDetails(
            mediaItem = item,
            episodes = mediaEpisodes,
            similarItems = similarItems,
            recommendedItems = recommendedItems
        )
    }

    private suspend fun fetchAnime(simklId: Int): MediaItemWithDetails {
        val details = publicSimklApiService.getAnimeDetails(simklId)
        val episodes = publicSimklApiService.getAnimeEpisodes(simklId)

        val endYear = parseEndYear(details.yearStartEnd)
        val (seasonQuarter, seasonYear) = parseSeasonNameYear(details.seasonNameYear)

        val (animeTitle, titleRomaji) = parseTitles(details.title, details.enTitle)

        val seasonsCount = episodes.mapNotNull { it.season }
            .filter { it > 0 }
            .toSet()
            .size
            .takeIf { it > 0 }

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
            status = SeriesStatus.fromKey(details.status),
            seasonsCount = seasonsCount,
            season = details.season
        )

        val item = MediaItem(
            simklId = simklId,
            mediaType = MediaType.ANIME,
            title = animeTitle,
            titleRomaji = titleRomaji,
            year = details.year,
            description = details.overview?.unescapeHtml(),
            poster = details.poster,
            genres = details.genres ?: emptyList(),
            rating = details.ratings?.simkl?.rating?.toFloat(),
            animeMetadata = animeMetadata
        )

        val mediaEpisodes = episodes.map { ep ->
            MediaEpisode(
                simklId = simklId,
                season = ep.season ?: 1,
                episodeNumber = ep.episode ?: 0,
                title = ep.title.unescapeHtml(),
                type = EpisodeType.fromKey(ep.type),
                aired = ep.aired,
                date = DateUtil.parseToInstant(ep.date)
            )
        }

        val relations = details.relations?.map { rel ->
            val (relTitle, relRomaji) = parseTitles(rel.title, rel.enTitle)
            RelatedMediaItem(
                simklId = simklId,
                relatedSimklId = rel.ids.simkl,
                mediaType = MediaType.ANIME,
                title = relTitle,
                titleRomaji = relRomaji,
                year = rel.year,
                poster = rel.poster,
                relationType = RelationType.fromKey(rel.relationType),
                isDirect = rel.isDirect
            )
        } ?: emptyList()

        val similarItems = details.similar?.map { sim ->
            val (simTitle, simRomaji) = parseTitles(sim.title, sim.enTitle)
            SimilarMediaItem(
                simklId = simklId,
                similarSimklId = sim.ids.simkl,
                mediaType = MediaType.fromKey(sim.type ?: "anime"),
                title = simTitle,
                titleRomaji = simRomaji,
                year = sim.year,
                poster = sim.poster
            )
        } ?: emptyList()

        val recommendedItems = details.usersRecommendations?.map { rec ->
            val (recTitle, recRomaji) = parseTitles(rec.title, rec.enTitle)
            RecommendedMediaItem(
                simklId = simklId,
                recommendedSimklId = rec.ids.simkl,
                mediaType = MediaType.fromKey(rec.type ?: "anime"),
                title = recTitle,
                titleRomaji = recRomaji,
                year = rec.year,
                poster = rec.poster,
                usersPercent = rec.usersPercent,
                usersCount = rec.usersCount
            )
        } ?: emptyList()

        return MediaItemWithDetails(
            mediaItem = item,
            episodes = mediaEpisodes,
            relations = relations,
            similarItems = similarItems,
            recommendedItems = recommendedItems
        )
    }

    private fun parseTitles(rawTitle: String, rawEnTitle: String?): Pair<String, String?> {
        val cleanedEn = stripSeasonSuffix(rawEnTitle?.ifBlank { null }?.unescapeHtml())
        val cleanedTitle = rawTitle.unescapeHtml()
        val title = cleanedEn ?: cleanedTitle
        val titleRomaji = if (!cleanedEn.isNullOrBlank()) cleanedTitle else null
        return Pair(title, titleRomaji)
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

    private fun stripSeasonSuffix(title: String?): String? {
        if (title.isNullOrBlank()) return null
        val cleaned = title.replace(Regex("""(?i)[\s:-]*(?:\(season\s+\d+\)|season\s+\d+)\s*$"""), "").trim()
        return cleaned.ifBlank { null }
    }
}

fun MediaItem?.isExpired(): Boolean {
    return this == null || Duration.between(lastUpdatedAt, Instant.now()).toHours() >= 24
}
