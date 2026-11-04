package com.felixbrucker.simklcalendar.ui.viewmodel

import android.content.Context
import android.widget.Toast
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.preferences.AuthPreferences
import com.felixbrucker.simklcalendar.data.preferences.AuthRepository
import com.felixbrucker.simklcalendar.data.util.OAuthCallbackEvent
import com.felixbrucker.simklcalendar.data.util.OAuthEventHub
import com.felixbrucker.simklcalendar.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val contextMock: Context = mockk(relaxed = true)
    private val userRepositoryMock: UserRepository = mockk(relaxed = true)
    private val authRepoMock: AuthRepository = mockk(relaxed = true)
    private val oAuthEventHubMock: OAuthEventHub = mockk(relaxed = true)

    private val userTokenFlow = MutableStateFlow<UserToken?>(null)
    private val authPreferencesFlow = MutableStateFlow(AuthPreferences())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Toast::class)
        userTokenFlow.value = null
        authPreferencesFlow.value = AuthPreferences()
        every { userRepositoryMock.activeUserToken } returns userTokenFlow
        every { authRepoMock.preferencesFlow } returns authPreferencesFlow
        every { oAuthEventHubMock.oauthCallbackEvents } returns MutableSharedFlow()
        every { Toast.makeText(any(), any<CharSequence>(), any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testIsRealApiConfiguredAndCreateAuthorizationUrl() = runTest {
        val appScope = backgroundScope
        every { userRepositoryMock.isRealApiConfigured() } returns true
        every { userRepositoryMock.createAuthorizationUrl("simklcalendar://auth") } returns "https://simkl.com/oauth"
        val viewModel = LoginViewModel(contextMock, userRepositoryMock, authRepoMock, oAuthEventHubMock, appScope)

        val isConfigured = viewModel.isRealApiConfigured()
        val authUrl = viewModel.createAuthorizationUrl("simklcalendar://auth")

        assertTrue(isConfigured)
        assertEquals("https://simkl.com/oauth", authUrl)
    }

    @Test
    fun testExchangeOAuthCodeSuccess() = runTest {
        val appScope = backgroundScope
        coEvery { userRepositoryMock.exchangeOAuthCode("code", "state", "simklcalendar://auth") } returns true
        val viewModel = LoginViewModel(contextMock, userRepositoryMock, authRepoMock, oAuthEventHubMock, appScope)
        val event = OAuthCallbackEvent("code", "state", "simklcalendar://auth")

        viewModel.exchangeOAuthCode(event)
        advanceUntilIdle()
        val isSyncing = viewModel.isSyncing.value

        assertFalse(isSyncing)
        coVerify { Toast.makeText(contextMock, "Successfully logged in", Toast.LENGTH_SHORT) }
    }

    @Test
    fun testExchangeOAuthCodeSurvivesCallerCancellation() = runTest {
        val appScope = backgroundScope
        coEvery { userRepositoryMock.exchangeOAuthCode("code", "state", "simklcalendar://auth") } coAnswers {
            delay(200)
            true
        }
        val viewModel = LoginViewModel(contextMock, userRepositoryMock, authRepoMock, oAuthEventHubMock, appScope)
        val event = OAuthCallbackEvent("code", "state", "simklcalendar://auth")

        val callerJob = launch {
            viewModel.exchangeOAuthCode(event)
        }
        advanceTimeBy(50)
        callerJob.cancelAndJoin()
        advanceUntilIdle()

        coVerify { Toast.makeText(contextMock, "Successfully logged in", Toast.LENGTH_SHORT) }
    }
}
