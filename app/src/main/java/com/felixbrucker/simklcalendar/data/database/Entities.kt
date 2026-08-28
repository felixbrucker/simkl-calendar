package com.felixbrucker.simklcalendar.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import java.time.Instant

@Entity(tableName = "user_token")
data class UserToken(
    @PrimaryKey val id: Int = 1, // Single-row lock for active user
    val accessToken: String,
    val username: String
)

@Entity(tableName = "calendar_items")
data class CalendarItem(
    @PrimaryKey val primaryKey: String, // e.g. "v2_${simklId}_${season}_${episodeNumber}" or "v2_${simklId}_theater" / "v2_${simklId}_digital"
    val simklId: Int, // Unique ID for Simkl
    val title: String, // Show or Movie title
    val episodeTitle: String?, // Strictly episode title (null for movies)
    val season: Int?, // Season number (null for movies)
    val episodeNumber: Int?, // Episode number (null for movies)
    val date: Instant, // Full air date/time as native Instant object
    val type: MediaType, // MediaType enum (TV, ANIME, MOVIE)
    val movieReleaseType: MovieReleaseType? = null, // Strictly for movies (THEATER, DIGITAL)
    val isSeasonPremiere: Boolean,
    val isSeasonFinale: Boolean,
    val poster: String?, // URL for show poster image
    val isNotified: Boolean = false, // Track whether notification has been dispatched
    val watchedAt: Instant? = null, // Timestamp of when the episode was watched
    val titleRomaji: String? = null // Romaji title for anime
) {
    val isWatched: Boolean
        get() = watchedAt != null

    fun updatedWith(newItem: CalendarItem): CalendarItem {
        val isDateRescheduledToFuture = this.date != newItem.date && newItem.date.isAfter(Instant.now())
        val updatedNotified = if (isDateRescheduledToFuture) false else this.isNotified

        return this.copy(
            title = newItem.title,
            titleRomaji = newItem.titleRomaji ?: this.titleRomaji,
            episodeTitle = newItem.episodeTitle,
            season = newItem.season,
            episodeNumber = newItem.episodeNumber,
            date = newItem.date,
            poster = newItem.poster,
            type = newItem.type,
            movieReleaseType = newItem.movieReleaseType,
            isSeasonPremiere = newItem.isSeasonPremiere,
            isSeasonFinale = newItem.isSeasonFinale,
            isNotified = updatedNotified,
            watchedAt = newItem.watchedAt
        )
    }
}

@Entity(tableName = "notification_settings")
data class NotificationSetting(
    @PrimaryKey val simklId: Int, // Simkl ID
    val notifyEveryEpisode: Boolean = false,
    val notifyAiredLastEpisode: Boolean = true
)

@Entity(tableName = "tracked_watchlist_items")
data class TrackedWatchlistItem(
    @PrimaryKey val simklId: Int, // Simkl ID
    val type: MediaType,
    val title: String,
    val poster: String? = null
)

@Entity(tableName = "watched_episodes", primaryKeys = ["simklId", "season", "episodeNumber"])
data class WatchedEpisode(
    val simklId: Int,
    val season: Int,
    val episodeNumber: Int,
    val watchedAt: Instant? = null
)

@Entity(tableName = "custom_search_links")
data class CustomSearchLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val subtitle: String? = null,
    val urlTemplate: String,
    val associatedTypes: List<MediaType> = listOf(MediaType.TV, MediaType.ANIME, MediaType.MOVIE)
) {
    /**
     * Builds the complete URL by replacing supported placeholders with values from the given CalendarItem.
     * Supported placeholders:
     * - {TITLE} -> Show/Movie/Anime title (URL encoded)
     * - {ROMAJI_TITLE} -> Romaji anime title (URL encoded, falls back to regular title if not available)
     * - {SEASON} -> Season number (e.g. "4")
     * - {EPISODE} -> Episode number (e.g. "3")
     * - {SEASON_SLUG} -> Season code (e.g. "S04")
     * - {EPISODE_SLUG} -> Episode code (e.g. "S04E03" or "E03")
     */
    fun buildUrl(item: CalendarItem): String {
        val rawTitle = item.title
        val encodedTitle = try {
            java.net.URLEncoder.encode(rawTitle, "UTF-8")
        } catch (_: Exception) {
            rawTitle
        }

        val rawRomajiTitle = item.titleRomaji?.takeIf { it.isNotBlank() } ?: item.title
        val encodedRomajiTitle = try {
            java.net.URLEncoder.encode(rawRomajiTitle, "UTF-8")
        } catch (_: Exception) {
            rawRomajiTitle
        }

        val seasonNumStr = item.season?.toString() ?: if (item.type != MediaType.MOVIE) "1" else ""
        val episodeNumStr = item.episodeNumber?.toString() ?: ""

        val seasonSlugStr = if (item.type == MediaType.MOVIE) {
            ""
        } else if (item.season != null && item.season > 0) {
            String.format(java.util.Locale.US, "S%02d", item.season)
        } else {
            "S01"
        }

        val episodeSlugStr = if (item.type == MediaType.MOVIE) {
            ""
        } else if (item.season != null && item.episodeNumber != null) {
            String.format(java.util.Locale.US, "S%02dE%02d", item.season, item.episodeNumber)
        } else if (item.episodeNumber != null) {
            String.format(java.util.Locale.US, "E%02d", item.episodeNumber)
        } else {
            ""
        }

        var result = urlTemplate

        // Replace supported {CAPSLOCK PLACEHOLDER} tokens (case-insensitive for user convenience)
        result = result.replace("{ROMAJI_TITLE}", encodedRomajiTitle, ignoreCase = true)
        result = result.replace("{TITLE}", encodedTitle, ignoreCase = true)
        result = result.replace("{EPISODE_SLUG}", episodeSlugStr, ignoreCase = true)
        result = result.replace("{SEASON_SLUG}", seasonSlugStr, ignoreCase = true)
        result = result.replace("{SEASON}", seasonNumStr, ignoreCase = true)
        result = result.replace("{EPISODE}", episodeNumStr, ignoreCase = true)

        val trimmed = result.trim()
        return if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }

    /**
     * Extracts domain/host from the urlTemplate to fetch favicon.
     */
    fun extractDomain(): String {
        return try {
            val cleanUrl = if (urlTemplate.startsWith("http://", ignoreCase = true) || urlTemplate.startsWith("https://", ignoreCase = true)) {
                urlTemplate
            } else {
                "https://$urlTemplate"
            }
            val uri = android.net.Uri.parse(cleanUrl)
            val host = uri.host ?: ""
            if (host.isNotBlank()) host else cleanUrl.substringBefore("/").substringBefore("?")
        } catch (_: Exception) {
            urlTemplate.substringBefore("/").substringBefore("?")
        }
    }

    /**
     * Returns Google's favicon service URL for this link's domain.
     */
    fun getFaviconUrl(): String {
        val domain = extractDomain()
        return "https://www.google.com/s2/favicons?domain=$domain&sz=64"
    }
}



