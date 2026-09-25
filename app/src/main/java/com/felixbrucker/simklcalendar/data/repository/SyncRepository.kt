package com.felixbrucker.simklcalendar.data.repository

import timber.log.Timber
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.LocalItemState
import com.felixbrucker.simklcalendar.data.database.NotificationSetting
import com.felixbrucker.simklcalendar.data.database.NotificationSettingDao
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.database.WatchedEpisode
import com.felixbrucker.simklcalendar.data.database.WatchedEpisodeDao
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.model.WatchlistStatus
import com.felixbrucker.simklcalendar.data.network.AuthenticatedSimklApiService
import com.felixbrucker.simklcalendar.data.network.PublicSimklApiService
import com.felixbrucker.simklcalendar.data.network.SyncMovieItem
import com.felixbrucker.simklcalendar.data.network.SyncSeasonItem
import com.felixbrucker.simklcalendar.data.network.SyncShowItem
import com.felixbrucker.simklcalendar.data.preferences.NotificationRepository
import com.felixbrucker.simklcalendar.data.preferences.SyncMetadataRepository
import com.felixbrucker.simklcalendar.data.util.DateUtil
import com.felixbrucker.simklcalendar.data.util.MediaStatusResolver
import com.felixbrucker.simklcalendar.receiver.alarm.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

internal data class SyncResult(
    val hasWatchlistItemChanges: Boolean = false,
    val hasCalendarItemChanges: Boolean = false,
    val hasWantedItems: Boolean = false,
)

