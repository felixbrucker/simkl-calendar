package com.example

import com.example.data.model.MediaType
import com.example.data.util.DateUtil
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class DateUtilTest {

    @Test
    fun testParseIso8601WithZulu() {
        val iso = "2026-08-22T20:30:00Z"
        val instant = DateUtil.parseToInstant(iso)
        assertNotNull(instant)
        assertEquals(Instant.parse("2026-08-22T20:30:00Z"), instant)
    }

    @Test
    fun testParseIso8601WithOffset() {
        val iso = "2026-08-22T20:30:00+00:00"
        val instant = DateUtil.parseToInstant(iso)
        assertNotNull(instant)
        assertEquals(Instant.parse("2026-08-22T20:30:00Z"), instant)
    }

    @Test
    fun testParseMovieDvdDateOnly() {
        val dvdDate = "2026-08-22"
        val instant = DateUtil.parseToInstant(dvdDate)
        assertNotNull(instant)
        val localDate = instant!!.atZone(ZoneId.systemDefault()).toLocalDate()
        assertEquals(LocalDate.of(2026, 8, 22), localDate)
    }

    @Test
    fun testLocalTimezoneShiftToNextDay() {
        val originalTz = TimeZone.getDefault()
        try {
            // Set timezone to Tokyo (UTC+9)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))

            val utcTime = "2026-08-22T20:30:00Z"
            val instant = DateUtil.parseToInstant(utcTime)
            assertNotNull(instant)

            val localZoned = instant!!.atZone(ZoneId.systemDefault())
            assertEquals(2026, localZoned.year)
            assertEquals(8, localZoned.monthValue)
            assertEquals(23, localZoned.dayOfMonth) // Should be the 23rd in Tokyo!
            assertEquals(5, localZoned.hour)
            assertEquals(30, localZoned.minute)

            val header = DateUtil.formatAiringDateHeader(instant)
            assertTrue("Header should contain 23: $header", header.contains("23") || header.contains("AUGUST 23"))

            // Verify 24h format in Tokyo (20:30 UTC -> 05:30 JST)
            val formattedTime = DateUtil.formatLocalizedTime(instant)
            assertEquals("05:30", formattedTime)
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun test24HourTimeFormatting() {
        val originalTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

            val instantEvening = Instant.parse("2026-08-22T20:45:00Z")
            assertEquals("20:45", DateUtil.formatLocalizedTime(instantEvening))
            assertFalse(DateUtil.formatLocalizedTime(instantEvening)!!.contains("PM", ignoreCase = true))

            val instantMorning = Instant.parse("2026-08-22T08:05:00Z")
            assertEquals("08:05", DateUtil.formatLocalizedTime(instantMorning))
            assertFalse(DateUtil.formatLocalizedTime(instantMorning)!!.contains("AM", ignoreCase = true))

            val instantMidnight = Instant.parse("2026-08-22T00:15:00Z")
            assertEquals("00:15", DateUtil.formatLocalizedTime(instantMidnight))
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun testNotificationContentFormatting() {
        val tvFinaleItem = com.example.data.database.CalendarItem(
            primaryKey = "key_1",
            simklId = 101,
            title = "Succession",
            episodeTitle = "With Open Eyes",
            season = 4,
            episodeNumber = 10,
            date = Instant.parse("2026-08-22T20:00:00Z"),
            type = MediaType.TV,
            isSeasonPremiere = false,
            isSeasonFinale = true,
            poster = null,
            isLastEpisode = true
        )

        val (tvTitle, tvMsg) = com.example.receiver.NotificationReceiver.formatNotificationContent(
            item = tvFinaleItem,
            isFinale = true,
            totalEpisodes = 10
        )
        assertEquals("Season finished airing", tvTitle)
        assertEquals("Succession (Season 4, 10 Episodes)", tvMsg)
        assertFalse("Finale message should not include episode number E10", tvMsg.contains("E10"))
        assertFalse("Finale message should not include ready to binge", tvMsg.contains("binge", ignoreCase = true))

        val animeFinaleItem = com.example.data.database.CalendarItem(
            primaryKey = "key_2",
            simklId = 202,
            title = "Demon Slayer",
            episodeTitle = "Hashira",
            season = 4,
            episodeNumber = 8,
            date = Instant.parse("2026-08-22T20:00:00Z"),
            type = MediaType.ANIME,
            isSeasonPremiere = false,
            isSeasonFinale = true,
            poster = null,
            isLastEpisode = true
        )

        val (animeTitle, animeMsg) = com.example.receiver.NotificationReceiver.formatNotificationContent(
            item = animeFinaleItem,
            isFinale = true,
            totalEpisodes = 8
        )
        assertEquals("Season finished airing", animeTitle)
        assertEquals("Demon Slayer (8 Episodes)", animeMsg)
        assertFalse("Anime message should not include season number", animeMsg.contains("Season"))
        assertFalse("Anime message should not include ready to binge", animeMsg.contains("binge", ignoreCase = true))

        val movieTheaterItem = com.example.data.database.CalendarItem(
            primaryKey = "key_3",
            simklId = 303,
            title = "Dune: Part Two",
            episodeTitle = null,
            season = null,
            episodeNumber = null,
            date = Instant.parse("2026-08-22T20:00:00Z"),
            type = MediaType.MOVIE,
            movieReleaseType = com.example.data.model.MovieReleaseType.THEATER,
            isSeasonPremiere = false,
            isSeasonFinale = false,
            poster = null,
            isLastEpisode = false
        )

        val (movieTheaterTitle, movieTheaterMsg) = com.example.receiver.NotificationReceiver.formatNotificationContent(
            item = movieTheaterItem,
            isFinale = false,
            totalEpisodes = null
        )
        assertEquals("Movie In Theaters Today", movieTheaterTitle)
        assertEquals("Dune: Part Two is now playing in theaters!", movieTheaterMsg)

        val movieDigitalItem = com.example.data.database.CalendarItem(
            primaryKey = "key_4",
            simklId = 303,
            title = "Dune: Part Two",
            episodeTitle = null,
            season = null,
            episodeNumber = null,
            date = Instant.parse("2026-08-22T20:00:00Z"),
            type = MediaType.MOVIE,
            movieReleaseType = com.example.data.model.MovieReleaseType.DIGITAL,
            isSeasonPremiere = false,
            isSeasonFinale = false,
            poster = null,
            isLastEpisode = false
        )

        val (movieDigitalTitle, movieDigitalMsg) = com.example.receiver.NotificationReceiver.formatNotificationContent(
            item = movieDigitalItem,
            isFinale = false,
            totalEpisodes = null
        )
        assertEquals("Movie Released Today", movieDigitalTitle)
        assertEquals("Dune: Part Two is now available on Digital / DVD!", movieDigitalMsg)
    }

    @Test
    fun testSimklMovieDetailReleaseDatesExtraction() {
        val detail = com.example.data.network.SimklMovieDetailResponse(
            title = "Inception",
            ids = com.example.data.network.SimklIds(simkl = 12345),
            releaseDates = listOf(
                com.example.data.network.SimklMovieReleaseDateCountry(
                    iso31661 = "US",
                    results = listOf(
                        com.example.data.network.SimklMovieReleaseResult(type = 1, releaseDate = "2010-07-13"),
                        com.example.data.network.SimklMovieReleaseResult(type = 3, releaseDate = "2010-07-16"),
                        com.example.data.network.SimklMovieReleaseResult(type = 5, releaseDate = "2010-12-07")
                    )
                ),
                com.example.data.network.SimklMovieReleaseDateCountry(
                    iso31661 = "GB",
                    results = listOf(
                        com.example.data.network.SimklMovieReleaseResult(type = 1, releaseDate = "2010-07-08"),
                        com.example.data.network.SimklMovieReleaseResult(type = 3, releaseDate = "2010-07-16")
                    )
                )
            )
        )

        val extractedDate = detail.extractDigitalOrDvdReleaseDate()
        assertEquals("2010-12-07", extractedDate)

        val tvMovieDetail = com.example.data.network.SimklMovieDetailResponse(
            title = "TV Film",
            ids = com.example.data.network.SimklIds(simkl = 67890),
            releaseDates = listOf(
                com.example.data.network.SimklMovieReleaseDateCountry(
                    iso31661 = "US",
                    results = listOf(
                        com.example.data.network.SimklMovieReleaseResult(type = 6, releaseDate = "2024-05-01")
                    )
                )
            )
        )
        assertEquals("2024-05-01", tvMovieDetail.extractDigitalOrDvdReleaseDate())
    }
}
