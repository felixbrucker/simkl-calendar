package com.felixbrucker.simklcalendar.data.util

import android.util.Base64
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64 as JavaBase64

class PkceUtilTest {

    @Before
    fun setUp() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            val bytes = firstArg<ByteArray>()
            JavaBase64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Base64::class)
    }

    @Test
    fun testGenerateCodeVerifier() {
        val verifier = PkceUtil.generateCodeVerifier()
        assertNotNull(verifier)
        assertTrue(verifier.isNotEmpty())
    }

    @Test
    fun testGenerateCodeChallenge() {
        val verifier = PkceUtil.generateCodeVerifier()
        val challenge = PkceUtil.generateCodeChallenge(verifier)
        assertNotNull(challenge)
        assertTrue(challenge.isNotEmpty())
    }

    @Test
    fun testGenerateState() {
        val state = PkceUtil.generateState()
        assertNotNull(state)
        assertTrue(state.isNotEmpty())
    }
}
