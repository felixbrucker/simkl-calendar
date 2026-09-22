package com.felixbrucker.simklcalendar.di

import android.content.Context
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.network.TorrentSearchManager
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TorrentModule {

    @Provides
    @Singleton
    fun provideTorrentSearchManager(
        itemDownloadSettingsDao: ItemDownloadSettingsDao,
        autoDownloadRepo: AutoDownloadRepository
    ): TorrentSearchManager {
        return TorrentSearchManager(itemDownloadSettingsDao, autoDownloadRepo)
    }

}
