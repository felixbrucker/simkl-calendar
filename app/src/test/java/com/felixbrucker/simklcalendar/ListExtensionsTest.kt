package com.felixbrucker.simklcalendar.data.util

import com.felixbrucker.simklcalendar.extensions.ensureAdded
import org.junit.Assert.assertEquals
import org.junit.Test

class ListExtensionsTest {

    @Test
    fun testEnsureAdded() {
        val list = mutableListOf("apple", "banana")
        list.ensureAdded("banana", "cherry", "date")

        assertEquals(4, list.size)
        assertEquals(listOf("apple", "banana", "cherry", "date"), list)
    }
}
