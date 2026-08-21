package com.example.data.network

import retrofit2.http.*

interface SimklApiService {
    @POST("oauth/token")
    suspend fun getAccessToken(
        @Header("User-Agent") userAgent: String = "simkl-calendar/1.0",
        @Body request: OAuthTokenRequest
    ): OAuthTokenResponse

    @POST("users/settings")
    suspend fun getUserSettings(
        @Header("Authorization") authorization: String? = null,
        @Header("User-Agent") userAgent: String = "simkl-calendar/1.0",
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = "simkl-calendar",
        @Query("app-version") appVersion: String = "1.0"
    ): UserSettingsResponse

    @GET
    suspend fun getV2Calendar(
        @Url url: String,
        @Header("User-Agent") userAgent: String = "simkl-calendar/1.0",
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = "simkl-calendar",
        @Query("app-version") appVersion: String = "1.0"
    ): SimklV2CalendarResponse

    @GET("sync/activities")
    suspend fun getSyncActivities(
        @Header("Authorization") authorization: String? = null,
        @Header("User-Agent") userAgent: String = "simkl-calendar/1.0",
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = "simkl-calendar",
        @Query("app-version") appVersion: String = "1.0"
    ): SyncActivitiesResponse

    @GET("sync/all-items")
    suspend fun getSyncAllItems(
        @Header("Authorization") authorization: String? = null,
        @Header("User-Agent") userAgent: String = "simkl-calendar/1.0",
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = "simkl-calendar",
        @Query("app-version") appVersion: String = "1.0",
        @Query("extended") extended: String = "full",
        @Query("next_watch_info") nextWatchInfo: String = "yes",
        @Query("episode_watched_at") episodeWatchedAt: String = "yes",
        @Query("date_from") dateFrom: String? = null
    ): SyncAllItemsResponse

    @GET("movies/{id}")
    suspend fun getMovieDetails(
        @Path("id") movieId: Int,
        @Header("Authorization") authorization: String? = null,
        @Header("User-Agent") userAgent: String = "simkl-calendar/1.0",
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = "simkl-calendar",
        @Query("app-version") appVersion: String = "1.0",
        @Query("extended") extended: String = "full"
    ): SimklMovieDetailResponse

    @POST("sync/history")
    suspend fun markHistoryWatched(
        @Header("Authorization") authorization: String? = null,
        @Header("User-Agent") userAgent: String = "simkl-calendar/1.0",
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = "simkl-calendar",
        @Query("app-version") appVersion: String = "1.0",
        @Body request: SyncHistoryRequest
    ): SyncHistoryResponse
}

