package com.felixbrucker.simklcalendar.data.network.interceptor

import com.felixbrucker.simklcalendar.BuildConfig.APP_NAME
import com.felixbrucker.simklcalendar.BuildConfig.SIMKL_CLIENT_ID
import com.felixbrucker.simklcalendar.BuildConfig.VERSION_NAME
import okhttp3.Interceptor
import okhttp3.Response

class SimklApiParameterInterceptor: Interceptor {
    private val userAgent = "$APP_NAME/${VERSION_NAME}"

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val urlBuilder = originalRequest.url.newBuilder()
            .addQueryParameter("client_id", SIMKL_CLIENT_ID)
            .addQueryParameter("app-name", APP_NAME)
            .addQueryParameter("app-version", VERSION_NAME)

        val requestBuilder = originalRequest.newBuilder()
            .url(urlBuilder.build())
            .header("User-Agent", userAgent)
            .header("app-name", APP_NAME)
            .header("app-version", VERSION_NAME)

        return chain.proceed(requestBuilder.build())
    }
}
