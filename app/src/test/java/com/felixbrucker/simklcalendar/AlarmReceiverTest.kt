package com.felixbrucker.simklcalendar

import android.content.Context
import android.content.Intent
import android.util.Log
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.NotificationSettingDao
import com.felixbrucker.simklcalendar.receiver.alarm.AlarmReceiver
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AlarmReceiverTest {

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
    fun testConstants() {
        assertEquals("extra_item_primary_key", AlarmReceiver.EXTRA_ITEM_PRIMARY_KEY)
        assertEquals("com.felixbrucker.simklcalendar.ACTION_ITEM_AIRED_ALARM", AlarmReceiver.ACTION_ITEM_AIRED_ALARM)
    }

    @Test
    fun testOnReceiveNullOrInvalidAction() {
        val receiver = AlarmReceiver()
        val context = mockk<Context>(relaxed = true)

        receiver.onReceive(null, null)
        receiver.onReceive(context, null)

        val invalidIntent = mockk<Intent>()
        every { invalidIntent.action } returns "INVALID_ACTION"
        receiver.onReceive(context, invalidIntent)
    }

    @Test
    fun testOnReceiveValidAction() {
        val receiver = spyk(AlarmReceiver())
        every { receiver.goAsync() } returns mockk(relaxed = true)

        val context = mockk<Context>(relaxed = true)
        val appDatabase = mockk<AppDatabase>(relaxed = true)
        val calendarDao = mockk<CalendarItemDao>(relaxed = true)
        val settingDao = mockk<NotificationSettingDao>(relaxed = true)

        every { appDatabase.calendarItemDao() } returns calendarDao
        every { appDatabase.notificationSettingDao() } returns settingDao

        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, appDatabase)

        val intent = mockk<Intent>()
        every { intent.action } returns AlarmReceiver.ACTION_ITEM_AIRED_ALARM
        every { intent.getStringExtra(AlarmReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_100_1_1"

        try {
            receiver.onReceive(context, intent)
        } finally {
            field.set(null, null)
        }
    }
}
