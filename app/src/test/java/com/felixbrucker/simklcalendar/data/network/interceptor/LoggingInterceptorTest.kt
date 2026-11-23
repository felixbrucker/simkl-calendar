package com.felixbrucker.simklcalendar.data.network.interceptor

import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class LoggingInterceptorTest {

    @Test
    fun testPlainLoggingInterceptor() {
        val interceptor = PlainLoggingInterceptor()
        assertEquals("test log", interceptor.transformLog("test log"))
        assertEquals("", interceptor.transformLog(""))
    }

    @Test
    fun testObfuscatingLoggingInterceptorSanitizesTokens() = runTest {
        val tokenDao = mockk<UserTokenDao>()
        every { tokenDao.getUserToken() } returns flowOf(
            UserToken(
                id = 1,
                accessToken = "secret_access_token_123",
                username = "testuser",
                refreshToken = "secret_refresh_token_456",
                accessTokenExpiresAt = Instant.now().plusSeconds(3600),
                refreshTokenExpiresAt = Instant.now().plusSeconds(7200)
            )
        )
        val testScope = TestScope(testScheduler)
        val interceptor = ObfuscatingLoggingInterceptor(tokenDao, testScope)
        testScheduler.advanceUntilIdle()

        val log = "Request with token secret_access_token_123 and refresh secret_refresh_token_456."
        val transformed = interceptor.transformLog(log)
        assertEquals("Request with token ******** and refresh ********.", transformed)
    }

    @Test
    fun testObfuscatingLoggingInterceptorNullTokens() = runTest {
        val tokenDao = mockk<UserTokenDao>()
        every { tokenDao.getUserToken() } returns flowOf(null)
        val testScope = TestScope(testScheduler)
        val interceptor = ObfuscatingLoggingInterceptor(tokenDao, testScope)
        testScheduler.advanceUntilIdle()

        val log = "Request with token abc"
        val transformed = interceptor.transformLog(log)
        assertEquals("Request with token abc", transformed)
    }
}
