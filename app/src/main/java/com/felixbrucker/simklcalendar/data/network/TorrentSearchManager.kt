package com.felixbrucker.simklcalendar.data.network

import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.model.EpisodeSearchStyle
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import com.felixbrucker.simklcalendar.data.util.Keyword
import com.felixbrucker.simklcalendar.data.util.MediaFormatter.makeEpisodeTerm
import com.felixbrucker.simklcalendar.data.util.MediaFormatter.makeSeasonAndEpisodeTerm
import com.felixbrucker.simklcalendar.data.util.contains
import com.felixbrucker.simklcalendar.extensions.asSearchTitleWithSeason
import com.felixbrucker.simklcalendar.extensions.ensureAdded
import com.felixbrucker.torrent_search_api.Category
import com.felixbrucker.torrent_search_api.NyaaProvider
import com.felixbrucker.torrent_search_api.OrderBy
import com.felixbrucker.torrent_search_api.SearchResultItem
import com.felixbrucker.torrent_search_api.TpbProvider
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.Instant
import java.time.Instant.now
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorrentSearchManager @Inject constructor(
    private val calendarDao: CalendarItemDao,
    private val watchlistDao: WatchlistDao,
    private val itemSettingsDao: ItemDownloadSettingsDao,
    private val autoDownloadDataSource: AutoDownloadRepository
) {
    companion object {
        private const val TAG = "TorrentSearchManager"
        private const val RELEASE_DATE_BUFFER_MONTHS = 3L
    }

    private val nyaaProvider = NyaaProvider()
    private val tpbProvider = TpbProvider()

    suspend fun search(
        item: CalendarItemWithWatchlist,
        customTitle: String? = null,
    ): List<SearchResultItem> {
        val simklId = item.simklId
        val itemSettings = itemSettingsDao.getSettings(simklId)

        return searchInternal(
            baseSearchTermBuilder = { makeItemSearchTerm(item, customTitle) },
            releaseDate = item.date,
            mediaType = item.type,
            itemSettings = itemSettings,
            searchResultFilter = item.makeInvalidAnimeEpisodeFilter(),
        )
    }

    suspend fun searchSeason(
        simklId: Int,
        season: Int,
        customTitle: String? = null,
    ): List<SearchResultItem> {
        val watchlistItem = watchlistDao.getItem(simklId)
            ?: throw IllegalStateException("Watchlist item not found for simklId $simklId")
        val itemSettings = itemSettingsDao.getSettings(simklId)
        val itemDate = calendarDao.findLastItemForSimklIdInSeason(simklId, season)?.date ?: now()

        return searchInternal(
            baseSearchTermBuilder = {
                makeSeasonSearchTerm(
                    watchlistItem = watchlistItem,
                    settings = itemSettings,
                    season = watchlistItem.animeSeason ?: season,
                    customTitle = customTitle
                )
            },
            releaseDate = itemDate,
            mediaType = watchlistItem.type,
            itemSettings = itemSettings,
        )
    }

    suspend fun detectTorrents(
        simklId: Int,
        customTitle: String,
    ): List<SearchResultItem> {
        val watchlistItem = watchlistDao.getItem(simklId)
            ?: throw IllegalStateException("Watchlist item not found for simklId $simklId")
        val baseItem = calendarDao.findFirstItemForSimklId(simklId)
        val itemSettings = itemSettingsDao.getSettings(simklId)
        val itemDate = baseItem?.date ?: now().minus(3, ChronoUnit.MONTHS)

        return searchInternal(
            baseSearchTermBuilder = {
                baseItem?.let { makeItemSearchTerm(it, customTitle) } ?: makeItemSearchTerm(
                    watchlistItem = watchlistItem,
                    settings = itemSettings,
                    season = 1,
                    episode = 1,
                    customTitle = customTitle
                )
            },
            releaseDate = itemDate,
            mediaType = watchlistItem.type,
            itemSettings = itemSettings,
            searchResultFilter = baseItem?.makeInvalidAnimeEpisodeFilter()
                ?: watchlistItem.type.makeInvalidAnimeEpisodeFilter(
                    settings = itemSettings,
                    season = 1,
                    episode = 1,
                ),
        )
    }

    private suspend fun searchInternal(
        baseSearchTermBuilder: () -> String,
        releaseDate: Instant,
        mediaType: MediaType,
        itemSettings: ItemDownloadSettings?,
        searchResultFilter: SearchResultFilter = { it },
    ): List<SearchResultItem> {
        val prefs = autoDownloadDataSource.preferencesFlow.first()
        val globalQuality = prefs.quality
        val globalPreferHevc = prefs.preferHevc
        val preferredKeywords: MutableList<Keyword> = prefs.preferredKeywords
            .map { Keyword.single(it) }
            .toMutableList()
        val ignoreKeywords: MutableList<Keyword> = prefs.ignoreKeywords
            .map { Keyword.single(it) }
            .toMutableList()
        val preferHevc = itemSettings?.preferHevcOverride ?: globalPreferHevc
        if (preferHevc) {
            preferredKeywords.ensureAdded(Keyword(listOf("hevc", "x265", "H.265"), ignoreCase = true))
        }
        // Ignore low quality releases
        ignoreKeywords.ensureAdded(
            Keyword(listOf("TS", "TELESYNC", "Telesync", "TeleCine", "HDTS", "hdts")),
            Keyword(listOf("CAM", "CamRip")),
            Keyword(listOf("DCPRip")),
            Keyword(listOf("DVDScr")),
        )
        val baseTerm = baseSearchTermBuilder()

        val quality = itemSettings?.qualityOverride ?: globalQuality
        val term = if (quality.isNotEmpty()) "$baseTerm $quality" else baseTerm

        val provider = if (mediaType == MediaType.ANIME) nyaaProvider else tpbProvider
        val category = if (mediaType == MediaType.ANIME) Category.ANIME_ENGLISH_TRANSLATED else Category.VIDEO

        Timber.tag(TAG).d("Searching provider ${provider.javaClass.simpleName} with term '$term'")

        val rawResults = provider
            .search(term = term, category = category, orderBy = OrderBy.SeederDescending)
            .getOrThrow()
            .results

        val finalResults = rawResults
            .excluding(ignoreKeywords)
            .filterByReleaseDate(releaseDate, RELEASE_DATE_BUFFER_MONTHS)
            .sortedUsing(preferredKeywords)
            .let { searchResultFilter(it) }

        Timber.tag(TAG).d("Torrent search returned ${finalResults.size} results for term '$term' (${rawResults.size} raw results)")
        return finalResults
    }
}

