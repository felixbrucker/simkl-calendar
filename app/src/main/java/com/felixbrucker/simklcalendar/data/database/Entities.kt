package com.felixbrucker.simklcalendar.data.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.felixbrucker.simklcalendar.data.model.EpisodeSearchStyle
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import java.time.Instant
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import kotlin.math.abs

@Entity(tableName = "user_token")
data class UserToken(
    @PrimaryKey val id: Int = 1, // Single-row lock for active user
    val accessToken: String,
    val username: String,
    val refreshToken: String? = null,
    val accessTokenExpiresAt: Instant? = null,
    val refreshTokenExpiresAt: Instant? = null
)

@Entity(
    tableName = "calendar_items",
    foreignKeys = [
        ForeignKey(
            entity = TrackedWatchlistItem::class,
            parentColumns = ["simklId"],
            childColumns = ["simklId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["simklId"]),
        Index(value = ["date"]),
        Index(value = ["watchedAt"])
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
    val watchedAt: Instant? = null // Timestamp of when the episode was watched
) {
    val isWatched: Boolean get() = watchedAt != null

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
            watchedAt = newItem.watchedAt
        )
    }
}

@Entity(
    tableName = "local_item_state",
    foreignKeys = [
        ForeignKey(
            entity = CalendarItem::class,
            parentColumns = ["primaryKey"],
            childColumns = ["primaryKey"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        )
    ]
)
data class LocalItemState(
    @PrimaryKey val primaryKey: String, // Reference to CalendarItem.primaryKey
    @ColumnInfo(defaultValue = "NOT_AIRED_YET") val mediaStatus: MediaStatus = MediaStatus.NOT_AIRED_YET,
    val downloadTaskId: String? = null
)

@Entity(
    tableName = "notification_settings",
    foreignKeys = [
        ForeignKey(
            entity = TrackedWatchlistItem::class,
            parentColumns = ["simklId"],
            childColumns = ["simklId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        )
    ]
)
data class NotificationSetting(
    @PrimaryKey val simklId: Int, // Simkl ID
    val notifyEveryEpisode: Boolean = false,
    val notifyAiredLastEpisode: Boolean = true
)

@Entity(
    tableName = "tracked_watchlist_items",
    indices = [
        Index(value = ["type"])
    ]
)
data class TrackedWatchlistItem(
    @PrimaryKey val simklId: Int, // Simkl ID
    val type: MediaType,
    val title: String,
    val titleRomaji: String? = null, // Romaji title for anime
    val poster: String? = null // URL for show poster image
) {
    companion object

    fun updatedWith(newItem: TrackedWatchlistItem): TrackedWatchlistItem {
        val newTitle = newItem.title.ifBlank { this.title }
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
@Immutable
data class CalendarItemWithWatchlist(
    @Embedded val calendarItem: CalendarItem,
    @Relation(
        parentColumn = "simklId",
        entityColumn = "simklId"
    )
    val watchlistItem: TrackedWatchlistItem?,
    @Relation(
        parentColumn = "primaryKey",
        entityColumn = "primaryKey"
    )
    val localState: LocalItemState? = null,
    @Relation(
        parentColumn = "simklId",
        entityColumn = "simklId"
    )
    val downloadSettings: ItemDownloadSettings? = null
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
    val mediaStatus: MediaStatus get() = localState?.mediaStatus ?: MediaStatus.NOT_AIRED_YET
    val downloadTaskId: String? get() = localState?.downloadTaskId
    val notificationId: Int get() = abs(primaryKey.hashCode())
}

@Entity(
    tableName = "watched_episodes",
    primaryKeys = ["simklId", "season", "episodeNumber"],
    foreignKeys = [
        ForeignKey(
            entity = TrackedWatchlistItem::class,
            parentColumns = ["simklId"],
            childColumns = ["simklId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["simklId"])
    ]
)
data class WatchedEpisode(
    val simklId: Int,
    val season: Int,
    val episodeNumber: Int,
    val watchedAt: Instant? = null
)

@Entity(
    tableName = "custom_search_links",
    indices = [
        Index(value = ["position"])
    ]
)
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
    fun buildUrl(
        title: String,
        titleRomaji: String?,
        type: MediaType,
        season: Int? = null,
        episode: Int? = null,
    ): String {
        val encodedTitle = try {
            java.net.URLEncoder.encode(title, "UTF-8")
        } catch (_: Exception) {
            title
        }

        val rawRomajiTitle = titleRomaji?.takeIf { it.isNotBlank() } ?: title
        val encodedRomajiTitle = try {
            java.net.URLEncoder.encode(rawRomajiTitle, "UTF-8")
        } catch (_: Exception) {
            rawRomajiTitle
        }

        val seasonNumStr = season?.toString() ?: ""
        val episodeNumStr = episode?.toString() ?: ""

        val seasonSlugStr = if (type == MediaType.MOVIE) {
            ""
        } else if (season != null && season > 0) {
            String.format(java.util.Locale.US, "S%02d", season)
        } else {
            ""
        }

        val episodeSlugStr = if (type == MediaType.MOVIE) {
            ""
        } else if (season != null && episode != null) {
            String.format(java.util.Locale.US, "S%02dE%02d", season, episode)
        } else if (episode != null) {
            String.format(java.util.Locale.US, "E%02d", episode)
        } else {
            ""
        }

        var result = urlTemplate

        // Replace supported {CAPSLOCK PLACEHOLDER} tokens (case-insensitive for user convenience)
        // More specific / longer tokens are replaced first to prevent partial matches
        result = result.replace("{TITLE}", title, ignoreCase = true)
        result = result.replace("{TITLE_URL_ENCODED}", encodedTitle, ignoreCase = true)
        result = result.replace("{TITLE_ROMAJI}", rawRomajiTitle, ignoreCase = true)
        result = result.replace("{TITLE_ROMAJI_URL_ENCODED}", encodedRomajiTitle, ignoreCase = true)
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
            host.ifBlank { cleanUrl.substringBefore("/").substringBefore("?") }
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

@Entity(
    tableName = "active_notifications",
    foreignKeys = [
        ForeignKey(
            entity = CalendarItem::class,
            parentColumns = ["primaryKey"],
            childColumns = ["primaryKey"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        )
    ]
)
data class ActiveNotification(
    @PrimaryKey val primaryKey: String
)

@Entity(
    tableName = "item_download_settings",
    foreignKeys = [
        ForeignKey(
            entity = TrackedWatchlistItem::class,
            parentColumns = ["simklId"],
            childColumns = ["simklId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        )
    ]
)
data class ItemDownloadSettings(
    @PrimaryKey val simklId: Int,
    val downloadUnwatched: Boolean? = null,
    val downloadSeasonUnwatched: Boolean? = null,
    val qualityOverride: String? = null, // "4K", "1080p", "720p"
    val preferHevcOverride: Boolean? = null,
    val titleOverride: String? = null,
    val seasonOverrides: Map<Int, Int>? = null, // Map of <Original Season, Search Season Override>
    val downloadSubdirectoryOverride: String? = null,
    val episodeSearchStyle: EpisodeSearchStyle? = null
)



