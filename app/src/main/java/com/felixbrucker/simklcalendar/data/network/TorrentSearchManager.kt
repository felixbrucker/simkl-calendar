package com.felixbrucker.simklcalendar.data.network

import android.content.SharedPreferences
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.util.ensureAdded
import com.felixbrucker.simklcalendar.data.util.getStringListWithMigration
import com.felixbrucker.torrent_search_api.Category
import com.felixbrucker.torrent_search_api.NyaaProvider
import com.felixbrucker.torrent_search_api.OrderBy
import com.felixbrucker.torrent_search_api.SearchResultItem
import com.felixbrucker.torrent_search_api.TpbProvider
import java.util.Locale

class TorrentSearchManager(
    private val itemSettingsDao: ItemDownloadSettingsDao,
    private val downloadPrefs: SharedPreferences
) {
    private val nyaaProvider = NyaaProvider()
    private val tpbProvider = TpbProvider()

    suspend fun search(item: CalendarItemWithWatchlist): List<SearchResultItem> {
        val simklId = item.simklId
        val season = item.season ?: 1

        val itemSettings = itemSettingsDao.getSettings(simklId)

        val searchTitle = itemSettings?.titleOverride ?: item.titleRomaji ?: item.title
        val searchSeason = itemSettings?.seasonOverrides?.get(season) ?: season
        val episode = item.episodeNumber

        val globalQuality = downloadPrefs.getString("quality", "1080p") ?: "1080p"
        val globalPreferHevc = downloadPrefs.getBoolean("prefer_hevc", true)
        val preferredKeywords: MutableList<Keyword> = downloadPrefs
            .getStringListWithMigration("preferred_keywords")
            .map { Keyword.single(it) }
            .toMutableList()
        val ignoreKeywords: MutableList<Keyword> = downloadPrefs
            .getStringListWithMigration("ignore_keywords")
            .map { Keyword.single(it) }
            .toMutableList()
        val preferHevc = itemSettings?.preferHevcOverride ?: globalPreferHevc
        if (preferHevc) {
            preferredKeywords.ensureAdded(Keyword(listOf("hevc", "x265")))
        }
        // Ignore low quality releases
        ignoreKeywords.ensureAdded(
            Keyword(listOf("TS", "TELESYNC", "Telesync", "TeleCine", "HDTS", "hdts"), ignoreCase = false),
            Keyword(listOf("CAM", "CamRip"), ignoreCase = false),
            Keyword(listOf("DCPRip"), ignoreCase = false),
            Keyword(listOf("DVDScr"), ignoreCase = false),
        )

        val seasonAndEpisodeTerm = String.format(Locale.US, "S%02dE%02d", searchSeason, episode ?: 1)
        val episodeTerm = String.format(Locale.US, "%02d", episode ?: 1)
        val episodeSearchTerm = when(item.type) {
            MediaType.TV -> seasonAndEpisodeTerm
            MediaType.ANIME -> episodeTerm
            MediaType.MOVIE -> ""
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

        val results = provider
            .search(term = term, category = category, orderBy = OrderBy.SeederDescending)
            .getOrThrow()
            .results
            .excluding(ignoreKeywords)
            .sortedUsing(preferredKeywords)

        // Only anime episode search terms are generic enough to match partially, filter out invalid
        // matches
        if (item.type == MediaType.ANIME) {
            val keyword = Keyword(listOf(
                " $episodeTerm ",
                seasonAndEpisodeTerm
            ))

            return results.including(listOf(keyword))
        }

        return results
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
    val ignoreCase: Boolean = true,
) {
    companion object {
        fun single(variant: String, ignoreCase: Boolean = true) = Keyword(listOf(variant), ignoreCase)
    }
}
