package com.felixbrucker.simklcalendar.data.repository

import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.SimklCalendarStats
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.util.MediaStatusResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class CalendarFilterParams(
    val showTv: Boolean,
    val showAnime: Boolean,
    val showMovies: Boolean,
    val filterOnlyPremiers: Boolean,
    val filterOnlyFinales: Boolean,
    val filterOnlyDigitalDvd: Boolean,
    val query: String
) {
    val hasSubtypeFilter: Boolean = filterOnlyPremiers || filterOnlyFinales || filterOnlyDigitalDvd
    val allowedTypes: List<String> = buildList {
        if (showTv) add(MediaType.TV.key)
        if (showAnime) add(MediaType.ANIME.key)
        if (showMovies) add(MediaType.MOVIE.key)
        if (isEmpty()) {
            add("__NONE__")
        }
    }
}

@Singleton
class CalendarRepository @Inject constructor(
    private val calendarDao: CalendarItemDao,
    private val watchlistDao: WatchlistDao,
    private val mediaStatusResolver: MediaStatusResolver,
) {
    val simklCalendarStats: Flow<List<SimklCalendarStats>> = calendarDao.getSimklCalendarStats()
    val wantedCalendarItems: Flow<List<CalendarItemWithWatchlist>> = calendarDao.getWantedCalendarItems()
    val downloadingCalendarItems: Flow<List<CalendarItemWithWatchlist>> = calendarDao.getDownloadingCalendarItemsFlow()
    val watchlistItems: Flow<List<TrackedWatchlistItem>> = watchlistDao.getAllTrackedItemsFlow()

    suspend fun findCalendarItem(
        primaryKey: String
    ): CalendarItemWithWatchlist? = withContext(Dispatchers.IO) {
        calendarDao.findItem(primaryKey)
    }

    fun getCalendarItemFlow(primaryKey: String): Flow<CalendarItemWithWatchlist?> =
        calendarDao.getItemFlow(primaryKey)

    fun getSortedCalendarItemsForSimklIdFlow(simklId: Int): Flow<List<CalendarItemWithWatchlist>> =
        calendarDao.getSortedItemsForSimklIdFlow(simklId)

    suspend fun getItemsInSeasonOrRelatedItems(
        simklId: Int,
        season: Int?
    ): List<CalendarItemWithWatchlist> = withContext(Dispatchers.IO) {
        calendarDao.getItemsInSeasonOrRelatedItems(simklId, season)
    }

    fun getWatchlistItemFlow(simklId: Int): Flow<TrackedWatchlistItem?> = watchlistDao.getItemFlow(simklId)

    suspend fun updateMediaStatus(primaryKey: String, status: MediaStatus) = withContext(Dispatchers.IO) {
        calendarDao.updateMediaStatus(primaryKey, status)
    }

    suspend fun updateSeasonMediaStatus(simklId: Int, season: Int, status: MediaStatus) = withContext(Dispatchers.IO) {
        calendarDao.updateSeasonMediaStatus(simklId, season, status, Instant.now())
    }

    suspend fun updateDownloadTaskId(primaryKey: String, taskId: String?, status: MediaStatus) = withContext(Dispatchers.IO) {
        calendarDao.updateDownloadTaskId(primaryKey, taskId, status)
    }

    suspend fun updateDownloadCompleted(primaryKey: String, taskId: String?, status: MediaStatus, filePath: String?) = withContext(Dispatchers.IO) {
        calendarDao.updateDownloadCompleted(primaryKey, taskId, status, filePath)
    }

    suspend fun updateItemAiredStatus(item: CalendarItemWithWatchlist) = withContext(Dispatchers.IO) {
        val calendarItem = item.calendarItem
        if (item.mediaStatus != MediaStatus.NOT_AIRED_YET) return@withContext

        val newStatus = mediaStatusResolver.resolve(item)
        updateMediaStatus(calendarItem.primaryKey, newStatus)
    }

    suspend fun deleteCalendarItem(primaryKey: String) = withContext(Dispatchers.IO) {
        calendarDao.deleteCalendarItemByPrimaryKey(primaryKey)
    }

    fun getFilteredCalendarItems(params: CalendarFilterParams): Flow<List<CalendarItemWithWatchlist>> {
        return calendarDao
            .getFilteredCalendarItems(
                allowedTypes = params.allowedTypes,
                hasSubtypeFilter = params.hasSubtypeFilter,
                premieres = params.filterOnlyPremiers,
                finales = params.filterOnlyFinales,
                digitalDvd = params.filterOnlyDigitalDvd,
                query = params.query
            )
            .flowOn(Dispatchers.IO)
    }
}
