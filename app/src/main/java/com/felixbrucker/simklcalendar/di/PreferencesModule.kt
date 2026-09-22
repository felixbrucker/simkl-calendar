package com.felixbrucker.simklcalendar.di

import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadDataSource
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesModule {

    @Binds
    @Singleton
    abstract fun bindAutoDownloadDataSource(impl: AutoDownloadRepository): AutoDownloadDataSource
}
