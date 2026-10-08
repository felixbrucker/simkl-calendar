package com.felixbrucker.simklcalendar.data.util

import com.felixbrucker.simklcalendar.extensions.cleanedForUseAsPath
import com.felixbrucker.simklcalendar.extensions.formatAnimeSeasonTokens
import com.felixbrucker.simklcalendar.extensions.toNormalizedAnimeTitle
import com.felixbrucker.simklcalendar.extensions.toOrdinal
import com.felixbrucker.simklcalendar.extensions.toPosterUrl
import com.felixbrucker.simklcalendar.extensions.toRomanNumeral
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

        assertEquals("Show Subtitle Episode 1", cleaned)
    }

    @Test
    fun testCleanedForUseAsPathWithMultipleWhitespaces() {
        val original = "Show    Subtitle \t\n Episode   1"

        val cleaned = original.cleanedForUseAsPath()

        assertEquals("Show Subtitle Episode 1", cleaned)
    }

    @Test
    fun testToNormalizedAnimeTitleExclamationAndDashes() {
        val original = "Mairimashita! Iruma-kun"

        val normalized = original.toNormalizedAnimeTitle()

        assertEquals("Mairimashita Iruma-kun", normalized)
    }

    @Test
    fun testToNormalizedAnimeTitleColonsSlashesAndTildes() {
        val original = "Fate/stay night: ~Unlimited Blade Works~"

        val normalized = original.toNormalizedAnimeTitle()

        assertEquals("Fate stay night Unlimited Blade Works", normalized)
    }

    @Test
    fun testToNormalizedAnimeTitleSymbolsAndQuotesBrackets() {
        val original = "☆Tensei☆ 'Shitara' Slime Datta [Ken] ♪"

        val normalized = original.toNormalizedAnimeTitle()

        assertEquals("Tensei Shitara Slime Datta Ken", normalized)
    }

    @Test
    fun testToNormalizedAnimeTitleDropsStandaloneJapaneseParticles() {
        val original = "Dungeon ni Deai wo Motomeru no wa Machigatteiru Darou ka"

        val normalized = original.toNormalizedAnimeTitle()

        assertEquals("Dungeon Deai Motomeru Machigatteiru Darou", normalized)
    }

    @Test
    fun testToNormalizedAnimeTitleParticleBokuNoHeroAcademia() {
        val original = "Boku no Hero Academia"

        val normalized = original.toNormalizedAnimeTitle()

        assertEquals("Boku Hero Academia", normalized)
    }

    @Test
    fun testToNormalizedAnimeTitleCleansExtraWhitespace() {
        val original = "  Mairimashita!   \t Iruma-kun   "

        val normalized = original.toNormalizedAnimeTitle()

        assertEquals("Mairimashita Iruma-kun", normalized)
    }

    @Test
    fun testToNormalizedAnimeTitleFallbackWhenOnlyParticles() {
        val original = "No"

        val normalized = original.toNormalizedAnimeTitle()

        assertEquals("No", normalized)
    }

    @Test
    fun testToNormalizedAnimeTitle() {
        val originalToExpected = listOf(
            "Honzuki no Gekokujou: Shisho ni Naru Tame ni wa Shudan o Erande Iraremasen - Ryoushu no Youjo" to "Honzuki Gekokujou Shisho Naru Tame Shudan Erande Iraremasen Ryoushu Youjo",
            "Re:Zero kara Hajimeru Isekai Seikatsu" to "Re Zero Hajimeru Isekai Seikatsu",
            "Buchigire Reijou wa Houfuku o Chikaimashita. Madousho no Chikara de Sokoku o Tatakitsubushimasu" to "Buchigire Reijou Houfuku Chikaimashita Madousho Chikara Sokoku Tatakitsubushimasu",
            "Saikyou Degarashi Ouji no An'yaku Teii Arasoi: Munou o Enjiru SS Rank Ouji wa Koui Keishou-sen o Kage kara Shihai Suru" to "Saikyou Degarashi Ouji Anyaku Teii Arasoi Munou Enjiru SS Rank Ouji Koui Keishou-sen Kage Shihai Suru",
        )

        for ((original, expected) in originalToExpected) {
            val normalized = original.toNormalizedAnimeTitle()
            assertEquals(expected, normalized)
        }
    }

    @Test
    fun testToOrdinal() {
        val inputList = listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 23)

        val resultList = inputList.map { it.toOrdinal() }

        assertEquals(
            listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd"),
            resultList
        )
    }

    @Test
    fun testToRomanNumeral() {
        val inputList = listOf(1, 2, 3, 4, 5, 9, 10, 12)

        val resultList = inputList.map { it.toRomanNumeral() }

        assertEquals(
            listOf("I", "II", "III", "IV", "V", "IX", "X", "XII"),
            resultList
        )
    }

    @Test
    fun testFormatAnimeSeasonTokens() {
        val season = 4

        val formattedTokens = formatAnimeSeasonTokens(season)

        assertEquals("(S4 | 4th | IV)", formattedTokens)
    }
}
