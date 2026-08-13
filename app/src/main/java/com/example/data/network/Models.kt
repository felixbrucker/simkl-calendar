package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OAuthTokenRequest(
    @Json(name = "code") val code: String,
    @Json(name = "client_id") val clientId: String,
    @Json(name = "code_verifier") val codeVerifier: String,
    @Json(name = "redirect_uri") val redirectUri: String,
    @Json(name = "grant_type") val grantType: String = "authorization_code"
)

@JsonClass(generateAdapter = true)
data class OAuthTokenResponse(
    @Json(name = "access_token") val accessToken: String?,
    @Json(name = "token_type") val tokenType: String?,
    @Json(name = "scope") val scope: String?,
    @Json(name = "expires_in") val expiresIn: Long?
)

@JsonClass(generateAdapter = true)
data class UserSettingsResponse(
    @Json(name = "user") val user: UserProfile?,
    @Json(name = "account") val account: UserAccountProfile?
)

@JsonClass(generateAdapter = true)
data class UserProfile(
    @Json(name = "name") val name: String?,
    @Json(name = "joined_at") val joinedAt: String?,
    @Json(name = "gender") val gender: String?,
    @Json(name = "avatar") val avatar: String?,
    @Json(name = "bio") val bio: String?,
    @Json(name = "loc") val loc: String?,
    @Json(name = "age") val age: String?
)

@JsonClass(generateAdapter = true)
data class UserAccountProfile(
    @Json(name = "id") val id: Int?,
    @Json(name = "timezone") val timezone: String?,
    @Json(name = "type") val type: String?
)

// CDN V2 Calendar models (data.simkl.in/calendar/v2/*.json)
@JsonClass(generateAdapter = true)
data class SimklV2CalendarResponse(
    @Json(name = "calendar") val calendar: List<SimklV2CalendarEntry>?,
    @Json(name = "metadata") val metadata: Map<String, SimklV2Metadata>?
)

@JsonClass(generateAdapter = true)
data class SimklV2CalendarEntry(
    @Json(name = "simkl_id") val simklId: Int?,
    @Json(name = "date") val date: String?,
    @Json(name = "premiere_type") val premiereType: Any?,
    @Json(name = "finale_type") val finaleType: Any?,
    @Json(name = "episode") val episode: SimklV2Episode?
)

@JsonClass(generateAdapter = true)
data class SimklV2Episode(
    @Json(name = "season") val season: Int?,
    @Json(name = "episode") val episode: Int?,
    @Json(name = "title") val title: String?,
    @Json(name = "url") val url: String?
)

@JsonClass(generateAdapter = true)
data class SimklV2Metadata(
    @Json(name = "title") val title: String?,
    @Json(name = "poster") val poster: String?,
    @Json(name = "status") val status: String?,
    @Json(name = "genres") val genres: List<String>?,
    @Json(name = "url") val url: String?,
    @Json(name = "ids") val ids: SimklIds?
)

@JsonClass(generateAdapter = true)
data class SimklMedia(
    @Json(name = "title") val title: String?,
    @Json(name = "ids") val ids: SimklIds?,
    @Json(name = "poster") val poster: String?,
    @Json(name = "status") val status: String?
)

@JsonClass(generateAdapter = true)
data class SimklIds(
    @Json(name = "simkl") val simkl: Int?,
    @Json(name = "simkl_id") val simklId: Int?,
    @Json(name = "imdb") val imdb: String?,
    @Json(name = "tmdb") val tmdb: String?,
    @Json(name = "tvdb") val tvdb: String?
)

@JsonClass(generateAdapter = true)
data class SimklEpisode(
    @Json(name = "title") val title: String?,
    @Json(name = "season") val season: Int?,
    @Json(name = "number") val number: Int?,
    @Json(name = "episode") val episodeNumber: Int?,
    @Json(name = "date") val date: String?
)

@JsonClass(generateAdapter = true)
data class SimklNextToWatchInfo(
    @Json(name = "title") val title: String? = null,
    @Json(name = "season") val season: Int? = null,
    @Json(name = "episode") val episode: Int? = null,
    @Json(name = "date") val date: String? = null,
    @Json(name = "url") val url: String? = null
)

@JsonClass(generateAdapter = true)
data class SyncAllItemsResponse(
    @Json(name = "shows") val shows: List<SyncShowItem>?,
    @Json(name = "anime") val anime: List<SyncShowItem>?,
    @Json(name = "movies") val movies: List<SyncMovieItem>?
)

@JsonClass(generateAdapter = true)
data class SyncShowItem(
    @Json(name = "added_to_watchlist_at") val addedToWatchlistAt: String? = null,
    @Json(name = "last_watched_at") val lastWatchedAt: String? = null,
    @Json(name = "user_rated_at") val userRatedAt: String? = null,
    @Json(name = "user_rating") val userRating: Int? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "last_watched") val lastWatched: String? = null,
    @Json(name = "next_to_watch") val nextToWatch: String? = null,
    @Json(name = "watched_episodes_count") val watchedEpisodesCount: Int? = null,
    @Json(name = "total_episodes_count") val totalEpisodesCount: Int? = null,
    @Json(name = "not_aired_episodes_count") val notAiredEpisodesCount: Int? = null,
    @Json(name = "anime_type") val animeType: String? = null,
    @Json(name = "show") val show: SimklMedia? = null,
    @Json(name = "anime") val anime: SimklMedia? = null,
    @Json(name = "next_to_watch_info") val nextToWatchInfo: SimklNextToWatchInfo? = null
)

@JsonClass(generateAdapter = true)
data class SyncMovieItem(
    @Json(name = "added_to_watchlist_at") val addedToWatchlistAt: String? = null,
    @Json(name = "last_watched_at") val lastWatchedAt: String? = null,
    @Json(name = "user_rated_at") val userRatedAt: String? = null,
    @Json(name = "user_rating") val userRating: Int? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "last_watched") val lastWatched: String? = null,
    @Json(name = "movie") val movie: SimklMedia? = null
)
