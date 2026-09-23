package com.felixbrucker.simklcalendar.data.network.interceptor

import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.network.RefreshMutexProvider
import com.felixbrucker.simklcalendar.data.network.TokenRefreshProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class SimklApiAuthenticator(
    private val tokenDao: UserTokenDao,
    private val refreshMutexProvider: RefreshMutexProvider,
    private val tokenRefreshProvider: TokenRefreshProvider,
): Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        var prior = response.priorResponse
        var count = 0
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        if (count >= 2) {
            return null
        }

        val failedAuthHeader = response.request.header("Authorization")

        return runBlocking {
            refreshMutexProvider.mutex.withLock {
                val currentToken = tokenDao.getActiveToken() ?: return@runBlocking null
                val currentBearer = "Bearer ${currentToken.accessToken}"

                if (failedAuthHeader != null && failedAuthHeader != currentBearer && currentToken.accessToken.isNotEmpty()) {
                    return@runBlocking response.request.newBuilder()
                        .header("Authorization", currentBearer)
                        .build()
                }

                val refreshToken = currentToken.refreshToken
                if (refreshToken.isEmpty()) {
                    return@runBlocking null
                }

                val newToken = tokenRefreshProvider.performRefreshToken(currentToken)
                if (newToken != null) {
                    return@runBlocking response.request.newBuilder()
                        .header("Authorization", "Bearer ${newToken.accessToken}")
                        .build()
                }

                null
            }
        }
    }
}
