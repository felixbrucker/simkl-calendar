package com.felixbrucker.simklcalendar.di

import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.network.AuthenticatedSimklApiService
import com.felixbrucker.simklcalendar.data.network.RefreshMutexProvider
import com.felixbrucker.simklcalendar.data.network.interceptor.RateLimitInterceptor
import com.felixbrucker.simklcalendar.data.network.interceptor.SimklApiParameterInterceptor
import com.felixbrucker.simklcalendar.data.network.PublicSimklApiService
import com.felixbrucker.simklcalendar.data.network.TokenRefreshProvider
import com.felixbrucker.simklcalendar.data.network.interceptor.LoggingInterceptor
import com.felixbrucker.simklcalendar.data.network.interceptor.SimklApiAuthenticator
import com.felixbrucker.simklcalendar.data.network.interceptor.SimklAuthenticatedApiParameterInterceptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun providePublicSimklApiService(
        moshi: Moshi,
    ): PublicSimklApiService {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(RateLimitInterceptor())
            .addInterceptor(SimklApiParameterInterceptor())
            .addInterceptor(LoggingInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.simkl.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(PublicSimklApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthenticatedSimklApiService(
        moshi: Moshi,
        tokenDao: UserTokenDao,
        refreshMutexProvider: RefreshMutexProvider,
        tokenRefreshProvider: TokenRefreshProvider,
    ): AuthenticatedSimklApiService {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(RateLimitInterceptor())
            .addInterceptor(SimklApiParameterInterceptor())
            .addInterceptor(SimklAuthenticatedApiParameterInterceptor(
                tokenDao = tokenDao,
                refreshMutexProvider = refreshMutexProvider,
                tokenRefreshProvider = tokenRefreshProvider,
            ))
            .addInterceptor(LoggingInterceptor())
            .authenticator(SimklApiAuthenticator(
                tokenDao = tokenDao,
                refreshMutexProvider = refreshMutexProvider,
                tokenRefreshProvider = tokenRefreshProvider,
            ))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.simkl.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(AuthenticatedSimklApiService::class.java)
    }
}
