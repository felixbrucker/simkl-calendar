package com.felixbrucker.simklcalendar.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class StringExtensionsTest {

    @Test
    fun testToPosterUrlNull() {
        val compactUrl = null.toPosterUrl(PosterSize.COMPACT)
        val wideUrl = null.toPosterUrl(PosterSize.WIDE)

        assertEquals("https://simkl.in/poster_no_pic_c.png", compactUrl)
        assertEquals("https://simkl.in/poster_no_pic.png", wideUrl)
    }

    @Test
    fun testToPosterUrlWithValue() {
        val path = "12/34/5678"

        val compactUrl = path.toPosterUrl(PosterSize.COMPACT)
        val wideUrl = path.toPosterUrl(PosterSize.WIDE)

        assertEquals("https://simkl.in/posters/12/34/5678_c.webp", compactUrl)
        assertEquals("https://simkl.in/posters/12/34/5678_w.webp", wideUrl)
    }

    @Test
    fun testCleanedForUseAsPath() {
        val original = "Show: Subtitle | Episode 1"

        val cleaned = original.cleanedForUseAsPath()

        assertEquals("Show  Subtitle   Episode 1", cleaned)
    }
}
