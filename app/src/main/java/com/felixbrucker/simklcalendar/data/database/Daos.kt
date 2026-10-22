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
    @Query("""
        SELECT
            ci.simklId AS simklId,
            CAST(SUM(CASE WHEN ci.watchedAt IS NULL THEN 1 ELSE 0 END) AS INTEGER) AS unwatchedCount,
            CAST(SUM(CASE WHEN ci.watchedAt IS NULL AND ci.date <= (strftime('%s', 'now') * 1000) THEN 1 ELSE 0 END) AS INTEGER) AS unwatchedReleasedCount,
            MIN(CASE WHEN ci.date > (strftime('%s', 'now') * 1000) THEN ci.date ELSE NULL END) AS nextEpisodeDate,
            MAX(CASE WHEN ci.date <= (strftime('%s', 'now') * 1000) THEN ci.date ELSE NULL END) AS lastAiredDate,
            CAST(COALESCE(SUM(CASE WHEN ci.date <= (strftime('%s', 'now') * 1000) AND ci.watchedAt IS NOT NULL THEN 1 ELSE 0 END), 0) AS INTEGER) AS watchedReleasedCount,
            CAST(COALESCE(SUM(CASE WHEN ci.date <= (strftime('%s', 'now') * 1000) THEN 1 ELSE 0 END), 0) AS INTEGER) AS totalReleasedCount,
            CAST(COALESCE(SUM(CASE WHEN ci.date <= (strftime('%s', 'now') * 1000) AND lis.mediaStatus = 'DOWNLOADED' THEN 1 ELSE 0 END), 0) AS INTEGER) AS downloadedReleasedCount,
            CAST(COALESCE(SUM(CASE WHEN ci.date <= (strftime('%s', 'now') * 1000) AND (lis.mediaStatus = 'WANTED' OR lis.mediaStatus = 'DOWNLOADING' OR lis.mediaStatus = 'DOWNLOADED') THEN 1 ELSE 0 END), 0) AS INTEGER) AS totalDownloadableReleasedCount
        FROM calendar_items ci
        LEFT JOIN local_item_state lis ON ci.primaryKey = lis.primaryKey
        GROUP BY ci.simklId
    """)
    fun getSimklCalendarStats(): Flow<List<SimklCalendarStats>>

    @Transaction
    @Query("""
        SELECT ci.* FROM calendar_items ci
        INNER JOIN tracked_watchlist_items twi ON ci.simklId = twi.simklId
        WHERE ci.watchedAt IS NULL
        AND twi.type IN (:allowedTypes)
        AND (
            (:hasSubtypeFilter = 0) OR
            (:premieres = 1 AND ci.isSeasonPremiere = 1) OR
            (:finales = 1 AND ci.isSeasonFinale = 1) OR
            (:digitalDvd = 1 AND twi.type = 'movie' AND ci.movieReleaseType = 'DIGITAL')
        )
        AND (
            :query = '' OR
            twi.title LIKE '%' || :query || '%' OR
            (twi.titleRomaji IS NOT NULL AND twi.titleRomaji LIKE '%' || :query || '%') OR
            (ci.episodeTitle IS NOT NULL AND ci.episodeTitle LIKE '%' || :query || '%')
        )
        ORDER BY ci.date ASC, twi.title ASC
    """)
    fun getFilteredCalendarItems(
        allowedTypes: List<String>,
        hasSubtypeFilter: Boolean,
        premieres: Boolean,
        finales: Boolean,
        digitalDvd: Boolean,
        query: String
    ): Flow<List<CalendarItemWithWatchlist>>

    @Transaction
    @Query("""
        SELECT ci.* FROM calendar_items ci
        INNER JOIN tracked_watchlist_items twi ON ci.simklId = twi.simklId
        INNER JOIN local_item_state lis ON ci.primaryKey = lis.primaryKey
        WHERE lis.mediaStatus = 'DOWNLOADING'
        ORDER BY ci.date ASC, twi.title ASC
    """)
    fun getDownloadingCalendarItemsFlow(): Flow<List<CalendarItemWithWatchlist>>

    @Transaction
    @Query("""
        SELECT ci.* FROM calendar_items ci
        INNER JOIN tracked_watchlist_items twi ON ci.simklId = twi.simklId
        INNER JOIN local_item_state lis ON ci.primaryKey = lis.primaryKey
        WHERE lis.mediaStatus = 'WANTED'
        ORDER BY ci.date ASC, twi.title ASC
    """)
    fun getWantedCalendarItems(): Flow<List<CalendarItemWithWatchlist>>

    @Transaction
    @Query("""
        SELECT ci.* FROM calendar_items ci 
        LEFT JOIN local_item_state lis ON ci.primaryKey = lis.primaryKey 
        WHERE ci.date > :now 
        OR (ci.date <= :now AND lis.mediaStatus = 'NOT_AIRED_YET')
        ORDER BY ci.date ASC
        """)
    suspend fun getCalendarItemsForAiredAlarm(now: Instant): List<CalendarItemWithWatchlist>

    @Transaction
    @Query("SELECT * FROM calendar_items WHERE simklId = :simklId ORDER BY date ASC")
    suspend fun getItemsForSimklId(simklId: Int): List<CalendarItemWithWatchlist>

    @Transaction
    @Query("SELECT * FROM calendar_items WHERE simklId = :simklId ORDER BY season ASC, episodeNumber ASC, date ASC")
    fun getSortedItemsForSimklIdFlow(simklId: Int): Flow<List<CalendarItemWithWatchlist>>

    @Transaction
    @Query("SELECT * FROM calendar_items WHERE simklId = :simklId ORDER BY date ASC LIMIT 1")
    suspend fun findFirstItemForSimklId(simklId: Int): CalendarItemWithWatchlist?

    @Transaction
    @Query("SELECT * FROM calendar_items WHERE simklId = :simklId AND season = :season ORDER BY date DESC LIMIT 1")
    suspend fun findLastItemForSimklIdInSeason(simklId: Int, season: Int): CalendarItemWithWatchlist?

    @Query("SELECT * FROM calendar_items WHERE simklId IN (:simklIds) ORDER BY date ASC")
    suspend fun getCalendarEntitiesForSimklIds(simklIds: List<Int>): List<CalendarItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarItems(items: List<CalendarItem>)

    @Update
    suspend fun updateCalendarItems(items: List<CalendarItem>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLocalItemStates(states: List<LocalItemState>)

    @Query("DELETE FROM calendar_items WHERE primaryKey = :primaryKey")
    suspend fun deleteCalendarItemByPrimaryKey(primaryKey: String)

    @Transaction
    @Query("SELECT * FROM calendar_items WHERE primaryKey = :primaryKey LIMIT 1")
    suspend fun findItem(primaryKey: String): CalendarItemWithWatchlist?

    @Transaction
    @Query("SELECT * FROM calendar_items WHERE primaryKey = :primaryKey LIMIT 1")
    fun getItemFlow(primaryKey: String): Flow<CalendarItemWithWatchlist?>

    @Query("UPDATE calendar_items SET isNotified = 1 WHERE primaryKey = :primaryKey")
    suspend fun markItemAsNotified(primaryKey: String)

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
    suspend fun ensureLocalStatesExistForSeasonReleased(simklId: Int, season: Int, now: Instant)

    @Query("""
        INSERT OR IGNORE INTO local_item_state (primaryKey)
        SELECT primaryKey FROM calendar_items
        WHERE simklId = :simklId AND season = :season
    """)
    suspend fun ensureLocalStatesExistForSeason(simklId: Int, season: Int)

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
        ensureLocalStatesExistForSeasonReleased(simklId, season, now)
        performUpdateSeasonMediaStatus(simklId, season, status, now)
    }

    @Query("UPDATE local_item_state SET mediaStatus = :status, downloadTaskId = :taskId WHERE primaryKey = :primaryKey")
    suspend fun performUpdateDownloadTaskId(primaryKey: String, taskId: String?, status: MediaStatus)

    @Transaction
    suspend fun updateDownloadTaskId(primaryKey: String, taskId: String?, status: MediaStatus) {
        ensureLocalStateExists(primaryKey)
        performUpdateDownloadTaskId(primaryKey, taskId, status)
    }

    @Query("""
        UPDATE local_item_state
        SET mediaStatus = :status, downloadTaskId = :taskId
        WHERE primaryKey IN (
            SELECT primaryKey FROM calendar_items 
            WHERE simklId = :simklId AND season = :season
        )
    """)
    suspend fun performUpdateSeasonDownloadTaskId(simklId: Int, season: Int, taskId: String?, status: MediaStatus)

    @Transaction
    suspend fun updateSeasonDownloadTaskId(simklId: Int, season: Int, taskId: String?, status: MediaStatus) {
        ensureLocalStatesExistForSeason(simklId, season)
        performUpdateSeasonDownloadTaskId(simklId, season, taskId, status)
    }

    @Query("""
        UPDATE local_item_state 
        SET mediaStatus = :status, downloadTaskId = :taskId, filePath = :filePath 
        WHERE primaryKey = :primaryKey
    """)
    suspend fun performUpdateDownloadCompleted(
        primaryKey: String,
        taskId: String?,
        status: MediaStatus,
        filePath: String?
    )

    @Transaction
    suspend fun updateDownloadCompleted(
        primaryKey: String,
        taskId: String?,
        status: MediaStatus,
        filePath: String?
    ) {
        ensureLocalStateExists(primaryKey)
        performUpdateDownloadCompleted(primaryKey, taskId, status, filePath)
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

    @Transaction
    @Query("""
        SELECT ci.* FROM calendar_items ci
        LEFT JOIN local_item_state lis ON ci.primaryKey = lis.primaryKey
        WHERE ci.simklId = :simklId
          AND ci.season = :season
          AND ci.watchedAt IS NULL
          AND (lis.mediaStatus IS NULL OR lis.mediaStatus NOT IN ('DOWNLOADED', 'DOWNLOADING'))
        ORDER BY ci.date ASC
    """)
    suspend fun getUnwatchedDownloadableSeasonItems(simklId: Int, season: Int): List<CalendarItemWithWatchlist>

    @Transaction
    @Query("""
        SELECT ci.* FROM calendar_items ci
        WHERE ci.simklId = :simklId
          AND ci.season = :season
        ORDER BY ci.date ASC
    """)
    suspend fun getSeasonItems(simklId: Int, season: Int): List<CalendarItemWithWatchlist>
}

