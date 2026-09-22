package com.felixbrucker.simklcalendar.receiver.alarm

import android.content.Context
import android.content.SharedPreferences
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.extensions.globalNotificationSettings
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DefaultNotificationSettingsTest {

    @Test
    fun testFromContext() {
        val context = mockk<Context>()
        val prefs = mockk<SharedPreferences>()

        every { context.globalNotificationSettings } returns prefs
        every { prefs.getBoolean("default_notify_airing", false) } returns true
        every { prefs.getBoolean("default_notify_season_finished", true) } returns true
        every { prefs.getBoolean("default_notify_movie_theater", false) } returns false
        every { prefs.getBoolean("default_notify_movie_digital", true) } returns true

        val settings = DefaultNotificationSettings.fromContext(context)
        assertTrue(settings.itemAired)
        assertTrue(settings.seasonFinished)
        assertFalse(settings.movieIsInTheaters)
        assertTrue(settings.movieIsReleasedOnDigital)
    }

    @Test
    fun testMakeNotificationSettingsTV() {
        val settings = DefaultNotificationSettings(
            itemAired = true,
            seasonFinished = false,
            movieIsInTheaters = false,
            movieIsReleasedOnDigital = true
        )

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
        val itemWithWatchlist = CalendarItemWithWatchlist(calItem, watchItem, null)

        val notifSetting = settings.makeNotificationSettings(itemWithWatchlist)
        assertEquals(100, notifSetting.simklId)
        assertTrue(notifSetting.notifyEveryEpisode)
        assertFalse(notifSetting.notifyAiredLastEpisode)
    }

    @Test
    fun testMakeNotificationSettingsMovie() {
        val settings = DefaultNotificationSettings(
            itemAired = false,
            seasonFinished = false,
            movieIsInTheaters = true,
            movieIsReleasedOnDigital = true
        )

        val calItem = CalendarItem(
            primaryKey = "v2_200_theater",
            simklId = 200,
            episodeTitle = null,
            season = null,
            episodeNumber = null,
            date = Instant.now(),
            movieReleaseType = null,
            isSeasonPremiere = false,
            isSeasonFinale = false
        )
        val watchItem = TrackedWatchlistItem(
            simklId = 200,
            type = MediaType.MOVIE,
            title = "Movie",
            titleRomaji = null,
            poster = null
        )
        val itemWithWatchlist = CalendarItemWithWatchlist(calItem, watchItem, null)

        val notifSetting = settings.makeNotificationSettings(itemWithWatchlist)
        assertEquals(200, notifSetting.simklId)
        assertTrue(notifSetting.notifyEveryEpisode)
        assertTrue(notifSetting.notifyAiredLastEpisode)
    }
}
