package com.felixbrucker.simklcalendar.data.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
}

@Database(
    entities = [UserToken::class, CalendarItem::class, NotificationSetting::class, TrackedWatchlistItem::class, WatchedEpisode::class, CustomSearchLink::class, ItemDownloadSettings::class, LocalItemState::class],
    version = 20,
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
        AutoMigration(from = 18, to = 19, spec = AppDatabase.Migration18To19::class)
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

    abstract fun userTokenDao(): UserTokenDao
    abstract fun calendarItemDao(): CalendarItemDao
    abstract fun notificationSettingDao(): NotificationSettingDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun watchedEpisodeDao(): WatchedEpisodeDao
    abstract fun customSearchLinkDao(): CustomSearchLinkDao
    abstract fun itemDownloadSettingsDao(): ItemDownloadSettingsDao

    companion object {
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

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "simkl_calendar_database"
                )
                .addMigrations(MIGRATION_19_20)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
