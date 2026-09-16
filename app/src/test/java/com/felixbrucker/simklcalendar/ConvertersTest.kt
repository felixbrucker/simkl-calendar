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

        val convertedTimestamp = converters.dateToTimestamp(now)
        val convertedDate = converters.fromTimestamp(epoch)
        val nullTimestamp = converters.dateToTimestamp(null)
        val nullDate = converters.fromTimestamp(null)

        assertEquals(epoch, convertedTimestamp)
        assertEquals(now.toEpochMilli(), convertedDate?.toEpochMilli())
        assertNull(nullTimestamp)
        assertNull(nullDate)
    }

    @Test
    fun testMediaTypeConverters() {
        val strMediaType = converters.fromMediaType(MediaType.TV)
        val objMediaType = converters.toMediaType("tv")
        val nullStrMediaType = converters.fromMediaType(null)
        val nullObjMediaType = converters.toMediaType(null)

        assertEquals("tv", strMediaType)
        assertEquals(MediaType.TV, objMediaType)
        assertNull(nullStrMediaType)
        assertNull(nullObjMediaType)
    }

    @Test
    fun testMovieReleaseTypeConverters() {
        val strReleaseType = converters.fromMovieReleaseType(MovieReleaseType.THEATER)
        val objReleaseType = converters.toMovieReleaseType("THEATER")
        val nullStrReleaseType = converters.fromMovieReleaseType(null)
        val invalidObjReleaseType = converters.toMovieReleaseType("INVALID")
        val nullObjReleaseType = converters.toMovieReleaseType(null)

        assertEquals("THEATER", strReleaseType)
        assertEquals(MovieReleaseType.THEATER, objReleaseType)
        assertNull(nullStrReleaseType)
        assertNull(invalidObjReleaseType)
        assertNull(nullObjReleaseType)
    }

    @Test
    fun testMediaTypeListConverters() {
        val list = listOf(MediaType.TV, MediaType.ANIME)
        val strList = converters.fromMediaTypeList(list)

        val parsedList = converters.toMediaTypeList(strList)
        val nullStrList = converters.fromMediaTypeList(null)
        val nullParsedList = converters.toMediaTypeList(null)
        val emptyParsedList = converters.toMediaTypeList("")

        assertEquals("tv,anime", strList)
        assertEquals(list, parsedList)
        assertNull(nullStrList)
        assertEquals(emptyList<MediaType>(), nullParsedList)
        assertEquals(emptyList<MediaType>(), emptyParsedList)
    }

    @Test
    fun testSeasonOverridesConverters() {
        val map = mapOf(1 to 2, 2 to 3)
        val strMap = converters.fromSeasonOverrides(map)

        val parsedMap = converters.toSeasonOverrides(strMap)
        val nullStrMap = converters.fromSeasonOverrides(null)
        val nullParsedMap = converters.toSeasonOverrides(null)
        val invalidParsedMap = converters.toSeasonOverrides("invalid")

        assertEquals("1:2,2:3", strMap)
        assertEquals(map, parsedMap)
        assertNull(nullStrMap)
        assertNull(nullParsedMap)
        assertNull(invalidParsedMap)
    }

    @Test
    fun testMediaStatusConverters() {
        val strStatus = converters.fromMediaStatus(MediaStatus.WANTED)
        val objStatus = converters.toMediaStatus("WANTED")
        val nullStrStatus = converters.fromMediaStatus(null)
        val nullObjStatus = converters.toMediaStatus(null)

        assertEquals("WANTED", strStatus)
        assertEquals(MediaStatus.WANTED, objStatus)
        assertNull(nullStrStatus)
        assertNull(nullObjStatus)
    }
}
