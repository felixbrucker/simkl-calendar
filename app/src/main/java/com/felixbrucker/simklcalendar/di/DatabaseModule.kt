package com.felixbrucker.simklcalendar.di

import android.content.Context
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CustomSearchLinkDao
import com.felixbrucker.simklcalendar.data.database.ActiveNotificationDao
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.NotificationSettingDao
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.database.WatchedEpisodeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideUserTokenDao(db: AppDatabase): UserTokenDao = db.userTokenDao()

    @Provides
    fun provideCalendarItemDao(db: AppDatabase): CalendarItemDao = db.calendarItemDao()

    @Provides
    fun provideNotificationSettingDao(db: AppDatabase): NotificationSettingDao = db.notificationSettingDao()

    @Provides
    fun provideWatchlistDao(db: AppDatabase): WatchlistDao = db.watchlistDao()

    @Provides
    fun provideWatchedEpisodeDao(db: AppDatabase): WatchedEpisodeDao = db.watchedEpisodeDao()

    @Provides
    fun provideCustomSearchLinkDao(db: AppDatabase): CustomSearchLinkDao = db.customSearchLinkDao()

    @Provides
    fun provideItemDownloadSettingsDao(db: AppDatabase): ItemDownloadSettingsDao = db.itemDownloadSettingsDao()

    @Provides
    fun provideActiveNotificationDao(db: AppDatabase): ActiveNotificationDao = db.activeNotificationDao()
}
