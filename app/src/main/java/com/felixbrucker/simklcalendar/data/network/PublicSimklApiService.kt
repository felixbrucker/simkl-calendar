package com.felixbrucker.simklcalendar.data.network

import retrofit2.http.*

interface PublicSimklApiService {
    @POST("oauth2/token")
    suspend fun getAccessToken(
        @Body request: OAuthTokenRequest
    ): OAuthTokenResponse

    @POST("oauth2/revoke")
    suspend fun revokeToken(
        @Body request: OAuthRevokeRequest
    )

    @GET("https://data.simkl.in/calendar/v2/{year}/{month}/{type}.json")
    suspend fun getV2Calendar(
        @Path("year") year: Int,
        @Path("month") month: Int,
        @Path("type") type: String,
        @Header("If-Modified-Since") ifModifiedSince: String? = null
    ): retrofit2.Response<SimklV2CalendarResponse>

    @GET("movies/{id}")
    suspend fun getMovieDetails(
        @Path("id") movieId: Int,
        @Query("extended") extended: String = "full"
    ): SimklMovieDetailResponse

    @GET("tv/episodes/{id}")
    suspend fun getTvEpisodes(
        @Path("id") id: Int
    ): List<SimklEpisodeResponse>

    @GET("anime/episodes/{id}")
    suspend fun getAnimeEpisodes(
        @Path("id") id: Int
    ): List<SimklEpisodeResponse>
}

