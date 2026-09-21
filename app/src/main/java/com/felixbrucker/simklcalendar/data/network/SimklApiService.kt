package com.felixbrucker.simklcalendar.data.network

import com.felixbrucker.simklcalendar.BuildConfig
import retrofit2.http.*

interface SimklApiService {
    @POST("oauth2/token")
    suspend fun getAccessToken(
        @Body request: OAuthTokenRequest
    ): OAuthTokenResponse

    @POST("oauth2/revoke")
    suspend fun revokeToken(
        @Body request: OAuthRevokeRequest
    ): retrofit2.Response<Unit>

    @Authenticated
    @POST("users/settings")
    suspend fun getUserSettings(): UserSettingsResponse

    @GET
    suspend fun getV2Calendar(
        @Url url: String,
        @Header("If-Modified-Since") ifModifiedSince: String? = null
    ): retrofit2.Response<SimklV2CalendarResponse>

    @Authenticated
    @GET("sync/activities")
    suspend fun getSyncActivities(): SyncActivitiesResponse

    @Authenticated
    @GET("sync/all-items")
    suspend fun getSyncAllItems(
        @Query("extended") extended: String = "full",
        @Query("next_watch_info") nextWatchInfo: String = "yes",
        @Query("episode_watched_at") episodeWatchedAt: String = "yes",
        @Query("language") language: String = "en", // ensures all titles use en language line with the languages of the calendar files
        @Query("date_from") dateFrom: String? = null
    ): SyncAllItemsResponse

    @GET("movies/{id}")
    suspend fun getMovieDetails(
        @Path("id") movieId: Int,
        @Query("extended") extended: String = "full"
    ): SimklMovieDetailResponse

    @Authenticated
    @POST("sync/history")
    suspend fun markHistoryWatched(
        @Body request: SyncHistoryRequest
    ): SyncHistoryResponse

    @Authenticated
    @POST("sync/history/remove")
    suspend fun markHistoryUnwatched(
        @Body request: SyncHistoryRequest
    ): SyncHistoryResponse

    @GET("tv/episodes/{id}")
    suspend fun getTvEpisodes(
        @Path("id") id: Int
    ): List<SimklEpisodeResponse>

    @GET("anime/episodes/{id}")
    suspend fun getAnimeEpisodes(
        @Path("id") id: Int
    ): List<SimklEpisodeResponse>
}

