package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.database.AppDatabase
import com.example.data.database.CalendarItem
import com.example.data.database.NotificationSetting
import com.example.data.database.TrackedWatchlistItem
import com.example.data.database.UserToken
import com.example.data.model.MediaType
import com.example.data.model.MovieReleaseType
import com.example.data.model.WatchlistStatus
import com.example.data.network.OAuthTokenRequest
import com.example.data.network.SimklApiService
import com.example.data.util.DateUtil
import com.example.data.util.ImageUtil
import com.example.data.util.PkceUtil
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

class SimklRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val tokenDao = db.userTokenDao()
    private val calendarDao = db.calendarItemDao()
    private val settingDao = db.notificationSettingDao()
    private val watchlistDao = db.watchlistDao()
    private val authPrefs = context.getSharedPreferences("simkl_pkce_auth", Context.MODE_PRIVATE)
    private val syncPrefs = context.getSharedPreferences("simkl_sync_prefs", Context.MODE_PRIVATE)

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

    /**
     * Prepares PKCE authorization URL with state and stores code_verifier & state in SharedPreferences
     * for CSRF protection and verification during the OAuth redirect callback.
     */
    fun createAuthorizationUrl(redirectUri: String = "simklcalendar://auth"): String? {
        val clientId = BuildConfig.SIMKL_CLIENT_ID.takeIf { it.isNotEmpty() && it != "YOUR_SIMKL_CLIENT_ID" } ?: return null
        val codeVerifier = PkceUtil.generateCodeVerifier()
        val codeChallenge = PkceUtil.generateCodeChallenge(codeVerifier)
        val state = PkceUtil.generateState()

        authPrefs.edit()
            .putString("pkce_code_verifier", codeVerifier)
            .putString("pkce_redirect_uri", redirectUri)
            .putString("pkce_state", state)
            .apply()

        val encodedRedirect = try {
            URLEncoder.encode(redirectUri, "UTF-8")
        } catch (e: Exception) {
            redirectUri
        }

        return "https://simkl.com/oauth/authorize?response_type=code&client_id=$clientId&redirect_uri=$encodedRedirect&code_challenge=$codeChallenge&code_challenge_method=S256&state=$state"
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        tokenDao.clearUserToken()
        calendarDao.clearCalendarItems()
        watchlistDao.clearAll()
        syncPrefs.edit().clear().apply()
    }

    suspend fun exchangeOAuthCode(
        code: String,
        state: String? = null,
        redirectUri: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val clientId = BuildConfig.SIMKL_CLIENT_ID.takeIf { it.isNotEmpty() && it != "YOUR_SIMKL_CLIENT_ID" }
            if (clientId.isNullOrEmpty()) {
                Log.e("SimklRepository", "Client ID is missing")
                return@withContext false
            }

            val savedState = authPrefs.getString("pkce_state", null)
            if (!savedState.isNullOrEmpty()) {
                if (state == null || state != savedState) {
                    Log.e("SimklRepository", "OAuth state mismatch or missing! CSRF verification failed.")
                    return@withContext false
                }
            }

            val codeVerifier = authPrefs.getString("pkce_code_verifier", null)
            val savedRedirectUri = authPrefs.getString("pkce_redirect_uri", "simklcalendar://auth") ?: "simklcalendar://auth"
            val effectiveRedirectUri = redirectUri ?: savedRedirectUri

            if (codeVerifier.isNullOrEmpty()) {
                Log.e("SimklRepository", "PKCE code_verifier is missing from local storage")
                return@withContext false
            }
            
            // 1. Exchange code for access token via POST /oauth/token using PKCE flow
            val response = apiService.getAccessToken(
                request = OAuthTokenRequest(
                    code = code,
                    clientId = clientId,
                    codeVerifier = codeVerifier,
                    redirectUri = effectiveRedirectUri
                )
            )
            val accessToken = response.accessToken
            if (accessToken.isNullOrEmpty()) {
                Log.e("SimklRepository", "OAuth returned empty access token")
                return@withContext false
            }

            // Successfully received token: clear stored PKCE parameters
            authPrefs.edit()
                .remove("pkce_code_verifier")
                .remove("pkce_redirect_uri")
                .remove("pkce_state")
                .apply()
            
            // 2. Fetch user profile from POST /users/settings to get the user's name
            val username = try {
                val userResponse = apiService.getUserSettings(
                    authorization = "Bearer $accessToken",
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

    suspend fun toggleNotificationSetting(simklId: Int, notifyEveryEpisode: Boolean, notifyAiredLastEpisode: Boolean) = withContext(Dispatchers.IO) {
        settingDao.saveSetting(
            NotificationSetting(
                simklId = simklId,
                notifyEveryEpisode = notifyEveryEpisode,
                notifyAiredLastEpisode = notifyAiredLastEpisode
            )
        )
        com.example.receiver.NotificationScheduler.scheduleNotificationsForShow(context, simklId)
    }

    suspend fun getActiveUserToken(): UserToken? = withContext(Dispatchers.IO) {
        tokenDao.getActiveToken()
    }

    suspend fun getSettingForShow(simklId: Int): NotificationSetting? = withContext(Dispatchers.IO) {
        settingDao.getSettingForShow(simklId)
    }

    suspend fun syncCalendar(force: Boolean = false) = withContext(Dispatchers.IO) {
        val userToken = tokenDao.getActiveToken()
        if (userToken == null || userToken.accessToken.isEmpty()) {
            Log.d("SimklRepository", "No authenticated user token found, skipping sync.")
            return@withContext
        }
        val clientId = BuildConfig.SIMKL_CLIENT_ID.takeIf { it.isNotEmpty() && it != "YOUR_SIMKL_CLIENT_ID" }
        val bearer = "Bearer ${userToken.accessToken}"

        // Two-Phase Sync for authenticated users
        try {
            // Phase 1: Check /sync/activities to see if any library changes occurred
            val activities = apiService.getSyncActivities(
                authorization = bearer,
                clientId = clientId
            )

            val currentActivitiesTimestamp = activities.all
            val savedTimestamp = syncPrefs.getString("last_activities_all", null)

            val shouldFetchDeltas = force || savedTimestamp == null || (currentActivitiesTimestamp != null && currentActivitiesTimestamp != savedTimestamp)

            if (shouldFetchDeltas) {
                val dateFromParam = if (force) null else savedTimestamp
                Log.d("SimklRepository", "Two-Phase Sync: Calling /sync/all-items with date_from=$dateFromParam (saved=$savedTimestamp, current=$currentActivitiesTimestamp)")

                val syncResponse = apiService.getSyncAllItems(
                    authorization = bearer,
                    clientId = clientId,
                    dateFrom = dateFromParam
                )

                val newTracked = mutableListOf<TrackedWatchlistItem>()

                // Process TV Shows
                syncResponse.shows?.forEach { item ->
                    val media = item.show ?: return@forEach
                    val simklId = media.ids?.simkl ?: media.ids?.simklId ?: return@forEach
                    val status = WatchlistStatus.fromString(item.status)
                    if (status == WatchlistStatus.WATCHING || status == WatchlistStatus.PLAN_TO_WATCH) {
                        newTracked.add(
                            TrackedWatchlistItem(
                                simklId = simklId,
                                type = MediaType.TV,
                                title = media.title ?: "Untitled",
                                poster = ImageUtil.formatPosterUrl(media.poster)
                            )
                        )
                    } else if (dateFromParam != null) {
                        watchlistDao.deleteItem(simklId)
                    }
                }

                // Process Anime
                syncResponse.anime?.forEach { item ->
                    val media = item.show ?: return@forEach
                    val simklId = media.ids?.simkl ?: media.ids?.simklId ?: return@forEach
                    val status = WatchlistStatus.fromString(item.status)
                    if (status == WatchlistStatus.WATCHING || status == WatchlistStatus.PLAN_TO_WATCH) {
                        newTracked.add(
                            TrackedWatchlistItem(
                                simklId = simklId,
                                type = MediaType.ANIME,
                                title = media.title ?: "Untitled",
                                poster = ImageUtil.formatPosterUrl(media.poster)
                            )
                        )
                    } else if (dateFromParam != null) {
                        watchlistDao.deleteItem(simklId)
                    }
                }

                // Process Movies
                syncResponse.movies?.forEach { item ->
                    val media = item.movie ?: return@forEach
                    val simklId = media.ids?.simkl ?: media.ids?.simklId ?: return@forEach
                    val status = WatchlistStatus.fromString(item.status)
                    if (status == WatchlistStatus.PLAN_TO_WATCH || status == WatchlistStatus.WATCHING) {
                        newTracked.add(
                            TrackedWatchlistItem(
                                simklId = simklId,
                                type = MediaType.MOVIE,
                                title = media.title ?: "Untitled",
                                poster = ImageUtil.formatPosterUrl(media.poster)
                            )
                        )
                    } else if (dateFromParam != null) {
                        watchlistDao.deleteItem(simklId)
                    }
                }

                if (dateFromParam == null) {
                    watchlistDao.clearAll()
                }
                if (newTracked.isNotEmpty()) {
                    watchlistDao.insertOrUpdateItems(newTracked)
                }

                if (!currentActivitiesTimestamp.isNullOrEmpty()) {
                    syncPrefs.edit().putString("last_activities_all", currentActivitiesTimestamp).apply()
                }
            } else {
                Log.d("SimklRepository", "Two-Phase Sync: /sync/activities timestamp unchanged ($savedTimestamp), skipping /sync/all-items")
            }
        } catch (e: Exception) {
            Log.e("SimklRepository", "Error during two-phase sync activities check", e)
        }

        // Load local tracked items
        val allTrackedItems = watchlistDao.getAllTrackedItems()
        val trackedShowIds = allTrackedItems.filter { it.type == MediaType.TV }.map { it.simklId }.toSet()
        val trackedAnimeIds = allTrackedItems.filter { it.type == MediaType.ANIME }.map { it.simklId }.toSet()
        val trackedMovieIds = allTrackedItems.filter { it.type == MediaType.MOVIE }.map { it.simklId }.toSet()

        // Load existing local calendar items to perform incremental diff comparison
        val existingDbItems = try {
            calendarDao.getAllCalendarItemsList()
        } catch (_: Exception) {
            emptyList()
        }
        val existingItemsMap = existingDbItems.associateBy { it.primaryKey }
        val itemsToInsert = mutableMapOf<String, CalendarItem>()
        val itemsToUpdate = mutableMapOf<String, CalendarItem>()

        fun processCalendarItem(newItem: CalendarItem) {
            val existing = existingItemsMap[newItem.primaryKey]
            if (existing == null) {
                val currentInsert = itemsToInsert[newItem.primaryKey]
                itemsToInsert[newItem.primaryKey] = currentInsert?.updatedWith(newItem) ?: newItem
                return
            }

            val base = itemsToUpdate[newItem.primaryKey] ?: existing
            val updated = base.updatedWith(newItem)
            if (updated != base) {
                itemsToUpdate[newItem.primaryKey] = updated
            }
        }

        // 2. Fetch CDN Calendars for past month (-1) through next 5 months (TV, Anime, Movies) from data.simkl.in
        val currentCal = java.util.Calendar.getInstance()
        val monthsToFetch = (-1..5).map { offset ->
            val cal = java.util.Calendar.getInstance().apply {
                time = currentCal.time
                add(java.util.Calendar.MONTH, offset)
            }
            val year = cal.get(java.util.Calendar.YEAR)
            val month = cal.get(java.util.Calendar.MONTH) + 1 // 1-12
            year to month
        }

        val mediaTypes = listOf(
            "tv" to MediaType.TV,
            "anime" to MediaType.ANIME,
            "movie_release" to MediaType.MOVIE
        )

        for ((year, month) in monthsToFetch) {
            for ((endpointType, defaultType) in mediaTypes) {
                val url = "https://data.simkl.in/calendar/v2/$year/$month/$endpointType.json"
                try {
                    val response = apiService.getV2Calendar(
                        url = url,
                        clientId = clientId
                    )
                    val entries = response.calendar
                    if (entries.isEmpty()) continue

                    val metadataMap = response.metadata ?: emptyMap()

                    for (entry in entries) {
                        val simklId = entry.simklId
                        val isTracked = when (defaultType) {
                            MediaType.TV -> trackedShowIds.contains(simklId)
                            MediaType.ANIME -> trackedAnimeIds.contains(simklId)
                            MediaType.MOVIE -> trackedMovieIds.contains(simklId)
                        }
                        if (!isTracked) continue

                        val meta = metadataMap[simklId.toString()] ?: metadataMap[simklId.toString().lowercase()]
                        val title = meta?.title ?: allTrackedItems.find { it.simklId == simklId }?.title ?: "Untitled"
                        val posterRaw = meta?.poster ?: allTrackedItems.find { it.simklId == simklId }?.poster
                        val posterUrl = ImageUtil.formatPosterUrl(posterRaw)

                        if (defaultType == MediaType.MOVIE) {
                            // 1. Process Theater Release
                            DateUtil.parseToInstant(entry.date)?.let { theaterInstant ->
                                processCalendarItem(
                                    CalendarItem(
                                        primaryKey = "v2_${simklId}_theater",
                                        simklId = simklId,
                                        title = title,
                                        episodeTitle = null,
                                        season = null,
                                        episodeNumber = null,
                                        date = theaterInstant,
                                        type = MediaType.MOVIE,
                                        movieReleaseType = MovieReleaseType.THEATER,
                                        isSeasonPremiere = false,
                                        isSeasonFinale = false,
                                        poster = posterUrl
                                    )
                                )
                            }

                            // 2. Process Digital / DVD Release from metadata if available
                            meta?.dvdDate?.takeIf { it.isNotBlank() }?.let { dvdDateStr ->
                                DateUtil.parseToInstant(dvdDateStr)?.let { dvdInstant ->
                                    processCalendarItem(
                                        CalendarItem(
                                            primaryKey = "v2_${simklId}_digital",
                                            simklId = simklId,
                                            title = title,
                                            episodeTitle = null,
                                            season = null,
                                            episodeNumber = null,
                                            date = dvdInstant,
                                            type = MediaType.MOVIE,
                                            movieReleaseType = MovieReleaseType.DIGITAL,
                                            isSeasonPremiere = false,
                                            isSeasonFinale = false,
                                            poster = posterUrl
                                        )
                                    )
                                }
                            }
                        } else {
                            val instant = DateUtil.parseToInstant(entry.date) ?: continue

                            val ep = entry.episode
                            val seasonNum = ep?.season ?: if (defaultType == MediaType.ANIME) null else 1
                            val epNum = ep?.episode ?: 1
                            val epTitle = ep?.title

                            val isPremiere = (seasonNum == 1 && epNum == 1) || epNum == 1
                            val isExplicitFinale = entry.finaleType != null && entry.finaleType != 0
                            val isMetadataFinale = meta?.totalEpisodes != null &&
                                meta.totalEpisodes > 1 &&
                                epNum > 1 &&
                                epNum >= meta.totalEpisodes
                            val isFinale = isExplicitFinale || isMetadataFinale

                            val keyUnique = if (seasonNum != null) "v2_${simklId}_${seasonNum}_${epNum}" else "v2_${simklId}_${epNum}"

                            processCalendarItem(
                                CalendarItem(
                                    primaryKey = keyUnique,
                                    simklId = simklId,
                                    title = title,
                                    episodeTitle = epTitle,
                                    season = seasonNum,
                                    episodeNumber = epNum,
                                    date = instant,
                                    type = defaultType,
                                    movieReleaseType = null,
                                    isSeasonPremiere = isPremiere,
                                    isSeasonFinale = isFinale,
                                    poster = posterUrl
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SimklRepository", "Failed fetching CDN v2 calendar from $url", e)
                }
            }
        }

        // Fetch movie details for all tracked movies missing either theatrical or DVD/digital release dates
        val candidateMovieIds = trackedMovieIds

        val moviesNeedingDetails = candidateMovieIds.filter { movieId ->
            val hasDigital = existingItemsMap.containsKey("v2_${movieId}_digital") || itemsToInsert.containsKey("v2_${movieId}_digital")
            val hasTheater = existingItemsMap.containsKey("v2_${movieId}_theater") || itemsToInsert.containsKey("v2_${movieId}_theater")
            !hasDigital || !hasTheater
        }

        if (moviesNeedingDetails.isNotEmpty()) {
            Log.d("SimklRepository", "Fetching details for ${moviesNeedingDetails.size} movies missing release dates")
            for (movieId in moviesNeedingDetails) {
                try {
                    val movieDetail = apiService.getMovieDetails(
                        movieId = movieId,
                        authorization = bearer,
                        clientId = clientId
                    )
                    val movieTitle = movieDetail.title ?: allTrackedItems.find { it.simklId == movieId }?.title ?: "Untitled"
                    val moviePoster = ImageUtil.formatPosterUrl(movieDetail.poster ?: allTrackedItems.find { it.simklId == movieId }?.poster)

                    // 1. Process Theatrical release date from regular released property
                    movieDetail.released?.takeIf { it.isNotBlank() }?.let { releasedStr ->
                        DateUtil.parseToInstant(releasedStr)?.let { theaterInstant ->
                            processCalendarItem(
                                CalendarItem(
                                    primaryKey = "v2_${movieId}_theater",
                                    simklId = movieId,
                                    title = movieTitle,
                                    episodeTitle = null,
                                    season = null,
                                    episodeNumber = null,
                                    date = theaterInstant,
                                    type = MediaType.MOVIE,
                                    movieReleaseType = MovieReleaseType.THEATER,
                                    isSeasonPremiere = false,
                                    isSeasonFinale = false,
                                    poster = moviePoster
                                )
                            )
                        }
                    }

                    // 2. Extract Digital / DVD release date from release_dates timeline
                    movieDetail.extractDigitalOrDvdReleaseDate()?.takeIf { it.isNotBlank() }?.let { digitalStr ->
                        DateUtil.parseToInstant(digitalStr)?.let { digitalInstant ->
                            processCalendarItem(
                                CalendarItem(
                                    primaryKey = "v2_${movieId}_digital",
                                    simklId = movieId,
                                    title = movieTitle,
                                    episodeTitle = null,
                                    season = null,
                                    episodeNumber = null,
                                    date = digitalInstant,
                                    type = MediaType.MOVIE,
                                    movieReleaseType = MovieReleaseType.DIGITAL,
                                    isSeasonPremiere = false,
                                    isSeasonFinale = false,
                                    poster = moviePoster
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SimklRepository", "Failed fetching movie details for movieId $movieId", e)
                }
            }
        }

        // Identify items that are no longer tracked in user's watchlist
        val itemsToDelete = existingDbItems.filter { item ->
            val isTracked = when (item.type) {
                MediaType.TV -> trackedShowIds.contains(item.simklId)
                MediaType.ANIME -> trackedAnimeIds.contains(item.simklId)
                MediaType.MOVIE -> trackedMovieIds.contains(item.simklId)
            }
            !isTracked
        }

        if (itemsToDelete.isNotEmpty()) {
            calendarDao.deleteCalendarItems(itemsToDelete)
            Log.d("SimklRepository", "Deleted ${itemsToDelete.size} untracked calendar items from DB")
        }

        if (itemsToInsert.isNotEmpty()) {
            calendarDao.insertCalendarItems(itemsToInsert.values.toList())
            Log.d("SimklRepository", "Inserted ${itemsToInsert.size} new calendar items into DB")
        }

        if (itemsToUpdate.isNotEmpty()) {
            calendarDao.updateCalendarItems(itemsToUpdate.values.toList())
            Log.d("SimklRepository", "Updated ${itemsToUpdate.size} changed calendar items in DB")
        }

        val totalDbChanges = itemsToDelete.size + itemsToInsert.size + itemsToUpdate.size
        if (totalDbChanges == 0) {
            Log.d("SimklRepository", "Calendar sync complete: no changes detected, skipped DB writes")
        } else {
            Log.d("SimklRepository", "Calendar sync complete: applied $totalDbChanges DB mutations (${itemsToInsert.size} inserted, ${itemsToUpdate.size} updated, ${itemsToDelete.size} deleted)")
        }

        // Initialize default notification settings for newly inserted shows
        if (itemsToInsert.isNotEmpty()) {
            try {
                val notifPrefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
                val defaultAiring = notifPrefs.getBoolean("default_notify_airing", false)
                val defaultSeasonFinished = notifPrefs.getBoolean("default_notify_season_finished", true)
                val defaultMovieTheater = notifPrefs.getBoolean("default_notify_movie_theater", false)
                val defaultMovieDigital = notifPrefs.getBoolean("default_notify_movie_digital", true)

                val distinctShows = itemsToInsert.values.groupBy { it.simklId }
                val newSettings = distinctShows.map { (simklId, items) ->
                    val sample = items.first()
                    val isMovie = sample.type == MediaType.MOVIE
                    NotificationSetting(
                        simklId = simklId,
                        notifyEveryEpisode = if (isMovie) defaultMovieTheater else defaultAiring,
                        notifyAiredLastEpisode = if (isMovie) defaultMovieDigital else defaultSeasonFinished
                    )
                }
                settingDao.insertSettings(newSettings)
            } catch (e: Exception) {
                Log.e("SimklRepository", "Error initializing default notification settings", e)
            }
        }

        if (totalDbChanges > 0) {
            com.example.receiver.NotificationScheduler.scheduleAllNotifications(context)
        }
    }
}
