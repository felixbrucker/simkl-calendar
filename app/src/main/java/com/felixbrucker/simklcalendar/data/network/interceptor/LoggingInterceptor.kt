package com.felixbrucker.simklcalendar.data.network.interceptor

import android.annotation.SuppressLint
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObfuscatingLoggingInterceptor @Inject constructor(
    tokenDao: UserTokenDao,
    @ApplicationScope appScope: CoroutineScope
): BaseLoggingInterceptor() {
    @Volatile
    private var stringsToObfuscate: List<String>? = null

    init {
        appScope.launch {
            tokenDao.getUserToken()
                .filterNotNull()
                .map { listOf(it.accessToken, it.refreshToken) }
                .distinctUntilChanged()
                .collect { stringsToObfuscate = it }
        }
    }

    override fun transformLog(log: String): String {
        return log.sanitized(stringsToObfuscate)
    }
}

@Singleton
class PlainLoggingInterceptor @Inject constructor(): BaseLoggingInterceptor() {
    override fun transformLog(log: String): String {
        return log
    }
}

abstract class BaseLoggingInterceptor: Interceptor {
    companion object {
        private const val TAG = "OkHttp"
    }

    abstract fun transformLog(log: String): String

    @SuppressLint("TimberExceptionLogging")
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
            val transformedLog = transformLog(logBuffer.toString())
            if (transformedLog.isNotEmpty()) {
                Timber.tag(TAG).e(e, transformedLog)
            } else {
                Timber.tag(TAG).e(e, "Network request failed: ${request.method} $url")
            }
            throw e
        }

        val transformedLog = transformLog(logBuffer.toString())
        if (transformedLog.isNotEmpty()) {
            if (response.isSuccessful || response.code == 304) {
                Timber.tag(TAG).d(transformedLog)
            } else {
                Timber.tag(TAG).e(transformedLog)
            }
        }

        return response
    }
}

private fun String.sanitized(stringsToObfuscate: List<String>?): String {
    if (stringsToObfuscate == null) {
        return this
    }
    if (isEmpty()) return this
    var sanitized = this
    stringsToObfuscate.forEach { stringToObfuscate ->
        sanitized = sanitized.replace(stringToObfuscate, "********")
    }
    return sanitized
}
