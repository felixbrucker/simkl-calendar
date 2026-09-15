package com.felixbrucker.simklcalendar.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelsTest {

    @Test
    fun testExtractDigitalOrDvdReleaseDateUS() {
        val usResult = SimklMovieReleaseResult(type = 4, releaseDate = "2026-05-01")
        val usCountry = SimklMovieReleaseDateCountry(iso31661 = "US", results = listOf(usResult))
        val detail = SimklMovieDetailResponse(
            title = "Movie",
            poster = null,
            released = "2026-03-01",
            releaseDates = listOf(usCountry),
            ids = SimklIds(simkl = 1)
        )

        assertEquals("2026-05-01", detail.extractDigitalOrDvdReleaseDate())
    }

    @Test
    fun testExtractDigitalOrDvdReleaseDateFallback() {
        val ukResult = SimklMovieReleaseResult(type = 5, releaseDate = "2026-06-01")
        val ukCountry = SimklMovieReleaseDateCountry(iso31661 = "GB", results = listOf(ukResult))
        val detail = SimklMovieDetailResponse(
            title = "Movie",
            poster = null,
            released = "2026-03-01",
            releaseDates = listOf(ukCountry),
            ids = SimklIds(simkl = 1)
        )

        assertEquals("2026-06-01", detail.extractDigitalOrDvdReleaseDate())
    }

    @Test
    fun testExtractDigitalOrDvdReleaseDateNull() {
        val detail = SimklMovieDetailResponse(
            title = "Movie",
            poster = null,
            released = "2026-03-01",
            releaseDates = null,
            ids = SimklIds(simkl = 1)
        )

        assertNull(detail.extractDigitalOrDvdReleaseDate())
    }
}
