package com.felixbrucker.simklcalendar.data.util

import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import java.util.Locale

/**
 * Unified formatting utility for media items, seasons, and episodes.
 * Ensures consistent handling across notifications, detail screens, calendar cards, and toasts.
 */
object MediaFormatter {

    /**
     * Checks if this media item is an Anime and belongs to Season 1 (or season is unspecified).
     */
    fun isAnimeSeasonOne(mediaType: MediaType, season: Int?): Boolean {
        return mediaType == MediaType.ANIME && (season == null || season == 1)
    }

    /**
     * Formats the episode code (e.g., "E05" for Anime Season 1, "S02E05" for TV or multi-season Anime).
     */
    fun formatEpisodeCode(
        mediaType: MediaType,
        season: Int?,
        episodeNumber: Int?
    ): String {
        val epNum = episodeNumber ?: 1
        return if (isAnimeSeasonOne(mediaType, season)) {
            String.format(Locale.US, "E%02d", epNum)
        } else {
            val sNum = season ?: 1
            String.format(Locale.US, "S%02dE%02d", sNum, epNum)
        }
    }

    /**
     * Formats the episode code for calendar card subtitles (e.g., "E05" or "S02 • E05").
     */
    fun formatEpisodeCardBadge(
        mediaType: MediaType,
        season: Int?,
        episodeNumber: Int?
    ): String {
        val epNum = episodeNumber ?: 1
        return if (isAnimeSeasonOne(mediaType, season)) {
            String.format(Locale.US, "E%02d", epNum)
        } else {
            val sNum = season ?: 1
            String.format(Locale.US, "S%02d • E%02d", sNum, epNum)
        }
    }

    /**
     * Formats the season label (e.g., "Season" for Anime Season 1, "Season 2" for TV / multi-season Anime).
     */
    fun formatSeasonLabel(
        mediaType: MediaType,
        season: Int?
    ): String {
        val sNum = season ?: 1
        return if (isAnimeSeasonOne(mediaType, season)) {
            "Season"
        } else {
            "Season $sNum"
        }
    }

    /**
     * Formats the detail screen header label for episode slug ("Episode" vs "Season & Episode").
     */
    fun formatEpisodeSlugHeader(
        mediaType: MediaType,
        season: Int?
    ): String {
        return if (isAnimeSeasonOne(mediaType, season)) {
            "Episode"
        } else {
            "Season & Episode"
        }
    }

    /**
     * Formats the SIMKL URL for a media item.
     */
    fun formatSimklUrl(simklId: Int, type: MediaType): String {
        val typePath = when (type) {
            MediaType.TV -> "tv"
            MediaType.ANIME -> "anime"
            MediaType.MOVIE -> "movies"
        }
        return "https://simkl.com/$typePath/$simklId"
    }

    /**
     * Formats the toast message when an episode is marked as watched.
     * Anime S1: "Marked [Title] E05 as watched"
     * TV / Anime S2+: "Marked [Title] S01E05 as watched"
     */
    fun formatEpisodeWatchedToast(
        showTitle: String?,
        mediaType: MediaType,
        season: Int?,
        episodeNumber: Int
    ): String {
        val epCode = formatEpisodeCode(mediaType, season, episodeNumber)
        val prefix = if (!showTitle.isNullOrBlank()) "$showTitle $epCode" else epCode
        return "Marked $prefix as watched"
    }

    /**
     * Formats the toast message when a season is marked as watched.
     * Anime S1: "Marked [Title] as watched"
     * TV / Anime S2+: "Marked [Title] Season 2 as watched"
     */
    fun formatSeasonWatchedToast(
        showTitle: String?,
        mediaType: MediaType,
        season: Int?,
        isCompleted: Boolean = false
    ): String {
        val sNum = season ?: 1
        val target = if (isAnimeSeasonOne(mediaType, season)) {
            if (!showTitle.isNullOrBlank()) showTitle else "anime"
        } else {
            if (!showTitle.isNullOrBlank()) "$showTitle Season $sNum" else "Season $sNum"
        }
        val suffix = if (isCompleted) " (Show Completed)" else ""
        return "Marked $target as watched$suffix"
    }

