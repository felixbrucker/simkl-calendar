package com.felixbrucker.simklcalendar.data.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RenameColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.felixbrucker.simklcalendar.data.model.EpisodeSearchStyle
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import java.time.Instant

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Instant?): Long? {
        return date?.toEpochMilli()
    }

    @TypeConverter
    fun fromMediaType(type: MediaType?): String? {
        return type?.key
    }

    @TypeConverter
    fun toMediaType(value: String?): MediaType? {
        return value?.let { MediaType.fromKey(it) }
    }

    @TypeConverter
    fun fromMovieReleaseType(releaseType: MovieReleaseType?): String? {
        return releaseType?.name
    }

    @TypeConverter
    fun toMovieReleaseType(value: String?): MovieReleaseType? {
        return value?.let {
            try {
                MovieReleaseType.valueOf(it)
            } catch (_: Exception) {
                null
            }
        }
    }

    @TypeConverter
    fun fromMediaTypeList(types: List<MediaType>?): String? {
        return types?.joinToString(",") { it.key }
    }

    @TypeConverter
    fun toMediaTypeList(value: String?): List<MediaType>? {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",").map { key ->
            MediaType.entries.firstOrNull { it.key.equals(key.trim(), ignoreCase = true) }
                ?: MediaType.fromKey(key.trim())
        }
    }

    @TypeConverter
    fun fromSeasonOverrides(value: Map<Int, Int>?): String? {
        if (value == null) return null
        return value.entries.joinToString(",") { "${it.key}:${it.value}" }
    }

    @TypeConverter
    fun toSeasonOverrides(value: String?): Map<Int, Int>? {
        if (value.isNullOrBlank()) return null
        return try {
            value.split(",").associate {
                val (k, v) = it.split(":")
                k.trim().toInt() to v.trim().toInt()
            }
        } catch (_: Exception) {
            null
        }
    }

    @TypeConverter
    fun fromMediaStatus(status: MediaStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toMediaStatus(value: String?): MediaStatus? {
        return value?.let { MediaStatus.fromString(it) }
    }

    @TypeConverter
    fun fromEpisodeSearchStyle(style: EpisodeSearchStyle?): String? {
        return style?.name
    }

    @TypeConverter
    fun toEpisodeSearchStyle(value: String?): EpisodeSearchStyle? {
        return value?.let {
            try {
                EpisodeSearchStyle.valueOf(it)
            } catch (_: Exception) {
                null
            }
        }
    }
}

