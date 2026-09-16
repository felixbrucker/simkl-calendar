package com.felixbrucker.simklcalendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.util.Log
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.receiver.alarm.AlarmScheduler
import com.felixbrucker.simklcalendar.receiver.alarm.makeItemAiredAlarmIntent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var appDatabase: AppDatabase
    private lateinit var calendarDao: CalendarItemDao

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
        sharedPreferences = mockk(relaxed = true)
        appDatabase = mockk(relaxed = true)
        calendarDao = mockk(relaxed = true)

        every { context.getSystemService(Context.ALARM_SERVICE) } returns alarmManager
        every { context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE) } returns sharedPreferences
        every { appDatabase.calendarItemDao() } returns calendarDao

        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, appDatabase)
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
        every { sharedPreferences.getBoolean("use_exact_alarms", false) } returns false
        val calItem = CalendarItem("v2_100_1_1", 100, "Ep 1", 1, 1, Instant.ofEpochMilli(1700000000000L), null, true, false)
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "TV Show", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, null)
        coEvery { calendarDao.getCalendarItemsForAiredAlarm(any()) } returns listOf(item)

        AlarmScheduler.scheduleAllItemsAiredAlarms(context)

        val typeSlot = slot<Int>()
        val triggerSlot = slot<Long>()
        verify { alarmManager.setAndAllowWhileIdle(capture(typeSlot), capture(triggerSlot), any<PendingIntent>()) }
        assertEquals(AlarmManager.RTC_WAKEUP, typeSlot.captured)
        assertEquals(1700000000000L, triggerSlot.captured)
    }

    @Test
    fun testScheduleAllItemsAiredAlarmsExact() = runTest {
        every { sharedPreferences.getBoolean("use_exact_alarms", false) } returns true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            every { alarmManager.canScheduleExactAlarms() } returns true
        }

        val calItem = CalendarItem("v2_200_digital", 200, null, null, null, Instant.ofEpochMilli(1700000000000L), MovieReleaseType.DIGITAL, false, false)
        val watchItem = TrackedWatchlistItem(200, MediaType.MOVIE, "Movie", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, null)
        coEvery { calendarDao.getCalendarItemsForAiredAlarm(any()) } returns listOf(item)

        AlarmScheduler.scheduleAllItemsAiredAlarms(context)

        verify { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, any(), any<PendingIntent>()) }
    }
}