typealias SearchResultFilter = (List<SearchResultItem>) -> List<SearchResultItem>

// Only anime episode search terms are generic enough to match partially, filter out invalid
// matches
internal fun CalendarItemWithWatchlist.makeInvalidAnimeEpisodeFilter(): SearchResultFilter {
    return type.makeInvalidAnimeEpisodeFilter(
        settings = downloadSettings,
        season = animeSeason ?: season ?: 1,
        episode = episodeNumber ?: 1
    )
}

internal fun MediaType.makeInvalidAnimeEpisodeFilter(
    settings: ItemDownloadSettings?,
    season: Int,
    episode: Int
): SearchResultFilter {
    if (this != MediaType.ANIME) {
        return { it }
    }
    val keyword = Keyword.makeEpisodeVariant(season, episode, this, settings)

    return { it.including(listOf(keyword)) }
}

internal fun ItemDownloadSettings?.getSeasonAndEpisodeTerm(season: Int, episode: Int): String {
    val season = this?.getSearchSeason(season) ?: season
    return makeSeasonAndEpisodeTerm(season, episode)
}

internal fun ItemDownloadSettings.getSearchSeason(season: Int): Int {
    return seasonOverrides?.get(season) ?: season
}

internal fun List<SearchResultItem>.filterByReleaseDate(
    releaseDate: Instant,
    bufferMonths: Long = 3
): List<SearchResultItem> {
    val earliestAllowedUploadDate = releaseDate.atZone(ZoneOffset.UTC).minusMonths(bufferMonths).toInstant()
    return filter { it.uploadedAt.isAfter(earliestAllowedUploadDate) }
}

