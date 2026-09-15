package com.felixbrucker.simklcalendar.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class StringExtensionsTest {

    @Test
    fun testToPosterUrlNull() {
        assertEquals("https://simkl.in/poster_no_pic_c.png", null.toPosterUrl(PosterSize.COMPACT))
        assertEquals("https://simkl.in/poster_no_pic.png", null.toPosterUrl(PosterSize.WIDE))
    }

    @Test
    fun testToPosterUrlWithValue() {
        val path = "12/34/5678"
        assertEquals("https://simkl.in/posters/12/34/5678_c.webp", path.toPosterUrl(PosterSize.COMPACT))
        assertEquals("https://simkl.in/posters/12/34/5678_w.webp", path.toPosterUrl(PosterSize.WIDE))
    }

    @Test
    fun testCleanedForUseAsPath() {
        val original = "Show: Subtitle | Episode 1"
        val cleaned = original.cleanedForUseAsPath()
        assertEquals("Show  Subtitle   Episode 1", cleaned)
    }
}
