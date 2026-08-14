package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_token")
data class UserToken(
    @PrimaryKey val id: Int = 1, // Single-row lock for active user
    val accessToken: String,
    val username: String,
    val loginTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "calendar_items")
data class CalendarItem(
    @PrimaryKey val primaryKey: String, // Constructed as "showId_season_episode_date" or movie "movieId_date"
    val id: Int, // Simkl main ID
    val title: String, // Show or Movie title
    val episodeTitle: String?, // Episode title (null for movies)
    val season: Int?, // Season number (null for movies)
    val episodeNumber: Int?, // Episode number (null for movies)
    val date: String, // Air date/time string, e.g. "YYYY-MM-DD HH:MM:SS"
    val type: String, // "tv", "anime", "movie"
    val isSeasonPremiere: Boolean,
    val isSeasonFinale: Boolean,
    val poster: String?, // URL for show poster image
    val simklId: Int?, // Unique ID for Simkl
    val isLastEpisode: Boolean = false, // Ready to binge?
    val notificationsScheduled: Boolean = false // Track alarm status
)

@Entity(tableName = "notification_settings")
data class NotificationSetting(
    @PrimaryKey val showId: Int, // Simkl ID or hash
    val showTitle: String,
    val type: String, // "tv", "anime", "movie"
    val notifyEveryEpisode: Boolean = false,
    val notifyAiredLastEpisode: Boolean = true
)

@Entity(tableName = "tracked_watchlist_items")
data class TrackedWatchlistItem(
    @PrimaryKey val id: Int, // Simkl ID
    val type: String, // "tv", "anime", "movie"
    val status: String, // "watching", "plantowatch"
    val title: String,
    val poster: String? = null
)