internal fun List<SearchResultItem>.including(keywords: List<Keyword>): List<SearchResultItem> {
    return filter { item ->
        keywords.any { keyword ->
            item.name.contains(keyword)
        }
    }
}

internal fun List<SearchResultItem>.excluding(keywords: List<Keyword>): List<SearchResultItem> {
    return filter { item ->
        keywords.none { keyword ->
            item.name.contains(keyword)
        }
    }
}

// Sort using the number of preferred keyword matches first, and if it's the same, using the
// position of the preferred keyword in the list. For example assuming the following list
// ["Erai-Raws", "SubsPlease", "hevc"]
// and the following search result names
// ["[SubsPlease] One Piece 1234", "[AWS] One Piece 1234 HEVC", "[Erai-Raws] One Piece 1234", "[Erai-Raws] One Piece 1234 HEVC"]
// we would sort the results as follows:
// 1. "[Erai-Raws] One Piece 1234 HEVC" (2 matches, first preferred keyword)
// 2. "[Erai-Raws] One Piece 1234" (1 match, first preferred keyword)
// 3. "[SubsPlease] One Piece 1234" (1 match, second preferred keyword)
// 4. "[AWS] One Piece 1234 HEVC" (1 match, third preferred keyword)
internal fun List<SearchResultItem>.sortedUsing(preferredKeywords: List<Keyword>): List<SearchResultItem> {
    return sortedWith(
        compareByDescending<SearchResultItem> { item ->
            preferredKeywords.count { keyword ->
                item.name.contains(keyword)
            }
        }.thenByDescending { item ->
            preferredKeywords.mapIndexed { index, keyword ->
                if (item.name.contains(keyword)) {
                    preferredKeywords.size - index
                } else {
                    0
                }
            }.sum()
        }
    )
}

internal fun makeItemSearchTerm(item: CalendarItemWithWatchlist, customTitle: String? = null): String {
    require(item.watchlistItem != null) { "WatchlistItem not found for item ${item.primaryKey}" }

    return makeItemSearchTerm(
        watchlistItem = item.watchlistItem,
        settings = item.downloadSettings,
        season = item.season ?: 1,
        episode = item.episodeNumber ?: 1,
        customTitle = customTitle
    )
}

internal fun makeItemSearchTerm(
    watchlistItem: TrackedWatchlistItem,
    settings: ItemDownloadSettings?,
    season: Int,
    episode: Int,
    customTitle: String? = null
): String {
    val searchTitle = if (!customTitle.isNullOrBlank()) {
        customTitle.trim()
    } else {
        watchlistItem.getSearchTitle(settings)
    }
    val searchStyle = settings?.episodeSearchStyle ?: watchlistItem.type.defaultEpisodeSearchStyle
    val searchSeason = settings?.seasonOverrides?.get(season) ?: season

    return watchlistItem.type.makeSearchTerm(searchTitle, searchStyle, searchSeason, episode)
}

internal fun MediaType.makeSearchTerm(
    searchTitle: String,
    searchStyle: EpisodeSearchStyle,
    searchSeason: Int,
    episode: Int
): String {
    return when (this) {
        MediaType.MOVIE -> searchTitle
        else -> when (searchStyle) {
            EpisodeSearchStyle.SeasonAndEpisode -> "$searchTitle ${makeSeasonAndEpisodeTerm(searchSeason, episode)}"
            EpisodeSearchStyle.Episode -> "$searchTitle ${makeEpisodeTerm(episode)}"
        }
    }
}

internal fun makeSeasonSearchTerm(
    watchlistItem: TrackedWatchlistItem,
    settings: ItemDownloadSettings?,
    season: Int,
    customTitle: String? = null
): String {
    if (!customTitle.isNullOrBlank()) {
        return customTitle.trim()
    }

    return watchlistItem
        .getSearchTitle(settings)
        .asSearchTitleWithSeason(season, watchlistItem.type, settings)
}
