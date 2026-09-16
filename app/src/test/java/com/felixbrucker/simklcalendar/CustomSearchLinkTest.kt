package com.felixbrucker.simklcalendar.data.database

import android.net.Uri
import com.felixbrucker.simklcalendar.data.model.MediaType
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
            type = MediaType.ANIME,
            season = 2,
            episode = 5
        )

        assertEquals("https://nyaa.si/?f=0&c=0_0&q=Attack+on+Titan+S02S02E05", url)
    }

    @Test
    fun testBuildUrlForMovieAndCustomTokens() {
        val link = CustomSearchLink(
            id = 2,
            name = "Movie Search",
            urlTemplate = "test.com/search?title={TITLE}&romaji={TITLE_ROMAJI}&s={SEASON}&e={EPISODE}",
            position = 1
        )

        val url = link.buildUrl(
            title = "Inception",
            titleRomaji = null,
            type = MediaType.MOVIE,
            season = null,
            episode = null
        )

        assertEquals("https://test.com/search?title=Inception&romaji=Inception&s=&e=", url)
    }

    @Test
    fun testExtractDomainAndFavicon() {
        val link = CustomSearchLink(
            id = 3,
            name = "Google",
            urlTemplate = "https://www.google.com/search?q={TITLE}",
            position = 1
        )

        val domain = link.extractDomain()
        val faviconUrl = link.getFaviconUrl()

        assertEquals("www.google.com", domain)
        assertEquals("https://www.google.com/s2/favicons?domain=www.google.com&sz=64", faviconUrl)
    }
}
