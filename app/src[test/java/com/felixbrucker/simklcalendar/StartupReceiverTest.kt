package com.felixbrucker.simklcalendar.receiver.startup

import android.content.Context
import android.content.Intent
import android.util.Log
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test

class StartupReceiverTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun testOnReceiveBootCompleted() {
        val receiver = StartupReceiver()
        val context = mockk<Context>(relaxed = true)
        val appDatabase = mockk<AppDatabase>(relaxed = true)
        val calendarDao = mockk<CalendarItemDao>(relaxed = true)

        every { appDatabase.calendarItemDao() } returns calendarDao

        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, appDatabase)

        try {
            receiver.onReceive(null, null)

            val intent = mockk<Intent>()
            every { intent.action } returns Intent.ACTION_BOOT_COMPLETED
            receiver.onReceive(context, intent)
        } finally {
            field.set(null, null)
        }
    }
}
