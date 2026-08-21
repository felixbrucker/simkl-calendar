package com.example.data.database

import androidx.room.*
import com.example.data.model.MediaType
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

    @Query("SELECT * FROM calendar_items WHERE simklId = :simklId ORDER BY date ASC")
    suspend fun getItemsForShow(simklId: Int): List<CalendarItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarItems(items: List<CalendarItem>)

    @Update
    suspend fun updateCalendarItems(items: List<CalendarItem>)

    @Delete
    suspend fun deleteCalendarItems(items: List<CalendarItem>)

    @Query("DELETE FROM calendar_items")
    suspend fun clearCalendarItems()

    @Query("SELECT * FROM calendar_items WHERE primaryKey = :primaryKey LIMIT 1")
    suspend fun findItem(primaryKey: String): CalendarItem?

    @Query("UPDATE calendar_items SET isNotified = 1 WHERE primaryKey = :primaryKey")
    suspend fun markItemAsNotified(primaryKey: String)

    @Query("UPDATE calendar_items SET isNotified = 0 WHERE simklId = :simklId")
    suspend fun resetNotifiedForShow(simklId: Int)

    @Query("UPDATE calendar_items SET isNotified = 0")
    suspend fun resetAllNotified()
}

@Dao
interface NotificationSettingDao {
    @Query("SELECT * FROM notification_settings")
    fun getAllSettings(): Flow<List<NotificationSetting>>

    @Query("SELECT * FROM notification_settings")
    suspend fun getAllSettingsList(): List<NotificationSetting>

    @Query("SELECT * FROM notification_settings WHERE simklId = :simklId LIMIT 1")
    suspend fun getSettingForShow(simklId: Int): NotificationSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: NotificationSetting)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSettings(settings: List<NotificationSetting>)

    @Query("DELETE FROM notification_settings WHERE simklId = :simklId")
    suspend fun deleteSetting(simklId: Int)
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM tracked_watchlist_items")
    suspend fun getAllTrackedItems(): List<TrackedWatchlistItem>

    @Query("SELECT * FROM tracked_watchlist_items WHERE type = :type")
    suspend fun getTrackedItemsByType(type: MediaType): List<TrackedWatchlistItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateItems(items: List<TrackedWatchlistItem>)

    @Query("DELETE FROM tracked_watchlist_items WHERE simklId = :simklId")
    suspend fun deleteItem(simklId: Int)

    @Query("DELETE FROM tracked_watchlist_items")
    suspend fun clearAll()
}

@Dao
interface WatchedEpisodeDao {
    @Query("SELECT * FROM watched_episodes")
    suspend fun getAllWatchedEpisodes(): List<WatchedEpisode>

    @Query("SELECT * FROM watched_episodes WHERE simklId = :simklId")
    suspend fun getWatchedEpisodesForShow(simklId: Int): List<WatchedEpisode>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchedEpisodes(episodes: List<WatchedEpisode>)

    @Delete
    suspend fun deleteWatchedEpisodes(episodes: List<WatchedEpisode>)

    @Query("DELETE FROM watched_episodes WHERE simklId = :simklId")
    suspend fun deleteWatchedForShow(simklId: Int)

    @Query("DELETE FROM watched_episodes")
    suspend fun clearAll()
}

