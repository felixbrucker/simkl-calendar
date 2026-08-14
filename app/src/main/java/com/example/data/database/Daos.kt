package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserTokenDao {
    @Query("SELECT * FROM user_token WHERE id = 1 LIMIT 1")
    fun getUserToken(): Flow<UserToken?>

    @Query("SELECT * FROM user_token WHERE id = 1 LIMIT 1")
    suspend fun getActiveToken(): UserToken?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserToken(token: UserToken)

    @Query("DELETE FROM user_token WHERE id = 1")
    suspend fun clearUserToken()
}

@Dao
interface CalendarItemDao {
    @Query("SELECT * FROM calendar_items ORDER BY date ASC")
    fun getAllCalendarItems(): Flow<List<CalendarItem>>

    @Query("SELECT * FROM calendar_items ORDER BY date ASC")
    suspend fun getAllCalendarItemsList(): List<CalendarItem>

    @Query("SELECT * FROM calendar_items WHERE id = :showId ORDER BY date ASC")
    suspend fun getItemsForShow(showId: Int): List<CalendarItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarItems(items: List<CalendarItem>)

    @Query("DELETE FROM calendar_items")
    suspend fun clearCalendarItems()

    @Query("SELECT * FROM calendar_items WHERE primaryKey = :primaryKey LIMIT 1")
    suspend fun findItem(primaryKey: String): CalendarItem?

    @Query("UPDATE calendar_items SET isNotified = 1 WHERE primaryKey = :primaryKey")
    suspend fun markItemAsNotified(primaryKey: String)
}

@Dao
interface NotificationSettingDao {
    @Query("SELECT * FROM notification_settings")
    fun getAllSettings(): Flow<List<NotificationSetting>>

    @Query("SELECT * FROM notification_settings")
    suspend fun getAllSettingsList(): List<NotificationSetting>

    @Query("SELECT * FROM notification_settings WHERE showId = :showId LIMIT 1")
    suspend fun getSettingForShow(showId: Int): NotificationSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: NotificationSetting)

    @Query("DELETE FROM notification_settings WHERE showId = :showId")
    suspend fun deleteSetting(showId: Int)
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM tracked_watchlist_items")
    suspend fun getAllTrackedItems(): List<TrackedWatchlistItem>

    @Query("SELECT * FROM tracked_watchlist_items WHERE type = :type")
    suspend fun getTrackedItemsByType(type: String): List<TrackedWatchlistItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateItems(items: List<TrackedWatchlistItem>)

    @Query("DELETE FROM tracked_watchlist_items WHERE id = :id")
    suspend fun deleteItem(id: Int)

    @Query("DELETE FROM tracked_watchlist_items")
    suspend fun clearAll()
}

