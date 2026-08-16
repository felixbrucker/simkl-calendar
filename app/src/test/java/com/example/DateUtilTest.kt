package com.example

import com.example.data.util.DateUtil
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class DateUtilTest {

    @Test
    fun testParseIso8601WithZulu() {
        val iso = "2026-08-22T20:30:00Z"
        val instant = DateUtil.parseToInstant(iso)
        assertNotNull(instant)
        assertEquals(Instant.parse("2026-08-22T20:30:00Z"), instant)
    }

    @Test
    fun testParseIso8601WithOffset() {
        val iso = "2026-08-22T20:30:00+00:00"
        val instant = DateUtil.parseToInstant(iso)
        assertNotNull(instant)
        assertEquals(Instant.parse("2026-08-22T20:30:00Z"), instant)
    }

    @Test
    fun testParseMovieDvdDateOnly() {
        val dvdDate = "2026-08-22"
        val instant = DateUtil.parseToInstant(dvdDate)
        assertNotNull(instant)
        val localDate = instant!!.atZone(ZoneId.systemDefault()).toLocalDate()
        assertEquals(LocalDate.of(2026, 8, 22), localDate)
    }

    @Test
    fun testLocalTimezoneShiftToNextDay() {
        val originalTz = TimeZone.getDefault()
        try {
            // Set timezone to Tokyo (UTC+9)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))

            val utcTime = "2026-08-22T20:30:00Z"
            val instant = DateUtil.parseToInstant(utcTime)
            assertNotNull(instant)

            val localZoned = instant!!.atZone(ZoneId.systemDefault())
            assertEquals(2026, localZoned.year)
            assertEquals(8, localZoned.monthValue)
            assertEquals(23, localZoned.dayOfMonth) // Should be the 23rd in Tokyo!
            assertEquals(5, localZoned.hour)
            assertEquals(30, localZoned.minute)

            val header = DateUtil.formatAiringDateHeader(instant)
            assertTrue("Header should contain 23: $header", header.contains("23") || header.contains("AUGUST 23"))

            // Verify 24h format in Tokyo (20:30 UTC -> 05:30 JST)
            val formattedTime = DateUtil.formatLocalizedTime(instant)
            assertEquals("05:30", formattedTime)
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun test24HourTimeFormatting() {
        val originalTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

            val instantEvening = Instant.parse("2026-08-22T20:45:00Z")
            assertEquals("20:45", DateUtil.formatLocalizedTime(instantEvening))
            assertFalse(DateUtil.formatLocalizedTime(instantEvening)!!.contains("PM", ignoreCase = true))

            val instantMorning = Instant.parse("2026-08-22T08:05:00Z")
            assertEquals("08:05", DateUtil.formatLocalizedTime(instantMorning))
            assertFalse(DateUtil.formatLocalizedTime(instantMorning)!!.contains("AM", ignoreCase = true))

            val instantMidnight = Instant.parse("2026-08-22T00:15:00Z")
            assertEquals("00:15", DateUtil.formatLocalizedTime(instantMidnight))
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }
}
