package com.felixbrucker.simklcalendar.data.sentry

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class SentryManagerTest {
    @Before
    fun setUp() {
        mockkStatic(SentryAndroid::class)
        mockkStatic(Sentry::class)
        every { SentryAndroid.init(any(), any<Sentry.OptionsConfiguration<SentryAndroidOptions>>()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkStatic(SentryAndroid::class)
        unmockkStatic(Sentry::class)
    }

    @Test
    fun testSentryManagerUpdateStateWhenDisabled() {
        val context: Context = mockk(relaxed = true)
        val sentryManager = SentryManager(dsn = "DSN")

        sentryManager.updateSentryState(context = context, isEnabled = false)

        verify(exactly = 0) { SentryAndroid.init(context, any<Sentry.OptionsConfiguration<SentryAndroidOptions>>()) }
    }

    @Test
    fun testSentryManagerUpdateStateWhenEnabled() {
        val context: Context = mockk(relaxed = true)
        val sentryManager = SentryManager(dsn = "DSN")

        sentryManager.updateSentryState(context = context, isEnabled = true)

        verify(exactly = 1) { SentryAndroid.init(context, any<Sentry.OptionsConfiguration<SentryAndroidOptions>>()) }
    }

    @Test
    fun testSentryManagerConfiguresTracesSampleRateWhenEnabled() {
        val context: Context = mockk(relaxed = true)
        val sentryManager = SentryManager(dsn = "DSN")
        val slot = slot<Sentry.OptionsConfiguration<SentryAndroidOptions>>()
        every { SentryAndroid.init(context, capture(slot)) } answers {
            val options = SentryAndroidOptions()
            slot.captured.configure(options)
            Assert.assertEquals(0.01, options.tracesSampleRate!!, 0.001)
        }

        sentryManager.updateSentryState(context = context, isEnabled = true)

        verify(exactly = 1) { SentryAndroid.init(context, any<Sentry.OptionsConfiguration<SentryAndroidOptions>>()) }
    }

    @Test
    fun testIsSentryRunningWhenTrue() {
        every { Sentry.isEnabled() } returns true
        val sentryManager = SentryManager(dsn = "DSN")

        val result = sentryManager.isSentryRunning

        Assert.assertTrue(result)
    }

    @Test
    fun testIsSentryRunningWhenFalse() {
        every { Sentry.isEnabled() } returns false
        val sentryManager = SentryManager(dsn = "DSN")

        val result = sentryManager.isSentryRunning

        Assert.assertFalse(result)
    }
}
