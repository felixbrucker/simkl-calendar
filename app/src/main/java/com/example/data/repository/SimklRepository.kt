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

    suspend fun toggleNotificationSetting(showId: Int, showTitle: String, type: MediaType, notifyEveryEpisode: Boolean, notifyAiredLastEpisode: Boolean) = withContext(Dispatchers.IO) {
        settingDao.saveSetting(
            NotificationSetting(
                showId = showId,
                showTitle = showTitle,
                type = type,
                notifyEveryEpisode = notifyEveryEpisode,
                notifyAiredLastEpisode = notifyAiredLastEpisode
            )
        )
        com.example.receiver.NotificationScheduler.scheduleNotificationsForShow(context, showId)
    }

    suspend fun getActiveUserToken(): UserToken? = withContext(Dispatchers.IO) {
        tokenDao.getActiveToken()
    }

    suspend fun getSettingForShow(showId: Int): NotificationSetting? = withContext(Dispatchers.IO) {
        settingDao.getSettingForShow(showId)
    }

    suspend fun syncCalendar(force: Boolean = false) = withContext(Dispatchers.IO) {
        val userToken = tokenDao.getActiveToken()
        if (userToken == null || userToken.accessToken.isNullOrEmpty()) {
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
                                id = simklId,
                                type = MediaType.TV,
                                status = status,
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
                                id = simklId,
                                type = MediaType.ANIME,
                                status = status,
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
                                id = simklId,
                                type = MediaType.MOVIE,
                                status = status,
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
        val trackedShowIds = allTrackedItems.filter { it.type == MediaType.TV }.map { it.id }.toSet()
        val trackedAnimeIds = allTrackedItems.filter { it.type == MediaType.ANIME }.map { it.id }.toSet()
        val trackedMovieIds = allTrackedItems.filter { it.type == MediaType.MOVIE }.map { it.id }.toSet()

        val dbItems = mutableListOf<CalendarItem>()

        // 2. Fetch CDN Calendars for current + next 3 months (TV, Anime, Movies) from data.simkl.in
        val currentCal = java.util.Calendar.getInstance()
        val monthsToFetch = (0..3).map { offset ->
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
                    val metadataMap = response.metadata ?: emptyMap()

                    if (!entries.isNullOrEmpty()) {
                        entries.forEach { entry ->
                            val simklId = entry.simklId ?: return@forEach
                            val meta = metadataMap[simklId.toString()] ?: metadataMap[simklId.toString().lowercase()]

                            // Only include items from user's watchlist ("watching" and "plan to watch")
                            val isTracked = when (defaultType) {
                                MediaType.TV -> trackedShowIds.contains(simklId)
                                MediaType.ANIME -> trackedAnimeIds.contains(simklId)
                                MediaType.MOVIE -> trackedMovieIds.contains(simklId)
                            }
                            if (!isTracked) return@forEach

                            val title = meta?.title ?: allTrackedItems.find { it.id == simklId }?.title ?: "Untitled"
                            val posterRaw = meta?.poster ?: allTrackedItems.find { it.id == simklId }?.poster
                            val posterUrl = ImageUtil.formatPosterUrl(posterRaw)

                            if (defaultType == MediaType.MOVIE) {
                                // 1. Process Theater Release
                                val theaterDateStr = entry.date
                                if (!theaterDateStr.isNullOrBlank()) {
                                    val theaterInstant = DateUtil.parseToInstant(theaterDateStr)
                                    if (theaterInstant != null) {
                                        val theaterKey = "v2_${simklId}_theater"
                                        val alreadyAdded = dbItems.any { it.primaryKey == theaterKey }
                                        if (!alreadyAdded) {
                                            dbItems.add(
                                                CalendarItem(
                                                    primaryKey = theaterKey,
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
                                                    poster = posterUrl,
                                                    isLastEpisode = false,
                                                    notificationsScheduled = false
                                                )
                                            )
                                        }
                                    }
                                }

                                // 2. Process Digital / DVD Release from metadata if available
                                val dvdDateStr = meta?.dvdDate?.takeIf { it.isNotBlank() }
                                if (!dvdDateStr.isNullOrBlank()) {
                                    val dvdInstant = DateUtil.parseToInstant(dvdDateStr)
                                    if (dvdInstant != null) {
                                        val digitalKey = "v2_${simklId}_digital"
                                        val alreadyAdded = dbItems.any { it.primaryKey == digitalKey }
                                        if (!alreadyAdded) {
                                            dbItems.add(
                                                CalendarItem(
                                                    primaryKey = digitalKey,
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
                                                    poster = posterUrl,
                                                    isLastEpisode = false,
                                                    notificationsScheduled = false
                                                )
                                            )
                                        }
                                    }
                                }
                            } else {
                                val rawDateStr = entry.date
                                val instant = DateUtil.parseToInstant(rawDateStr) ?: return@forEach

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

                                val alreadyAdded = dbItems.any { it.primaryKey == keyUnique }

                                if (!alreadyAdded) {
                                    dbItems.add(
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
                                            poster = posterUrl,
                                            isLastEpisode = isFinale && meta?.status == "ended",
                                            notificationsScheduled = false
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SimklRepository", "Failed fetching CDN v2 calendar from $url", e)
                }
            }
        }

        // Fetch movie details for all tracked movies without a DVD/digital release date yet
        val candidateMovieIds = trackedMovieIds

        val moviesWithoutDigital = candidateMovieIds.filter { movieId ->
            !dbItems.any { it.simklId == movieId && it.type == MediaType.MOVIE && it.movieReleaseType == MovieReleaseType.DIGITAL }
        }

        if (moviesWithoutDigital.isNotEmpty()) {
            Log.d("SimklRepository", "Fetching details for ${moviesWithoutDigital.size} movies missing DVD/digital release dates")
            for (movieId in moviesWithoutDigital) {
                try {
                    val movieDetail = apiService.getMovieDetails(
                        movieId = movieId,
                        authorization = bearer,
                        clientId = clientId
                    )
                    val movieTitle = movieDetail.title ?: allTrackedItems.find { it.id == movieId }?.title ?: "Untitled"
                    val moviePoster = ImageUtil.formatPosterUrl(movieDetail.poster ?: allTrackedItems.find { it.id == movieId }?.poster)

                    // Extract Digital / DVD release date from release_dates
                    val digitalStr = movieDetail.extractDigitalOrDvdReleaseDate()?.takeIf { it.isNotBlank() }
                    if (!digitalStr.isNullOrBlank()) {
                        val digitalInstant = DateUtil.parseToInstant(digitalStr)
                        if (digitalInstant != null) {
                            val digitalKey = "v2_${movieId}_digital"
                            val alreadyAdded = dbItems.any { it.primaryKey == digitalKey }
                            if (!alreadyAdded) {
                                dbItems.add(
                                    CalendarItem(
                                        primaryKey = digitalKey,
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
                                        poster = moviePoster,
                                        isLastEpisode = false,
                                        notificationsScheduled = false
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SimklRepository", "Failed fetching movie details for movieId $movieId", e)
                }
            }
        }

        val existingNotifiedKeys = try {
            calendarDao.getAllCalendarItemsList().filter { it.isNotified }.map { it.primaryKey }.toSet()
        } catch (_: Exception) {
            emptySet()
        }

        val finalDbItems = dbItems.map { item ->
            if (existingNotifiedKeys.contains(item.primaryKey)) {
                item.copy(isNotified = true)
            } else {
                item
            }
        }

        calendarDao.clearCalendarItems()
        if (finalDbItems.isNotEmpty()) {
            calendarDao.insertCalendarItems(finalDbItems)
            Log.d("SimklRepository", "Successfully synchronized ${finalDbItems.size} calendar items")

            // Initialize default notification settings for new shows while preserving user's existing settings
            try {
                val notifPrefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
                val defaultAiring = notifPrefs.getBoolean("default_notify_airing", false)
                val defaultSeasonFinished = notifPrefs.getBoolean("default_notify_season_finished", true)
                val defaultMovieTheater = notifPrefs.getBoolean("default_notify_movie_theater", false)
                val defaultMovieDigital = notifPrefs.getBoolean("default_notify_movie_digital", true)

                val distinctShows = finalDbItems.groupBy { it.simklId }
                val newSettings = distinctShows.map { (showId, items) ->
                    val sample = items.first()
                    val isMovie = sample.type == MediaType.MOVIE
                    NotificationSetting(
                        showId = showId,
                        showTitle = sample.title,
                        type = sample.type,
                        notifyEveryEpisode = if (isMovie) defaultMovieTheater else defaultAiring,
                        notifyAiredLastEpisode = if (isMovie) defaultMovieDigital else defaultSeasonFinished
                    )
                }
                settingDao.insertSettings(newSettings)
            } catch (e: Exception) {
                Log.e("SimklRepository", "Error initializing default notification settings", e)
            }

            com.example.receiver.NotificationScheduler.scheduleAllNotifications(context)
        } else {
            Log.w("SimklRepository", "No calendar items retrieved from CDN or sync")
        }
    }
}
