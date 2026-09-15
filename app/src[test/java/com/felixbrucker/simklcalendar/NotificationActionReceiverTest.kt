package com.felixbrucker.simklcalendar

import android.content.Context
import android.content.Intent
import android.util.Log
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.receiver.notification.NotificationActionReceiver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class NotificationActionReceiverTest {

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
        assertEquals("com.felixbrucker.simklcalendar.ACTION_MARK_ITEM_WATCHED", NotificationActionReceiver.ACTION_MARK_ITEM_WATCHED)
        assertEquals("com.felixbrucker.simklcalendar.ACTION_MARK_SEASON_WATCHED", NotificationActionReceiver.ACTION_MARK_SEASON_WATCHED)
        assertEquals("com.felixbrucker.simklcalendar.ACTION_DOWNLOAD_ITEM", NotificationActionReceiver.ACTION_DOWNLOAD_ITEM)
        assertEquals("com.felixbrucker.simklcalendar.ACTION_DOWNLOAD_SEASON_MISSING_EPISODES", NotificationActionReceiver.ACTION_DOWNLOAD_SEASON_MISSING_EPISODES)
        assertEquals("extra_item_primary_key", NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY)
    }

    @Test
    fun testOnReceiveActions() = runBlocking {
        val receiver = spyk(NotificationActionReceiver())
        every { receiver.goAsync() } returns mockk(relaxed = true)

        val context = mockk<Context>(relaxed = true)
        val appDatabase = mockk<AppDatabase>(relaxed = true)
        val calendarDao = mockk<CalendarItemDao>(relaxed = true)

        every { appDatabase.calendarItemDao() } returns calendarDao

        val calItem = CalendarItem(
            primaryKey = "v2_100_1_1",
            simklId = 100,
            episodeTitle = "Ep 1",
            season = 1,
            episodeNumber = 1,
            date = Instant.now(),
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

        coEvery { calendarDao.findItem("v2_100_1_1") } returns item
        coEvery { calendarDao.getItemsInSeasonOrRelatedItems(100, 1) } returns listOf(item)

        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, appDatabase)

        try {
            receiver.onReceive(null, null)

            val actions = listOf(
                NotificationActionReceiver.ACTION_MARK_ITEM_WATCHED,
                NotificationActionReceiver.ACTION_MARK_SEASON_WATCHED,
                NotificationActionReceiver.ACTION_DOWNLOAD_ITEM,
                NotificationActionReceiver.ACTION_DOWNLOAD_SEASON_MISSING_EPISODES
            )

            for (action in actions) {
                val intent = mockk<Intent>()
                every { intent.action } returns action
                every { intent.getStringExtra(NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_100_1_1"
                receiver.onReceive(context, intent)
            }

            coVerify { calendarDao.findItem("v2_100_1_1") }
        } finally {
            field.set(null, null)
        }
    }
}
