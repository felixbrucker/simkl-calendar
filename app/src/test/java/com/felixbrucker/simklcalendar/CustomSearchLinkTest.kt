package com.felixbrucker.simklcalendar.data.database

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CustomSearchLinkTest {

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        val uri = mockk<Uri>()
        every { uri.host } returns "www.google.com"
        every { Uri.parse(any()) } returns uri
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun testBuildUrlWithPlaceholders() {
        val link = CustomSearchLink(
            id = 1,
            name = "Nyaa Search",
            urlTemplate = "https://nyaa.si/?f=0&c=0_0&q={TITLE_URL_ENCODED}+{SEASON_SLUG}{EPISODE_SLUG}",
            position = 0
        )

        val url = link.buildUrl(
            title = "Attack on Titan",
            titleRomaji = "Shingeki no Kyojin",
            type = com.felixbrucker.simklcalendar.data.model.MediaType.ANIME,
            season = 2,
            episode = 5
        )

        assertEquals("https://nyaa.si/?f=0&c=0_0&q=Attack+on+Titan+S02S02E05", url)
    }

    @Test
    fun testExtractDomainAndFavicon() {
        val link = CustomSearchLink(
            id = 2,
            name = "Google",
            urlTemplate = "https://www.google.com/search?q={TITLE}",
            position = 1
        )

        assertEquals("www.google.com", link.extractDomain())
        assertEquals("https://www.google.com/s2/favicons?domain=www.google.com&sz=64", link.getFaviconUrl())
    }
}
