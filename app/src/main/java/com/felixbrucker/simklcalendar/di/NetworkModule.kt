package com.felixbrucker.simklcalendar.di

import com.felixbrucker.simklcalendar.BuildConfig
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.network.Authenticated
import com.felixbrucker.simklcalendar.data.network.RateLimitInterceptor
import com.felixbrucker.simklcalendar.data.network.SimklApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Invocation
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
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
    fun provideSimklApiService(
        moshi: Moshi,
        tokenDao: UserTokenDao
    ): SimklApiService {
        val appName = BuildConfig.APP_NAME
        val appVersion = BuildConfig.VERSION_NAME
        val userAgent = "$appName/$appVersion"

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(RateLimitInterceptor())
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val invocation = originalRequest.tag(Invocation::class.java)
                val isAuthenticatedEndpoint = invocation != null && invocation.method().isAnnotationPresent(Authenticated::class.java)

                val urlBuilder = originalRequest.url.newBuilder()
                    .addQueryParameter("client_id", BuildConfig.SIMKL_CLIENT_ID)
                    .addQueryParameter("app-name", appName)
                    .addQueryParameter("app-version", appVersion)

                val requestBuilder = originalRequest.newBuilder()
                    .url(urlBuilder.build())
                    .header("User-Agent", userAgent)
                    .header("app-name", appName)
                    .header("app-version", appVersion)

                if (isAuthenticatedEndpoint) {
                    val token = runBlocking { tokenDao.getActiveToken() }
                    if (token != null && token.accessToken.isNotEmpty()) {
                        requestBuilder.header("Authorization", "Bearer ${token.accessToken}")
                    }
                }

                chain.proceed(requestBuilder.build())
            }
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                val isCalendarJson = url.contains("calendar/v2") || url.contains("data.simkl.in") || url.endsWith(".json")

                val logBuffer = StringBuilder()
                val loggingInterceptor = HttpLoggingInterceptor { line ->
                    if (logBuffer.isNotEmpty()) {
                        logBuffer.append("\n")
                    }
                    logBuffer.append(line)
                }.apply {
                    level = if (isCalendarJson) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.BODY
                }

                val response = try {
                    loggingInterceptor.intercept(chain)
                } catch (e: Exception) {
                    if (logBuffer.isNotEmpty()) {
                        Timber.tag("OkHttp").e(e, logBuffer.toString())
                    } else {
                        Timber.tag("OkHttp").e(e, "Network request failed: ${request.method} $url")
                    }
                    throw e
                }

                if (logBuffer.isNotEmpty()) {
                    if (response.isSuccessful) {
                        Timber.tag("OkHttp").d(logBuffer.toString())
                    } else {
                        Timber.tag("OkHttp").e(logBuffer.toString())
                    }
                }

                response
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.simkl.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(SimklApiService::class.java)
    }
}
