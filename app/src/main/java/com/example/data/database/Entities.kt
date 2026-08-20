package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.MediaType
import com.example.data.model.MovieReleaseType
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
    val isNotified: Boolean = false // Track whether notification has been dispatched
) {
    fun updatedWith(newItem: CalendarItem): CalendarItem {
        val isDateRescheduledToFuture = this.date != newItem.date && newItem.date.isAfter(Instant.now())
        val updatedNotified = if (isDateRescheduledToFuture) false else this.isNotified

        return this.copy(
            title = newItem.title,
            episodeTitle = newItem.episodeTitle,
            season = newItem.season,
            episodeNumber = newItem.episodeNumber,
            date = newItem.date,
            poster = newItem.poster,
            type = newItem.type,
            movieReleaseType = newItem.movieReleaseType,
            isSeasonPremiere = newItem.isSeasonPremiere,
            isSeasonFinale = newItem.isSeasonFinale,
            isNotified = updatedNotified
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


