package com.felixbrucker.simklcalendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.receiver.alarm.AlarmScheduler
import com.felixbrucker.simklcalendar.receiver.alarm.makeItemAiredAlarmIntent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

class AlarmSchedulerTest {

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
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkStatic(Uri::class)
        unmockkStatic(PendingIntent::class)
    }

    @Test
    fun testMakeItemAiredAlarmIntent() {
        val calItem = CalendarItem(
            primaryKey = "v2_100_1_1",
            simklId = 100,
            episodeTitle = "Ep 1",
            season = 1,
            episodeNumber = 1,
            date = Instant.ofEpochMilli(1700000000000L),
            movieReleaseType = null,
            isSeasonPremiere = true,
            isSeasonFinale = false
        )
        val watchItem = TrackedWatchlistItem(
            simklId = 100,
            type = MediaType.TV,
            title = "TV Show",
            titleRomaji = null,
            poster = null
        )
        val item = CalendarItemWithWatchlist(calItem, watchItem, null)

        val context = mockk<Context>(relaxed = true)
        val result = item.makeItemAiredAlarmIntent(0, context)
        assertNotNull(result)

        verify {
            PendingIntent.getBroadcast(
                context,
                item.notificationId,
                any<Intent>(),
                0
            )
        }
    }

    @Test
    fun testScheduleAllItemsAiredAlarmsInexact() {
        runBlocking {
            val context = mockk<Context>(relaxed = true)
            val alarmManager = mockk<AlarmManager>(relaxed = true)
            val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
            val appDatabase = mockk<AppDatabase>(relaxed = true)
            val calendarDao = mockk<CalendarItemDao>(relaxed = true)

            every { context.getSystemService(Context.ALARM_SERVICE) } returns alarmManager
            every { context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE) } returns sharedPreferences
            every { sharedPreferences.getBoolean("use_exact_alarms", false) } returns false
            every { appDatabase.calendarItemDao() } returns calendarDao

            val calDate = Instant.ofEpochMilli(1700000000000L)
            val calItem = CalendarItem(
                primaryKey = "v2_100_1_1",
                simklId = 100,
                episodeTitle = "Ep 1",
                season = 1,
                episodeNumber = 1,
                date = calDate,
                movieReleaseType = null,
                isSeasonPremiere = true,
                isSeasonFinale = false
            )
            val watchItem = TrackedWatchlistItem(
                simklId = 100,
                type = MediaType.TV,
                title = "TV Show",
                titleRomaji = null,
                poster = null
            )
            val item = CalendarItemWithWatchlist(calItem, watchItem, null)

            coEvery { calendarDao.getCalendarItemsForAiredAlarm(any()) } returns listOf(item)

            val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
            field.isAccessible = true
            field.set(null, appDatabase)

            try {
                AlarmScheduler.scheduleAllItemsAiredAlarms(context)

                // Verify alarm was scheduled with setAndAllowWhileIdle
                val typeSlot = slot<Int>()
                val triggerSlot = slot<Long>()
                verify {
                    alarmManager.setAndAllowWhileIdle(
                        capture(typeSlot),
                        capture(triggerSlot),
                        any<PendingIntent>()
                    )
                }

                assertEquals(AlarmManager.RTC_WAKEUP, typeSlot.captured)
                assertEquals(1700000000000L, triggerSlot.captured)
            } finally {
                field.set(null, null)
            }
        }
    }
}