@Singleton
class SyncRepository @Inject constructor(
    private val tokenDao: UserTokenDao,
    private val calendarDao: CalendarItemDao,
    private val settingDao: NotificationSettingDao,
    private val watchlistDao: WatchlistDao,
    private val watchedDao: WatchedEpisodeDao,
    private val itemDownloadSettingsDao: ItemDownloadSettingsDao,
    private val publicSimklApiService: PublicSimklApiService,
    private val authenticatedSimklApiService: AuthenticatedSimklApiService,
    private val notificationRepo: NotificationRepository,
    private val syncMetadataRepo: SyncMetadataRepository,
    private val downloadRepository: DownloadRepository,
    private val mediaStatusResolver: MediaStatusResolver,
    private val alarmScheduler: AlarmScheduler,
) {
    suspend fun syncCalendar(force: Boolean = false) = withContext(Dispatchers.IO) {
        val token = tokenDao.getActiveToken()
        if (token == null || token.accessToken.isEmpty()) {
            Timber.tag("SyncRepository").d("Skipping syncCalendar: user is not authenticated")
            return@withContext
        }
        val watchlistSyncResult = syncWatchlist(forceFullSync = force)
        val lastJsonSyncTimestamp = syncMetadataRepo.preferencesFlow.first().lastCalendarJsonSync
        // If calendar jsons haven't been synced in >6h, sync calendar jsons
        val calendarJsonSyncResult = syncCalendarJsons(forceFullSync = force)
        // Backfill missing past episodes if month changed and > 1 day since last sync
        val backfillSyncResult = backfillPastEpisodes(lastSyncTimestamp = if (force) 0L else lastJsonSyncTimestamp)
        if (watchlistSyncResult.hasWantedItems || calendarJsonSyncResult.hasWantedItems || backfillSyncResult.hasWantedItems) {
            downloadRepository.searchAndDownloadWantedItems()
        }
        if (watchlistSyncResult.hasCalendarItemChanges || calendarJsonSyncResult.hasCalendarItemChanges || backfillSyncResult.hasCalendarItemChanges) {
            alarmScheduler.scheduleAllItemsAiredAlarms()
        }
    }

    /**
     * Performs lightweight watchlist and watched history synchronization.
     * Uses /sync/activities timestamp to determine if changes exist.
     * Only transfers tiny JSON payloads on delta updates.
     */
    internal suspend fun syncWatchlist(forceFullSync: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        val token = tokenDao.getActiveToken()
        if (token == null || token.accessToken.isEmpty()) {
            Timber.tag("SyncRepository").d("Skipping syncWatchlist: user is not authenticated")
            return@withContext SyncResult()
        }
        var changesDetected = false

        try {
            // Phase 1: Check /sync/activities to see if any library changes occurred
            val activities = authenticatedSimklApiService.getSyncActivities()

            val currentActivitiesTimestamp = activities.all
            val savedTimestamp = if (forceFullSync) null else syncMetadataRepo.preferencesFlow.first().lastActivitiesAll

            val shouldFetchDeltas = savedTimestamp == null || (currentActivitiesTimestamp != null && currentActivitiesTimestamp != savedTimestamp)

            if (shouldFetchDeltas) {
                Timber.tag("SyncRepository").d("Watchlist Sync: Calling /sync/all-items (forceFullSync=$forceFullSync, saved=$savedTimestamp, current=$currentActivitiesTimestamp)")

                val syncResponse = authenticatedSimklApiService.getSyncAllItems(
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
                    Timber.tag("SyncRepository").d("Deleted ${trackedToDelete.size} untracked watchlist items from DB")
                }

                if (trackedToInsert.isNotEmpty()) {
                    watchlistDao.insertItems(trackedToInsert.values.toList())
                    Timber.tag("SyncRepository").d("Inserted ${trackedToInsert.size} new tracked watchlist items into DB")

                    // Initialize default notification settings for newly inserted shows
                    try {
                        val notifPrefs = notificationRepo.preferencesFlow.first()
                        val defaultAiring = notifPrefs.defaultNotifyAiring
                        val defaultSeasonFinished = notifPrefs.defaultNotifySeasonFinished
                        val defaultMovieTheater = notifPrefs.defaultNotifyMovieTheater
                        val defaultMovieDigital = notifPrefs.defaultNotifyMovieDigital

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
                        Timber.tag("SyncRepository").e(e, "Error initializing default notification settings")
                    }
                }
                if (trackedToUpdate.isNotEmpty()) {
                    watchlistDao.updateItems(trackedToUpdate.values.toList())
                    Timber.tag("SyncRepository").d("Updated ${trackedToUpdate.size} changed tracked watchlist items in DB")
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
                        Timber.tag("SyncRepository").d("Removed ${allWatchedToRemove.size} WatchedEpisode entities not present in API response")
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
                    syncMetadataRepo.setLastActivitiesAll(currentActivitiesTimestamp)
                }
                changesDetected = true
            } else {
                Timber.tag("SyncRepository").d("Watchlist Sync: /sync/activities timestamp unchanged ($savedTimestamp), skipping /sync/all-items")
            }
        } catch (e: Exception) {
            Timber.tag("SyncRepository").e(e, "Error during watchlist sync")
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
    internal suspend fun syncCalendarJsons(forceFullSync: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        // Load local tracked items (IDs only for filtering)
        val allTrackedIds = watchlistDao.getAllTrackedIds().toSet()
        if (allTrackedIds.isEmpty()) {
            Timber.tag("SyncRepository").d("No tracked items in watchlist, skipping calendar json sync.")
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
                val lastModifiedPrefKey = "cal_json_last_mod_${year}_${month}_$endpointType"
                val lastModifiedHeaderKey = "cal_json_header_${year}_${month}_$endpointType"

                val syncMetadata = syncMetadataRepo.preferencesFlow.first()
                val lastModifiedTimestamp = if (forceFullSync) 0L else syncMetadata.calendarLastModifiedAt[lastModifiedPrefKey] ?: 0L
                val savedHeader = if (forceFullSync) null else syncMetadata.calendarLastModifiedHeader[lastModifiedHeaderKey]

                // Only sync calendar jsons when their last modified was over 6h in the past
                val isOver6Hours = (nowMillis - lastModifiedTimestamp) >= sixHoursMillis
                if (lastModifiedTimestamp > 0L && !isOver6Hours) {
                    continue
                }

                try {
                    val response = publicSimklApiService.getV2Calendar(
                        year = year,
                        month = month,
                        type = endpointType,
                        ifModifiedSince = savedHeader
                    )

                    if (response.code() == 304) {
                        Timber.tag("SyncRepository").d("Calendar JSON for $year/$month/$endpointType not modified (HTTP 304)")
                        syncMetadataRepo.setCalendarLastModifiedAt(lastModifiedPrefKey, nowMillis)
                        continue
                    }

                    if (!response.isSuccessful) {
                        Timber.tag("SyncRepository").w("HTTP ${response.code()} for calendar JSON $year/$month/$endpointType")
                        continue
                    }

                    val calendarResponse = response.body() ?: continue

                    // Track Last-Modified header from response
                    val responseLastModifiedHeader = response.headers()["Last-Modified"]
                    val parsedHeaderMillis = DateUtil.parseHttpDateToMillis(responseLastModifiedHeader) ?: nowMillis
                    syncMetadataRepo.setCalendarLastModifiedAt(lastModifiedPrefKey, parsedHeaderMillis)
                    syncMetadataRepo.setCalendarLastModifiedHeader(lastModifiedHeaderKey, responseLastModifiedHeader ?: "")

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
                                val status = mediaStatusResolver.resolve(
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
                                    val status = mediaStatusResolver.resolve(
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

                            val status = mediaStatusResolver.resolve(
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
                    Timber.tag("SyncRepository").e(e, "Failed fetching CDN v2 calendar for $year/$month/$endpointType")
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
            Timber.tag("SyncRepository").d("Fetching details for ${moviesNeedingDetails.size} movies missing release dates")
            for (movieId in moviesNeedingDetails) {
                try {
                    val movieDetail = publicSimklApiService.getMovieDetails(
                        movieId = movieId
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
                            val status = mediaStatusResolver.resolve(
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
                            val status = mediaStatusResolver.resolve(
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
                    Timber.tag("SyncRepository").e(e, "Failed fetching movie details for movieId $movieId")
                }
            }
        }

        if (trackedToUpdate.isNotEmpty()) {
            watchlistDao.updateItems(trackedToUpdate.values.toList())
            Timber.tag("SyncRepository").d("Updated ${trackedToUpdate.size} changed tracked watchlist items with metadata in DB")
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
            Timber.tag("SyncRepository").d("Calendar sync complete: no changes detected, skipped DB writes")
        } else {
            Timber.tag("SyncRepository").d("Calendar sync complete: applied $totalCalendarItemDbChanges DB mutations (${itemsToInsert.size} inserted, ${itemsToUpdate.size} updated)")
        }

        syncMetadataRepo.setLastCalendarJsonSync(nowMillis)

        SyncResult(
            hasCalendarItemChanges = totalCalendarItemDbChanges > 0,
            hasWantedItems = localStatesToInsert.any { it.mediaStatus == MediaStatus.WANTED },
        )
    }

    /**
     * Fetches all episodes for tracked TV shows and Anime to backfill past episodes
     * that are missing from the CDN calendar V2 JSONs (which only cover 4 months).
     *
     * @param lastSyncTimestamp The global JSON calendar sync timestamp from BEFORE the current sync run.
     */
    internal suspend fun backfillPastEpisodes(
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
            Timber.tag("SyncRepository").d("Backfill skipped: same month or < 1 day since last sync")
            return@withContext SyncResult()
        }

        Timber.tag("SyncRepository").d("Starting backfill for past episodes...")

        val trackedShows = watchlistDao.getTrackedItemsByTypes(listOf(MediaType.TV, MediaType.ANIME))
        if (trackedShows.isEmpty()) return@withContext SyncResult()

        val trackedIds = trackedShows.map { it.simklId }

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
                            publicSimklApiService.getTvEpisodes(show.simklId)
                        } else {
                            publicSimklApiService.getAnimeEpisodes(show.simklId)
                        }
                        show to episodes
                    } catch (e: Exception) {
                        Timber.tag("SyncRepository").e(e, "Failed backfill for ${show.simklId}")
                        show to null
                    }
                }
            }

            val results = deferred.awaitAll()
            val oneMonthAgo = Instant.now().minus(30, ChronoUnit.DAYS)

            for ((show, episodes) in results) {
                if (episodes == null) continue

                val maxEpPerSeason = episodes
                    .filter { it.type == "episode" && it.episode != null }
                    .groupBy { it.season ?: 1 }
                    .mapValues { (_, seasonEpisodes) -> seasonEpisodes.maxOf { it.episode!! } }

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

                    val status = mediaStatusResolver.resolve(
                        airDate = instant,
                        settings = settingsMap[show.simklId],
                        mediaType = show.type,
                        isTheaterRelease = false,
                        isWatched = epWatchedTimestamp != null,
                    )

                    val maxEp = maxEpPerSeason[seasonNum]
                    val isFinale = maxEp != null && epNum == maxEp

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
                            isSeasonFinale = isFinale,
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

        Timber.tag("SyncRepository").d("Backfill complete: applied ${itemsToInsert.size + itemsToUpdate.size} DB mutations (${itemsToInsert.size} inserted, ${itemsToUpdate.size} updated)")
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
    private suspend fun cleanupOldWatchedCalendarItems(cutoffDays: Long = 30): Int = withContext(Dispatchers.IO) {
        try {
            val cutoff = Instant.now().minus(cutoffDays, ChronoUnit.DAYS)
            val deletedCount = calendarDao.deleteWatchedItemsOlderThan(cutoff)
            if (deletedCount > 0) {
                Timber.tag("SyncRepository").d("Cleaned up $deletedCount old watched calendar items (watched over $cutoffDays days ago)")
            }
            deletedCount
        } catch (e: Exception) {
            Timber.tag("SyncRepository").e(e, "Error cleaning up old watched calendar items")
            0
        }
    }
}

private fun TrackedWatchlistItem.Companion.fromShowItem(item: SyncShowItem, type: MediaType): TrackedWatchlistItem {
    val media = item.show

    return TrackedWatchlistItem(
        simklId = media.ids.simkl,
        type = type,
        title = media.title,
        poster = media.poster
    )
}

private fun TrackedWatchlistItem.Companion.fromMovieItem(item: SyncMovieItem): TrackedWatchlistItem {
    val media = item.movie

    return TrackedWatchlistItem(
        simklId = media.ids.simkl,
        type = MediaType.MOVIE,
        title = media.title,
        poster = media.poster
    )
}
