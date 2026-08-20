package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.model.MediaType
import com.example.data.model.MovieReleaseType
import com.example.data.model.WatchlistStatus
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
}

@Database(
    entities = [UserToken::class, CalendarItem::class, NotificationSetting::class, TrackedWatchlistItem::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userTokenDao(): UserTokenDao
    abstract fun calendarItemDao(): CalendarItemDao
    abstract fun notificationSettingDao(): NotificationSettingDao
    abstract fun watchlistDao(): WatchlistDao

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
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
