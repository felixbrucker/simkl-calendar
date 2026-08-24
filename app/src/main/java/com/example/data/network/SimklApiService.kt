package com.example.data.network

import com.example.BuildConfig
import retrofit2.http.*

interface SimklApiService {
    companion object {
        val APP_NAME: String = BuildConfig.APP_NAME
        val APP_VERSION: String = BuildConfig.VERSION_NAME
        val USER_AGENT: String = "$APP_NAME/$APP_VERSION"
    }

    @POST("oauth/token")
    suspend fun getAccessToken(
        @Body request: OAuthTokenRequest
    ): OAuthTokenResponse

    @POST("users/settings")
    suspend fun getUserSettings(
        @Header("Authorization") authorization: String? = null,
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

    @GET("sync/activities")
    suspend fun getSyncActivities(
        @Header("Authorization") authorization: String? = null,
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = APP_NAME,
        @Query("app-version") appVersion: String = APP_VERSION
    ): SyncActivitiesResponse

    @GET("sync/all-items")
    suspend fun getSyncAllItems(
        @Header("Authorization") authorization: String? = null,
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = APP_NAME,
        @Query("app-version") appVersion: String = APP_VERSION,
        @Query("extended") extended: String = "full",
        @Query("next_watch_info") nextWatchInfo: String = "yes",
        @Query("episode_watched_at") episodeWatchedAt: String = "yes",
        @Query("date_from") dateFrom: String? = null
    ): SyncAllItemsResponse

    @GET("movies/{id}")
    suspend fun getMovieDetails(
        @Path("id") movieId: Int,
        @Header("Authorization") authorization: String? = null,
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = APP_NAME,
        @Query("app-version") appVersion: String = APP_VERSION,
        @Query("extended") extended: String = "full"
    ): SimklMovieDetailResponse

    @POST("sync/history")
    suspend fun markHistoryWatched(
        @Header("Authorization") authorization: String? = null,
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = APP_NAME,
        @Query("app-version") appVersion: String = APP_VERSION,
        @Body request: SyncHistoryRequest
    ): SyncHistoryResponse

    @POST("sync/history/remove")
    suspend fun markHistoryUnwatched(
        @Header("Authorization") authorization: String? = null,
        @Query("client_id") clientId: String? = null,
        @Query("app-name") appName: String = APP_NAME,
        @Query("app-version") appVersion: String = APP_VERSION,
        @Body request: SyncHistoryRequest
    ): SyncHistoryResponse
}

