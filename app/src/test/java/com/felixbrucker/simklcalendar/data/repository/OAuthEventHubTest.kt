package com.felixbrucker.simklcalendar.data.repository

import com.felixbrucker.simklcalendar.data.util.OAuthEventHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OAuthEventHubTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testOnOAuthCallbackReceivedEmitsEvent() = runTest {
        val oAuthEventHub = OAuthEventHub()

        oAuthEventHub.onOAuthCallbackReceived("code123", "state123", "simklcalendar://auth")
        val event = oAuthEventHub.oauthCallbackEvents.first()
        val code = event.code
        val state = event.state

        assertEquals("code123", code)
        assertEquals("state123", state)
    }
}
