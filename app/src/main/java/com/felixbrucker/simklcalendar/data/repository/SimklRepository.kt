package com.felixbrucker.simklcalendar.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import com.felixbrucker.simklcalendar.BuildConfig
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.LocalItemState
import com.felixbrucker.simklcalendar.data.database.NotificationSetting
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.database.WatchedEpisode
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.model.WatchlistStatus
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.network.OAuthTokenRequest
import com.felixbrucker.simklcalendar.data.network.SimklApiService
import com.felixbrucker.simklcalendar.data.network.SimklIds
import com.felixbrucker.simklcalendar.data.network.SyncHistoryEpisodeItem
import com.felixbrucker.simklcalendar.data.network.SyncHistoryMovieItem
import com.felixbrucker.simklcalendar.data.network.SyncHistoryRequest
import com.felixbrucker.simklcalendar.data.network.SyncHistorySeasonItem
import com.felixbrucker.simklcalendar.data.network.SyncHistoryShowItem
import com.felixbrucker.simklcalendar.data.network.SyncMovieItem
import com.felixbrucker.simklcalendar.data.network.SyncSeasonItem
import com.felixbrucker.simklcalendar.data.network.SyncShowItem
import com.felixbrucker.simklcalendar.data.util.DateUtil
import com.felixbrucker.simklcalendar.data.util.PkceUtil
import com.felixbrucker.simklcalendar.data.network.TorrentSearchManager
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import com.felixbrucker.simklcalendar.data.util.destinationSubdirectory
import com.felixbrucker.torrent_search_api.SearchResultItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit
import androidx.core.content.edit
import com.felixbrucker.simklcalendar.receiver.alarm.AlarmScheduler
import com.felixbrucker.simklcalendar.receiver.download.DownloadCompletedReceiver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.temporal.ChronoUnit
import java.util.Calendar
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class SimklRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val tokenDao = db.userTokenDao()
    private val calendarDao = db.calendarItemDao()
    private val settingDao = db.notificationSettingDao()
    private val watchlistDao = db.watchlistDao()
    private val watchedDao = db.watchedEpisodeDao()
    private val searchLinkDao = db.customSearchLinkDao()
    private val itemDownloadSettingsDao = db.itemDownloadSettingsDao()
    private val authPrefs = context.getSharedPreferences("simkl_pkce_auth", Context.MODE_PRIVATE)
    private val syncPrefs = context.getSharedPreferences("simkl_sync_prefs", Context.MODE_PRIVATE)
    private val downloadPrefs = context.getSharedPreferences("auto_download_prefs", Context.MODE_PRIVATE)

    private val torrentSearchManager = TorrentSearchManager(itemDownloadSettingsDao, downloadPrefs)
    val torrentServiceHelper = TorrentServiceHelper.getInstance(context)

    val activeUserToken: Flow<UserToken?> = tokenDao.getUserToken()
    val calendarItems: Flow<List<CalendarItemWithWatchlist>> = calendarDao.getAllCalendarItems()
    val notificationSettings: Flow<List<NotificationSetting>> = settingDao.getAllSettings()
    val watchedEpisodes: Flow<List<WatchedEpisode>> = watchedDao.getAllWatchedEpisodesFlow()
    val customSearchLinks: Flow<List<CustomSearchLink>> = searchLinkDao.getAllSearchLinks()
    val watchlistItems: Flow<List<TrackedWatchlistItem>> = watchlistDao.getAllTrackedItemsFlow()

    suspend fun saveItemDownloadSettings(settings: ItemDownloadSettings) = withContext(Dispatchers.IO) {
        itemDownloadSettingsDao.insertOrUpdate(settings)
    }

    fun getItemDownloadSettingsFlow(simklId: Int): Flow<ItemDownloadSettings?> {
        return itemDownloadSettingsDao.getSettingsFlow(simklId)
    }

    suspend fun searchTorrents(item: CalendarItemWithWatchlist): List<SearchResultItem> {
        return torrentSearchManager.search(item)
    }

    suspend fun insertSearchLink(link: CustomSearchLink): Long = withContext(Dispatchers.IO) {
        searchLinkDao.insertSearchLink(link)
    }

    suspend fun updateSearchLink(link: CustomSearchLink) = withContext(Dispatchers.IO) {
        searchLinkDao.updateSearchLink(link)
    }

    suspend fun updateSearchLinks(links: List<CustomSearchLink>) = withContext(Dispatchers.IO) {
        searchLinkDao.updateSearchLinks(links)
    }

    suspend fun deleteSearchLink(link: CustomSearchLink) = withContext(Dispatchers.IO) {
        searchLinkDao.deleteSearchLink(link)
    }

    suspend fun updateMediaStatus(primaryKey: String, status: MediaStatus) = withContext(Dispatchers.IO) {
        calendarDao.updateMediaStatus(primaryKey, status)
    }

    suspend fun updateSeasonMediaStatus(simklId: Int, season: Int, status: MediaStatus) = withContext(Dispatchers.IO) {
        calendarDao.updateSeasonMediaStatus(simklId, season, status, Instant.now())
    }

    suspend fun updateDownloadTaskId(primaryKey: String, taskId: String?, status: MediaStatus) = withContext(Dispatchers.IO) {
        calendarDao.updateDownloadTaskId(primaryKey, taskId, status)
    }

    suspend fun searchAndDownloadSeason(simklId: Int, season: Int) = withContext(Dispatchers.IO) {
        val unwatchedItems = calendarDao.getUnwatchedDownloadableSeasonItems(simklId, season)
        unwatchedItems.forEach { item ->
            updateMediaStatus(item.primaryKey, MediaStatus.WANTED)
            val updatedItem = calendarDao.findItem(item.primaryKey) ?: item
            try {
                searchAndDownloadEpisode(updatedItem)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("SimklRepository", "Failed to search and download episode ${item.primaryKey}", e)
            }
        }
    }

    suspend fun updateItemAiredStatus(item: CalendarItemWithWatchlist) = withContext(Dispatchers.IO) {
        val calendarItem = item.calendarItem
        if (item.mediaStatus != MediaStatus.NOT_AIRED_YET) return@withContext

        val settings = itemDownloadSettingsDao.getSettings(item.simklId)

        val newStatus = determineStatus(
            airDate = calendarItem.date,
            settings = settings,
            mediaType = item.type,
            isTheaterRelease = calendarItem.movieReleaseType == MovieReleaseType.THEATER,
            isWatched = item.isWatched,
        )
        updateMediaStatus(calendarItem.primaryKey, newStatus)
        if (newStatus == MediaStatus.WANTED) {
            val updatedItem = calendarDao.findItem(calendarItem.primaryKey) ?: return@withContext
            try {
                searchAndDownloadEpisode(updatedItem)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("SimklRepository", "Failed to search and download episode ${calendarItem.primaryKey}", e)
            }
        }

        if (calendarItem.isSeasonFinale && calendarItem.season != null && item.type != MediaType.MOVIE) {
            val isDownloadSeasonUnwatchedEnabled = settings?.downloadSeasonUnwatched ?: when (item.type) {
                MediaType.TV -> downloadPrefs.getBoolean("auto_download_season_unwatched_tv", false)
                MediaType.ANIME -> downloadPrefs.getBoolean("auto_download_season_unwatched_anime", false)
                MediaType.MOVIE -> false
            }
            if (isDownloadSeasonUnwatchedEnabled) {
                searchAndDownloadSeason(item.simklId, calendarItem.season)
            }
        }
    }

    fun determineStatus(
        airDate: Instant,
        settings: ItemDownloadSettings?,
        mediaType: MediaType,
        isTheaterRelease: Boolean,
        isWatched: Boolean,
    ): MediaStatus {
        if (airDate.isAfter(Instant.now())) return MediaStatus.NOT_AIRED_YET
        if (isTheaterRelease || isWatched) return MediaStatus.IGNORED

        val globalKey = when (mediaType) {
            MediaType.TV -> "auto_download_unwatched_tv"
            MediaType.ANIME -> "auto_download_unwatched_anime"
            MediaType.MOVIE -> "auto_download_unwatched_movie"
        }
        val globalIsAutoDownloadUnwatched = downloadPrefs.getBoolean(globalKey, false)

        val isAutoDownloadUnwatched = settings?.downloadUnwatched ?: globalIsAutoDownloadUnwatched
        return if (isAutoDownloadUnwatched) MediaStatus.WANTED else MediaStatus.IGNORED
    }

    fun generateCompletionIntentUri(primaryKey: String): String {
        val intent = Intent(DownloadCompletedReceiver.ACTION_DOWNLOAD_COMPLETED).apply {
            setClassName(context.packageName, DownloadCompletedReceiver::class.java.name)
            putExtra(DownloadCompletedReceiver.EXTRA_ITEM_PRIMARY_KEY, primaryKey)
        }
        return intent.toUri(Intent.URI_INTENT_SCHEME)
    }

    suspend fun searchAndDownloadEpisode(
        item: CalendarItemWithWatchlist
    ): Result<String> = withContext(Dispatchers.IO) {
        // 1. Set status to WANTED (if not already)
        if (item.mediaStatus != MediaStatus.WANTED) {
            updateMediaStatus(item.primaryKey, MediaStatus.WANTED)
        }

        // 2. Search torrents
        val results = try {
            searchTorrents(item)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        if (results.isEmpty()) {
            return@withContext Result.failure(Exception("No torrent results found for this episode."))
        }

        // 3. Select first result and start download
        val firstResult = results.first()
        val completionUri = generateCompletionIntentUri(item.primaryKey)

        val result = torrentServiceHelper.addTorrent(
            uri = firstResult.uri.toString(),
            name = firstResult.name,
            destinationSubdirectory = item.destinationSubdirectory(),
            createSubfolderByName = false,
            notifyOnCompletion = true,
            fileSelectionMode = "BIGGEST",
            onCompletionIntentUri = completionUri,
        )

        result.onSuccess { taskId ->
            // 4. Update status to DOWNLOADING with taskId
            updateDownloadTaskId(item.primaryKey, taskId, MediaStatus.DOWNLOADING)
        }

        result
    }

    suspend fun searchAndDownloadWantedItems(
        withDelay: Duration = 50.milliseconds,
        onProgress: (current: Int, total: Int, itemTitle: String, success: Boolean) -> Unit = { _, _, _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val items = calendarItems.first()
        val wantedItems = items.filter { it.mediaStatus == MediaStatus.WANTED }

        if (wantedItems.isEmpty()) return@withContext

        wantedItems.forEachIndexed { index, item ->
            val result = searchAndDownloadEpisode(item)
            onProgress(index + 1, wantedItems.size, item.title, result.isSuccess)
            delay(withDelay) // Artificial delay to prevent flicker and show progress
        }
    }

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val appName = BuildConfig.APP_NAME
    private val appVersion = BuildConfig.VERSION_NAME
    private val userAgent = "$appName/$appVersion"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", userAgent)
                .header("app-name", appName)
                .header("app-version", appVersion)
                .build()
            chain.proceed(request)
        }
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

        authPrefs.edit {
            putString("pkce_code_verifier", codeVerifier)
                .putString("pkce_redirect_uri", redirectUri)
                .putString("pkce_state", state)
        }

        val encodedRedirect = try {
            URLEncoder.encode(redirectUri, "UTF-8")
        } catch (_: Exception) {
            redirectUri
        }

        return "https://simkl.com/oauth/authorize?response_type=code&client_id=$clientId&redirect_uri=$encodedRedirect&code_challenge=$codeChallenge&code_challenge_method=S256&state=$state"
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        tokenDao.clearUserToken()
        calendarDao.clearCalendarItems()
        watchlistDao.clearAll()
        watchedDao.clearAll()
        syncPrefs.edit { clear() }
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
            if (accessToken.isEmpty()) {
                Log.e("SimklRepository", "OAuth returned empty access token")
                return@withContext false
            }

            // Successfully received token: clear stored PKCE parameters
            authPrefs.edit {
                remove("pkce_code_verifier")
                    .remove("pkce_redirect_uri")
                    .remove("pkce_state")
            }

            // 2. Fetch user profile from POST /users/settings to get the user's name
            val username = try {
                val userResponse = apiService.getUserSettings(
                    authorization = "Bearer $accessToken",
                    clientId = clientId
                )
                // In Simkl POST /users/settings, user profile contains "name" (which holds username)
                userResponse.user.name
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
    }

    suspend fun getActiveUserToken(): UserToken? = withContext(Dispatchers.IO) {
        tokenDao.getActiveToken()
    }

    suspend fun syncCalendar(force: Boolean = false) = withContext(Dispatchers.IO) {
        val watchlistSyncResult = syncWatchlist(forceFullSync = force)
        val lastJsonSyncTimestamp = syncPrefs.getLong("last_calendar_json_sync", 0L)
        // If calendar jsons haven't been synced in >6h, sync calendar jsons
        val calendarJsonSyncResult = syncCalendarJsons(forceFullSync = force)
        // Backfill missing past episodes if month changed and > 1 day since last sync
        val backfillSyncResult = backfillPastEpisodes(lastSyncTimestamp = if (force) 0L else lastJsonSyncTimestamp)
        if (watchlistSyncResult.hasWantedItems || calendarJsonSyncResult.hasWantedItems || backfillSyncResult.hasWantedItems) {
            searchAndDownloadWantedItems()
        }
        if (watchlistSyncResult.hasCalendarItemChanges || calendarJsonSyncResult.hasCalendarItemChanges || backfillSyncResult.hasCalendarItemChanges) {
            AlarmScheduler.scheduleAllItemsAiredAlarms(context)
        }
    }

    /**
     * Fetches all episodes for tracked TV shows and Anime to backfill past episodes
     * that are missing from the CDN calendar V2 JSONs (which only cover 4 months).
     *
     * @param lastSyncTimestamp The global JSON calendar sync timestamp from BEFORE the current sync run.
     */
    suspend fun backfillPastEpisodes(
        lastSyncTimestamp: Long
    ): SyncResult = withContext(Dispatchers.IO) {
        val userToken = tokenDao.getActiveToken()
        if (userToken == null || userToken.accessToken.isEmpty()) return@withContext SyncResult()

        val now = Instant.now()
        val lastSyncInstant = Instant.ofEpochMilli(lastSyncTimestamp)

        val nowCal = Calendar.getInstance()
        val lastCal = Calendar.getInstance().apply { timeInMillis = lastSyncTimestamp }

        val sameMonth = nowCal.get(Calendar.YEAR) == lastCal.get(Calendar.YEAR) &&
                nowCal.get(Calendar.MONTH) == lastCal.get(Calendar.MONTH)

        val oneDayAgo = now.minus(1, ChronoUnit.DAYS)
        val moreThanOneDayAgo = lastSyncInstant.isBefore(oneDayAgo)

        // Logic: Sync when month changed AND more than 1 day since last sync
        if (sameMonth || !moreThanOneDayAgo) {
            Log.d("SimklRepository", "Backfill skipped: same month or < 1 day since last sync")
            return@withContext SyncResult()
        }

        Log.d("SimklRepository", "Starting backfill for past episodes...")

        val trackedShows = watchlistDao.getTrackedItemsByTypes(listOf(MediaType.TV, MediaType.ANIME))
        if (trackedShows.isEmpty()) return@withContext SyncResult()

        val trackedIds = trackedShows.map { it.simklId }
        val clientId = BuildConfig.SIMKL_CLIENT_ID.takeIf { it.isNotEmpty() && it != "YOUR_SIMKL_CLIENT_ID" }

        // Fetch targeted data from DB
        val existingDbItems = calendarDao.getCalendarEntitiesForSimklIds(trackedIds)
        val existingItemsMap = existingDbItems.associateBy { it.primaryKey }
        val watchedLookup = watchedDao.getWatchedEpisodesForSimklIds(trackedIds).groupBy { it.simklId }
        val settingsMap = itemDownloadSettingsDao.getSettingsBySimklIds(trackedIds).associateBy { it.simklId }

        val itemsToInsert = mutableMapOf<String, CalendarItem>()
        val itemsToUpdate = mutableMapOf<String, CalendarItem>()
        val localStatesToInsert = mutableListOf<LocalItemState>()

        fun processCalendarItem(newItem: CalendarItem, initialStatus: MediaStatus) {
            val existing = existingItemsMap[newItem.primaryKey]
            if (existing == null) {
                val currentInsert = itemsToInsert[newItem.primaryKey]
                itemsToInsert[newItem.primaryKey] = currentInsert?.updatedWith(newItem) ?: newItem
                localStatesToInsert.add(LocalItemState(newItem.primaryKey, initialStatus))
                return
            }

            val base = itemsToUpdate[newItem.primaryKey] ?: existing
            val updated = base.updatedWith(newItem)
            if (updated != base) {
                itemsToUpdate[newItem.primaryKey] = updated
            }
        }

        coroutineScope {
            val deferred = trackedShows.map { show ->
                async {
                    try {
                        val episodes = if (show.type == MediaType.TV) {
                            apiService.getTvEpisodes(show.simklId, clientId)
                        } else {
                            apiService.getAnimeEpisodes(show.simklId, clientId)
                        }
                        show to episodes
                    } catch (e: Exception) {
                        Log.e("SimklRepository", "Failed backfill for ${show.simklId}", e)
                        show to null
                    }
                }
            }

            val results = deferred.awaitAll()
            val oneMonthAgo = Instant.now().minus(30, ChronoUnit.DAYS)

            for ((show, episodes) in results) {
                if (episodes == null) continue

                val showWatchedList = watchedLookup[show.simklId]

                for (ep in episodes) {
                    // Only regular episodes (no specials) and already aired
                    if (ep.type != "episode" || !ep.aired) continue

                    val instant = DateUtil.parseToInstant(ep.date) ?: continue
                    val seasonNum = ep.season ?: 1
                    val epNum = ep.episode ?: continue
                    val epTitle = ep.title

                    val keyUnique = "v2_${show.simklId}_${seasonNum}_${epNum}"

                    val watchedEntry = showWatchedList?.firstOrNull {
                        it.season == seasonNum && it.episodeNumber == epNum
                    }
                    val epWatchedTimestamp = watchedEntry?.watchedAt

                    // Automatic cleanup filter: Omit episodes that have already been watched over 1 month ago
                    if (epWatchedTimestamp != null && epWatchedTimestamp.isBefore(oneMonthAgo)) {
                        continue
                    }

                    val status = determineStatus(
                        airDate = instant,
                        settings = settingsMap[show.simklId],
                        mediaType = show.type,
                        isTheaterRelease = false,
                        isWatched = epWatchedTimestamp != null,
                    )

                    processCalendarItem(
                        CalendarItem(
                            primaryKey = keyUnique,
                            simklId = show.simklId,
                            episodeTitle = epTitle,
                            season = seasonNum,
                            episodeNumber = epNum,
                            date = instant,
                            movieReleaseType = null,
                            isSeasonPremiere = epNum == 1,
                            isSeasonFinale = false, // Not available in this endpoint, will be updated by calendar jsons if recent
                            watchedAt = epWatchedTimestamp,
                        ),
                        initialStatus = status
                    )
                }
            }
        }

        if (itemsToInsert.isNotEmpty()) {
            calendarDao.insertCalendarItems(itemsToInsert.values.toList())
            calendarDao.insertLocalItemStates(localStatesToInsert)
        }
        if (itemsToUpdate.isNotEmpty()) {
            calendarDao.updateCalendarItems(itemsToUpdate.values.toList())
        }

        Log.d("SimklRepository", "Backfill complete: applied ${itemsToInsert.size + itemsToUpdate.size} DB mutations (${itemsToInsert.size} inserted, ${itemsToUpdate.size} updated)")
        val hasWantedItems = localStatesToInsert.any { it.mediaStatus == MediaStatus.WANTED }

        SyncResult(
            hasCalendarItemChanges = itemsToInsert.isNotEmpty() || itemsToUpdate.isNotEmpty(),
            hasWantedItems = hasWantedItems,
        )
    }

    /**
     * Cleans up old calendar items that have been watched over a month ago (30 days).
     * Unwatched episodes remain in the calendar indefinitely so users don't miss past unaired/unwatched episodes.
     */
    suspend fun cleanupOldWatchedCalendarItems(cutoffDays: Long = 30): Int = withContext(Dispatchers.IO) {
        try {
            val cutoff = Instant.now().minus(cutoffDays, ChronoUnit.DAYS)
            val deletedCount = calendarDao.deleteWatchedItemsOlderThan(cutoff)
            if (deletedCount > 0) {
                Log.d("SimklRepository", "Cleaned up $deletedCount old watched calendar items (watched over $cutoffDays days ago)")
            }
            deletedCount
        } catch (e: Exception) {
            Log.e("SimklRepository", "Error cleaning up old watched calendar items", e)
            0
        }
    }

    /**
     * Performs lightweight watchlist and watched history synchronization.
     * Uses /sync/activities timestamp to determine if changes exist.
     * Only transfers tiny JSON payloads on delta updates.
     */
    suspend fun syncWatchlist(forceFullSync: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        val userToken = tokenDao.getActiveToken()
        if (userToken == null || userToken.accessToken.isEmpty()) {
            Log.d("SimklRepository", "No authenticated user token found, skipping watchlist sync.")
            return@withContext SyncResult()
        }
        val clientId = BuildConfig.SIMKL_CLIENT_ID.takeIf { it.isNotEmpty() && it != "YOUR_SIMKL_CLIENT_ID" }
        val bearer = "Bearer ${userToken.accessToken}"
        var changesDetected = false

        try {
            // Phase 1: Check /sync/activities to see if any library changes occurred
            val activities = apiService.getSyncActivities(
                authorization = bearer,
                clientId = clientId
            )

            val currentActivitiesTimestamp = activities.all
            val savedTimestamp = if (forceFullSync) null else syncPrefs.getString("last_activities_all", null)

            val shouldFetchDeltas = savedTimestamp == null || (currentActivitiesTimestamp != null && currentActivitiesTimestamp != savedTimestamp)

            if (shouldFetchDeltas) {
                Log.d("SimklRepository", "Watchlist Sync: Calling /sync/all-items (forceFullSync=$forceFullSync, saved=$savedTimestamp, current=$currentActivitiesTimestamp)")

                val syncResponse = apiService.getSyncAllItems(
                    authorization = bearer,
                    clientId = clientId,
                    dateFrom = savedTimestamp,
                )

                val collectedSimklIds = mutableSetOf<Int>()
                syncResponse.shows?.forEach { collectedSimklIds.add(it.show.ids.simkl) }
                syncResponse.anime?.forEach { collectedSimklIds.add(it.show.ids.simkl) }
                syncResponse.movies?.forEach { collectedSimklIds.add(it.movie.ids.simkl) }

                val existingTrackedMap = watchlistDao.getTrackedItemsBySimklIds(collectedSimklIds.toList()).associateBy { it.simklId }
                val trackedToInsert = mutableMapOf<Int, TrackedWatchlistItem>()
                val trackedToUpdate = mutableMapOf<Int, TrackedWatchlistItem>()
                val trackedToDelete = mutableSetOf<Int>()

                fun processWatchlistItem(status: WatchlistStatus, newItem: TrackedWatchlistItem) {
                    val existing = existingTrackedMap[newItem.simklId]
                    if (status != WatchlistStatus.WATCHING && status != WatchlistStatus.PLAN_TO_WATCH) {
                        if (existing != null) {
                            trackedToDelete.add(newItem.simklId)
                        }

                        return
                    }

                    if (existing == null) {
                        val currentInsert = trackedToInsert[newItem.simklId]
                        trackedToInsert[newItem.simklId] = currentInsert?.updatedWith(newItem) ?: newItem

                        return
                    }

                    val base = trackedToUpdate[newItem.simklId] ?: existing
                    val updated = base.updatedWith(newItem)
                    if (updated != base) {
                        trackedToUpdate[newItem.simklId] = updated
                    }
                }

                val newWatchedEpisodes = mutableListOf<WatchedEpisode>()

                fun extractWatched(simklId: Int, seasons: List<SyncSeasonItem>?) {
                    seasons?.forEach { seasonItem ->
                        val sNum = seasonItem.number
                        seasonItem.episodes?.forEach { epItem ->
                            if (!epItem.watchedAt.isNullOrBlank()) {
                                val watchedInstant = DateUtil.parseToInstant(epItem.watchedAt)
                                newWatchedEpisodes.add(
                                    WatchedEpisode(
                                        simklId = simklId,
                                        season = sNum,
                                        episodeNumber = epItem.number,
                                        watchedAt = watchedInstant
                                    )
                                )
                            }
                        }
                    }
                }

                // Process TV Shows
                syncResponse.shows?.forEach { item ->
                    val trackedWatchlistItem = TrackedWatchlistItem.fromShowItem(item, type = MediaType.TV)
                    extractWatched(trackedWatchlistItem.simklId, item.seasons)
                    processWatchlistItem(
                        status = WatchlistStatus.fromString(item.status),
                        newItem = trackedWatchlistItem,
                    )
                }

                // Process Anime
                syncResponse.anime?.forEach { item ->
                    val trackedWatchlistItem = TrackedWatchlistItem.fromShowItem(item, type = MediaType.ANIME)
                    extractWatched(trackedWatchlistItem.simklId, item.seasons)
                    processWatchlistItem(
                        status = WatchlistStatus.fromString(item.status),
                        newItem = trackedWatchlistItem,
                    )
                }

                // Process Movies
                syncResponse.movies?.forEach { item ->
                    val trackedWatchlistItem = TrackedWatchlistItem.fromMovieItem(item)
                    processWatchlistItem(
                        status = WatchlistStatus.fromString(item.status),
                        newItem = trackedWatchlistItem,
                    )
                }

                // Remove tracked items no longer in user's active watchlist
                if (trackedToDelete.isNotEmpty()) {
                    for (simklId in trackedToDelete) {
                        watchlistDao.deleteItem(simklId)
                    }
                    Log.d("SimklRepository", "Deleted ${trackedToDelete.size} untracked watchlist items from DB")
                }

                if (trackedToInsert.isNotEmpty()) {
                    watchlistDao.insertItems(trackedToInsert.values.toList())
                    Log.d("SimklRepository", "Inserted ${trackedToInsert.size} new tracked watchlist items into DB")

                    // Initialize default notification settings for newly inserted shows
                    try {
                        val notifPrefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
                        val defaultAiring = notifPrefs.getBoolean("default_notify_airing", false)
                        val defaultSeasonFinished = notifPrefs.getBoolean("default_notify_season_finished", true)
                        val defaultMovieTheater = notifPrefs.getBoolean("default_notify_movie_theater", false)
                        val defaultMovieDigital = notifPrefs.getBoolean("default_notify_movie_digital", true)

                        val newSettings = trackedToInsert.values.map { item ->
                            val isMovie = item.type == MediaType.MOVIE
                            NotificationSetting(
                                simklId = item.simklId,
                                notifyEveryEpisode = if (isMovie) defaultMovieTheater else defaultAiring,
                                notifyAiredLastEpisode = if (isMovie) defaultMovieDigital else defaultSeasonFinished
                            )
                        }
                        settingDao.insertSettings(newSettings)
                    } catch (e: Exception) {
                        Log.e("SimklRepository", "Error initializing default notification settings", e)
                    }
                }
                if (trackedToUpdate.isNotEmpty()) {
                    watchlistDao.updateItems(trackedToUpdate.values.toList())
                    Log.d("SimklRepository", "Updated ${trackedToUpdate.size} changed tracked watchlist items in DB")
                }

                if (savedTimestamp == null) {
                    watchedDao.clearAll()
                    calendarDao.markAllUnwatched()
                } else {
                    // Remove any WatchedEpisode entities in our DB that aren't present in the list returned by the API
                    val existingWatched = watchedDao.getWatchedEpisodesForSimklIds(collectedSimklIds.toList())
                    val newWatchedEpisodesBySimklId = newWatchedEpisodes.groupBy { it.simklId }
                    val existingWatchedBySimklId = existingWatched.groupBy { it.simklId }
                    val allWatchedToRemove = mutableListOf<WatchedEpisode>()
                    newWatchedEpisodesBySimklId.forEach { (simklId, newWatchedEpisodes) ->
                        val existingEpisodes = existingWatchedBySimklId[simklId] ?: emptyList()
                        val newWatchedKeys = newWatchedEpisodes.map { "${it.simklId}_${it.season}_${it.episodeNumber}" }.toSet()
                        val watchedToRemove = existingEpisodes.filter {
                            "${it.simklId}_${it.season}_${it.episodeNumber}" !in newWatchedKeys
                        }
                        allWatchedToRemove.addAll(watchedToRemove)
                    }

                    if (allWatchedToRemove.isNotEmpty()) {
                        watchedDao.deleteWatchedEpisodes(allWatchedToRemove)
                        for (removed in allWatchedToRemove) {
                            calendarDao.markEpisodeWatched(
                                simklId = removed.simklId,
                                season = removed.season,
                                episodeNumber = removed.episodeNumber,
                                watchedAt = null
                            )
                        }
                        Log.d("SimklRepository", "Removed ${allWatchedToRemove.size} WatchedEpisode entities not present in API response")
                    }
                }

                if (newWatchedEpisodes.isNotEmpty()) {
                    watchedDao.insertWatchedEpisodes(newWatchedEpisodes)
                    for (watched in newWatchedEpisodes) {
                        calendarDao.markEpisodeWatched(
                            simklId = watched.simklId,
                            season = watched.season,
                            episodeNumber = watched.episodeNumber,
                            watchedAt = watched.watchedAt
                        )
                    }
                }

                if (!currentActivitiesTimestamp.isNullOrEmpty()) {
                    syncPrefs.edit { putString("last_activities_all", currentActivitiesTimestamp) }
                }
                changesDetected = true
            } else {
                Log.d("SimklRepository", "Watchlist Sync: /sync/activities timestamp unchanged ($savedTimestamp), skipping /sync/all-items")
            }
        } catch (e: Exception) {
            Log.e("SimklRepository", "Error during watchlist sync", e)
        }

        // Automatic cleanup of old watched calendar items (> 30 days)
        cleanupOldWatchedCalendarItems()

        SyncResult(
            hasWatchlistItemChanges = changesDetected,
        )
    }

    /**
     * Synchronizes CDN calendar JSON files (TV, Anime, Movies) covering current month plus next 3 months (0..3).
     * Checks Last-Modified response header and only downloads files when their Last-Modified was over 6 hours ago.
     * Skips inserting episodes that were already watched over a month ago to prevent calendar backlog clutter.
     */
    suspend fun syncCalendarJsons(forceFullSync: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        val userToken = tokenDao.getActiveToken()
        if (userToken == null || userToken.accessToken.isEmpty()) {
            Log.d("SimklRepository", "No authenticated user token found, skipping calendar json sync.")
            return@withContext SyncResult()
        }
        val clientId = BuildConfig.SIMKL_CLIENT_ID.takeIf { it.isNotEmpty() && it != "YOUR_SIMKL_CLIENT_ID" }
        val bearer = "Bearer ${userToken.accessToken}"

        // Load local tracked items (IDs only for filtering)
        val allTrackedIds = watchlistDao.getAllTrackedIds().toSet()
        if (allTrackedIds.isEmpty()) {
            Log.d("SimklRepository", "No tracked items in watchlist, skipping calendar json sync.")
            return@withContext SyncResult()
        }

        val itemsToInsert = mutableMapOf<String, CalendarItem>()
        val itemsToUpdate = mutableMapOf<String, CalendarItem>()
        val localStatesToInsert = mutableListOf<LocalItemState>()
        val trackedToUpdate = mutableMapOf<Int, TrackedWatchlistItem>()

        fun processCalendarItem(
            newItem: CalendarItem,
            initialStatus: MediaStatus,
            existingItemsMap: Map<String, CalendarItem>
        ) {
            val existing = existingItemsMap[newItem.primaryKey]
            if (existing == null) {
                val currentInsert = itemsToInsert[newItem.primaryKey]
                itemsToInsert[newItem.primaryKey] = currentInsert?.updatedWith(newItem) ?: newItem
                localStatesToInsert.add(LocalItemState(newItem.primaryKey, initialStatus))
                return
            }

            val base = itemsToUpdate[newItem.primaryKey] ?: existing
            val updated = base.updatedWith(newItem)
            if (updated != base) {
                itemsToUpdate[newItem.primaryKey] = updated
            }
        }

        fun processTrackedItem(
            newItem: TrackedWatchlistItem,
            trackedItemMap: Map<Int, TrackedWatchlistItem>
        ) {
            val existing = trackedItemMap[newItem.simklId] ?: return
            val base = trackedToUpdate[newItem.simklId] ?: existing
            val updated = base.updatedWith(newItem)
            if (updated != base) {
                trackedToUpdate[newItem.simklId] = updated
            }
        }

        val sixHoursMillis = 6 * 60 * 60 * 1000L
        val nowMillis = System.currentTimeMillis()
        val oneMonthAgo = Instant.now().minus(30, ChronoUnit.DAYS)

        // 2. Fetch CDN Calendars for current month plus next 3 months (0..3) (TV, Anime, Movies) from data.simkl.in
        val currentCal = Calendar.getInstance()
        val monthsToFetch = (0..3).map { offset ->
            val cal = Calendar.getInstance().apply {
                time = currentCal.time
                add(Calendar.MONTH, offset)
            }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1 // 1-12
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
                val lastModifiedPrefKey = "cal_json_last_mod_${year}_${month}_$endpointType"
                val lastModifiedHeaderKey = "cal_json_header_${year}_${month}_$endpointType"

                val lastModifiedTimestamp = if (forceFullSync) 0L else syncPrefs.getLong(lastModifiedPrefKey, 0L)
                val savedHeader = if (forceFullSync) null else syncPrefs.getString(lastModifiedHeaderKey, null)

                // Only sync calendar jsons when their last modified was over 6h in the past
                val isOver6Hours = (nowMillis - lastModifiedTimestamp) >= sixHoursMillis
                if (lastModifiedTimestamp > 0L && !isOver6Hours) {
                    continue
                }

                try {
                    val response = apiService.getV2Calendar(
                        url = url,
                        ifModifiedSince = savedHeader,
                        clientId = clientId
                    )

                    if (response.code() == 304) {
                        Log.d("SimklRepository", "Calendar JSON $url not modified (HTTP 304)")
                        syncPrefs.edit { putLong(lastModifiedPrefKey, nowMillis) }
                        continue
                    }

                    if (!response.isSuccessful) {
                        Log.w("SimklRepository", "HTTP ${response.code()} for calendar JSON $url")
                        continue
                    }

                    val calendarResponse = response.body() ?: continue

                    // Track Last-Modified header from response
                    val responseLastModifiedHeader = response.headers()["Last-Modified"]
                    val parsedHeaderMillis = DateUtil.parseHttpDateToMillis(responseLastModifiedHeader) ?: nowMillis
                    syncPrefs.edit {
                        putLong(lastModifiedPrefKey, parsedHeaderMillis)
                            .putString(lastModifiedHeaderKey, responseLastModifiedHeader ?: "")
                    }

                    val relevantEntries = calendarResponse.calendar.filter {
                        allTrackedIds.contains(it.simklId)
                    }
                    if (relevantEntries.isEmpty()) continue

                    val metadataMap = calendarResponse.metadata

                    // Targeted DB fetch for this JSON's content
                    val relevantSimklIds = relevantEntries.map { it.simklId }.distinct()
                    val currentTrackedItemMap = watchlistDao.getTrackedItemsBySimklIds(relevantSimklIds).associateBy { it.simklId }
                    val currentExistingItemsMap = calendarDao.getCalendarEntitiesForSimklIds(relevantSimklIds).associateBy { it.primaryKey }
                    val currentWatchedLookup = watchedDao.getWatchedEpisodesForSimklIds(relevantSimklIds).groupBy { it.simklId }
                    val currentSettingsMap = itemDownloadSettingsDao.getSettingsBySimklIds(relevantSimklIds).associateBy { it.simklId }

                    for (entry in relevantEntries) {
                        val simklId = entry.simklId
                        val meta = metadataMap[simklId.toString()]
                        if (meta != null) {
                            processTrackedItem(
                                TrackedWatchlistItem(
                                    simklId = simklId,
                                    type = defaultType,
                                    title = meta.title,
                                    titleRomaji = meta.titleRomaji,
                                    poster = meta.poster
                                ),
                                currentTrackedItemMap
                            )
                        }

                        if (defaultType == MediaType.MOVIE) {
                            // 1. Process Theater Release
                            DateUtil.parseToInstant(entry.date)?.let { theaterInstant ->
                                val status = determineStatus(
                                    airDate = theaterInstant,
                                    settings = currentSettingsMap[simklId],
                                    mediaType = MediaType.MOVIE,
                                    isTheaterRelease = true,
                                    isWatched = false,
                                )
                                processCalendarItem(
                                    CalendarItem(
                                        primaryKey = "v2_${simklId}_theater",
                                        simklId = simklId,
                                        episodeTitle = null,
                                        season = null,
                                        episodeNumber = null,
                                        date = theaterInstant,
                                        movieReleaseType = MovieReleaseType.THEATER,
                                        isSeasonPremiere = false,
                                        isSeasonFinale = false,
                                    ),
                                    initialStatus = status,
                                    currentExistingItemsMap
                                )
                            }

                            // 2. Process Digital / DVD Release from metadata if available
                            meta?.dvdDate?.takeIf { it.isNotBlank() }?.let { dvdDateStr ->
                                DateUtil.parseToInstant(dvdDateStr)?.let { dvdInstant ->
                                    val status = determineStatus(
                                        airDate = dvdInstant,
                                        settings = currentSettingsMap[simklId],
                                        mediaType = MediaType.MOVIE,
                                        isTheaterRelease = false,
                                        isWatched = false,
                                    )
                                    processCalendarItem(
                                        CalendarItem(
                                            primaryKey = "v2_${simklId}_digital",
                                            simklId = simklId,
                                            episodeTitle = null,
                                            season = null,
                                            episodeNumber = null,
                                            date = dvdInstant,
                                            movieReleaseType = MovieReleaseType.DIGITAL,
                                            isSeasonPremiere = false,
                                            isSeasonFinale = false,
                                        ),
                                        initialStatus = status,
                                        currentExistingItemsMap
                                    )
                                }
                            }
                        } else {
                            val instant = DateUtil.parseToInstant(entry.date) ?: continue

                            val ep = entry.episode
                            val seasonNum = ep?.season ?: 1
                            val epNum = ep?.episode ?: 1
                            val epTitle = ep?.title

                            val isPremiere = epNum == 1
                            val isExplicitFinale = entry.finaleType != null && entry.finaleType != 0
                            val isMetadataFinale = meta?.totalEpisodes != null &&
                                meta.totalEpisodes > 1 &&
                                epNum > 1 &&
                                epNum >= meta.totalEpisodes
                            val isFinale = isExplicitFinale || isMetadataFinale

                            val keyUnique = "v2_${simklId}_${seasonNum}_${epNum}"

                            val showWatchedList = currentWatchedLookup[simklId]
                            val watchedEntry = showWatchedList?.firstOrNull {
                                it.season == seasonNum && it.episodeNumber == epNum
                            }
                            val epWatchedTimestamp = watchedEntry?.watchedAt

                            // Automatic cleanup filter: Omit episodes that have already been watched over 1 month ago
                            if (epWatchedTimestamp != null && epWatchedTimestamp.isBefore(oneMonthAgo)) {
                                continue
                            }

                            val status = determineStatus(
                                airDate = instant,
                                settings = currentSettingsMap[simklId],
                                mediaType = defaultType,
                                isTheaterRelease = false,
                                isWatched = epWatchedTimestamp != null,
                            )

                            processCalendarItem(
                                CalendarItem(
                                    primaryKey = keyUnique,
                                    simklId = simklId,
                                    episodeTitle = epTitle,
                                    season = seasonNum,
                                    episodeNumber = epNum,
                                    date = instant,
                                    movieReleaseType = null,
                                    isSeasonPremiere = isPremiere,
                                    isSeasonFinale = isFinale,
                                    watchedAt = epWatchedTimestamp,
                                ),
                                initialStatus = status,
                                currentExistingItemsMap
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SimklRepository", "Failed fetching CDN v2 calendar from $url", e)
                }
            }
        }

        // Fetch movie details for all tracked movies missing either theatrical or DVD/digital release dates
        val candidateMovieIds = watchlistDao.getTrackedIdsByTypes(listOf(MediaType.MOVIE))

        // Targeted fetch for movie details backfill
        val movieExistingItemsMap = calendarDao.getCalendarEntitiesForSimklIds(candidateMovieIds).associateBy { it.primaryKey }
        val movieSettingsMap = itemDownloadSettingsDao.getSettingsBySimklIds(candidateMovieIds).associateBy { it.simklId }
        val movieTrackedItemMap = watchlistDao.getTrackedItemsBySimklIds(candidateMovieIds).associateBy { it.simklId }

        val moviesNeedingDetails = candidateMovieIds.filter { movieId ->
            val hasDigital = movieExistingItemsMap.containsKey("v2_${movieId}_digital") || itemsToInsert.containsKey("v2_${movieId}_digital")
            val hasTheater = movieExistingItemsMap.containsKey("v2_${movieId}_theater") || itemsToInsert.containsKey("v2_${movieId}_theater")
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
                    processTrackedItem(
                        TrackedWatchlistItem(
                            simklId = movieId,
                            type = MediaType.MOVIE,
                            title = movieDetail.title,
                            poster = movieDetail.poster
                        ),
                        movieTrackedItemMap
                    )

                    // 1. Process Theatrical release date from regular released property
                    movieDetail.released?.takeIf { it.isNotBlank() }?.let { releasedStr ->
                        DateUtil.parseToInstant(releasedStr)?.let { theaterInstant ->
                            val status = determineStatus(
                                airDate = theaterInstant,
                                settings = movieSettingsMap[movieId],
                                mediaType = MediaType.MOVIE,
                                isTheaterRelease = true,
                                isWatched = false,
                            )
                            processCalendarItem(
                                CalendarItem(
                                    primaryKey = "v2_${movieId}_theater",
                                    simklId = movieId,
                                    episodeTitle = null,
                                    season = null,
                                    episodeNumber = null,
                                    date = theaterInstant,
                                    movieReleaseType = MovieReleaseType.THEATER,
                                    isSeasonPremiere = false,
                                    isSeasonFinale = false,
                                ),
                                initialStatus = status,
                                movieExistingItemsMap
                            )
                        }
                    }

                    // 2. Extract Digital / DVD release date from release_dates timeline
                    movieDetail.extractDigitalOrDvdReleaseDate()?.takeIf { it.isNotBlank() }?.let { digitalStr ->
                        DateUtil.parseToInstant(digitalStr)?.let { digitalInstant ->
                            val status = determineStatus(
                                airDate = digitalInstant,
                                settings = movieSettingsMap[movieId],
                                mediaType = MediaType.MOVIE,
                                isTheaterRelease = false,
                                isWatched = false,
                            )
                            processCalendarItem(
                                CalendarItem(
                                    primaryKey = "v2_${movieId}_digital",
                                    simklId = movieId,
                                    episodeTitle = null,
                                    season = null,
                                    episodeNumber = null,
                                    date = digitalInstant,
                                    movieReleaseType = MovieReleaseType.DIGITAL,
                                    isSeasonPremiere = false,
                                    isSeasonFinale = false,
                                ),
                                initialStatus = status,
                                movieExistingItemsMap
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SimklRepository", "Failed fetching movie details for movieId $movieId", e)
                }
            }
        }

        if (trackedToUpdate.isNotEmpty()) {
            watchlistDao.updateItems(trackedToUpdate.values.toList())
            Log.d("SimklRepository", "Updated ${trackedToUpdate.size} changed tracked watchlist items with metadata in DB")
        }

        if (itemsToInsert.isNotEmpty()) {
            calendarDao.insertCalendarItems(itemsToInsert.values.toList())
            calendarDao.insertLocalItemStates(localStatesToInsert)
        }

        if (itemsToUpdate.isNotEmpty()) {
            calendarDao.updateCalendarItems(itemsToUpdate.values.toList())
        }

        // Cleanup any old watched items from calendar table
        cleanupOldWatchedCalendarItems()

        val totalCalendarItemDbChanges = itemsToInsert.size + itemsToUpdate.size
        if (totalCalendarItemDbChanges == 0) {
            Log.d("SimklRepository", "Calendar sync complete: no changes detected, skipped DB writes")
        } else {
            Log.d("SimklRepository", "Calendar sync complete: applied $totalCalendarItemDbChanges DB mutations (${itemsToInsert.size} inserted, ${itemsToUpdate.size} updated)")
        }

        syncPrefs.edit { putLong("last_calendar_json_sync", nowMillis) }

        SyncResult(
            hasCalendarItemChanges = totalCalendarItemDbChanges > 0,
            hasWantedItems = localStatesToInsert.any { it.mediaStatus == MediaStatus.WANTED },
        )
    }

    suspend fun markEpisodeWatched(
        simklId: Int,
        season: Int?,
        episodeNumber: Int,
        mediaType: MediaType
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userToken = tokenDao.getActiveToken()
            if (userToken == null || userToken.accessToken.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("User is not logged in"))
            }

            val bearer = "Bearer ${userToken.accessToken}"
            val clientId = BuildConfig.SIMKL_CLIENT_ID.takeIf { it.isNotEmpty() && it != "YOUR_SIMKL_CLIENT_ID" }
            val effectiveSeason = season ?: 1

            val request = if (mediaType == MediaType.ANIME) {
                SyncHistoryRequest(
                    anime = listOf(
                        SyncHistoryShowItem(
                            ids = SimklIds(simkl = simklId),
                            seasons = listOf(
                                SyncHistorySeasonItem(
                                    number = effectiveSeason,
                                    episodes = listOf(
                                        SyncHistoryEpisodeItem(number = episodeNumber)
                                    )
                                )
                            )
                        )
                    )
                )
            } else {
                SyncHistoryRequest(
                    shows = listOf(
                        SyncHistoryShowItem(
                            ids = SimklIds(simkl = simklId),
                            seasons = listOf(
                                SyncHistorySeasonItem(
                                    number = effectiveSeason,
                                    episodes = listOf(
                                        SyncHistoryEpisodeItem(number = episodeNumber)
                                    )
                                )
                            )
                        )
                    )
                )
            }

            apiService.markHistoryWatched(
                authorization = bearer,
                clientId = clientId,
                request = request
            )

            // Update local database immediately
            val now = Instant.now()
            watchedDao.insertWatchedEpisodes(
                listOf(
                    WatchedEpisode(
                        simklId = simklId,
                        season = effectiveSeason,
                        episodeNumber = episodeNumber,
                        watchedAt = now
                    )
                )
            )
            calendarDao.markEpisodeWatched(
                simklId = simklId,
                season = season,
                episodeNumber = episodeNumber,
                watchedAt = now
            )

            // Trigger background watchlist sync to refresh metadata/activities
            syncWatchlist()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SimklRepository", "Failed to mark episode S${season}E${episodeNumber} as watched for simklId $simklId", e)
            Result.failure(e)
        }
    }

    suspend fun markSeasonWatched(
        simklId: Int,
        season: Int,
        mediaType: MediaType
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val userToken = tokenDao.getActiveToken()
            if (userToken == null || userToken.accessToken.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("User is not logged in"))
            }

            val bearer = "Bearer ${userToken.accessToken}"
            val clientId = BuildConfig.SIMKL_CLIENT_ID.takeIf { it.isNotEmpty() && it != "YOUR_SIMKL_CLIENT_ID" }

            // Determine if show should be marked as "completed"
            val showCalendarItems = calendarDao.getItemsForSimklId(simklId)
            val showWatchedItems = watchedDao.getWatchedEpisodesForShow(simklId)

            val seasonsSet = mutableSetOf<Int>()
            showCalendarItems.forEach { item -> item.season?.let { if (it > 0) seasonsSet.add(it) } }
            showWatchedItems.forEach { w -> if (w.season > 0) seasonsSet.add(w.season) }
            seasonsSet.add(season)
            val sortedSeasons = seasonsSet.sorted()
            val isLastSeason = sortedSeasons.isNotEmpty() && season == sortedSeasons.last()
            val prevSeasons = sortedSeasons.filter { it < season }
            val allPrevWatched = prevSeasons.all { sNum ->
                val epInSeason = showCalendarItems.filter { (it.season ?: 1) == sNum }
                val watchedInSeason = showWatchedItems.filter { it.season == sNum }
                if (epInSeason.isNotEmpty()) {
                    epInSeason.all { it.isWatched }
                } else {
                    watchedInSeason.isNotEmpty()
                }
            }
            val shouldMarkCompleted = isLastSeason && allPrevWatched

            val statusValue = if (shouldMarkCompleted) "completed" else null
            Log.d("SimklRepository", "Marking season $season as watched for simklId $simklId (isCompleted=$shouldMarkCompleted, status=$statusValue)")

            val request = if (mediaType == MediaType.ANIME) {
                SyncHistoryRequest(
                    anime = listOf(
                        SyncHistoryShowItem(
                            ids = SimklIds(simkl = simklId),
                            status = statusValue,
                            seasons = listOf(
                                SyncHistorySeasonItem(
                                    number = season
                                )
                            )
                        )
                    )
                )
            } else {
                SyncHistoryRequest(
                    shows = listOf(
                        SyncHistoryShowItem(
                            ids = SimklIds(simkl = simklId),
                            status = statusValue,
                            seasons = listOf(
                                SyncHistorySeasonItem(
                                    number = season
                                )
                            )
                        )
                    )
                )
            }

            apiService.markHistoryWatched(
                authorization = bearer,
                clientId = clientId,
                request = request
            )

            // Update local database immediately
            val now = Instant.now()
            val seasonEpisodes = showCalendarItems.filter { (it.season ?: 1) == season }

            if (seasonEpisodes.isNotEmpty()) {
                val newWatched = seasonEpisodes.mapNotNull { item ->
                    item.episodeNumber?.let { epNum ->
                        WatchedEpisode(
                            simklId = simklId,
                            season = season,
                            episodeNumber = epNum,
                            watchedAt = now
                        )
                    }
                }
                if (newWatched.isNotEmpty()) {
                    watchedDao.insertWatchedEpisodes(newWatched)
                }
            }

            calendarDao.markSeasonWatched(
                simklId = simklId,
                season = season,
                watchedAt = now
            )

            // Trigger background watchlist sync
            syncWatchlist()

            Result.success(shouldMarkCompleted)
        } catch (e: Exception) {
            Log.e("SimklRepository", "Failed to mark season $season as watched for simklId $simklId", e)
            Result.failure(e)
        }
    }

    suspend fun markMovieWatched(
        simklId: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userToken = tokenDao.getActiveToken()
            if (userToken == null || userToken.accessToken.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("User is not logged in"))
            }

            val bearer = "Bearer ${userToken.accessToken}"
            val clientId = BuildConfig.SIMKL_CLIENT_ID.takeIf { it.isNotEmpty() && it != "YOUR_SIMKL_CLIENT_ID" }

            val request = SyncHistoryRequest(
                movies = listOf(
                    SyncHistoryMovieItem(
                        ids = SimklIds(simkl = simklId)
                    )
                )
            )

            apiService.markHistoryWatched(
                authorization = bearer,
                clientId = clientId,
                request = request
            )

            val now = Instant.now()
            calendarDao.markMovieWatched(simklId = simklId, watchedAt = now)

            // Trigger background watchlist sync
            syncWatchlist()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SimklRepository", "Failed to mark movie as watched for simklId $simklId", e)
            Result.failure(e)
        }
    }

    suspend fun markEpisodeUnwatched(
        simklId: Int,
        season: Int?,
        episodeNumber: Int,
        mediaType: MediaType
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userToken = tokenDao.getActiveToken()
            if (userToken == null || userToken.accessToken.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("User is not logged in"))
            }

            val bearer = "Bearer ${userToken.accessToken}"
            val clientId = BuildConfig.SIMKL_CLIENT_ID.takeIf { it.isNotEmpty() && it != "YOUR_SIMKL_CLIENT_ID" }
            val effectiveSeason = season ?: 1

            val request = if (mediaType == MediaType.ANIME) {
                SyncHistoryRequest(
                    anime = listOf(
                        SyncHistoryShowItem(
                            ids = SimklIds(simkl = simklId),
                            seasons = listOf(
                                SyncHistorySeasonItem(
                                    number = effectiveSeason,
                                    episodes = listOf(
                                        SyncHistoryEpisodeItem(number = episodeNumber)
                                    )
                                )
                            )
                        )
                    )
                )
            } else {
                SyncHistoryRequest(
                    shows = listOf(
                        SyncHistoryShowItem(
                            ids = SimklIds(simkl = simklId),
                            seasons = listOf(
                                SyncHistorySeasonItem(
                                    number = effectiveSeason,
                                    episodes = listOf(
                                        SyncHistoryEpisodeItem(number = episodeNumber)
                                    )
                                )
                            )
                        )
                    )
                )
            }

            apiService.markHistoryUnwatched(
                authorization = bearer,
                clientId = clientId,
                request = request
            )

            // Revert local changes immediately
            watchedDao.deleteWatchedEpisode(
                simklId = simklId,
                season = effectiveSeason,
                episodeNumber = episodeNumber
            )
            calendarDao.markEpisodeWatched(
                simklId = simklId,
                season = season,
                episodeNumber = episodeNumber,
                watchedAt = null
            )

            // Trigger background watchlist sync to refresh metadata/activities
            syncWatchlist()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SimklRepository", "Failed to mark episode S${season}E${episodeNumber} as unwatched for simklId $simklId", e)
            Result.failure(e)
        }
    }

    suspend fun markSeasonUnwatched(
        simklId: Int,
        season: Int,
        mediaType: MediaType
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userToken = tokenDao.getActiveToken()
            if (userToken == null || userToken.accessToken.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("User is not logged in"))
            }

            val bearer = "Bearer ${userToken.accessToken}"
            val clientId = BuildConfig.SIMKL_CLIENT_ID.takeIf { it.isNotEmpty() && it != "YOUR_SIMKL_CLIENT_ID" }

            val request = if (mediaType == MediaType.ANIME) {
                SyncHistoryRequest(
                    anime = listOf(
                        SyncHistoryShowItem(
                            ids = SimklIds(simkl = simklId),
                            seasons = listOf(
                                SyncHistorySeasonItem(
                                    number = season
                                )
                            )
                        )
                    )
                )
            } else {
                SyncHistoryRequest(
                    shows = listOf(
                        SyncHistoryShowItem(
                            ids = SimklIds(simkl = simklId),
                            seasons = listOf(
                                SyncHistorySeasonItem(
                                    number = season
                                )
                            )
                        )
                    )
                )
            }

            apiService.markHistoryUnwatched(
                authorization = bearer,
                clientId = clientId,
                request = request
            )

            // Revert local changes immediately
            watchedDao.deleteWatchedSeason(
                simklId = simklId,
                season = season
            )

            calendarDao.markSeasonWatched(
                simklId = simklId,
                season = season,
                watchedAt = null
            )

            // Trigger background watchlist sync
            syncWatchlist()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SimklRepository", "Failed to mark season $season as unwatched for simklId $simklId", e)
            Result.failure(e)
        }
    }

    suspend fun markMovieUnwatched(
        simklId: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userToken = tokenDao.getActiveToken()
            if (userToken == null || userToken.accessToken.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("User is not logged in"))
            }

            val bearer = "Bearer ${userToken.accessToken}"
            val clientId = BuildConfig.SIMKL_CLIENT_ID.takeIf { it.isNotEmpty() && it != "YOUR_SIMKL_CLIENT_ID" }

            val request = SyncHistoryRequest(
                movies = listOf(
                    SyncHistoryMovieItem(
                        ids = SimklIds(simkl = simklId)
                    )
                )
            )

            apiService.markHistoryUnwatched(
                authorization = bearer,
                clientId = clientId,
                request = request
            )

            calendarDao.markMovieWatched(simklId = simklId, watchedAt = null)

            // Trigger background watchlist sync
            syncWatchlist()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SimklRepository", "Failed to mark movie as unwatched for simklId $simklId", e)
            Result.failure(e)
        }
    }
}

data class SyncResult(
    val hasWatchlistItemChanges: Boolean = false,
    val hasCalendarItemChanges: Boolean = false,
    val hasWantedItems: Boolean = false,
)

fun TrackedWatchlistItem.Companion.fromShowItem(item: SyncShowItem, type: MediaType): TrackedWatchlistItem {
    val media = item.show

    return TrackedWatchlistItem(
        simklId = media.ids.simkl,
        type = type,
        title = media.title,
        poster = media.poster
    )
}

fun TrackedWatchlistItem.Companion.fromMovieItem(item: SyncMovieItem): TrackedWatchlistItem {
    val media = item.movie

    return TrackedWatchlistItem(
        simklId = media.ids.simkl,
        type = MediaType.MOVIE,
        title = media.title,
        poster = media.poster
    )
}
