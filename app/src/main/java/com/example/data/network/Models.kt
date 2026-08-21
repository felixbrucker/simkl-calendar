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
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String? = null,
    @Json(name = "scope") val scope: String? = null
)

@JsonClass(generateAdapter = true)
data class UserSettingsResponse(
    @Json(name = "user") val user: UserProfile
)

@JsonClass(generateAdapter = true)
data class UserProfile(
    @Json(name = "name") val name: String
)

// CDN V2 Calendar models (data.simkl.in/calendar/v2/*.json)
@JsonClass(generateAdapter = true)
data class SimklV2CalendarResponse(
    @Json(name = "calendar") val calendar: List<SimklV2CalendarEntry>,
    @Json(name = "metadata") val metadata: Map<String, SimklV2Metadata>
)

@JsonClass(generateAdapter = true)
data class SimklV2CalendarEntry(
    @Json(name = "simkl_id") val simklId: Int,
    @Json(name = "date") val date: String,
    @Json(name = "finale_type") val finaleType: Int? = null,
    @Json(name = "episode") val episode: SimklV2Episode? = null
)

@JsonClass(generateAdapter = true)
data class SimklV2Episode(
    @Json(name = "season") val season: Int? = null,
    @Json(name = "episode") val episode: Int,
    @Json(name = "title") val title: String? = null
)

@JsonClass(generateAdapter = true)
data class SimklV2Metadata(
    @Json(name = "title") val title: String,
    @Json(name = "poster") val poster: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "total_episodes") val totalEpisodes: Int? = null,
    @Json(name = "dvd_date") val dvdDate: String? = null
)

@JsonClass(generateAdapter = true)
data class SimklMedia(
    @Json(name = "title") val title: String,
    @Json(name = "poster") val poster: String? = null,
    @Json(name = "ids") val ids: SimklIds
)

@JsonClass(generateAdapter = true)
data class SimklIds(
    @Json(name = "simkl") val simkl: Int,
)

@JsonClass(generateAdapter = true)
data class SyncActivitiesResponse(
    @Json(name = "all") val all: String? = null
)

@JsonClass(generateAdapter = true)
data class SyncAllItemsResponse(
    @Json(name = "shows") val shows: List<SyncShowItem>? = null,
    @Json(name = "anime") val anime: List<SyncShowItem>? = null,
    @Json(name = "movies") val movies: List<SyncMovieItem>? = null
)

@JsonClass(generateAdapter = true)
data class SyncShowItem(
    @Json(name = "status") val status: String,
    @Json(name = "show") val show: SimklMedia,
    @Json(name = "seasons") val seasons: List<SyncSeasonItem>? = null
)

@JsonClass(generateAdapter = true)
data class SyncSeasonItem(
    @Json(name = "number") val number: Int,
    @Json(name = "episodes") val episodes: List<SyncEpisodeItem>? = null
)

@JsonClass(generateAdapter = true)
data class SyncEpisodeItem(
    @Json(name = "number") val number: Int,
    @Json(name = "watched_at") val watchedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SyncMovieItem(
    @Json(name = "status") val status: String,
    @Json(name = "movie") val movie: SimklMedia
)

@JsonClass(generateAdapter = true)
data class SimklMovieReleaseResult(
    @Json(name = "type") val type: Int, // 1 = premiere, 2 = theatrical limited, 3 = theatrical wide, 4 = digital, 5 = physical, 6 = TV
    @Json(name = "release_date") val releaseDate: String
)

@JsonClass(generateAdapter = true)
data class SimklMovieReleaseDateCountry(
    @Json(name = "iso_3166_1") val iso31661: String,
    @Json(name = "results") val results: List<SimklMovieReleaseResult>
)

@JsonClass(generateAdapter = true)
data class SimklMovieDetailResponse(
    @Json(name = "title") val title: String,
    @Json(name = "poster") val poster: String? = null,
    @Json(name = "released") val released: String? = null,
    @Json(name = "release_dates") val releaseDates: List<SimklMovieReleaseDateCountry>? = null,
    @Json(name = "ids") val ids: SimklIds
) {
    /**
     * Extracts Digital (type 4), Physical / DVD (type 5), or TV (type 6) release date from the release_dates timeline.
     * Checks for US releases first, then any available country.
     */
    fun extractDigitalOrDvdReleaseDate(): String? {
        val dates = releaseDates ?: return null
        val releaseTypes = setOf(4, 5, 6)

        // Check US entries first
        val usEntry = dates.firstOrNull { it.iso31661.equals("US", ignoreCase = true) }
        val usDate = usEntry?.results
            ?.filter { it.type in releaseTypes && it.releaseDate.isNotBlank() }
            ?.minByOrNull { it.releaseDate }
            ?.releaseDate
        if (!usDate.isNullOrBlank()) {
            return usDate
        }

        // Fall back to any country's digital, physical, or TV release
        val anyDate = dates.flatMap { it.results }
            .filter { it.type in releaseTypes && it.releaseDate.isNotBlank() }
            .minByOrNull { it.releaseDate }
            ?.releaseDate

        return anyDate
    }
}


