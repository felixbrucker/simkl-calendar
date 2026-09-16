package com.felixbrucker.simklcalendar.data.util

import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MediaExtensionsTest {

    @Test
    fun testSubdirectoryName() {
        val movieSubdir = MediaType.MOVIE.subdirectoryName()
        val tvSubdir = MediaType.TV.subdirectoryName()
        val animeSubdir = MediaType.ANIME.subdirectoryName()

        assertEquals("movies", movieSubdir)
        assertEquals("series", tvSubdir)
        assertEquals("anime", animeSubdir)
    }

    @Test
    fun testTrackedWatchlistItemDefaultDestinationSubdirectoryMovie() {
        val movieItem = TrackedWatchlistItem(
            simklId = 100,
            type = MediaType.MOVIE,
            title = "Inception",
            titleRomaji = null,
            poster = "poster.jpg"
        )

        val subdir = movieItem.defaultDestinationSubdirectory()

        assertEquals("movies", subdir)
    }

    @Test
    fun testTrackedWatchlistItemDefaultDestinationSubdirectoryTV() {
        val tvItem = TrackedWatchlistItem(
            simklId = 200,
            type = MediaType.TV,
            title = "Breaking Bad",
            titleRomaji = null,
            poster = "poster.jpg"
        )

        val subdir = tvItem.defaultDestinationSubdirectory()

        assertEquals("series/Breaking Bad", subdir)
    }

    @Test
    fun testTrackedWatchlistItemDefaultDestinationSubdirectoryAnimeWithRomaji() {
        val animeItem = TrackedWatchlistItem(
            simklId = 300,
            type = MediaType.ANIME,
            title = "Shingeki no Kyojin",
            titleRomaji = "Attack on Titan",
            poster = "poster.jpg"
        )

        val subdir = animeItem.defaultDestinationSubdirectory()

        assertEquals("anime/Attack on Titan", subdir)
    }

    @Test
    fun testCalendarItemWithWatchlistDestinationSubdirectory() {
        val calendarItem = CalendarItem(
            primaryKey = "v2_200_1_1",
            simklId = 200,
            episodeTitle = "Pilot",
            season = 1,
            episodeNumber = 1,
            date = Instant.now(),
            movieReleaseType = null,
            isSeasonPremiere = true,
            isSeasonFinale = false
        )
        val watchlistItem = TrackedWatchlistItem(
            simklId = 200,
            type = MediaType.TV,
            title = "Breaking Bad",
            titleRomaji = null,
            poster = "poster.jpg"
        )
        val itemWithWatchlist = CalendarItemWithWatchlist(
            calendarItem = calendarItem,
            watchlistItem = watchlistItem,
            downloadSettings = null
        )
        val itemWithOverride = CalendarItemWithWatchlist(
            calendarItem = calendarItem,
            watchlistItem = watchlistItem,
            downloadSettings = ItemDownloadSettings(
                simklId = 200,
                downloadSubdirectoryOverride = "custom/path"
            )
        )

        val defaultSubdir = itemWithWatchlist.destinationSubdirectory()
        val overrideSubdir = itemWithOverride.destinationSubdirectory()

        assertEquals("series/Breaking Bad", defaultSubdir)
        assertEquals("custom/path", overrideSubdir)
    }
}
