package com.felixbrucker.simklcalendar.data.util

import com.felixbrucker.simklcalendar.extensions.toAnimeSeasonTokens
import com.felixbrucker.simklcalendar.extensions.toOrdinal
import com.felixbrucker.simklcalendar.extensions.toRomanNumeral
import org.junit.Assert.assertEquals
import org.junit.Test

class IntExtensionsTest {

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
    fun testToAnimeSeasonTokens() {
        val season = 4

        val formattedTokens = season.toAnimeSeasonTokens()

        assertEquals("(S4 | 4th | IV)", formattedTokens)
    }
}
