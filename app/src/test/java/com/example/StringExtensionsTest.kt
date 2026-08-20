package com.example

import com.example.data.util.PosterSize
import com.example.data.util.toPosterUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class StringExtensionsTest {

    @Test
    fun testPosterUrlWithRawFragment() {
        val rawFragment = "73/732890471b0a88019b"
        val compactUrl = rawFragment.toPosterUrl(PosterSize.COMPACT)
        val wideUrl = rawFragment.toPosterUrl(PosterSize.WIDE)

        assertEquals("https://simkl.in/posters/73/732890471b0a88019b_c.webp", compactUrl)
        assertEquals("https://simkl.in/posters/73/732890471b0a88019b_w.webp", wideUrl)
    }

    @Test
    fun testPosterUrlWithNull() {
        val nullString: String? = null

        assertEquals("https://simkl.in/poster_no_pic_c.png", nullString.toPosterUrl(PosterSize.COMPACT))
        assertEquals("https://simkl.in/poster_no_pic.png", nullString.toPosterUrl(PosterSize.WIDE))
    }
}
