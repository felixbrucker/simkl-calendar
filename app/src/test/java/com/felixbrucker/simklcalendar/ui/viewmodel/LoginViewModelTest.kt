package com.felixbrucker.simklcalendar.ui.viewmodel

import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.preferences.AuthPreferences
import com.felixbrucker.simklcalendar.data.preferences.AuthRepository
import com.felixbrucker.simklcalendar.data.repository.OAuthRepository
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private val repositoryMock: SimklRepository = mockk(relaxed = true)
    private val authRepoMock: AuthRepository = mockk(relaxed = true)
    private val oAuthRepoMock: OAuthRepository = mockk(relaxed = true)

    private val userTokenFlow = MutableStateFlow<UserToken?>(null)
    private val authPreferencesFlow = MutableStateFlow(AuthPreferences())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        userTokenFlow.value = null
        authPreferencesFlow.value = AuthPreferences()
        every { repositoryMock.activeUserToken } returns userTokenFlow
        every { authRepoMock.preferencesFlow } returns authPreferencesFlow
        every { oAuthRepoMock.oauthCodeEvents } returns MutableSharedFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testIsRealApiConfiguredAndCreateAuthorizationUrl() {
        every { repositoryMock.isRealApiConfigured() } returns true
        every { repositoryMock.createAuthorizationUrl("simklcalendar://auth") } returns "https://simkl.com/oauth"
        val viewModel = LoginViewModel(repositoryMock, authRepoMock, oAuthRepoMock)

        val isConfigured = viewModel.isRealApiConfigured()
        val authUrl = viewModel.createAuthorizationUrl()

        assertTrue(isConfigured)
        assertEquals("https://simkl.com/oauth", authUrl)
    }

    @Test
    fun testExchangeOAuthCodeSuccess() = runTest {
        coEvery { repositoryMock.exchangeOAuthCode("code", "state", "simklcalendar://auth") } returns true
        val viewModel = LoginViewModel(repositoryMock, authRepoMock, oAuthRepoMock)
        var successCalled = false
        var failureCalled = false

        viewModel.exchangeOAuthCode(
            code = "code",
            state = "state",
            redirectUri = "simklcalendar://auth",
            onSuccess = { successCalled = true },
            onFailure = { failureCalled = true }
        )
        advanceUntilIdle()

        assertTrue(successCalled)
        assertFalse(failureCalled)
    }
}
