package com.felixbrucker.simklcalendar

import com.felixbrucker.simklcalendar.data.util.DateUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DateUtilTest {

    @Test
    fun testIsEarlierThanToday() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 8, 23)

        val yesterdayInstant = today.minusDays(1).atStartOfDay(zone).toInstant()
        val todayInstant = today.atStartOfDay(zone).toInstant()
        val tomorrowInstant = today.plusDays(1).atStartOfDay(zone).toInstant()

        assertTrue(DateUtil.isEarlierThanToday(yesterdayInstant, zone, today))
        assertFalse(DateUtil.isEarlierThanToday(todayInstant, zone, today))
        assertFalse(DateUtil.isEarlierThanToday(tomorrowInstant, zone, today))
    }

    @Test
    fun testFormatAiringDateHeader() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 8, 23)

        val todayInstant = today.atStartOfDay(zone).toInstant()
        val tomorrowInstant = today.plusDays(1).atStartOfDay(zone).toInstant()
        val yesterdayInstant = today.minusDays(1).atStartOfDay(zone).toInstant()

        val todayHeader = DateUtil.formatAiringDateHeader(todayInstant, zone, today)
        val tomorrowHeader = DateUtil.formatAiringDateHeader(tomorrowInstant, zone, today)
        val yesterdayHeader = DateUtil.formatAiringDateHeader(yesterdayInstant, zone, today)

        assertTrue(todayHeader.startsWith("TODAY -"))
        assertTrue(tomorrowHeader.startsWith("TOMORROW -"))
        assertTrue(yesterdayHeader.startsWith("YESTERDAY -"))
    }

    @Test
    fun testFormatAiringDateHeaderWithLocalDate() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 8, 23)
        val tomorrow = today.plusDays(1)
        val yesterday = today.minusDays(1)
        val nextYear = LocalDate.of(2027, 1, 1)

        val todayHeader = DateUtil.formatAiringDateHeader(today, zone, today)
        val tomorrowHeader = DateUtil.formatAiringDateHeader(tomorrow, zone, today)
        val yesterdayHeader = DateUtil.formatAiringDateHeader(yesterday, zone, today)
        val nextYearHeader = DateUtil.formatAiringDateHeader(nextYear, zone, today)

        assertTrue(todayHeader.startsWith("TODAY -"))
        assertTrue(tomorrowHeader.startsWith("TOMORROW -"))
        assertTrue(yesterdayHeader.startsWith("YESTERDAY -"))
        assertTrue(nextYearHeader.contains("2027"))
    }

    @Test
    fun testFormatLocalizedTime() {
        val instant = Instant.parse("2026-08-23T16:30:00Z")
        val formatted = DateUtil.formatLocalizedTime(instant, isDateOnly = false)
        assertNotNull(formatted)

        val nullFormatted = DateUtil.formatLocalizedTime(instant, isDateOnly = true)
        assertNull(nullFormatted)
    }

    @Test
    fun testParseToInstant() {
        val instant = DateUtil.parseToInstant("2026-08-23T20:30:00Z")
        assertNotNull(instant)
        assertEquals(Instant.parse("2026-08-23T20:30:00Z"), instant)
    }

    @Test
    fun testFormatDuration() {
        assertEquals("1d 2h 3m 4s", DateUtil.formatDuration(86400 + 7200 + 180 + 4))
        assertEquals("0s", DateUtil.formatDuration(0))
    }
}
