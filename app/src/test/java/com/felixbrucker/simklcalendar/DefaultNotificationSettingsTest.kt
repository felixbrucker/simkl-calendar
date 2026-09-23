package com.felixbrucker.simklcalendar.receiver.alarm

import com.felixbrucker.simklcalendar.data.database.*
import com.felixbrucker.simklcalendar.data.model.*
import com.felixbrucker.simklcalendar.data.preferences.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DefaultNotificationSettingsTest {

    @Test
    fun testToDefaultNotificationSettings() {
        val prefs = NotificationPreferences(
            defaultNotifyAiring = true,
            defaultNotifySeasonFinished = true,
            defaultNotifyMovieTheater = false,
            defaultNotifyMovieDigital = true
        )

        val settings = prefs.toDefaultNotificationSettings()

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
