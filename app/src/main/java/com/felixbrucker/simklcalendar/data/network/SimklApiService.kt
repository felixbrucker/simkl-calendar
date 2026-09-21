package com.felixbrucker.simklcalendar.data.network

import com.felixbrucker.simklcalendar.BuildConfig
import retrofit2.http.*

interface SimklApiService {
    companion object {
        const val APP_NAME: String = BuildConfig.APP_NAME
        const val APP_VERSION: String = BuildConfig.VERSION_NAME
    }

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
    suspend fun getUserSettings(
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = APP_NAME,
        @Query("app-version") appVersion: String = APP_VERSION
    ): UserSettingsResponse

    @GET
    suspend fun getV2Calendar(
        @Url url: String,
        @Header("If-Modified-Since") ifModifiedSince: String? = null,
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = APP_NAME,
        @Query("app-version") appVersion: String = APP_VERSION
    ): retrofit2.Response<SimklV2CalendarResponse>

    @Authenticated
    @GET("sync/activities")
    suspend fun getSyncActivities(
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = APP_NAME,
        @Query("app-version") appVersion: String = APP_VERSION
    ): SyncActivitiesResponse

    @Authenticated
    @GET("sync/all-items")
    suspend fun getSyncAllItems(
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = APP_NAME,
        @Query("app-version") appVersion: String = APP_VERSION,
        @Query("extended") extended: String = "full",
        @Query("next_watch_info") nextWatchInfo: String = "yes",
        @Query("episode_watched_at") episodeWatchedAt: String = "yes",
        @Query("language") language: String = "en", // ensures all titles use en language line with the languages of the calendar files
        @Query("date_from") dateFrom: String? = null
    ): SyncAllItemsResponse

    @Authenticated
    @GET("movies/{id}")
    suspend fun getMovieDetails(
        @Path("id") movieId: Int,
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = APP_NAME,
        @Query("app-version") appVersion: String = APP_VERSION,
        @Query("extended") extended: String = "full"
    ): SimklMovieDetailResponse

    @Authenticated
    @POST("sync/history")
    suspend fun markHistoryWatched(
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = APP_NAME,
        @Query("app-version") appVersion: String = APP_VERSION,
        @Body request: SyncHistoryRequest
    ): SyncHistoryResponse

    @Authenticated
    @POST("sync/history/remove")
    suspend fun markHistoryUnwatched(
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = APP_NAME,
        @Query("app-version") appVersion: String = APP_VERSION,
        @Body request: SyncHistoryRequest
    ): SyncHistoryResponse

    @GET("tv/episodes/{id}")
    suspend fun getTvEpisodes(
        @Path("id") id: Int,
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = APP_NAME,
        @Query("app-version") appVersion: String = APP_VERSION
    ): List<SimklEpisodeResponse>

    @GET("anime/episodes/{id}")
    suspend fun getAnimeEpisodes(
        @Path("id") id: Int,
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = APP_NAME,
        @Query("app-version") appVersion: String = APP_VERSION
    ): List<SimklEpisodeResponse>
}

