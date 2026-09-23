package com.felixbrucker.simklcalendar.data.network

import com.felixbrucker.simklcalendar.data.network.interceptor.RateLimitInterceptor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class RateLimitInterceptorTest {

    @Test
    fun testPerSecondRateLimitRetriesAndSucceeds() {
        val delays = mutableListOf<Long>()
        val interceptor = RateLimitInterceptor(
            maxRetries = 3,
            defaultDelayMs = 100L,
            delayFunc = { delays.add(it) }
        )
        val chain = mockk<Interceptor.Chain>()
        val request = Request.Builder().url("https://api.simkl.com/sync/activities").build()
        val response429 = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .body("{\"error\": \"rate_limit\"}".toResponseBody("application/json".toMediaType()))
            .build()
        val response200 = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{\"status\": \"ok\"}".toResponseBody("application/json".toMediaType()))
            .build()
        every { chain.request() } returns request
        every { chain.proceed(any()) } returns response429 andThen response200

        val response = interceptor.intercept(chain)
        val responseCode = response.code
        val delayCount = delays.size
        val firstDelay = delays.firstOrNull()

        assertEquals(200, responseCode)
        assertEquals(1, delayCount)
        assertEquals(100L, firstDelay)
        verify(exactly = 2) { chain.proceed(any()) }
    }

    @Test
    fun testPerSecondRateLimitExceedsMaxRetries() {
        val delays = mutableListOf<Long>()
        val interceptor = RateLimitInterceptor(
            maxRetries = 3,
            defaultDelayMs = 100L,
            delayFunc = { delays.add(it) }
        )
        val chain = mockk<Interceptor.Chain>()
        val request = Request.Builder().url("https://api.simkl.com/sync/activities").build()
        val response429 = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .body("{\"error\": \"rate_limit\"}".toResponseBody("application/json".toMediaType()))
            .build()
        every { chain.request() } returns request
        every { chain.proceed(any()) } returns response429

        val response = interceptor.intercept(chain)
        val responseCode = response.code
        val delayCount = delays.size
        val expectedDelays = listOf(100L, 200L, 300L)

        assertEquals(429, responseCode)
        assertEquals(3, delayCount)
        assertEquals(expectedDelays, delays)
        verify(exactly = 4) { chain.proceed(any()) }
    }

    @Test
    fun testUserLimitExceededDoesNotRetry() {
        val delays = mutableListOf<Long>()
        val interceptor = RateLimitInterceptor(
            maxRetries = 3,
            defaultDelayMs = 100L,
            delayFunc = { delays.add(it) }
        )
        val chain = mockk<Interceptor.Chain>()
        val request = Request.Builder().url("https://api.simkl.com/sync/activities").build()
        val response429UserLimit = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .header("Retry-After", "3600")
            .body("{\"error\": \"user_limit_exceeded\", \"code\": 429, \"message\": \"This user has reached their daily API request limit\"}".toResponseBody("application/json".toMediaType()))
            .build()
        every { chain.request() } returns request
        every { chain.proceed(any()) } returns response429UserLimit

        val response = interceptor.intercept(chain)
        val responseCode = response.code
        val retryAfterHeader = response.header("Retry-After")
        val delayCount = delays.size

        assertEquals(429, responseCode)
        assertEquals("3600", retryAfterHeader)
        assertEquals(0, delayCount)
        verify(exactly = 1) { chain.proceed(any()) }
    }

    @Test
    fun testSuccessfulResponsePassesThrough() {
        val delays = mutableListOf<Long>()
        val interceptor = RateLimitInterceptor(
            maxRetries = 3,
            defaultDelayMs = 100L,
            delayFunc = { delays.add(it) }
        )
        val chain = mockk<Interceptor.Chain>()
        val request = Request.Builder().url("https://api.simkl.com/sync/activities").build()
        val response200 = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{\"status\": \"ok\"}".toResponseBody("application/json".toMediaType()))
            .build()
        every { chain.request() } returns request
        every { chain.proceed(any()) } returns response200

        val response = interceptor.intercept(chain)
        val responseCode = response.code
        val delayCount = delays.size

        assertEquals(200, responseCode)
        assertEquals(0, delayCount)
        verify(exactly = 1) { chain.proceed(any()) }
    }

    @Test
    fun testOtherError429DoesNotRetry() {
        val delays = mutableListOf<Long>()
        val interceptor = RateLimitInterceptor(
            maxRetries = 3,
            defaultDelayMs = 100L,
            delayFunc = { delays.add(it) }
        )
        val chain = mockk<Interceptor.Chain>()
        val request = Request.Builder().url("https://api.simkl.com/sync/activities").build()
        val response429Other = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .body("{\"error\": \"unknown_limit\"}".toResponseBody("application/json".toMediaType()))
            .build()
        every { chain.request() } returns request
        every { chain.proceed(any()) } returns response429Other

        val response = interceptor.intercept(chain)
        val responseCode = response.code
        val delayCount = delays.size

        assertEquals(429, responseCode)
        assertEquals(0, delayCount)
        verify(exactly = 1) { chain.proceed(any()) }
    }

    @Test
    fun testNon429ErrorPassesThrough() {
        val delays = mutableListOf<Long>()
        val interceptor = RateLimitInterceptor(
            maxRetries = 3,
            defaultDelayMs = 100L,
            delayFunc = { delays.add(it) }
        )
        val chain = mockk<Interceptor.Chain>()
        val request = Request.Builder().url("https://api.simkl.com/sync/activities").build()
        val response500 = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("Internal Server Error")
            .body("{\"error\": \"server_error\"}".toResponseBody("application/json".toMediaType()))
            .build()
        every { chain.request() } returns request
        every { chain.proceed(any()) } returns response500

        val response = interceptor.intercept(chain)
        val responseCode = response.code
        val delayCount = delays.size

        assertEquals(500, responseCode)
        assertEquals(0, delayCount)
        verify(exactly = 1) { chain.proceed(any()) }
    }

    @Test
    fun testDefaultConstructorDelayExecutes() {
        val interceptor = RateLimitInterceptor(
            maxRetries = 1,
            defaultDelayMs = 1L
        )
        val chain = mockk<Interceptor.Chain>()
        val request = Request.Builder().url("https://api.simkl.com/sync/activities").build()
        val response429 = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .body("{\"error\": \"rate_limit\"}".toResponseBody("application/json".toMediaType()))
            .build()
        val response200 = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{\"status\": \"ok\"}".toResponseBody("application/json".toMediaType()))
            .build()
        every { chain.request() } returns request
        every { chain.proceed(any()) } returns response429 andThen response200

        val response = interceptor.intercept(chain)
        val responseCode = response.code

        assertEquals(200, responseCode)
        verify(exactly = 2) { chain.proceed(any()) }
    }
}
