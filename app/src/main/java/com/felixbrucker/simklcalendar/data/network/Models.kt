package com.felixbrucker.simklcalendar.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OAuthTokenRequest(
    @Json(name = "code") val code: String? = null,
    @Json(name = "client_id") val clientId: String,
    @Json(name = "code_verifier") val codeVerifier: String? = null,
    @Json(name = "redirect_uri") val redirectUri: String? = null,
    @Json(name = "grant_type") val grantType: String = "authorization_code",
    @Json(name = "refresh_token") val refreshToken: String? = null
)

@JsonClass(generateAdapter = true)
data class OAuthTokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String,
    @Json(name = "expires_in") val expiresIn: Long,
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "scope") val scope: String
)

@JsonClass(generateAdapter = true)
data class OAuthRevokeRequest(
    @Json(name = "client_id") val clientId: String,
    @Json(name = "token") val token: String
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
    @Json(name = "title_romaji") val titleRomaji: String? = null,
    @Json(name = "poster") val poster: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "total_episodes") val totalEpisodes: Int? = null,
    @Json(name = "dvd_date") val dvdDate: String? = null,
    @Json(name = "ratings") val ratings: SimklRatings? = null
)

@JsonClass(generateAdapter = true)
data class SimklEpisodeResponse(
    @Json(name = "title") val title: String,
    @Json(name = "type") val type: String, // episode, special
    @Json(name = "season") val season: Int? = null,
    @Json(name = "episode") val episode: Int? = null, // specials might not have an episode number
    @Json(name = "aired") val aired: Boolean,
    @Json(name = "date") val date: String? = null
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
    @Json(name = "all") val all: String? = null,
    @Json(name = "tv_shows") val tvShows: SyncActivitiesCategory? = null,
    @Json(name = "anime") val anime: SyncActivitiesCategory? = null,
    @Json(name = "movies") val movies: SyncActivitiesCategory? = null
)

@JsonClass(generateAdapter = true)
data class SyncActivitiesCategory(
    @Json(name = "all") val all: String? = null,
    @Json(name = "removed_from_list") val removedFromList: String? = null
)

@JsonClass(generateAdapter = true)
data class SyncAllItemsResponse(
    @Json(name = "shows") val shows: List<SyncShowItem>? = null,
    @Json(name = "anime") val anime: List<SyncShowItem>? = null,
    @Json(name = "movies") val movies: List<SyncMovieItem>? = null
)

@JsonClass(generateAdapter = true)
data class SyncIdsOnlyResponse(
    @Json(name = "shows") val shows: List<SyncIdItem>? = null,
    @Json(name = "anime") val anime: List<SyncIdItem>? = null,
    @Json(name = "movies") val movies: List<SyncIdMovieItem>? = null
)

@JsonClass(generateAdapter = true)
data class SyncIdItem(
    @Json(name = "show") val show: SimklIdsWrapper
)

@JsonClass(generateAdapter = true)
data class SyncIdMovieItem(
    @Json(name = "movie") val movie: SimklIdsWrapper
)

@JsonClass(generateAdapter = true)
data class SimklIdsWrapper(
    @Json(name = "ids") val ids: SimklIds
)

@JsonClass(generateAdapter = true)
data class SyncShowItem(
    @Json(name = "status") val status: String,
    @Json(name = "show") val show: SimklMedia,
    @Json(name = "total_episodes_count") val totalEpisodes: Int? = null,
    @Json(name = "watched_episodes_count") val watchedEpisodes: Int? = null,
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
data class SimklRating(
    @Json(name = "rating") val rating: Double? = null
)

@JsonClass(generateAdapter = true)
data class SimklRatings(
    @Json(name = "simkl") val simkl: SimklRating? = null
)

@JsonClass(generateAdapter = true)
data class SimklMovieDetailResponse(
    @Json(name = "title") val title: String,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "poster") val poster: String? = null,
    @Json(name = "released") val released: String? = null,
    @Json(name = "release_dates") val releaseDates: List<SimklMovieReleaseDateCountry>? = null,
    @Json(name = "budget") val budget: Long? = null,
    @Json(name = "revenue") val revenue: Long? = null,
    @Json(name = "genres") val genres: List<String>? = null,
    @Json(name = "similar") val similar: List<SimklSimilarItem>? = null,
    @Json(name = "ratings") val ratings: SimklRatings? = null,
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

@JsonClass(generateAdapter = true)
data class SimklTvDetailResponse(
    @Json(name = "title") val title: String,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "year_start_end") val yearStartEnd: String? = null,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "poster") val poster: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "total_episodes") val totalEpisodes: Int? = null,
    @Json(name = "genres") val genres: List<String>? = null,
    @Json(name = "similar") val similar: List<SimklSimilarItem>? = null,
    @Json(name = "ratings") val ratings: SimklRatings? = null,
    @Json(name = "ids") val ids: SimklIds
)

