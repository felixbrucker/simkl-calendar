package com.felixbrucker.simklcalendar.data.repository

import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.util.MediaStatusResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRepository @Inject constructor(
    private val calendarDao: CalendarItemDao,
    watchlistDao: WatchlistDao,
    private val mediaStatusResolver: MediaStatusResolver,
) {
    val calendarItems: Flow<List<CalendarItemWithWatchlist>> = calendarDao.getAllCalendarItems()
    val watchlistItems: Flow<List<TrackedWatchlistItem>> = watchlistDao.getAllTrackedItemsFlow()

    suspend fun updateMediaStatus(primaryKey: String, status: MediaStatus) = withContext(Dispatchers.IO) {
        calendarDao.updateMediaStatus(primaryKey, status)
    }

    suspend fun updateSeasonMediaStatus(simklId: Int, season: Int, status: MediaStatus) = withContext(Dispatchers.IO) {
        calendarDao.updateSeasonMediaStatus(simklId, season, status, Instant.now())
    }

    suspend fun updateDownloadTaskId(primaryKey: String, taskId: String?, status: MediaStatus) = withContext(Dispatchers.IO) {
        calendarDao.updateDownloadTaskId(primaryKey, taskId, status)
    }

    suspend fun updateItemAiredStatus(item: CalendarItemWithWatchlist) = withContext(Dispatchers.IO) {
        val calendarItem = item.calendarItem
        if (item.mediaStatus != MediaStatus.NOT_AIRED_YET) return@withContext

        val newStatus = mediaStatusResolver.resolve(item)
        updateMediaStatus(calendarItem.primaryKey, newStatus)
    }
}
