package com.felixbrucker.simklcalendar.di

import android.content.Context
import com.felixbrucker.simklcalendar.data.preferences.AppSettingsRepository
import com.felixbrucker.simklcalendar.data.preferences.AuthRepository
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import com.felixbrucker.simklcalendar.data.preferences.NotificationRepository
import com.felixbrucker.simklcalendar.data.preferences.SyncMetadataRepository
import com.felixbrucker.simklcalendar.data.preferences.UiRepository
import com.felixbrucker.simklcalendar.data.preferences.appSettingsDataStore
import com.felixbrucker.simklcalendar.data.preferences.authDataStore
import com.felixbrucker.simklcalendar.data.preferences.autoDownloadDataStore
import com.felixbrucker.simklcalendar.data.preferences.notificationDataStore
import com.felixbrucker.simklcalendar.data.preferences.syncMetadataDataStore
import com.felixbrucker.simklcalendar.data.preferences.uiDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun provideAppSettingsRepository(@ApplicationContext context: Context): AppSettingsRepository {
        return AppSettingsRepository(context.appSettingsDataStore)
    }

    @Provides
    @Singleton
    fun provideAutoDownloadRepository(@ApplicationContext context: Context): AutoDownloadRepository {
        return AutoDownloadRepository(context.autoDownloadDataStore)
    }

    @Provides
    @Singleton
    fun provideNotificationRepository(@ApplicationContext context: Context): NotificationRepository {
        return NotificationRepository(context.notificationDataStore)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(@ApplicationContext context: Context): AuthRepository {
        return AuthRepository(context.authDataStore)
    }

    @Provides
    @Singleton
    fun provideSyncMetadataRepository(@ApplicationContext context: Context): SyncMetadataRepository {
        return SyncMetadataRepository(context.syncMetadataDataStore)
    }

    @Provides
    @Singleton
    fun provideUiRepository(@ApplicationContext context: Context): UiRepository {
        return UiRepository(context.uiDataStore)
    }
}
