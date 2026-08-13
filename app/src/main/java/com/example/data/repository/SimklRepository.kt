package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.database.AppDatabase
import com.example.data.database.CalendarItem
import com.example.data.database.NotificationSetting
import com.example.data.database.UserToken
import com.example.data.network.OAuthTokenRequest
import com.example.data.network.SimklApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class SimklRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val tokenDao = db.userTokenDao()
    private val calendarDao = db.calendarItemDao()
    private val settingDao = db.notificationSettingDao()

    val activeUserToken: Flow<UserToken?> = tokenDao.getUserToken()
    val calendarItems: Flow<List<CalendarItem>> = calendarDao.getAllCalendarItems()
    val notificationSettings: Flow<List<NotificationSetting>> = settingDao.getAllSettings()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            val isCalendarJson = url.contains("calendar/v2") || url.contains("data.simkl.in") || url.endsWith(".json")

            val loggingInterceptor = HttpLoggingInterceptor { message ->
                Log.d("OkHttp", message)
            }.apply {
                level = if (isCalendarJson) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.BODY
            }

            val response = try {
                loggingInterceptor.intercept(chain)
            } catch (e: Exception) {
                Log.e("SimklRepository", "Network request failed: ${request.method} $url", e)
                throw e
            }

            if (!response.isSuccessful) {
                Log.e("SimklRepository", "Network response error: HTTP ${response.code} ${response.message} for ${request.method} $url")
            }

            response
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.simkl.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val apiService = retrofit.create(SimklApiService::class.java)

    // Check if client ID is configured in BuildConfig
    fun isRealApiConfigured(): Boolean {
        val clientId = BuildConfig.SIMKL_CLIENT_ID
        return clientId.isNotEmpty() && clientId != "YOUR_SIMKL_CLIENT_ID"
    }

    suspend fun getClientCredentials(): Pair<String, String> {
        return Pair(BuildConfig.SIMKL_CLIENT_ID, BuildConfig.SIMKL_CLIENT_SECRET)
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        tokenDao.clearUserToken()
        calendarDao.clearCalendarItems()
    }

    suspend fun exchangeOAuthCode(code: String, redirectUri: String = "simklcalendar://auth"): Boolean = withContext(Dispatchers.IO) {
        try {
            val clientId = BuildConfig.SIMKL_CLIENT_ID.takeIf { it.isNotEmpty() && it != "YOUR_SIMKL_CLIENT_ID" }
            
            // 1. Exchange code for access token via POST /oauth/token
            val response = apiService.getAccessToken(
                apiKey = clientId,
                request = OAuthTokenRequest(
                    code = code,
                    clientId = BuildConfig.SIMKL_CLIENT_ID,
                    clientSecret = BuildConfig.SIMKL_CLIENT_SECRET,
                    redirectUri = redirectUri
                )
            )
            val accessToken = response.accessToken
            if (accessToken.isNullOrEmpty()) {
                Log.e("SimklRepository", "OAuth returned empty access token")
                return@withContext false
            }
            
            // 2. Fetch user profile from POST /users/settings to get the user's name
            val username = try {
                val userResponse = apiService.getUserSettings(
                    authorization = "Bearer $accessToken",
                    apiKey = clientId,
                    clientId = clientId
                )
                // In Simkl POST /users/settings, user profile contains "name" (which holds username)
                userResponse.user?.name ?: "SimklUser"
            } catch (e: Exception) {
                Log.e("SimklRepository", "Could not fetch user profile details, using default name", e)
                "SimklUser"
            }

            tokenDao.insertUserToken(
                UserToken(accessToken = accessToken, username = username)
            )
            calendarDao.clearCalendarItems()
            syncCalendar()
            true
        } catch (e: Exception) {
            Log.e("SimklRepository", "OAuth Code exchange failed", e)
            false
        }
    }

    suspend fun toggleNotificationSetting(showId: Int, showTitle: String, type: String, notifyEveryEpisode: Boolean, notifyAiredLastEpisode: Boolean) = withContext(Dispatchers.IO) {
        settingDao.saveSetting(
            NotificationSetting(
                showId = showId,
                showTitle = showTitle,
                type = type,
                notifyEveryEpisode = notifyEveryEpisode,
                notifyAiredLastEpisode = notifyAiredLastEpisode
            )
        )
    }

    suspend fun getSettingForShow(showId: Int): NotificationSetting? = withContext(Dispatchers.IO) {
        settingDao.getSettingForShow(showId)
    }

    suspend fun syncCalendar(force: Boolean = false) = withContext(Dispatchers.IO) {
        val userToken = tokenDao.getActiveToken()
        val clientId = BuildConfig.SIMKL_CLIENT_ID.takeIf { it.isNotEmpty() && it != "YOUR_SIMKL_CLIENT_ID" }
        val bearer = userToken?.accessToken?.takeIf { it.isNotEmpty() }?.let { "Bearer $it" }

        val dbItems = mutableListOf<CalendarItem>()

        // 1. Fetch CDN v2 Calendars (TV, Anime, Movies) from data.simkl.in
        val cdnEndpoints = listOf(
            "https://data.simkl.in/calendar/v2/tv.json" to "tv",
            "https://data.simkl.in/calendar/v2/anime.json" to "anime",
            "https://data.simkl.in/calendar/v2/movie_release.json" to "movie"
        )

        for ((url, defaultType) in cdnEndpoints) {
            try {
                val response = apiService.getV2Calendar(
                    url = url,
                    clientId = clientId
                )
                val entries = response.calendar ?: emptyList()
                val metadataMap = response.metadata ?: emptyMap()

                entries.forEach { entry ->
                    val simklId = entry.simklId ?: return@forEach
                    val dateStr = entry.date ?: return@forEach
                    val meta = metadataMap[simklId.toString()] ?: metadataMap[simklId.toString().lowercase()]

                    val title = meta?.title ?: "Untitled"
                    val ep = entry.episode
                    val seasonNum = ep?.season
                    val epNum = ep?.episode
                    val epTitle = ep?.title

                    val isPremiere = entry.premiereType != null && entry.premiereType.toString() != "0"
                    val isFinale = entry.finaleType != null && entry.finaleType.toString() != "0"

                    val posterRaw = meta?.poster
                    val posterUrl = when {
                        posterRaw.isNullOrEmpty() -> "https://simkl.in/poster_no_pic.png"
                        posterRaw.startsWith("http") -> posterRaw
                        posterRaw.contains("/") -> "https://simkl.in/$posterRaw"
                        else -> "https://simkl.in/posters/${posterRaw}_m.jpg"
                    }

                    val keyUnique = "v2_${simklId}_${seasonNum ?: 0}_${epNum ?: 0}_$dateStr"

                    dbItems.add(
                        CalendarItem(
                            primaryKey = keyUnique,
                            id = simklId,
                            title = title,
                            episodeTitle = epTitle,
                            season = seasonNum,
                            episodeNumber = epNum,
                            date = dateStr,
                            type = defaultType,
                            isSeasonPremiere = isPremiere,
                            isSeasonFinale = isFinale,
                            poster = posterUrl,
                            simklId = simklId,
                            isLastEpisode = isFinale && meta?.status == "ended",
                            notificationsScheduled = false
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("SimklRepository", "Failed fetching CDN v2 calendar from $url", e)
            }
        }

        // 2. Authenticated User Sync Items Integration (GET /sync/all-items)
        if (bearer != null) {
            try {
                val userSync = apiService.getSyncAllItems(
                    authorization = bearer,
                    apiKey = clientId,
                    clientId = clientId
                )

                userSync.shows?.forEach { item ->
                    val media = item.show ?: item.anime ?: return@forEach
                    val epInfo = item.nextToWatchInfo
                    val simklId = media.ids?.simkl ?: media.ids?.simklId ?: return@forEach
                    val dateStr = epInfo?.date ?: item.lastWatchedAt ?: item.addedToWatchlistAt ?: return@forEach

                    val posterRaw = media.poster
                    val posterUrl = when {
                        posterRaw.isNullOrEmpty() -> "https://simkl.in/poster_no_pic.png"
                        posterRaw.startsWith("http") -> posterRaw
                        posterRaw.contains("/") -> "https://simkl.in/$posterRaw"
                        else -> "https://simkl.in/posters/${posterRaw}_m.jpg"
                    }

                    dbItems.add(
                        CalendarItem(
                            primaryKey = "sync_${simklId}_${epInfo?.season ?: 0}_${epInfo?.episode ?: 0}_$dateStr",
                            id = simklId,
                            title = media.title ?: "Untitled",
                            episodeTitle = epInfo?.title,
                            season = epInfo?.season,
                            episodeNumber = epInfo?.episode,
                            date = dateStr,
                            type = "tv",
                            isSeasonPremiere = false,
                            isSeasonFinale = false,
                            poster = posterUrl,
                            simklId = simklId,
                            isLastEpisode = false,
                            notificationsScheduled = false
                        )
                    )
                }

                userSync.anime?.forEach { item ->
                    val media = item.anime ?: item.show ?: return@forEach
                    val epInfo = item.nextToWatchInfo
                    val simklId = media.ids?.simkl ?: media.ids?.simklId ?: return@forEach
                    val dateStr = epInfo?.date ?: item.lastWatchedAt ?: item.addedToWatchlistAt ?: return@forEach

                    val posterRaw = media.poster
                    val posterUrl = when {
                        posterRaw.isNullOrEmpty() -> "https://simkl.in/poster_no_pic.png"
                        posterRaw.startsWith("http") -> posterRaw
                        posterRaw.contains("/") -> "https://simkl.in/$posterRaw"
                        else -> "https://simkl.in/posters/${posterRaw}_m.jpg"
                    }

                    dbItems.add(
                        CalendarItem(
                            primaryKey = "sync_${simklId}_${epInfo?.season ?: 0}_${epInfo?.episode ?: 0}_$dateStr",
                            id = simklId,
                            title = media.title ?: "Untitled",
                            episodeTitle = epInfo?.title,
                            season = epInfo?.season,
                            episodeNumber = epInfo?.episode,
                            date = dateStr,
                            type = "anime",
                            isSeasonPremiere = false,
                            isSeasonFinale = false,
                            poster = posterUrl,
                            simklId = simklId,
                            isLastEpisode = false,
                            notificationsScheduled = false
                        )
                    )
                }

                userSync.movies?.forEach { item ->
                    val media = item.movie ?: return@forEach
                    val simklId = media.ids?.simkl ?: media.ids?.simklId ?: return@forEach
                    val dateStr = item.lastWatchedAt ?: item.addedToWatchlistAt ?: return@forEach

                    val posterRaw = media.poster
                    val posterUrl = when {
                        posterRaw.isNullOrEmpty() -> "https://simkl.in/poster_no_pic.png"
                        posterRaw.startsWith("http") -> posterRaw
                        posterRaw.contains("/") -> "https://simkl.in/$posterRaw"
                        else -> "https://simkl.in/posters/${posterRaw}_m.jpg"
                    }

                    dbItems.add(
                        CalendarItem(
                            primaryKey = "sync_${simklId}_0_0_$dateStr",
                            id = simklId,
                            title = media.title ?: "Untitled",
                            episodeTitle = null,
                            season = null,
                            episodeNumber = null,
                            date = dateStr,
                            type = "movie",
                            isSeasonPremiere = false,
                            isSeasonFinale = false,
                            poster = posterUrl,
                            simklId = simklId,
                            isLastEpisode = false,
                            notificationsScheduled = false
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("SimklRepository", "Error fetching user sync items", e)
            }
        }

        if (dbItems.isNotEmpty()) {
            calendarDao.clearCalendarItems()
            calendarDao.insertCalendarItems(dbItems)
            Log.d("SimklRepository", "Successfully synchronized ${dbItems.size} calendar items")
        } else {
            Log.w("SimklRepository", "No calendar items retrieved from CDN or sync")
        }
    }
}
