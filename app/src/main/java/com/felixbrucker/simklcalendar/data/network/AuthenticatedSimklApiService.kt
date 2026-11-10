package com.felixbrucker.simklcalendar.data.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthenticatedSimklApiService {
    @POST("users/settings")
    suspend fun getUserSettings(): UserSettingsResponse

    @GET("sync/activities")
    suspend fun getSyncActivities(): SyncActivitiesResponse

    @GET("sync/all-items")
    suspend fun getSyncAllItems(
        @Query("extended") extended: String = "full",
        @Query("next_watch_info") nextWatchInfo: String = "yes",
        @Query("episode_watched_at") episodeWatchedAt: String = "yes",
        @Query("language") language: String = "en", // ensures all titles use en language line with the languages of the calendar files
        @Query("date_from") dateFrom: String? = null
    ): SyncAllItemsResponse

    @GET("sync/all-items")
    suspend fun getSyncSimklIdsOnly(
        @Query("extended") extended: String = "simkl_ids_only"
    ): SyncIdsOnlyResponse

    @POST("sync/history")
    suspend fun markHistoryWatched(
        @Body request: SyncHistoryRequest
    ): SyncHistoryResponse

    @POST("sync/history/remove")
    suspend fun markHistoryUnwatched(
        @Body request: SyncHistoryRequest
    ): SyncHistoryResponse

    @POST("sync/add-to-list")
    suspend fun addToList(
        @Body request: SyncAddToListRequest
    ): SyncAddToListResponse
}
