package com.felixbrucker.simklcalendar.data.repository

import timber.log.Timber
import com.felixbrucker.simklcalendar.BuildConfig
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.database.WatchedEpisodeDao
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.network.AuthenticatedSimklApiService
import com.felixbrucker.simklcalendar.data.network.OAuthRevokeRequest
import com.felixbrucker.simklcalendar.data.network.OAuthTokenRequest
import com.felixbrucker.simklcalendar.data.network.PublicSimklApiService
import com.felixbrucker.simklcalendar.data.preferences.AppSettingsRepository
import com.felixbrucker.simklcalendar.data.preferences.AuthRepository
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import com.felixbrucker.simklcalendar.data.preferences.NotificationRepository
import com.felixbrucker.simklcalendar.data.preferences.SyncMetadataRepository
import com.felixbrucker.simklcalendar.data.preferences.UiRepository
import com.felixbrucker.simklcalendar.data.util.PkceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val tokenDao: UserTokenDao,
    private val calendarDao: CalendarItemDao,
    private val watchlistDao: WatchlistDao,
    private val watchedDao: WatchedEpisodeDao,
    private val publicSimklApiService: PublicSimklApiService,
    private val authenticatedSimklApiService: AuthenticatedSimklApiService,
    private val appSettingsRepo: AppSettingsRepository,
    private val autoDownloadRepo: AutoDownloadRepository,
    private val notificationRepo: NotificationRepository,
    private val authRepo: AuthRepository,
    private val syncMetadataRepo: SyncMetadataRepository,
    private val uiRepo: UiRepository
) {
    val activeUserToken: Flow<UserToken?> = tokenDao.getUserToken()

    fun isRealApiConfigured(): Boolean {
        return BuildConfig.SIMKL_CLIENT_ID.isNotEmpty()
    }

    fun createAuthorizationUrl(redirectUri: String = "simklcalendar://auth"): String? {
        val clientId = BuildConfig.SIMKL_CLIENT_ID.ifEmpty { return null }
        val codeVerifier = PkceUtil.generateCodeVerifier()
        val codeChallenge = PkceUtil.generateCodeChallenge(codeVerifier)
        val state = PkceUtil.generateState()

        runBlocking {
            authRepo.setPkceParams(codeVerifier, redirectUri, state)
        }

        val params = mapOf(
            "response_type" to "code",
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "scope" to "media:read media:write",
            "state" to state,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256"
        )

        val queryString = params.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }

        return "https://simkl.com/oauth2/authorize?$queryString"
    }

    private suspend fun clearUserTokenOnly(isV1Upgrade: Boolean = false) = withContext(Dispatchers.IO) {
        Timber.tag("UserRepository").w("Clearing active user token (isV1Upgrade=$isV1Upgrade) while retaining all local user data and preferences.")
        tokenDao.clearUserToken()
        if (isV1Upgrade) {
            authRepo.setShowAuthV2UpgradeHint(true)
        }
    }

    suspend fun resetAuthIfNeeded(): Unit = withContext(Dispatchers.IO) {
        val userToken = tokenDao.getActiveToken() ?: return@withContext

        if (!userToken.accessToken.startsWith("simkl_at_") || userToken.refreshToken.isEmpty()) {
            Timber.tag("UserRepository").w("Detected legacy or migrated Auth V1 token. Transitioning user to Auth V2 login while retaining data.")
            clearUserTokenOnly(isV1Upgrade = true)
            return@withContext
        }

        if (userToken.isRefreshTokenExpired) {
            Timber.tag("UserRepository").w("Refresh token has expired after 180 days. Transitioning user to login while retaining user data.")
            clearUserTokenOnly(isV1Upgrade = false)
            return@withContext
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val userToken = tokenDao.getActiveToken()
        if (userToken != null) {
            val revokeTarget = userToken.refreshToken
            if (revokeTarget.isNotEmpty()) {
                try {
                    publicSimklApiService.revokeToken(OAuthRevokeRequest(clientId = BuildConfig.SIMKL_CLIENT_ID, token = revokeTarget))
                } catch (e: Exception) {
                    Timber.tag("UserRepository").w(e, "Failed to revoke token on logout")
                }
            }
        }
        tokenDao.clearUserToken()
        calendarDao.clearCalendarItems()
        watchlistDao.clearAll()
        watchedDao.clearAll()

        appSettingsRepo.clear()
        notificationRepo.clear()
        autoDownloadRepo.clear()
        authRepo.clear()
        syncMetadataRepo.clear()
        uiRepo.clear()
    }

    suspend fun exchangeOAuthCode(
        code: String,
        state: String? = null,
        redirectUri: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val authPrefs = authRepo.preferencesFlow.first()
            val savedState = authPrefs.pkceState
            if (!savedState.isNullOrEmpty()) {
                if (state == null || state != savedState) {
                    Timber.tag("UserRepository").e("OAuth state mismatch or missing! CSRF verification failed.")
                    return@withContext false
                }
            }

            val codeVerifier = authPrefs.pkceCodeVerifier
            val savedRedirectUri = authPrefs.pkceRedirectUri ?: "simklcalendar://auth"
            val effectiveRedirectUri = redirectUri ?: savedRedirectUri

            if (codeVerifier.isNullOrEmpty()) {
                Timber.tag("UserRepository").e("PKCE code_verifier is missing from local storage")
                return@withContext false
            }

            val response = publicSimklApiService.getAccessToken(
                request = OAuthTokenRequest(
                    code = code,
                    clientId = BuildConfig.SIMKL_CLIENT_ID,
                    codeVerifier = codeVerifier,
                    redirectUri = effectiveRedirectUri,
                    grantType = "authorization_code"
                )
            )
            val accessToken = response.accessToken
            if (!accessToken.startsWith("simkl_at_")) {
                Timber.tag("UserRepository").e("OAuth returned invalid V2 access token prefix")
                return@withContext false
            }

            val refreshToken = response.refreshToken
            val accessTokenExpiresAt = Instant.now().plusSeconds(response.expiresIn)
            val refreshTokenExpiresAt = Instant.now().plus(180, ChronoUnit.DAYS)

            authRepo.clearPkceParams()
            authRepo.setShowAuthV2UpgradeHint(false)

            tokenDao.insertUserToken(
                UserToken(
                    accessToken = accessToken,
                    username = "",
                    refreshToken = refreshToken,
                    accessTokenExpiresAt = accessTokenExpiresAt,
                    refreshTokenExpiresAt = refreshTokenExpiresAt
                )
            )

            val username = try {
                val userResponse = authenticatedSimklApiService.getUserSettings()
                userResponse.user.name
            } catch (e: Exception) {
                Timber.tag("UserRepository").e(e, "Could not fetch user profile details, using empty string fallback")
                ""
            }

            if (username.isNotEmpty()) {
                tokenDao.insertUserToken(
                    UserToken(
                        accessToken = accessToken,
                        username = username,
                        refreshToken = refreshToken,
                        accessTokenExpiresAt = accessTokenExpiresAt,
                        refreshTokenExpiresAt = refreshTokenExpiresAt
                    )
                )
            }
            true
        } catch (e: Exception) {
            Timber.tag("UserRepository").e(e, "OAuth Code exchange failed")
            false
        }
    }

    suspend fun getActiveUserToken(): UserToken? = withContext(Dispatchers.IO) {
        tokenDao.getActiveToken()
    }
}
