package com.felixbrucker.simklcalendar.data.network

import android.content.SharedPreferences
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.model.MediaType
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
        val preferredKeywords = downloadPrefs.getStringSet("preferred_keywords", emptySet()) ?: emptySet()
        val ignoreKeywords = downloadPrefs.getStringSet("ignore_keywords", emptySet()) ?: emptySet()
        val allPreferredKeywords = preferredKeywords.toMutableSet()
        val preferHevc = itemSettings?.preferHevcOverride ?: globalPreferHevc
        if (preferHevc) {
            allPreferredKeywords.addAll(listOf("hevc", "x265"))
        }

        var term = when(item.type) {
            MediaType.TV -> String.format(Locale.US, "%s S%02dE%02d", searchTitle, searchSeason, episode ?: 1)
            MediaType.ANIME -> String.format(Locale.US, "%s %02d", searchTitle, episode ?: 1)
            MediaType.MOVIE -> searchTitle
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

        return processResults(results, allPreferredKeywords, ignoreKeywords)
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
