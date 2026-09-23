package com.felixbrucker.simklcalendar.data.network.interceptor

import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.network.RefreshMutexProvider
import com.felixbrucker.simklcalendar.data.network.TokenRefreshProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.Response

class SimklAuthenticatedApiParameterInterceptor(
    private val tokenDao: UserTokenDao,
    private val refreshMutexProvider: RefreshMutexProvider,
    private val tokenRefreshProvider: TokenRefreshProvider,
): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()

        var token = runBlocking { tokenDao.getActiveToken() }
        if (token?.isAccessTokenExpired == true) {
            token = runBlocking {
                refreshMutexProvider.mutex.withLock {
                    val currentToken = tokenDao.getActiveToken() ?: throw Exception("No active token for authenticated endpoint")
                    if (!currentToken.isAccessTokenExpired) {
                        return@withLock currentToken
                    }

                    return@withLock tokenRefreshProvider.performRefreshToken(currentToken)
                }
            }
        }
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer ${token.accessToken}")
        }

        return chain.proceed(requestBuilder.build())
    }
}
