package com.felixbrucker.simklcalendar.data.database

import androidx.room.*
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

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
    @Transaction
    @Query("SELECT * FROM calendar_items ORDER BY date ASC")
    fun getAllCalendarItems(): Flow<List<CalendarItemWithWatchlist>>

    @Query("SELECT * FROM calendar_items")
    fun getAllCalendarEntitiesFlow(): Flow<List<CalendarItem>>

    @Transaction
    @Query("SELECT * FROM calendar_items ORDER BY date ASC")
    suspend fun getAllCalendarItemsList(): List<CalendarItemWithWatchlist>

    @Transaction
    @Query("SELECT * FROM calendar_items WHERE date > :now ORDER BY date ASC")
    suspend fun getAllUpcomingCalendarItems(now: Instant): List<CalendarItemWithWatchlist>

    @Query("SELECT * FROM calendar_items ORDER BY date ASC")
    suspend fun getAllCalendarEntities(): List<CalendarItem>

    @Transaction
    @Query("SELECT * FROM calendar_items WHERE simklId = :simklId ORDER BY date ASC")
    suspend fun getItemsForSimklId(simklId: Int): List<CalendarItemWithWatchlist>

    @Query("SELECT * FROM calendar_items WHERE simklId IN (:simklIds) ORDER BY date ASC")
    suspend fun getCalendarEntitiesForSimklIds(simklIds: List<Int>): List<CalendarItem>

    @Query("SELECT * FROM calendar_items WHERE simklId = :simklId ORDER BY date ASC")
    suspend fun getCalendarEntitiesForSimklId(simklId: Int): List<CalendarItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarItems(items: List<CalendarItem>)

    @Update
    suspend fun updateCalendarItems(items: List<CalendarItem>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLocalItemStates(states: List<LocalItemState>)

    @Delete
    suspend fun deleteCalendarItems(items: List<CalendarItem>)

    @Query("DELETE FROM calendar_items")
    suspend fun clearCalendarItems()

    @Transaction
    @Query("SELECT * FROM calendar_items WHERE primaryKey = :primaryKey LIMIT 1")
    suspend fun findItem(primaryKey: String): CalendarItemWithWatchlist?

    @Query("SELECT * FROM calendar_items WHERE primaryKey = :primaryKey LIMIT 1")
    suspend fun findCalendarEntity(primaryKey: String): CalendarItem?

    @Query("UPDATE calendar_items SET isNotified = 1 WHERE primaryKey = :primaryKey")
    suspend fun markItemAsNotified(primaryKey: String)

    @Query("UPDATE calendar_items SET isNotified = 0 WHERE simklId = :simklId")
    suspend fun resetNotifiedForShow(simklId: Int)

    @Query("UPDATE calendar_items SET isNotified = 0")
    suspend fun resetAllNotified()

    @Query("INSERT OR IGNORE INTO local_item_state (primaryKey) VALUES (:primaryKey)")
    suspend fun ensureLocalStateExists(primaryKey: String)

    @Query("UPDATE local_item_state SET mediaStatus = :status WHERE primaryKey = :primaryKey")
    suspend fun performUpdateMediaStatus(primaryKey: String, status: MediaStatus)

    @Transaction
    suspend fun updateMediaStatus(primaryKey: String, status: MediaStatus) {
        ensureLocalStateExists(primaryKey)
        performUpdateMediaStatus(primaryKey, status)
    }

    @Query("""
        INSERT OR IGNORE INTO local_item_state (primaryKey)
        SELECT primaryKey FROM calendar_items
        WHERE simklId = :simklId AND season = :season AND date <= :now
    """)
    suspend fun ensureLocalStatesExistForSeason(simklId: Int, season: Int, now: Instant)

    @Query("""
        UPDATE local_item_state 
        SET mediaStatus = :status 
        WHERE primaryKey IN (
            SELECT primaryKey FROM calendar_items 
            WHERE simklId = :simklId AND season = :season AND date <= :now
        )
    """)
    suspend fun performUpdateSeasonMediaStatus(simklId: Int, season: Int, status: MediaStatus, now: Instant)

    @Transaction
    suspend fun updateSeasonMediaStatus(simklId: Int, season: Int, status: MediaStatus, now: Instant) {
        ensureLocalStatesExistForSeason(simklId, season, now)
        performUpdateSeasonMediaStatus(simklId, season, status, now)
    }

    @Query("UPDATE local_item_state SET mediaStatus = :status, downloadTaskId = :taskId WHERE primaryKey = :primaryKey")
    suspend fun performUpdateDownloadTaskId(primaryKey: String, taskId: String?, status: MediaStatus)

    @Transaction
    suspend fun updateDownloadTaskId(primaryKey: String, taskId: String?, status: MediaStatus) {
        ensureLocalStateExists(primaryKey)
        performUpdateDownloadTaskId(primaryKey, taskId, status)
    }

    @Query("UPDATE calendar_items SET watchedAt = :watchedAt WHERE simklId = :simklId AND season = :season AND episodeNumber = :episodeNumber")
    suspend fun markEpisodeWatched(simklId: Int, season: Int?, episodeNumber: Int, watchedAt: Instant?)

    @Query("UPDATE calendar_items SET watchedAt = :watchedAt WHERE simklId = :simklId AND season = :season")
    suspend fun markSeasonWatched(simklId: Int, season: Int, watchedAt: Instant?)

    @Query("UPDATE calendar_items SET watchedAt = :watchedAt WHERE simklId = :simklId")
    suspend fun markMovieWatched(simklId: Int, watchedAt: Instant?)

    @Query("DELETE FROM calendar_items WHERE watchedAt IS NOT NULL AND watchedAt < :cutoff")
    suspend fun deleteWatchedItemsOlderThan(cutoff: Instant): Int

    @Query("UPDATE calendar_items SET watchedAt = NULL")
    suspend fun markAllUnwatched()

    @Transaction
    @Query("SELECT * FROM calendar_items WHERE simklId = :simklId AND season = :season AND isSeasonFinale = 1 LIMIT 1")
    suspend fun getSeasonFinaleItem(simklId: Int, season: Int): CalendarItemWithWatchlist?

    @Transaction
    @Query("SELECT * FROM calendar_items WHERE simklId = :simklId AND (season = :season OR season IS NULL) ORDER BY date ASC")
    suspend fun getItemsInSeasonOrRelatedItems(simklId: Int, season: Int?): List<CalendarItemWithWatchlist>
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
    fun getAllTrackedItemsFlow(): Flow<List<TrackedWatchlistItem>>

    @Query("SELECT * FROM tracked_watchlist_items")
    suspend fun getAllTrackedItems(): List<TrackedWatchlistItem>

    @Query("SELECT simklId FROM tracked_watchlist_items")
    suspend fun getAllTrackedIds(): List<Int>

    @Query("SELECT simklId FROM tracked_watchlist_items WHERE type IN (:types)")
    suspend fun getTrackedIdsByTypes(types: List<MediaType>): List<Int>

    @Query("SELECT * FROM tracked_watchlist_items WHERE simklId = :simklId LIMIT 1")
    suspend fun getItem(simklId: Int): TrackedWatchlistItem?

    @Query("SELECT * FROM tracked_watchlist_items WHERE simklId IN (:simklIds)")
    suspend fun getTrackedItemsBySimklIds(simklIds: List<Int>): List<TrackedWatchlistItem>

    @Query("SELECT * FROM tracked_watchlist_items WHERE type IN (:types)")
    suspend fun getTrackedItemsByTypes(types: List<MediaType>): List<TrackedWatchlistItem>

    @Query("SELECT * FROM tracked_watchlist_items WHERE type = :type")
    suspend fun getTrackedItemsByType(type: MediaType): List<TrackedWatchlistItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<TrackedWatchlistItem>)

    @Update
    suspend fun updateItems(items: List<TrackedWatchlistItem>)

    @Query("DELETE FROM tracked_watchlist_items WHERE simklId = :simklId")
    suspend fun deleteItem(simklId: Int)

    @Query("DELETE FROM tracked_watchlist_items")
    suspend fun clearAll()
}