@JsonClass(generateAdapter = true)
data class SimklAnimeDetailResponse(
    @Json(name = "title") val title: String,
    @Json(name = "title_romaji") val titleRomaji: String? = null,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "year_start_end") val yearStartEnd: String? = null,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "poster") val poster: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "anime_type") val animeType: String? = null,
    @Json(name = "total_episodes") val totalEpisodes: Int? = null,
    @Json(name = "season_name_year") val seasonNameYear: String? = null,
    @Json(name = "genres") val genres: List<String>? = null,
    @Json(name = "similar") val similar: List<SimklSimilarItem>? = null,
    @Json(name = "ratings") val ratings: SimklRatings? = null,
    @Json(name = "season") val season: Int? = null,
    @Json(name = "ids") val ids: SimklIds
)

@JsonClass(generateAdapter = true)
data class SimklSimilarItem(
    @Json(name = "title") val title: String,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "poster") val poster: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "ids") val ids: SimklIds
)

// POST /sync/history models
@JsonClass(generateAdapter = true)
data class SyncHistoryRequest(
    @Json(name = "movies") val movies: List<SyncHistoryMovieItem>? = null,
    @Json(name = "shows") val shows: List<SyncHistoryShowItem>? = null,
    @Json(name = "anime") val anime: List<SyncHistoryShowItem>? = null
)

@JsonClass(generateAdapter = true)
data class SyncHistoryMovieItem(
    @Json(name = "ids") val ids: SimklIds,
    @Json(name = "status") val status: String? = null,
    @Json(name = "watched_at") val watchedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SyncHistoryShowItem(
    @Json(name = "ids") val ids: SimklIds,
    @Json(name = "status") val status: String? = null,
    @Json(name = "seasons") val seasons: List<SyncHistorySeasonItem>? = null
)

@JsonClass(generateAdapter = true)
data class SyncHistorySeasonItem(
    @Json(name = "number") val number: Int,
    @Json(name = "episodes") val episodes: List<SyncHistoryEpisodeItem>? = null
)

@JsonClass(generateAdapter = true)
data class SyncHistoryEpisodeItem(
    @Json(name = "number") val number: Int,
    @Json(name = "watched_at") val watchedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SyncHistoryResponse(
    @Json(name = "added") val added: SyncHistoryAddedResult? = null,
    @Json(name = "not_found") val notFound: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
data class SyncHistoryAddedResult(
    @Json(name = "movies") val movies: Int? = null,
    @Json(name = "shows") val shows: Int? = null,
    @Json(name = "anime") val anime: Int? = null,
    @Json(name = "episodes") val episodes: Int? = null
)

// POST /sync/add-to-list models
@JsonClass(generateAdapter = true)
data class SyncAddToListRequest(
    @Json(name = "movies") val movies: List<SyncAddToListMovieItem>? = null,
    @Json(name = "shows") val shows: List<SyncAddToListShowItem>? = null,
    @Json(name = "anime") val anime: List<SyncAddToListShowItem>? = null
)

@JsonClass(generateAdapter = true)
data class SyncAddToListMovieItem(
    @Json(name = "to") val to: String,
    @Json(name = "ids") val ids: SimklIds
)

@JsonClass(generateAdapter = true)
data class SyncAddToListShowItem(
    @Json(name = "to") val to: String,
    @Json(name = "ids") val ids: SimklIds
)

@JsonClass(generateAdapter = true)
data class SyncAddToListResponse(
    @Json(name = "added") val added: Map<String, Any>? = null,
    @Json(name = "not_found") val notFound: Map<String, Any>? = null
)

// Trending item model
@JsonClass(generateAdapter = true)
data class TrendingItemDto(
    @Json(name = "title") val title: String,
    @Json(name = "title_romaji") val titleRomaji: String? = null,
    @Json(name = "poster") val poster: String? = null,
    @Json(name = "ids") val ids: SimklIds,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "ratings") val ratings: SimklRatings? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "anime_type") val animeType: String? = null,
    @Json(name = "total_episodes") val totalEpisodes: Int? = null,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "genres") val genres: List<String>? = null,
    @Json(name = "dvd_date") val dvdDate: String? = null,
    @Json(name = "metadata") val metadata: String? = null
)

// Search item model
@JsonClass(generateAdapter = true)
data class SearchItemDto(
    @Json(name = "title") val title: String,
    @Json(name = "title_romaji") val titleRomaji: String? = null,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "endpoint_type") val endpointType: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "poster") val poster: String? = null,
    @Json(name = "ids") val ids: SimklIds,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "genres") val genres: List<String>? = null,
    @Json(name = "ratings") val ratings: SimklRatings? = null,
    @Json(name = "ep_count") val epCount: Int? = null,
    @Json(name = "status") val status: String? = null
)


