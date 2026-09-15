package com.felixbrucker.simklcalendar.data.database

import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun testTimestampConverters() {
        val now = Instant.now()
        val epoch = now.toEpochMilli()

        assertEquals(epoch, converters.dateToTimestamp(now))
        assertEquals(now.toEpochMilli(), converters.fromTimestamp(epoch)?.toEpochMilli())

        assertNull(converters.dateToTimestamp(null))
        assertNull(converters.fromTimestamp(null))
    }

    @Test
    fun testMediaTypeConverters() {
        assertEquals("tv", converters.fromMediaType(MediaType.TV))
        assertEquals(MediaType.TV, converters.toMediaType("tv"))
        assertNull(converters.fromMediaType(null))
        assertNull(converters.toMediaType(null))
    }

    @Test
    fun testMovieReleaseTypeConverters() {
        assertEquals("THEATER", converters.fromMovieReleaseType(MovieReleaseType.THEATER))
        assertEquals(MovieReleaseType.THEATER, converters.toMovieReleaseType("THEATER"))
        assertNull(converters.fromMovieReleaseType(null))
        assertNull(converters.toMovieReleaseType("INVALID"))
        assertNull(converters.toMovieReleaseType(null))
    }

    @Test
    fun testMediaTypeListConverters() {
        val list = listOf(MediaType.TV, MediaType.ANIME)
        val str = converters.fromMediaTypeList(list)
        assertEquals("tv,anime", str)

        val parsed = converters.toMediaTypeList(str)
        assertEquals(list, parsed)

        assertNull(converters.fromMediaTypeList(null))
        assertEquals(emptyList<MediaType>(), converters.toMediaTypeList(null))
        assertEquals(emptyList<MediaType>(), converters.toMediaTypeList(""))
    }

    @Test
    fun testSeasonOverridesConverters() {
        val map = mapOf(1 to 2, 2 to 3)
        val str = converters.fromSeasonOverrides(map)
        assertEquals("1:2,2:3", str)

        val parsed = converters.toSeasonOverrides(str)
        assertEquals(map, parsed)

        assertNull(converters.fromSeasonOverrides(null))
        assertNull(converters.toSeasonOverrides(null))
        assertNull(converters.toSeasonOverrides("invalid"))
    }

    @Test
    fun testMediaStatusConverters() {
        assertEquals("WANTED", converters.fromMediaStatus(MediaStatus.WANTED))
        assertEquals(MediaStatus.WANTED, converters.toMediaStatus("WANTED"))
        assertNull(converters.fromMediaStatus(null))
        assertNull(converters.toMediaStatus(null))
    }
}
