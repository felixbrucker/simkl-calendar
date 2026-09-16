package com.felixbrucker.simklcalendar.data.util

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenceExtensionsTest {

    @Test
    fun testGetStringListNullReturnsDefault() {
        val prefs = mockk<SharedPreferences>()
        every { prefs.getString("key", null) } returns null

        val defaultList = listOf("a", "b")
        val result = prefs.getStringList("key", defaultList)
        assertEquals(defaultList, result)
    }

    @Test
    fun testGetStringListParsed() {
        val prefs = mockk<SharedPreferences>()
        every { prefs.getString("key", null) } returns "item1\nitem2\n\nitem3 "

        val result = prefs.getStringList("key")
        assertEquals(listOf("item1", "item2", "item3 "), result)
    }

    @Test
    fun testPutStringList() {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val keySlot = slot<String>()
        val valueSlot = slot<String>()

        every { editor.putString(capture(keySlot), capture(valueSlot)) } returns editor

        editor.putStringList("key", listOf("1", "2", "3"))

        verify { editor.putString("key", "1\n2\n3") }
        assertEquals("key", keySlot.captured)
        assertEquals("1\n2\n3", valueSlot.captured)
    }

    @Test
    fun testGetStringListWithMigrationLegacySet() {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)

        every { prefs.getString("key", null) } throws ClassCastException()
        every { prefs.getStringSet("key", null) } returns setOf("item1", "item2")
        every { prefs.edit() } returns editor

        val result = prefs.getStringListWithMigration("key")
        assertEquals(2, result.size)
        assertEquals(setOf("item1", "item2"), result.toSet())
    }
}
