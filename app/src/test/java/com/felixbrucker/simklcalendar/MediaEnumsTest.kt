package com.felixbrucker.simklcalendar.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaEnumsTest {

    @Test
    fun testMediaTypeFromKey() {
        assertEquals(MediaType.TV, MediaType.fromKey("tv"))
        assertEquals(MediaType.TV, MediaType.fromKey("shows"))
        assertEquals(MediaType.TV, MediaType.fromKey("show"))
        assertEquals(MediaType.ANIME, MediaType.fromKey("anime"))
        assertEquals(MediaType.ANIME, MediaType.fromKey("ANIME"))
        assertEquals(MediaType.MOVIE, MediaType.fromKey("movie"))
        assertEquals(MediaType.MOVIE, MediaType.fromKey("movies"))
        assertEquals(MediaType.TV, MediaType.fromKey("unknown"))
        assertEquals(MediaType.TV, MediaType.fromKey(null))
    }

    @Test
    fun testWatchlistStatusFromKey() {
        assertEquals(WatchlistStatus.WATCHING, WatchlistStatus.fromKey("watching"))
        assertEquals(WatchlistStatus.PLAN_TO_WATCH, WatchlistStatus.fromKey("plantowatch"))
        assertEquals(WatchlistStatus.PLAN_TO_WATCH, WatchlistStatus.fromKey("plan_to_watch"))
        assertEquals(WatchlistStatus.COMPLETED, WatchlistStatus.fromKey("completed"))
        assertEquals(WatchlistStatus.HOLD, WatchlistStatus.fromKey("hold"))
        assertEquals(WatchlistStatus.DROPPED, WatchlistStatus.fromKey("dropped"))
        assertEquals(WatchlistStatus.WATCHING, WatchlistStatus.fromKey("invalid"))
        assertEquals(WatchlistStatus.WATCHING, WatchlistStatus.fromKey(null))
    }

    @Test
    fun testMediaStatusFromString() {
        assertEquals(MediaStatus.IGNORED, MediaStatus.fromString("IGNORED"))
        assertEquals(MediaStatus.NOT_AIRED_YET, MediaStatus.fromString("not_aired_yet"))
        assertEquals(MediaStatus.WANTED, MediaStatus.fromString("WANTED"))
        assertEquals(MediaStatus.DOWNLOADING, MediaStatus.fromString("downloading"))
        assertEquals(MediaStatus.DOWNLOADED, MediaStatus.fromString("Downloaded"))
        assertEquals(MediaStatus.IGNORED, MediaStatus.fromString("invalid"))
        assertEquals(MediaStatus.IGNORED, MediaStatus.fromString(null))
    }

    @Test
    fun testMovieReleaseTypeDisplayNames() {
        assertEquals("Theater Release", MovieReleaseType.THEATER.displayName)
        assertEquals("Digital / DVD Release", MovieReleaseType.DIGITAL.displayName)
    }
}
