package com.felixbrucker.simklcalendar.data.provider.contract

import android.net.Uri
import androidx.core.net.toUri

/**
 * Contract for the Simkl Calendar ContentProvider.
 * This class can be copied into other apps to facilitate integration.
 */
object SimklContract {
    const val AUTHORITY = "com.felixbrucker.simklcalendar.provider"
    val BASE_CONTENT_URI: Uri = "content://$AUTHORITY".toUri()

    /**
     * Permission required to read from this provider.
     */
    const val READ_PERMISSION = "com.felixbrucker.simklcalendar.READ_DATABASE"

    /**
     * Columns for Tracked Watchlist Items.
     */
    object TrackedItems {
        const val PATH = "tracked_watchlist_items"
        val CONTENT_URI: Uri = BASE_CONTENT_URI.buildUpon().appendPath(PATH).build()

        const val COLUMN_SIMKL_ID = "simklId"
        const val COLUMN_TYPE = "type"
        const val COLUMN_TITLE = "title"
        const val COLUMN_TITLE_ROMAJI = "titleRomaji"
        const val COLUMN_POSTER = "poster"
    }

    /**
     * Columns for Calendar Items (Episodes and Movie Releases).
     */
    object CalendarItems {
        const val PATH = "calendar_items"
        val CONTENT_URI: Uri = BASE_CONTENT_URI.buildUpon().appendPath(PATH).build()

        const val COLUMN_PRIMARY_KEY = "primaryKey"
        const val COLUMN_SIMKL_ID = "simklId"
        const val COLUMN_EPISODE_TITLE = "episodeTitle"
        const val COLUMN_SEASON = "season"
        const val COLUMN_EPISODE_NUMBER = "episodeNumber"
        const val COLUMN_DATE = "date"
        const val COLUMN_MOVIE_RELEASE_TYPE = "movieReleaseType"
        const val COLUMN_IS_SEASON_PREMIERE = "isSeasonPremiere"
        const val COLUMN_IS_SEASON_FINALE = "isSeasonFinale"
        const val COLUMN_IS_NOTIFIED = "isNotified"
        const val COLUMN_WATCHED_AT = "watchedAt"
    }

    /**
     * Columns for Watched Episodes.
     */
    object WatchedEpisodes {
        const val PATH = "watched_episodes"
        val CONTENT_URI: Uri = BASE_CONTENT_URI.buildUpon().appendPath(PATH).build()

        const val COLUMN_SIMKL_ID = "simklId"
        const val COLUMN_SEASON = "season"
        const val COLUMN_EPISODE_NUMBER = "episodeNumber"
        const val COLUMN_WATCHED_AT = "watchedAt"
    }
}
