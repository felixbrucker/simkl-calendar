package com.felixbrucker.simklcalendar.data.util

import android.content.SharedPreferences
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.torrent_search_api.Category
import com.felixbrucker.torrent_search_api.NyaaProvider
import com.felixbrucker.torrent_search_api.OrderBy
import com.felixbrucker.torrent_search_api.SearchResultItem
import com.felixbrucker.torrent_search_api.TpbProvider

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
        val preferredKeywords = downloadPrefs.getStringSet("preferred_keywords", emptySet()) ?: emptySet()
        val ignoreKeywords = downloadPrefs.getStringSet("ignore_keywords", emptySet()) ?: emptySet()

        val quality = itemSettings?.qualityOverride ?: globalQuality
        val preferHevc = itemSettings?.preferHevcOverride ?: globalPreferHevc

        var baseQuery = when(item.type) {
            MediaType.TV -> String.format(java.util.Locale.US, "%s S%02dE%02d", searchTitle, searchSeason, episode ?: 1)
            MediaType.ANIME -> String.format(java.util.Locale.US, "%s %02d", searchTitle, episode ?: 1)
            MediaType.MOVIE -> searchTitle
        }

        if (quality.isNotEmpty()) {
            baseQuery += " $quality"
        }

        val provider = if (item.type == MediaType.ANIME) nyaaProvider else tpbProvider
        val category = if (item.type == MediaType.ANIME) Category.ANIME_ENGLISH_TRANSLATED else Category.VIDEO

        if (preferHevc) {
            val hevcSuffix = if (item.type == MediaType.ANIME) " hevc" else " x265"
            val hevcQuery = baseQuery + hevcSuffix
            val hevcResults = provider
                .search(term = hevcQuery, category = category, orderBy = OrderBy.SeederDescending)
                .getOrThrow()
                .results
            val filteredAndRankedHevcResults = processResults(hevcResults, preferredKeywords, ignoreKeywords)
            if (filteredAndRankedHevcResults.isNotEmpty()) {
                return filteredAndRankedHevcResults
            }
        }

        val results = provider
            .search(term = baseQuery, category = category, orderBy = OrderBy.SeederDescending)
            .getOrThrow()
            .results

        return processResults(results, preferredKeywords, ignoreKeywords)
    }

    private fun processResults(
        results: List<SearchResultItem>,
        preferredKeywords: Set<String>,
        ignoreKeywords: Set<String>
    ): List<SearchResultItem> {
        return results
            .filter { item ->
                ignoreKeywords.none { keyword ->
                    item.name.contains(keyword, ignoreCase = true)
                }
            }
            .sortedByDescending { item ->
                preferredKeywords.count { keyword ->
                    item.name.contains(keyword, ignoreCase = true)
                }
            }
    }
}
