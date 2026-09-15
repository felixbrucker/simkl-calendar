package com.felixbrucker.simklcalendar

import android.content.Context
import android.content.Intent
import android.util.Log
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.receiver.download.DownloadCompletedReceiver
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DownloadCompletedReceiverTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun testConstants() {
        assertEquals("com.felixbrucker.simklcalendar.ACTION_DOWNLOAD_COMPLETED", DownloadCompletedReceiver.ACTION_DOWNLOAD_COMPLETED)
        assertEquals("extra_item_primary_key", DownloadCompletedReceiver.EXTRA_ITEM_PRIMARY_KEY)
    }

    @Test
    fun testOnReceiveNullOrInvalidAction() {
        val receiver = DownloadCompletedReceiver()
        val context = mockk<Context>(relaxed = true)

        receiver.onReceive(null, null)
        receiver.onReceive(context, null)

        val invalidIntent = mockk<Intent>()
        every { invalidIntent.action } returns "INVALID_ACTION"
        receiver.onReceive(context, invalidIntent)
    }

    @Test
    fun testOnReceiveValidAction() {
        val receiver = spyk(DownloadCompletedReceiver())
        every { receiver.goAsync() } returns mockk(relaxed = true)

        val context = mockk<Context>(relaxed = true)
        val appDatabase = mockk<AppDatabase>(relaxed = true)
        val calendarDao = mockk<CalendarItemDao>(relaxed = true)

        every { appDatabase.calendarItemDao() } returns calendarDao

        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, appDatabase)

        val intent = mockk<Intent>()
        every { intent.action } returns DownloadCompletedReceiver.ACTION_DOWNLOAD_COMPLETED
        every { intent.getStringExtra(DownloadCompletedReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_100_1_1"

        try {
            receiver.onReceive(context, intent)
        } finally {
            field.set(null, null)
        }
    }
}
