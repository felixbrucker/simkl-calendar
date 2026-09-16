package com.felixbrucker.simklcalendar.data.util

import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFormatterTest {

    @Test
    fun testIsAnimeSeasonOne() {
        assertTrue(MediaFormatter.isAnimeSeasonOne(MediaType.ANIME, 1))
        assertFalse(MediaFormatter.isAnimeSeasonOne(MediaType.ANIME, 2))
        assertFalse(MediaFormatter.isAnimeSeasonOne(MediaType.TV, 1))
        assertFalse(MediaFormatter.isAnimeSeasonOne(MediaType.MOVIE, 1))
        assertFalse(MediaFormatter.isAnimeSeasonOne(MediaType.ANIME, null))
    }

    @Test
    fun testFormatEpisodeCode() {
        assertEquals("E05", MediaFormatter.formatEpisodeCode(MediaType.ANIME, 1, 5))
        assertEquals("S02E05", MediaFormatter.formatEpisodeCode(MediaType.ANIME, 2, 5))
        assertEquals("S01E05", MediaFormatter.formatEpisodeCode(MediaType.TV, 1, 5))
        assertEquals("S01E01", MediaFormatter.formatEpisodeCode(MediaType.TV, null, null))
    }

    @Test
    fun testFormatEpisodeCardBadge() {
        assertEquals("E05", MediaFormatter.formatEpisodeCardBadge(MediaType.ANIME, 1, 5))
        assertEquals("S02 • E05", MediaFormatter.formatEpisodeCardBadge(MediaType.ANIME, 2, 5))
        assertEquals("S01 • E05", MediaFormatter.formatEpisodeCardBadge(MediaType.TV, 1, 5))
        assertEquals("S01 • E01", MediaFormatter.formatEpisodeCardBadge(MediaType.TV, null, null))
    }

    @Test
    fun testFormatSeasonLabel() {
        assertEquals("Season", MediaFormatter.formatSeasonLabel(MediaType.ANIME, 1))
        assertEquals("Season 2", MediaFormatter.formatSeasonLabel(MediaType.ANIME, 2))
        assertEquals("Season 1", MediaFormatter.formatSeasonLabel(MediaType.TV, 1))
        assertEquals("Season 1", MediaFormatter.formatSeasonLabel(MediaType.TV, null))
    }

    @Test
    fun testFormatEpisodeSlugHeader() {
        assertEquals("Episode", MediaFormatter.formatEpisodeSlugHeader(MediaType.ANIME, 1))
        assertEquals("Season & Episode", MediaFormatter.formatEpisodeSlugHeader(MediaType.ANIME, 2))
        assertEquals("Season & Episode", MediaFormatter.formatEpisodeSlugHeader(MediaType.TV, 1))
    }

    @Test
    fun testFormatSimklUrl() {
        assertEquals("https://simkl.com/tv/123", MediaFormatter.formatSimklUrl(123, MediaType.TV))
        assertEquals("https://simkl.com/anime/456", MediaFormatter.formatSimklUrl(456, MediaType.ANIME))
        assertEquals("https://simkl.com/movies/789", MediaFormatter.formatSimklUrl(789, MediaType.MOVIE))
    }

    @Test
    fun testFormatEpisodeWatchedToast() {
        assertEquals(
            "Marked Naruto E05 as watched",
            MediaFormatter.formatEpisodeWatchedToast("Naruto", MediaType.ANIME, 1, 5)
        )
        assertEquals(
            "Marked Breaking Bad S01E05 as watched",
            MediaFormatter.formatEpisodeWatchedToast("Breaking Bad", MediaType.TV, 1, 5)
        )
        assertEquals(
            "Marked S01E05 as watched",
            MediaFormatter.formatEpisodeWatchedToast(null, MediaType.TV, 1, 5)
        )
    }

    @Test
    fun testFormatSeasonWatchedToast() {
        assertEquals(
            "Marked Naruto as watched",
            MediaFormatter.formatSeasonWatchedToast("Naruto", MediaType.ANIME, 1)
        )
        assertEquals(
            "Marked Breaking Bad Season 2 as watched",
            MediaFormatter.formatSeasonWatchedToast("Breaking Bad", MediaType.TV, 2)
        )
        assertEquals(
            "Marked anime as watched",
            MediaFormatter.formatSeasonWatchedToast(null, MediaType.ANIME, 1)
        )
        assertEquals(
            "Marked Season 2 as watched",
            MediaFormatter.formatSeasonWatchedToast(null, MediaType.TV, 2)
        )
        assertEquals(
            "Marked Breaking Bad Season 2 as watched (Show Completed)",
            MediaFormatter.formatSeasonWatchedToast("Breaking Bad", MediaType.TV, 2, isCompleted = true)
        )
    }

    @Test
    fun testFormatMovieWatchedToast() {
        assertEquals("Marked Inception as watched", MediaFormatter.formatMovieWatchedToast("Inception"))
        assertEquals("Marked Movie as watched", MediaFormatter.formatMovieWatchedToast(null))
        assertEquals("Marked Movie as watched", MediaFormatter.formatMovieWatchedToast(""))
    }

    @Test
    fun testFormatEpisodeUnwatchedToast() {
        assertEquals(
            "Marked Naruto E05 as unwatched",
            MediaFormatter.formatEpisodeUnwatchedToast("Naruto", MediaType.ANIME, 1, 5)
        )
        assertEquals(
            "Marked Breaking Bad S01E05 as unwatched",
            MediaFormatter.formatEpisodeUnwatchedToast("Breaking Bad", MediaType.TV, 1, 5)
        )
    }

    @Test
    fun testFormatSeasonUnwatchedToast() {
        assertEquals(
            "Marked Naruto as unwatched",
            MediaFormatter.formatSeasonUnwatchedToast("Naruto", MediaType.ANIME, 1)
        )
        assertEquals(
            "Marked Breaking Bad Season 2 as unwatched",
            MediaFormatter.formatSeasonUnwatchedToast("Breaking Bad", MediaType.TV, 2)
        )
    }

    @Test
    fun testFormatMovieUnwatchedToast() {
        assertEquals("Marked Inception as unwatched", MediaFormatter.formatMovieUnwatchedToast("Inception"))
        assertEquals("Marked Movie as unwatched", MediaFormatter.formatMovieUnwatchedToast(null))
    }

    @Test
    fun testFormatNotificationContentMovie() {
        val theater = MediaFormatter.formatNotificationContent(
            showTitle = "Avatar 3",
            type = MediaType.MOVIE,
            episodeTitle = null,
            season = null,
            episodeNumber = null,
            isFinale = false,
            movieReleaseType = MovieReleaseType.THEATER
        )
        assertEquals("Movie In Theaters Today", theater.first)
        assertEquals("Avatar 3 is now in theaters!", theater.second)

        val digital = MediaFormatter.formatNotificationContent(
            showTitle = "Avatar 3",
            type = MediaType.MOVIE,
            episodeTitle = null,
            season = null,
            episodeNumber = null,
            isFinale = false,
            movieReleaseType = MovieReleaseType.DIGITAL
        )
        assertEquals("Movie Released Today", digital.first)
        assertEquals("Avatar 3 is now available on Digital / DVD!", digital.second)
    }

    @Test
    fun testFormatNotificationContentFinale() {
        val (title1, msg1) = MediaFormatter.formatNotificationContent(
            showTitle = "Attack on Titan",
            type = MediaType.ANIME,
            episodeTitle = "The End",
            season = 1,
            episodeNumber = 12,
            isFinale = true,
            totalEpisodes = 12
        )
        assertEquals("Season finished airing", title1)
        assertEquals("Attack on Titan: 12 Episodes", msg1)

        val (title2, msg2) = MediaFormatter.formatNotificationContent(
            showTitle = "Loki",
            type = MediaType.TV,
            episodeTitle = "Glorious Purpose",
            season = 2,
            episodeNumber = 6,
            isFinale = true,
            totalEpisodes = 6
        )
        assertEquals("Season finished airing", title2)
        assertEquals("Loki S02: 6 Episodes", msg2)
    }

    @Test
    fun testFormatNotificationContentRegularEpisode() {
        val (title, msg) = MediaFormatter.formatNotificationContent(
            showTitle = "One Piece",
            type = MediaType.ANIME,
            episodeTitle = "A New Dawn",
            season = 1,
            episodeNumber = 1000,
            isFinale = false
        )
        assertEquals("New Episode Released", title)
        assertEquals("One Piece E1000: \"A New Dawn\" is now airing.", msg)
    }
}
