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

        val expected = "https://nyaa.si/?f=0&c=0_0&q=Attack+on+Titan+S02S02E05"

        assertEquals(expected, url)
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

        val expected = "https://test.com/search?title=Inception&romaji=Inception&s=&e="

        assertEquals(expected, url)
    }

    @Test
    fun testBuildUrlCaseInsensitiveAndAllTokens() {
        val link = CustomSearchLink(
            id = 4,
            name = "All Tokens Search",
            urlTemplate = "https://test.com/search?t={title}&te={title_url_encoded}&r={title_romaji}&re={title_romaji_url_encoded}&s={season}&e={episode}&ss={season_slug}&es={episode_slug}",
            position = 0
        )

        val url = link.buildUrl(
            title = "My Show & More",
            titleRomaji = "Boku no Show & More",
            type = MediaType.TV,
            season = 3,
            episode = 12
        )

        val expected = "https://test.com/search?t=My Show & More&te=My+Show+%26+More&r=Boku no Show & More&re=Boku+no+Show+%26+More&s=3&e=12&ss=S03&es=S03E12"

        assertEquals(expected, url)
    }

    @Test
    fun testBuildUrlEdgeCasesUnclosedAndUnknownBraces() {
        val link = CustomSearchLink(
            id = 5,
            name = "Edge Case Search",
            urlTemplate = "https://test.com/search?q={title}&unknown={UNKNOWN_TOKEN}&unclosed={UNCLOSED",
            position = 0
        )

        val url = link.buildUrl(
            title = "Test",
            titleRomaji = null,
            type = MediaType.TV,
            season = 1,
            episode = 1
        )

        val expected = "https://test.com/search?q=Test&unknown={UNKNOWN_TOKEN}&unclosed={UNCLOSED"

        assertEquals(expected, url)
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

        val expectedDomain = "www.google.com"
        val expectedFaviconUrl = "https://www.google.com/s2/favicons?domain=www.google.com&sz=64"

        assertEquals(expectedDomain, domain)
        assertEquals(expectedFaviconUrl, faviconUrl)
    }
}
