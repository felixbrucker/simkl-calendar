package com.felixbrucker.simklcalendar.data.network

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

class RateLimitInterceptor(
    private val maxRetries: Int = 3,
    private val defaultDelayMs: Long = 1000L,
    private val delayFunc: (Long) -> Unit = { millis ->
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var retryCount = 0

        while (response.code == 429 && retryCount < maxRetries) {
            val bodyString = try {
                response.peekBody(64 * 1024).string()
            } catch (e: Exception) {
                ""
            }

            if (isPerSecondRateLimit(bodyString)) {
                response.close()
                retryCount++
                Timber.tag("RateLimitInterceptor").w("Per-second rate limit hit (429 rate_limit). Retrying ($retryCount/$maxRetries)...")
                delayFunc(defaultDelayMs * retryCount)
                response = chain.proceed(request)
            } else {
                break
            }
        }

        return response
    }

    private fun isPerSecondRateLimit(body: String): Boolean {
        return body.contains("\"rate_limit\"", ignoreCase = true)
    }
}
