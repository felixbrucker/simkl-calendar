package com.felixbrucker.simklcalendar.data.repository

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
class OAuthRepositoryTest {

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
    fun testOnOAuthCodeReceivedEmitsEvent() = runTest {
        val oauthRepo = OAuthRepository()

        oauthRepo.onOAuthCodeReceived("code123", "state123")
        val event = oauthRepo.oauthCodeEvents.first()
        val code = event.code
        val state = event.state

        assertEquals("code123", code)
        assertEquals("state123", state)
    }
}