    /**
     * Formats the toast message when a movie is marked as watched.
     * "Marked [Title] as watched"
     */
    fun formatMovieWatchedToast(showTitle: String?): String {
        val target = if (!showTitle.isNullOrBlank()) showTitle else "Movie"
        return "Marked $target as watched"
    }

    /**
     * Formats the toast message when an episode is marked as unwatched.
     */
    fun formatEpisodeUnwatchedToast(
        showTitle: String?,
        mediaType: MediaType,
        season: Int?,
        episodeNumber: Int
    ): String {
        val epCode = formatEpisodeCode(mediaType, season, episodeNumber)
        val prefix = if (!showTitle.isNullOrBlank()) "$showTitle $epCode" else epCode
        return "Marked $prefix as unwatched"
    }

    /**
     * Formats the toast message when a season is marked as unwatched.
     */
    fun formatSeasonUnwatchedToast(
        showTitle: String?,
        mediaType: MediaType,
        season: Int?
    ): String {
        val sNum = season ?: 1
        val target = if (isAnimeSeasonOne(mediaType, season)) {
            if (!showTitle.isNullOrBlank()) showTitle else "anime"
        } else {
            if (!showTitle.isNullOrBlank()) "$showTitle Season $sNum" else "Season $sNum"
        }
        return "Marked $target as unwatched"
    }

    /**
     * Formats the toast message when a movie is marked as unwatched.
     */
    fun formatMovieUnwatchedToast(showTitle: String?): String {
        val target = if (!showTitle.isNullOrBlank()) showTitle else "Movie"
        return "Marked $target as unwatched"
    }

    /**
     * Formats notification content (title & message).
     */
    fun formatNotificationContent(
        showTitle: String,
        type: MediaType,
        episodeTitle: String?,
        season: Int?,
        episodeNumber: Int?,
        isFinale: Boolean,
        totalEpisodes: Int? = null,
        movieReleaseType: MovieReleaseType? = null
    ): Pair<String, String> {
        if (type == MediaType.MOVIE) {
            return if (movieReleaseType == MovieReleaseType.THEATER) {
                "Movie In Theaters Today" to "$showTitle is now in theaters!"
            } else {
                "Movie Released Today" to "$showTitle is now available on Digital / DVD!"
            }
        }

        if (isFinale) {
            val title = "Season finished airing"
            val total = totalEpisodes ?: episodeNumber
            val episodeCountStr = total?.let { "$it ${if (it == 1) "Episode" else "Episodes"}" }

            val finaleTag = when {
                isAnimeSeasonOne(type, season) -> if (episodeCountStr != null) ": $episodeCountStr" else ""
                season != null && episodeCountStr != null -> String.format(Locale.US, " S%02d: %s", season, episodeCountStr)
                season != null -> String.format(Locale.US, " S%02d", season)
                episodeCountStr != null -> ": $episodeCountStr"
                else -> ""
            }
            val message = "$showTitle$finaleTag"
            return title to message
        }

        val title = "New Episode Released"
        val epCode = if (episodeNumber != null) {
            " " + formatEpisodeCode(type, season, episodeNumber)
        } else {
            ""
        }
        val epName = if (!episodeTitle.isNullOrBlank()) ": \"$episodeTitle\"" else ""
        val message = "$showTitle$epCode$epName is now airing."
        return title to message
    }
}

// Extension properties for convenience on CalendarItemWithWatchlist
val CalendarItemWithWatchlist.formattedEpisodeCode: String
    get() = MediaFormatter.formatEpisodeCode(type, season, episodeNumber)

val CalendarItemWithWatchlist.formattedEpisodeCardBadge: String
    get() = MediaFormatter.formatEpisodeCardBadge(type, season, episodeNumber)

val CalendarItemWithWatchlist.formattedSeasonLabel: String
    get() = MediaFormatter.formatSeasonLabel(type, season)

val CalendarItemWithWatchlist.formattedEpisodeSlugHeader: String
    get() = MediaFormatter.formatEpisodeSlugHeader(type, season)
