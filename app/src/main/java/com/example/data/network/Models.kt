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
    @Json(name = "access_token") val accessToken: String?
)

@JsonClass(generateAdapter = true)
data class UserSettingsResponse(
    @Json(name = "user") val user: UserProfile?
)

@JsonClass(generateAdapter = true)
data class UserProfile(
    @Json(name = "name") val name: String?
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
    @Json(name = "finale_type") val finaleType: Any?,
    @Json(name = "episode") val episode: SimklV2Episode?
)

@JsonClass(generateAdapter = true)
data class SimklV2Episode(
    @Json(name = "season") val season: Int?,
    @Json(name = "episode") val episode: Int?,
    @Json(name = "title") val title: String?
)

@JsonClass(generateAdapter = true)
data class SimklV2Metadata(
    @Json(name = "title") val title: String?,
    @Json(name = "poster") val poster: String?,
    @Json(name = "status") val status: String?,
    @Json(name = "total_episodes") val totalEpisodes: Int? = null,
    @Json(name = "dvd") val dvd: String? = null
)

@JsonClass(generateAdapter = true)
data class SimklMedia(
    @Json(name = "title") val title: String?,
    @Json(name = "poster") val poster: String?,
    @Json(name = "ids") val ids: SimklIds?
)

@JsonClass(generateAdapter = true)
data class SimklIds(
    @Json(name = "simkl") val simkl: Int?,
    @Json(name = "simkl_id") val simklId: Int? = null
)

@JsonClass(generateAdapter = true)
data class SyncActivitiesResponse(
    @Json(name = "all") val all: String? = null
)

@JsonClass(generateAdapter = true)
data class SyncAllItemsResponse(
    @Json(name = "shows") val shows: List<SyncShowItem>?,
    @Json(name = "anime") val anime: List<SyncShowItem>?,
    @Json(name = "movies") val movies: List<SyncMovieItem>?
)

@JsonClass(generateAdapter = true)
data class SyncShowItem(
    @Json(name = "status") val status: String? = null,
    @Json(name = "show") val show: SimklMedia? = null
)

@JsonClass(generateAdapter = true)
data class SyncMovieItem(
    @Json(name = "status") val status: String? = null,
    @Json(name = "movie") val movie: SimklMedia? = null
)

@JsonClass(generateAdapter = true)
data class SimklMovieDetailResponse(
    @Json(name = "title") val title: String? = null,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "poster") val poster: String? = null,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "runtime") val runtime: Int? = null,
    @Json(name = "released") val released: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "dvd") val dvd: String? = null,
    @Json(name = "dvd_release_date") val dvdReleaseDate: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "ids") val ids: SimklIds? = null
)

