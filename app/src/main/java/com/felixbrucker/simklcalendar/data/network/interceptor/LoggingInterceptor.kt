package com.felixbrucker.simklcalendar.data.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber

class LoggingInterceptor: Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
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

        return response
    }
}
