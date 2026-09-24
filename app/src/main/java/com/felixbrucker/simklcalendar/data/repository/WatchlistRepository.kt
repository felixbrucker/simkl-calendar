package com.felixbrucker.simklcalendar.data.repository

import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistRepository @Inject constructor(
    private val watchlistDao: WatchlistDao
) {
    val watchlistItems: Flow<List<TrackedWatchlistItem>> = watchlistDao.getAllTrackedItemsFlow()
}