@Dao
interface WatchedEpisodeDao {
    @Query("SELECT * FROM watched_episodes")
    fun getAllWatchedEpisodesFlow(): Flow<List<WatchedEpisode>>

    @Query("SELECT * FROM watched_episodes")
    suspend fun getAllWatchedEpisodes(): List<WatchedEpisode>

    @Query("SELECT * FROM watched_episodes WHERE simklId = :simklId")
    fun getWatchedEpisodesForShowFlow(simklId: Int): Flow<List<WatchedEpisode>>

    @Query("SELECT * FROM watched_episodes WHERE simklId = :simklId")
    suspend fun getWatchedEpisodesForShow(simklId: Int): List<WatchedEpisode>

    @Query("SELECT * FROM watched_episodes WHERE simklId IN (:simklIds)")
    suspend fun getWatchedEpisodesForSimklIds(simklIds: List<Int>): List<WatchedEpisode>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchedEpisodes(episodes: List<WatchedEpisode>)

    @Delete
    suspend fun deleteWatchedEpisodes(episodes: List<WatchedEpisode>)

    @Query("DELETE FROM watched_episodes WHERE simklId = :simklId")
    suspend fun deleteWatchedForShow(simklId: Int)

    @Query("DELETE FROM watched_episodes WHERE simklId = :simklId AND season = :season AND episodeNumber = :episodeNumber")
    suspend fun deleteWatchedEpisode(simklId: Int, season: Int, episodeNumber: Int)

