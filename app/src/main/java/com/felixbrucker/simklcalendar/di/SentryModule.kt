package com.felixbrucker.simklcalendar.di

import com.felixbrucker.simklcalendar.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SentryDsn

@Module
@InstallIn(SingletonComponent::class)
object SentryModule {
    @Provides
    @Singleton
    @SentryDsn
    fun provideSentryDsn(): String {
        return BuildConfig.SENTRY_DSN
    }
}
