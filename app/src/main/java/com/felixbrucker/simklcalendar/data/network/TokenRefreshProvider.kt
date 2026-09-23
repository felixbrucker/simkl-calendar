package com.felixbrucker.simklcalendar.data.network

import com.felixbrucker.simklcalendar.BuildConfig
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import timber.log.Timber
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class TokenRefreshProvider @Inject constructor(
    private val publicApiService: PublicSimklApiService,
    private val userTokenDao: UserTokenDao,
) {
    suspend fun performRefreshToken(current: UserToken): UserToken? = withContext(Dispatchers.IO) {
        if (current.refreshToken == null) throw Exception("No refresh token available")
        try {
            val response = publicApiService.getAccessToken(
                OAuthTokenRequest(
                    grantType = "refresh_token",
                    clientId = BuildConfig.SIMKL_CLIENT_ID,
                    refreshToken = current.refreshToken,
                )
            )
            val newAccessToken = response.accessToken
            val newRefreshToken = response.refreshToken
            val newAccessTokenExpiresAt = Instant.now().plusSeconds(response.expiresIn)
            val newRefreshTokenExpiresAt = Instant.now().plus(180, ChronoUnit.DAYS)
            val newToken = current.copy(
                accessToken = newAccessToken,
                refreshToken = newRefreshToken,
                accessTokenExpiresAt = newAccessTokenExpiresAt,
                refreshTokenExpiresAt = newRefreshTokenExpiresAt
            )
            userTokenDao.insertUserToken(newToken)

            newToken
        } catch (e: HttpException) {
            if (e.code() == 400 || e.code() == 401) {
                Timber.tag("TokenRefreshProvider").w(e, "Refresh token is invalid or expired. Resetting user token.")
                userTokenDao.clearUserToken()
            } else {
                Timber.tag("TokenRefreshProvider").e(e, "HTTP exception during token refresh (non-auth error)")
            }

            null
        } catch (e: Exception) {
            Timber.tag("TokenRefreshProvider").e(e, "Network or unexpected exception during token refresh")

            null
        }
    }
}
