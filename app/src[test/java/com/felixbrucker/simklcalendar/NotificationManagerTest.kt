package com.felixbrucker.simklcalendar

import android.app.NotificationManager as AndroidNotificationManager
import android.content.Context
import android.util.Log
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.receiver.notification.NotificationManager
import com.felixbrucker.simklcalendar.receiver.notification.formatNotificationContent
import com.felixbrucker.simklcalendar.receiver.notification.makeDownloadItemIntent
import com.felixbrucker.simklcalendar.receiver.notification.makeDownloadSeasonMissingEpisodesIntent
import com.felixbrucker.simklcalendar.receiver.notification.makeMarkSeasonWatchedIntent
import com.felixbrucker.simklcalendar.receiver.notification.makeMarkWatchedIntent
import com.felixbrucker.simklcalendar.receiver.notification.makeOpenReleaseDetailViewIntent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

class NotificationManagerTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun testIntentBuilders() {
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
        val context = mockk<Context>(relaxed = true)

        val openIntent = item.makeOpenReleaseDetailViewIntent(context)
        val markWatchedIntent = item.makeMarkWatchedIntent(context)
        val markSeasonWatchedIntent = item.makeMarkSeasonWatchedIntent(context)
        val downloadItemIntent = item.makeDownloadItemIntent(context)
        val downloadSeasonIntent = item.makeDownloadSeasonMissingEpisodesIntent(context)

        assertNotNull(openIntent)
        assertNotNull(markWatchedIntent)
        assertNotNull(markSeasonWatchedIntent)
        assertNotNull(downloadItemIntent)
        assertNotNull(downloadSeasonIntent)
    }

    @Test
    fun testFormatNotificationContent() {
        val calItem = CalendarItem(
            primaryKey = "v2_100_1_1",
            simklId = 100,
            episodeTitle = "Pilot",
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
            title = "Breaking Bad",
            titleRomaji = null,
            poster = null
        )
        val item = CalendarItemWithWatchlist(calItem, watchItem, null)

        val (title, content) = item.formatNotificationContent(10)
        assertEquals("New Episode Released", title)
        assertEquals("Breaking Bad S01E01: \"Pilot\" is now airing.", content)
    }

    @Test
    fun testCreateNotificationChannel() {
        val context = mockk<Context>(relaxed = true)
        val notificationManager = mockk<AndroidNotificationManager>(relaxed = true)
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager

        NotificationManager.createNotificationChannel(context)

        verify { notificationManager.createNotificationChannel(any()) }
    }
}
