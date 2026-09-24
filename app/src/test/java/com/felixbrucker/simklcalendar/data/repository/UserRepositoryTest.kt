package com.felixbrucker.simklcalendar.data.repository

import android.util.Base64
import android.util.Log
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.database.WatchedEpisodeDao
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.network.AuthenticatedSimklApiService
import com.felixbrucker.simklcalendar.data.network.OAuthTokenResponse
import com.felixbrucker.simklcalendar.data.network.PublicSimklApiService
import com.felixbrucker.simklcalendar.data.network.UserProfile
import com.felixbrucker.simklcalendar.data.network.UserSettingsResponse
import com.felixbrucker.simklcalendar.data.preferences.AppSettingsRepository
import com.felixbrucker.simklcalendar.data.preferences.AuthPreferences
import com.felixbrucker.simklcalendar.data.preferences.AuthRepository
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import com.felixbrucker.simklcalendar.data.preferences.NotificationRepository
import com.felixbrucker.simklcalendar.data.preferences.SyncMetadataRepository
import com.felixbrucker.simklcalendar.data.preferences.UiRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserRepositoryTest {

    private lateinit var tokenDao: UserTokenDao
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var watchedDao: WatchedEpisodeDao
    private lateinit var publicApiService: PublicSimklApiService
    private lateinit var authenticatedApiService: AuthenticatedSimklApiService
    private lateinit var appSettingsRepo: AppSettingsRepository
    private lateinit var autoDownloadRepo: AutoDownloadRepository
    private lateinit var notificationRepo: NotificationRepository
    private lateinit var authRepo: AuthRepository
    private lateinit var syncMetadataRepo: SyncMetadataRepository
    private lateinit var uiRepo: UiRepository
    private lateinit var userRepository: UserRepository

    @Before
    fun setUp() {
        tokenDao = mockk(relaxed = true)
        calendarDao = mockk(relaxed = true)
        watchlistDao = mockk(relaxed = true)
        watchedDao = mockk(relaxed = true)
        publicApiService = mockk(relaxed = true)
        authenticatedApiService = mockk(relaxed = true)
        appSettingsRepo = mockk(relaxed = true)
        autoDownloadRepo = mockk(relaxed = true)
        notificationRepo = mockk(relaxed = true)
        authRepo = mockk(relaxed = true)
        syncMetadataRepo = mockk(relaxed = true)
        uiRepo = mockk(relaxed = true)

        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } returns "base64"
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        userRepository = UserRepository(
            tokenDao = tokenDao,
            calendarDao = calendarDao,
            watchlistDao = watchlistDao,
            watchedDao = watchedDao,
            publicSimklApiService = publicApiService,
            authenticatedSimklApiService = authenticatedApiService,
            appSettingsRepo = appSettingsRepo,
            autoDownloadRepo = autoDownloadRepo,
            notificationRepo = notificationRepo,
            authRepo = authRepo,
            syncMetadataRepo = syncMetadataRepo,
            uiRepo = uiRepo
        )

        every { authRepo.preferencesFlow } returns flowOf(AuthPreferences())
    }

    @After
    fun tearDown() {
        unmockkStatic(Base64::class)
        unmockkStatic(Log::class)
    }

    @Test
    fun testGetActiveUserTokenAndLogout() = runTest {
        val expectedToken = UserToken(1, "token123", "User")
        coEvery { tokenDao.getActiveToken() } returns expectedToken

        val retrievedToken = userRepository.getActiveUserToken()
        userRepository.logout()

        assertNotNull(retrievedToken)
        assertEquals("token123", retrievedToken?.accessToken)
        coVerify { tokenDao.clearUserToken() }
        coVerify { calendarDao.clearCalendarItems() }
        coVerify { watchlistDao.clearAll() }
        coVerify { watchedDao.clearAll() }
    }

    @Test
    fun testExchangeOAuthCodeSuccessAndFailure() = runTest {
        every { authRepo.preferencesFlow } returns flowOf(
            AuthPreferences(
                pkceState = "valid_state",
                pkceCodeVerifier = "verifier_123"
            )
        )
        coEvery { publicApiService.getAccessToken(any()) } returns OAuthTokenResponse(
            accessToken = "simkl_at_access_token_abc",
            tokenType = "Bearer",
            expiresIn = 604800,
            refreshToken = "simkl_rt_refresh_token_abc",
            scope = "media:read media:write"
        )
        coEvery { authenticatedApiService.getUserSettings() } returns UserSettingsResponse(UserProfile("SimklUser123"))

        val failureState = userRepository.exchangeOAuthCode("code", "wrong_state", "uri")
        every { authRepo.preferencesFlow } returns flowOf(
            AuthPreferences(
                pkceState = "valid_state",
                pkceCodeVerifier = null
            )
        )
        val failureVerifier = userRepository.exchangeOAuthCode("code", "valid_state", "uri")

        assertFalse(failureState)
        assertFalse(failureVerifier)
    }
}