    @Query("DELETE FROM watched_episodes WHERE simklId = :simklId AND season = :season")
    suspend fun deleteWatchedSeason(simklId: Int, season: Int)

    @Query("DELETE FROM watched_episodes")
    suspend fun clearAll()
}

@Dao
interface CustomSearchLinkDao {
    @Query("SELECT * FROM custom_search_links ORDER BY position ASC, id ASC")
    fun getAllSearchLinks(): Flow<List<CustomSearchLink>>

    @Query("SELECT * FROM custom_search_links ORDER BY position ASC, id ASC")
    suspend fun getAllSearchLinksList(): List<CustomSearchLink>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchLink(link: CustomSearchLink): Long

    @Update
    suspend fun updateSearchLink(link: CustomSearchLink)

    @Update
    suspend fun updateSearchLinks(links: List<CustomSearchLink>)

    @Delete
    suspend fun deleteSearchLink(link: CustomSearchLink)

    @Query("DELETE FROM custom_search_links WHERE id = :id")
    suspend fun deleteSearchLinkById(id: Long)
}

@Dao
interface ItemDownloadSettingsDao {
    @Query("SELECT * FROM item_download_settings WHERE simklId = :simklId LIMIT 1")
    suspend fun getSettings(simklId: Int): ItemDownloadSettings?

    @Query("SELECT * FROM item_download_settings WHERE simklId IN (:simklIds)")
    suspend fun getSettingsBySimklIds(simklIds: List<Int>): List<ItemDownloadSettings>

    @Query("SELECT * FROM item_download_settings WHERE simklId = :simklId LIMIT 1")
    fun getSettingsFlow(simklId: Int): Flow<ItemDownloadSettings?>

    @Query("SELECT * FROM item_download_settings")
    fun getAllSettings(): Flow<List<ItemDownloadSettings>>

    @Query("SELECT * FROM item_download_settings")
    suspend fun getAllSettingsList(): List<ItemDownloadSettings>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: ItemDownloadSettings)

    @Delete
    suspend fun delete(settings: ItemDownloadSettings)
}