@Database(
    entities = [UserToken::class, CalendarItem::class, NotificationSetting::class, TrackedWatchlistItem::class, WatchedEpisode::class, CustomSearchLink::class, ItemDownloadSettings::class, LocalItemState::class, ActiveNotification::class],
    version = 30,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9, spec = AppDatabase.Migration8To9::class),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14, spec = AppDatabase.Migration13To14::class),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17, spec = AppDatabase.Migration16To17::class),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19, spec = AppDatabase.Migration18To19::class),
        AutoMigration(from = 20, to = 21),
        AutoMigration(from = 24, to = 25),
        AutoMigration(from = 25, to = 26),
        AutoMigration(from = 26, to = 27),
        AutoMigration(from = 27, to = 28),
        AutoMigration(from = 28, to = 29),
        AutoMigration(from = 29, to = 30, spec = AppDatabase.Migration29To30::class),
    ]
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    @DeleteColumn(tableName = "calendar_items", columnName = "isWatched")
    class Migration8To9 : AutoMigrationSpec

    @DeleteColumn(tableName = "calendar_items", columnName = "title")
    @DeleteColumn(tableName = "calendar_items", columnName = "titleRomaji")
    @DeleteColumn(tableName = "calendar_items", columnName = "poster")
    @DeleteColumn(tableName = "calendar_items", columnName = "type")
    class Migration13To14 : AutoMigrationSpec

    @androidx.room.DeleteTable(tableName = "torrent_search_overrides")
    class Migration16To17 : AutoMigrationSpec

    @DeleteColumn(tableName = "calendar_items", columnName = "downloadPath")
    class Migration18To19 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE calendar_items SET mediaStatus = 'DOWNLOADED' WHERE mediaStatus = 'ARCHIVED'")
        }
    }

    @RenameColumn(tableName = "user_token", fromColumnName = "expiresAt", toColumnName = "accessTokenExpiresAt")
    class Migration29To30 : AutoMigrationSpec

    abstract fun userTokenDao(): UserTokenDao
    abstract fun calendarItemDao(): CalendarItemDao
    abstract fun notificationSettingDao(): NotificationSettingDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun watchedEpisodeDao(): WatchedEpisodeDao
    abstract fun customSearchLinkDao(): CustomSearchLinkDao
    abstract fun activeNotificationDao(): ActiveNotificationDao
    abstract fun itemDownloadSettingsDao(): ItemDownloadSettingsDao

    companion object {
        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Recreate notification_settings with FK CASCADE and cleanup orphaned rows
                db.execSQL("CREATE TABLE IF NOT EXISTS `notification_settings_new` (`simklId` INTEGER NOT NULL, `notifyEveryEpisode` INTEGER NOT NULL, `notifyAiredLastEpisode` INTEGER NOT NULL, PRIMARY KEY(`simklId`), FOREIGN KEY(`simklId`) REFERENCES `tracked_watchlist_items`(`simklId`) ON UPDATE CASCADE ON DELETE CASCADE )")
                db.execSQL("INSERT INTO `notification_settings_new` SELECT * FROM `notification_settings` WHERE simklId IN (SELECT simklId FROM tracked_watchlist_items)")
                db.execSQL("DROP TABLE `notification_settings`")
                db.execSQL("ALTER TABLE `notification_settings_new` RENAME TO `notification_settings`")

                // 2. Recreate watched_episodes with FK CASCADE, Index, and cleanup orphaned rows
                db.execSQL("CREATE TABLE IF NOT EXISTS `watched_episodes_new` (`simklId` INTEGER NOT NULL, `season` INTEGER NOT NULL, `episodeNumber` INTEGER NOT NULL, `watchedAt` INTEGER, PRIMARY KEY(`simklId`, `season`, `episodeNumber`), FOREIGN KEY(`simklId`) REFERENCES `tracked_watchlist_items`(`simklId`) ON UPDATE CASCADE ON DELETE CASCADE )")
                db.execSQL("INSERT INTO `watched_episodes_new` SELECT * FROM `watched_episodes` WHERE simklId IN (SELECT simklId FROM tracked_watchlist_items)")
                db.execSQL("DROP TABLE `watched_episodes`")
                db.execSQL("ALTER TABLE `watched_episodes_new` RENAME TO `watched_episodes`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_watched_episodes_simklId` ON `watched_episodes` (`simklId`)")

                // 3. Recreate item_download_settings with FK CASCADE and cleanup orphaned rows
                db.execSQL("CREATE TABLE IF NOT EXISTS `item_download_settings_new` (`simklId` INTEGER NOT NULL, `downloadUnwatched` INTEGER, `qualityOverride` TEXT, `preferHevcOverride` INTEGER, `titleOverride` TEXT, `seasonOverrides` TEXT, `downloadSubdirectoryOverride` TEXT, PRIMARY KEY(`simklId`), FOREIGN KEY(`simklId`) REFERENCES `tracked_watchlist_items`(`simklId`) ON UPDATE CASCADE ON DELETE CASCADE )")
                db.execSQL("INSERT INTO `item_download_settings_new` SELECT * FROM `item_download_settings` WHERE simklId IN (SELECT simklId FROM tracked_watchlist_items)")
                db.execSQL("DROP TABLE `item_download_settings`")
                db.execSQL("ALTER TABLE `item_download_settings_new` RENAME TO `item_download_settings`")

                // 4. Recreate calendar_items to update ON UPDATE CASCADE
                db.execSQL("PRAGMA foreign_keys = OFF")
                db.execSQL("CREATE TABLE IF NOT EXISTS `calendar_items_new` (`primaryKey` TEXT NOT NULL, `simklId` INTEGER NOT NULL, `episodeTitle` TEXT, `season` INTEGER, `episodeNumber` INTEGER, `date` INTEGER NOT NULL, `movieReleaseType` TEXT, `isSeasonPremiere` INTEGER NOT NULL, `isSeasonFinale` INTEGER NOT NULL, `isNotified` INTEGER NOT NULL, `watchedAt` INTEGER, PRIMARY KEY(`primaryKey`), FOREIGN KEY(`simklId`) REFERENCES `tracked_watchlist_items`(`simklId`) ON UPDATE CASCADE ON DELETE CASCADE )")
                db.execSQL("INSERT INTO `calendar_items_new` SELECT * FROM `calendar_items` WHERE simklId IN (SELECT simklId FROM tracked_watchlist_items)")
                db.execSQL("DROP TABLE `calendar_items`")
                db.execSQL("ALTER TABLE `calendar_items_new` RENAME TO `calendar_items`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_items_simklId` ON `calendar_items` (`simklId`)")
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }

        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Fix orphaned local_item_state entries where the parent primaryKey changed but the child didn't cascade
                // Old key: v2_${simklId}_${epNum}, New key: v2_${simklId}_1_${epNum}
                db.execSQL("PRAGMA foreign_keys = OFF")
                db.execSQL("""
                    UPDATE local_item_state 
                    SET primaryKey = (
                        SELECT ci.primaryKey 
                        FROM calendar_items ci 
                        WHERE ci.simklId = CAST(SUBSTR(local_item_state.primaryKey, 4, INSTR(SUBSTR(local_item_state.primaryKey, 4), '_') - 1) AS INTEGER)
                          AND ci.episodeNumber = CAST(SUBSTR(local_item_state.primaryKey, INSTR(SUBSTR(local_item_state.primaryKey, 4), '_') + 4) AS INTEGER)
                          AND ci.season = 1
                          AND ci.movieReleaseType IS NULL
                    )
                    WHERE primaryKey NOT IN (SELECT primaryKey FROM calendar_items)
                      AND primaryKey LIKE 'v2_%'
                      AND (LENGTH(primaryKey) - LENGTH(REPLACE(primaryKey, '_', ''))) = 2
                """.trimIndent())
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Recreate local_item_state to add ON UPDATE CASCADE
                db.execSQL("CREATE TABLE IF NOT EXISTS `local_item_state_new` (`primaryKey` TEXT NOT NULL, `mediaStatus` TEXT NOT NULL DEFAULT 'NOT_AIRED_YET', `downloadTaskId` TEXT, PRIMARY KEY(`primaryKey`), FOREIGN KEY(`primaryKey`) REFERENCES `calendar_items`(`primaryKey`) ON UPDATE CASCADE ON DELETE CASCADE )")
                db.execSQL("INSERT INTO `local_item_state_new` SELECT * FROM `local_item_state`")
                db.execSQL("DROP TABLE `local_item_state`")
                db.execSQL("ALTER TABLE `local_item_state_new` RENAME TO `local_item_state`")

                // 2. Update season to 1 for items where it's null and not a movie
                db.execSQL("UPDATE calendar_items SET season = 1 WHERE season IS NULL AND movieReleaseType IS NULL")

                // 3. Update primaryKey in calendar_items (v2_simklId_1_episodeNumber)
                // We perform a manual update on local_item_state as a safeguard because PRAGMA foreign_keys
                // might not be effective within a Room migration transaction.
                db.execSQL("PRAGMA foreign_keys = OFF")

                db.execSQL("""
                    UPDATE local_item_state 
                    SET primaryKey = 'v2_' || (SELECT simklId FROM calendar_items WHERE calendar_items.primaryKey = local_item_state.primaryKey) || '_1_' || (SELECT episodeNumber FROM calendar_items WHERE calendar_items.primaryKey = local_item_state.primaryKey)
                    WHERE primaryKey IN (
                        SELECT primaryKey FROM calendar_items 
                        WHERE season = 1 
                        AND movieReleaseType IS NULL 
                        AND primaryKey = 'v2_' || simklId || '_' || episodeNumber
                    )
                """.trimIndent())

                db.execSQL("""
                    UPDATE calendar_items 
                    SET primaryKey = 'v2_' || simklId || '_1_' || episodeNumber 
                    WHERE season = 1 
                    AND movieReleaseType IS NULL 
                    AND primaryKey = 'v2_' || simklId || '_' || episodeNumber
                """.trimIndent())

                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create new table for local state
                db.execSQL("CREATE TABLE IF NOT EXISTS `local_item_state` (`primaryKey` TEXT NOT NULL, `mediaStatus` TEXT NOT NULL DEFAULT 'NOT_AIRED_YET', `downloadTaskId` TEXT, PRIMARY KEY(`primaryKey`), FOREIGN KEY(`primaryKey`) REFERENCES `calendar_items`(`primaryKey`) ON UPDATE NO ACTION ON DELETE CASCADE )")

                // 2. Transfer data from calendar_items to local_item_state
                db.execSQL("INSERT INTO `local_item_state` (primaryKey, mediaStatus, downloadTaskId) SELECT primaryKey, mediaStatus, downloadTaskId FROM calendar_items")

                // 3. Recreate calendar_items table without mediaStatus and downloadTaskId columns
                db.execSQL("CREATE TABLE IF NOT EXISTS `calendar_items_new` (`primaryKey` TEXT NOT NULL, `simklId` INTEGER NOT NULL, `episodeTitle` TEXT, `season` INTEGER, `episodeNumber` INTEGER, `date` INTEGER NOT NULL, `movieReleaseType` TEXT, `isSeasonPremiere` INTEGER NOT NULL, `isSeasonFinale` INTEGER NOT NULL, `isNotified` INTEGER NOT NULL, `watchedAt` INTEGER, PRIMARY KEY(`primaryKey`), FOREIGN KEY(`simklId`) REFERENCES `tracked_watchlist_items`(`simklId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("INSERT INTO `calendar_items_new` (primaryKey, simklId, episodeTitle, season, episodeNumber, date, movieReleaseType, isSeasonPremiere, isSeasonFinale, isNotified, watchedAt) SELECT primaryKey, simklId, episodeTitle, season, episodeNumber, date, movieReleaseType, isSeasonPremiere, isSeasonFinale, isNotified, watchedAt FROM calendar_items")
                db.execSQL("DROP TABLE calendar_items")
                db.execSQL("ALTER TABLE calendar_items_new RENAME TO calendar_items")

                // 4. Recreate index on calendar_items
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_items_simklId` ON `calendar_items` (`simklId`)")
            }
        }

        fun makeDatabase(context: Context): AppDatabase {
            return Room
                .databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "simkl_calendar_database"
                )
                .addMigrations(
                    MIGRATION_19_20,
                    MIGRATION_21_22,
                    MIGRATION_22_23,
                    MIGRATION_23_24,
                )
                .build()
        }
    }
}
