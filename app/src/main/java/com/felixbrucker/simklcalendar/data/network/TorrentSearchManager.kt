package com.felixbrucker.simklcalendar.data.network

import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.model.EpisodeSearchStyle
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import com.felixbrucker.simklcalendar.extensions.ensureAdded
import com.felixbrucker.torrent_search_api.Category
import com.felixbrucker.torrent_search_api.NyaaProvider
import com.felixbrucker.torrent_search_api.OrderBy
import com.felixbrucker.torrent_search_api.SearchResultItem
import com.felixbrucker.torrent_search_api.TpbProvider
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.Locale

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorrentSearchManager @Inject constructor(
    private val itemSettingsDao: ItemDownloadSettingsDao,
    private val autoDownloadDataSource: AutoDownloadRepository
) {
    companion object {
        private const val TAG = "TorrentSearchManager"
    }

    private val nyaaProvider = NyaaProvider()
    private val tpbProvider = TpbProvider()

    suspend fun search(item: CalendarItemWithWatchlist): List<SearchResultItem> {
        Timber.tag(TAG).d("Initiating torrent search for '${item.title}' (simklId=${item.simklId})")
        val simklId = item.simklId
        val season = item.season ?: 1

        val itemSettings = itemSettingsDao.getSettings(simklId)

        val searchTitle = itemSettings?.titleOverride ?: item.titleRomaji ?: item.title
        val searchSeason = itemSettings?.seasonOverrides?.get(season) ?: season
        val episode = item.episodeNumber

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

        val searchStyle = itemSettings?.episodeSearchStyle ?: item.type.defaultEpisodeSearchStyle

        val seasonAndEpisodeTerm = String.format(Locale.US, "S%02dE%02d", searchSeason, episode ?: 1)
        val episodeTerm = String.format(Locale.US, "%02d", episode ?: 1)
        val episodeSearchTerm = when(item.type) {
            MediaType.MOVIE -> ""
            else -> when (searchStyle) {
                EpisodeSearchStyle.SeasonAndEpisode -> seasonAndEpisodeTerm
                EpisodeSearchStyle.Episode -> episodeTerm
            }
        }
        var term = if (episodeSearchTerm.isNotEmpty()) {
            "$searchTitle $episodeSearchTerm"
        } else {
            searchTitle
        }

        val quality = itemSettings?.qualityOverride ?: globalQuality
        if (quality.isNotEmpty()) {
            term += " $quality"
        }

        val provider = if (item.type == MediaType.ANIME) nyaaProvider else tpbProvider
        val category = if (item.type == MediaType.ANIME) Category.ANIME_ENGLISH_TRANSLATED else Category.VIDEO

        Timber.tag(TAG).d("Searching provider ${provider.javaClass.simpleName} with term '$term'")

        val rawResults = provider
            .search(term = term, category = category, orderBy = OrderBy.SeederDescending)
            .getOrThrow()
            .results

        val filteredResults = rawResults
            .excluding(ignoreKeywords)
            .sortedUsing(preferredKeywords)

        // Only anime episode search terms are generic enough to match partially, filter out invalid
        // matches
        val finalResults = if (item.type == MediaType.ANIME) {
            val keyword = Keyword(
                variants = listOf(
                    " $episodeTerm ",
                    seasonAndEpisodeTerm
                ),
                ignoreCase = true
            )

            filteredResults.including(listOf(keyword))
        } else {
            filteredResults
        }

        Timber.tag(TAG).d("Torrent search returned ${finalResults.size} results for term '$term' (${rawResults.size} raw results)")
        return finalResults
    }
}

private fun List<SearchResultItem>.including(keywords: List<Keyword>): List<SearchResultItem> {
    return filter { item ->
        keywords.any { keyword ->
            item.name.contains(keyword)
        }
    }
}

private fun List<SearchResultItem>.excluding(keywords: List<Keyword>): List<SearchResultItem> {
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
private fun List<SearchResultItem>.sortedUsing(preferredKeywords: List<Keyword>): List<SearchResultItem> {
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

private fun String.contains(keyword: Keyword): Boolean {
    return keyword.variants.any { contains(it, ignoreCase = keyword.ignoreCase) }
}

private data class Keyword(
    val variants: List<String>,
    val ignoreCase: Boolean = false,
) {
    companion object {
        fun single(variant: String, ignoreCase: Boolean = false) = Keyword(listOf(variant), ignoreCase)
    }
}
