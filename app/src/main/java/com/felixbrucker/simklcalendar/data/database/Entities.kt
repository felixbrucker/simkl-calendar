package com.felixbrucker.simklcalendar.data.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import java.time.Instant
import androidx.core.net.toUri

@Entity(tableName = "user_token")
data class UserToken(
    @PrimaryKey val id: Int = 1, // Single-row lock for active user
    val accessToken: String,
    val username: String
)

@Entity(
    tableName = "calendar_items",
    foreignKeys = [
        ForeignKey(
            entity = TrackedWatchlistItem::class,
            parentColumns = ["simklId"],
            childColumns = ["simklId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["simklId"])
    ]
)
data class CalendarItem(
    @PrimaryKey val primaryKey: String, // e.g. "v2_${simklId}_${season}_${episodeNumber}" or "v2_${simklId}_theater" / "v2_${simklId}_digital"
    val simklId: Int, // Reference to TrackedWatchlistItem
    val episodeTitle: String?, // Strictly episode title (null for movies)
    val season: Int?, // Season number (null for movies)
    val episodeNumber: Int?, // Episode number (null for movies)
    val date: Instant, // Full air date/time as native Instant object
    val movieReleaseType: MovieReleaseType? = null, // Strictly for movies (THEATER, DIGITAL)
    val isSeasonPremiere: Boolean,
    val isSeasonFinale: Boolean,
    val isNotified: Boolean = false, // Track whether notification has been dispatched
    val watchedAt: Instant? = null, // Timestamp of when the episode was watched
    @ColumnInfo(defaultValue = "NOT_AIRED_YET") val mediaStatus: MediaStatus = MediaStatus.NOT_AIRED_YET,
    val downloadTaskId: String? = null,
    val downloadPath: String? = null
) {
    val isWatched: Boolean
        get() = watchedAt != null

    fun updatedWith(newItem: CalendarItem): CalendarItem {
        val isDateRescheduledToFuture = this.date != newItem.date && newItem.date.isAfter(Instant.now())
        val updatedNotified = if (isDateRescheduledToFuture) false else this.isNotified

        return this.copy(
            episodeTitle = newItem.episodeTitle,
            season = newItem.season,
            episodeNumber = newItem.episodeNumber,
            date = newItem.date,
            movieReleaseType = newItem.movieReleaseType,
            isSeasonPremiere = newItem.isSeasonPremiere,
            isSeasonFinale = newItem.isSeasonFinale,
            isNotified = updatedNotified,
            watchedAt = newItem.watchedAt,
            mediaStatus = if (newItem.mediaStatus != MediaStatus.NOT_AIRED_YET) newItem.mediaStatus else this.mediaStatus,
            downloadTaskId = newItem.downloadTaskId ?: this.downloadTaskId,
            downloadPath = newItem.downloadPath ?: this.downloadPath
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
    val titleRomaji: String? = null, // Romaji title for anime
    val poster: String? = null // URL for show poster image
) {
    companion object

    fun updatedWith(newItem: TrackedWatchlistItem): TrackedWatchlistItem {
        val newTitle = if (newItem.title.isNotBlank()) newItem.title else this.title
        val newRomaji = if (!newItem.titleRomaji.isNullOrBlank()) newItem.titleRomaji else this.titleRomaji
        val newPoster = if (!newItem.poster.isNullOrBlank()) newItem.poster else this.poster

        return this.copy(
            type = newItem.type,
            title = newTitle,
            titleRomaji = newRomaji,
            poster = newPoster
        )
    }
}

/**
 * Joined relational model combining a CalendarItem episode/movie release with its parent TrackedWatchlistItem metadata.
 */
data class CalendarItemWithWatchlist(
    @Embedded val calendarItem: CalendarItem,
    @Relation(
        parentColumn = "simklId",
        entityColumn = "simklId"
    )
    val watchlistItem: TrackedWatchlistItem?
) {
    val primaryKey: String get() = calendarItem.primaryKey
    val simklId: Int get() = calendarItem.simklId
    val title: String get() = watchlistItem?.title ?: "Untitled"
    val titleRomaji: String? get() = watchlistItem?.titleRomaji
    val poster: String? get() = watchlistItem?.poster
    val type: MediaType get() = watchlistItem?.type ?: MediaType.TV
    val episodeTitle: String? get() = calendarItem.episodeTitle
    val season: Int? get() = calendarItem.season
    val episodeNumber: Int? get() = calendarItem.episodeNumber
    val date: Instant get() = calendarItem.date
    val movieReleaseType: MovieReleaseType? get() = calendarItem.movieReleaseType
    val isSeasonPremiere: Boolean get() = calendarItem.isSeasonPremiere
    val isSeasonFinale: Boolean get() = calendarItem.isSeasonFinale
    val isNotified: Boolean get() = calendarItem.isNotified
    val watchedAt: Instant? get() = calendarItem.watchedAt
    val isWatched: Boolean get() = calendarItem.isWatched
    val mediaStatus: MediaStatus get() = calendarItem.mediaStatus
    val downloadTaskId: String? get() = calendarItem.downloadTaskId
    val downloadPath: String? get() = calendarItem.downloadPath
}

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
    val associatedTypes: List<MediaType> = listOf(MediaType.TV, MediaType.ANIME, MediaType.MOVIE),
    @ColumnInfo(defaultValue = "0") val position: Int = 0
) {
    /**
     * Builds the complete URL by replacing supported placeholders with values from the given CalendarItemWithWatchlist.
     * Supported placeholders:
     * - {TITLE} -> Show/Movie/Anime title (raw / unencoded)
     * - {TITLE_URL_ENCODED} -> Show/Movie/Anime title (URL encoded)
     * - {TITLE_ROMAJI} -> Romaji anime title (raw / unencoded, falls back to regular title if not available)
     * - {TITLE_ROMAJI_URL_ENCODED} -> Romaji anime title (URL encoded, falls back to regular title if not available)
     * - {SEASON} -> Season number (e.g. "4")
     * - {EPISODE} -> Episode number (e.g. "3")
     * - {SEASON_SLUG} -> Season code (e.g. "S04")
     * - {EPISODE_SLUG} -> Episode code (e.g. "S04E03" or "E03")
     */
    fun buildUrl(item: CalendarItemWithWatchlist): String {
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

        val s = item.season
        val e = item.episodeNumber

        val seasonSlugStr = if (item.type == MediaType.MOVIE) {
            ""
        } else if (s != null && s > 0) {
            String.format(java.util.Locale.US, "S%02d", s)
        } else {
            "S01"
        }

        val episodeSlugStr = if (item.type == MediaType.MOVIE) {
            ""
        } else if (s != null && e != null) {
            String.format(java.util.Locale.US, "S%02dE%02d", s, e)
        } else if (e != null) {
            String.format(java.util.Locale.US, "E%02d", e)
        } else {
            ""
        }

        var result = urlTemplate

        // Replace supported {CAPSLOCK PLACEHOLDER} tokens (case-insensitive for user convenience)
        // More specific / longer tokens are replaced first to prevent partial matches
        result = result.replace("{TITLE_ROMAJI_URL_ENCODED}", encodedRomajiTitle, ignoreCase = true)
        result = result.replace("{TITLE_ROMAJI_ENCODED}", encodedRomajiTitle, ignoreCase = true)
        result = result.replace("{TITLE_ROMAJI_URLENCODED}", encodedRomajiTitle, ignoreCase = true)
        result = result.replace("{ROMAJI_TITLE_URL_ENCODED}", encodedRomajiTitle, ignoreCase = true)
        result = result.replace("{ROMAJI_TITLE_ENCODED}", encodedRomajiTitle, ignoreCase = true)
        result = result.replace("{ROMAJI_TITLE_URLENCODED}", encodedRomajiTitle, ignoreCase = true)

        result = result.replace("{TITLE_URL_ENCODED}", encodedTitle, ignoreCase = true)
        result = result.replace("{TITLE_ENCODED}", encodedTitle, ignoreCase = true)
        result = result.replace("{TITLE_URLENCODED}", encodedTitle, ignoreCase = true)
        result = result.replace("{ENCODED_TITLE}", encodedTitle, ignoreCase = true)

        result = result.replace("{TITLE_ROMAJI}", rawRomajiTitle, ignoreCase = true)
        result = result.replace("{ROMAJI_TITLE}", rawRomajiTitle, ignoreCase = true)
        result = result.replace("{TITLE}", rawTitle, ignoreCase = true)

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
            val uri = cleanUrl.toUri()
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

@Entity(tableName = "item_download_settings")
data class ItemDownloadSettings(
    @PrimaryKey val simklId: Int,
    val downloadUnwatched: Boolean? = null,
    val qualityOverride: String? = null, // "4K", "1080p", "720p"
    val preferHevcOverride: Boolean? = null,
    val titleOverride: String? = null,
    val seasonOverrides: Map<Int, Int>? = null // Map of <Original Season, Search Season Override>
)