@Dao
interface NotificationSettingDao {
    @Query("SELECT * FROM notification_settings")
    fun getAllSettings(): Flow<List<NotificationSetting>>

    @Query("SELECT * FROM notification_settings WHERE simklId = :simklId LIMIT 1")
    suspend fun getSettingForSimklId(simklId: Int): NotificationSetting?

    @Query("SELECT * FROM notification_settings WHERE simklId = :simklId LIMIT 1")
    fun getSettingForSimklIdFlow(simklId: Int): Flow<NotificationSetting?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: NotificationSetting)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSettings(settings: List<NotificationSetting>)
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM tracked_watchlist_items")
    fun getAllTrackedItemsFlow(): Flow<List<TrackedWatchlistItem>>

    @Query("SELECT simklId FROM tracked_watchlist_items")
    suspend fun getAllTrackedIds(): List<Int>

    @Query("SELECT simklId FROM tracked_watchlist_items WHERE type IN (:types)")
    suspend fun getTrackedIdsByTypes(types: List<MediaType>): List<Int>

    @Query("SELECT * FROM tracked_watchlist_items WHERE simklId = :simklId LIMIT 1")
    suspend fun getItem(simklId: Int): TrackedWatchlistItem?

    @Query("SELECT * FROM tracked_watchlist_items WHERE simklId = :simklId LIMIT 1")
    fun getItemFlow(simklId: Int): Flow<TrackedWatchlistItem?>

    @Query("SELECT * FROM tracked_watchlist_items WHERE simklId IN (:simklIds)")
    suspend fun getTrackedItemsBySimklIds(simklIds: List<Int>): List<TrackedWatchlistItem>

    @Query("SELECT * FROM tracked_watchlist_items WHERE type IN (:types)")
    suspend fun getTrackedItemsByTypes(types: List<MediaType>): List<TrackedWatchlistItem>

    @Query("SELECT simklId FROM tracked_watchlist_items WHERE type = :type AND (lastSyncedAt IS NULL OR lastSyncedAt < :cutoff)")
    suspend fun getItemIdsNeedingSync(type: MediaType, cutoff: Instant): List<Int>

    @Query("UPDATE tracked_watchlist_items SET lastSyncedAt = :lastSyncedAt WHERE simklId IN (:simklIds)")
    suspend fun updateLastSyncedAt(simklIds: List<Int>, lastSyncedAt: Instant)

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

    @Query("SELECT * FROM watched_episodes WHERE simklId = :simklId")
    fun getWatchedEpisodesForSimklIdFlow(simklId: Int): Flow<List<WatchedEpisode>>

    @Query("SELECT * FROM watched_episodes WHERE simklId = :simklId")
    suspend fun getWatchedEpisodesForSimklId(simklId: Int): List<WatchedEpisode>

    @Query("SELECT * FROM watched_episodes WHERE simklId IN (:simklIds)")
    suspend fun getWatchedEpisodesForSimklIds(simklIds: List<Int>): List<WatchedEpisode>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchedEpisodes(episodes: List<WatchedEpisode>)

    @Delete
    suspend fun deleteWatchedEpisodes(episodes: List<WatchedEpisode>)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchLink(link: CustomSearchLink): Long

    @Update
    suspend fun updateSearchLink(link: CustomSearchLink)

    @Update
    suspend fun updateSearchLinks(links: List<CustomSearchLink>)

    @Delete
    suspend fun deleteSearchLink(link: CustomSearchLink)

    @Query("DELETE FROM custom_search_links")
    suspend fun clearAll()
}

@Dao
interface ActiveNotificationDao {
    @Query("SELECT primaryKey FROM active_notifications")
    suspend fun getAllActiveKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActiveNotification(notification: ActiveNotification)

    @Query("DELETE FROM active_notifications WHERE primaryKey = :primaryKey")
    suspend fun deleteActiveNotification(primaryKey: String)
}

@Dao
interface ItemDownloadSettingsDao {
    @Query("SELECT * FROM item_download_settings WHERE simklId = :simklId LIMIT 1")
    suspend fun getSettings(simklId: Int): ItemDownloadSettings?

    @Query("SELECT * FROM item_download_settings WHERE simklId IN (:simklIds)")
    suspend fun getSettingsBySimklIds(simklIds: List<Int>): List<ItemDownloadSettings>

    @Query("SELECT * FROM item_download_settings WHERE simklId = :simklId LIMIT 1")
    fun getSettingsFlow(simklId: Int): Flow<ItemDownloadSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: ItemDownloadSettings)
}



