package com.felixbrucker.simklcalendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.felixbrucker.simklcalendar.data.database.*
import com.felixbrucker.simklcalendar.data.model.*
import com.felixbrucker.simklcalendar.data.preferences.*
import com.felixbrucker.simklcalendar.receiver.alarm.AlarmScheduler
import com.felixbrucker.simklcalendar.receiver.alarm.makeItemAiredAlarmIntent
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmSchedulerTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var appDatabase: AppDatabase
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var notificationRepo: NotificationRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockkStatic(Uri::class)
        val uri = mockk<Uri>(relaxed = true)
        every { Uri.parse(any()) } returns uri

        mockkStatic(PendingIntent::class)
        val pendingIntent = mockk<PendingIntent>(relaxed = true)
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns pendingIntent

        context = mockk(relaxed = true)
        alarmManager = mockk(relaxed = true)
        appDatabase = mockk(relaxed = true)
        calendarDao = mockk(relaxed = true)
        notificationRepo = mockk(relaxed = true)

        every { context.getSystemService(Context.ALARM_SERVICE) } returns alarmManager
        every { appDatabase.calendarItemDao() } returns calendarDao

        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, appDatabase)
        
        mockkStatic("com.felixbrucker.simklcalendar.data.preferences.NotificationPreferencesKt")
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkStatic(Uri::class)
        unmockkStatic(PendingIntent::class)
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    @Test
    fun testMakeItemAiredAlarmIntent() {
        val calItem = CalendarItem("v2_100_1_1", 100, "Ep 1", 1, 1, Instant.ofEpochMilli(1700000000000L), null, true, false)
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "TV Show", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, null)

        val result = item.makeItemAiredAlarmIntent(0, context)

        assertNotNull(result)
        verify { PendingIntent.getBroadcast(context, item.notificationId, any<Intent>(), 0) }
    }

    @Test
    fun testScheduleAllItemsAiredAlarmsInexact() = runTest {
        every { notificationRepo.preferencesFlow } returns flowOf(NotificationPreferences(useExactAlarms = false))

        val calItem = CalendarItem("v2_100_1_1", 100, "Ep 1", 1, 1, Instant.ofEpochMilli(1700000000000L), null, true, false)
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "TV Show", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, null)
        coEvery { calendarDao.getCalendarItemsForAiredAlarm(any()) } returns listOf(item)

        val scheduler = AlarmScheduler(context, calendarDao, notificationRepo)
        scheduler.scheduleAllItemsAiredAlarms()

        val typeSlot = slot<Int>()
        val triggerSlot = slot<Long>()
        verify { alarmManager.setAndAllowWhileIdle(capture(typeSlot), capture(triggerSlot), any<PendingIntent>()) }
        assertEquals(AlarmManager.RTC_WAKEUP, typeSlot.captured)
        assertEquals(1700000000000L, triggerSlot.captured)
    }

    @Test
    fun testScheduleAllItemsAiredAlarmsExact() = runTest {
        every { notificationRepo.preferencesFlow } returns flowOf(NotificationPreferences(useExactAlarms = true))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            every { alarmManager.canScheduleExactAlarms() } returns true
        }

        val calItem = CalendarItem("v2_200_digital", 200, null, null, null, Instant.ofEpochMilli(1700000000000L), MovieReleaseType.DIGITAL, false, false)
        val watchItem = TrackedWatchlistItem(200, MediaType.MOVIE, "Movie", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, null)
        coEvery { calendarDao.getCalendarItemsForAiredAlarm(any()) } returns listOf(item)

        val scheduler = AlarmScheduler(context, calendarDao, notificationRepo)
        scheduler.scheduleAllItemsAiredAlarms()

        verify { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, any(), any<PendingIntent>()) }
    }
}
