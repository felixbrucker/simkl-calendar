package com.felixbrucker.simklcalendar.data.model

enum class MediaType(val key: String, val displayName: String) {
    TV("tv", "TV Show"),
    ANIME("anime", "Anime"),
    MOVIE("movie", "Movie");

    val defaultEpisodeSearchStyle: EpisodeSearchStyle
        get() = when (this) {
            ANIME -> EpisodeSearchStyle.episode
            else -> EpisodeSearchStyle.seasonAndEpisode
        }

    companion object {
        fun fromKey(key: String?): MediaType {
            return when (key?.lowercase()) {
                "tv", "shows", "show" -> TV
                "anime" -> ANIME
                "movie", "movies" -> MOVIE
                else -> TV
            }
        }

    }
}

enum class MovieReleaseType(val displayName: String) {
    THEATER("Theater Release"),
    DIGITAL("Digital / DVD Release");
}

enum class WatchlistStatus(val key: String) {
    WATCHING("watching"),
    PLAN_TO_WATCH("plantowatch"),
    COMPLETED("completed"),
    HOLD("hold"),
    DROPPED("dropped");

    companion object {
        fun fromKey(key: String?): WatchlistStatus {
            return when (key?.lowercase()) {
                "watching" -> WATCHING
                "plantowatch", "plan_to_watch" -> PLAN_TO_WATCH
                "completed" -> COMPLETED
                "hold" -> HOLD
                "dropped" -> DROPPED
                else -> WATCHING
            }
        }

        fun fromString(key: String?): WatchlistStatus = fromKey(key)
    }
}

enum class MediaStatus(val displayName: String) {
    IGNORED("Ignored"),
    NOT_AIRED_YET("Not Aired Yet"),
    WANTED("Wanted"),
    DOWNLOADING("Downloading"),
    DOWNLOADED("Downloaded");

    companion object {
        fun fromString(str: String?): MediaStatus {
            return entries.firstOrNull { it.name.equals(str, ignoreCase = true) } ?: IGNORED
        }
    }
}

enum class EpisodeSearchStyle {
    seasonAndEpisode,
    episode
}
