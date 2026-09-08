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
        val preferredKeywords = downloadPrefs
            .getStringListWithMigration("preferred_keywords")
            .toMutableList()
        val ignoreKeywords = downloadPrefs.getStringListWithMigration("ignore_keywords")
        val preferHevc = itemSettings?.preferHevcOverride ?: globalPreferHevc
        if (preferHevc) {
            // Anime uses HEVC primarily, while x265 is used everywhere else. Avoid adding both to
            // prevent results with both to score higher than other preferred items.
            if (item.type == MediaType.ANIME) {
                preferredKeywords.ensureAdded("hevc")
            } else {
                preferredKeywords.ensureAdded("x265")
            }
        }

        val episodeSearchTerm = when(item.type) {
            MediaType.TV -> String.format(Locale.US, "S%02dE%02d", searchSeason, episode ?: 1)
            MediaType.ANIME -> String.format(Locale.US, "%02d", episode ?: 1)
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
            .filteredUsing(ignoreKeywords)
            .sortedUsing(preferredKeywords)

        // Only anime episode search terms are generic enough to match partially
        if (item.type == MediaType.ANIME) {
            return results.filteredUsingTerm(episodeSearchTerm)
        }

        return results
    }
}

// Filter out partial matches of the term by matching the term explicitly with spaces around it
private fun List<SearchResultItem>.filteredUsingTerm(searchTerm: String): List<SearchResultItem> {
    return filter { it.name.contains(" $searchTerm ", ignoreCase = true) }
}

private fun List<SearchResultItem>.filteredUsing(ignoreKeywords: List<String>): List<SearchResultItem> {
    return filter { item ->
        ignoreKeywords.none { keyword ->
            item.name.contains(keyword, ignoreCase = true)
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
private fun List<SearchResultItem>.sortedUsing(preferredKeywords: List<String>): List<SearchResultItem> {
    return sortedWith(
        compareByDescending<SearchResultItem> { item ->
            preferredKeywords.count { keyword ->
                item.name.contains(keyword, ignoreCase = true)
            }
        }.thenByDescending { item ->
            preferredKeywords.mapIndexed { index, keyword ->
                if (item.name.contains(keyword, ignoreCase = true)) {
                    preferredKeywords.size - index
                } else {
                    0
                }
            }.sum()
        }
    )
}
