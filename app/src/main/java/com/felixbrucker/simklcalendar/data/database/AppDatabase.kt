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
import androidx.sqlite.db.SupportSQLiteDatabase
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.model.WatchlistStatus
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
    fun fromWatchlistStatus(status: WatchlistStatus?): String? {
        return status?.key
    }

    @TypeConverter
    fun toWatchlistStatus(value: String?): WatchlistStatus? {
        return value?.let { WatchlistStatus.fromKey(it) }
    }

    @TypeConverter
    fun fromMediaTypeList(types: List<MediaType>?): String? {
        return types?.joinToString(",") { it.key }
    }

    @TypeConverter
    fun toMediaTypeList(value: String?): List<MediaType>? {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",").mapNotNull { key ->
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
    entities = [UserToken::class, CalendarItem::class, NotificationSetting::class, TrackedWatchlistItem::class, WatchedEpisode::class, CustomSearchLink::class, ItemDownloadSettings::class],
    version = 19,
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
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "simkl_calendar_database"
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
