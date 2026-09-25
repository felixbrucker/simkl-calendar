package com.felixbrucker.simklcalendar

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityNavigationTest {

    @Test
    fun testPopBackStackSafelyReturnsFalseWhenCurrentBackStackEntryIsNull() {
        val navController = mockk<NavController>(relaxed = true)
        every { navController.currentBackStackEntry } returns null

        val result = navController.popBackStackSafely()

        assertFalse(result)
    }

    @Test
    fun testPopBackStackSafelyReturnsFalseWhenCurrentEntryIsNotResumed() {
        val navController = mockk<NavController>(relaxed = true)
        val currentEntry = mockk<NavBackStackEntry>(relaxed = true)
        every { navController.currentBackStackEntry } returns currentEntry
        every { currentEntry.lifecycle.currentState } returns Lifecycle.State.STARTED

        val result = navController.popBackStackSafely()

        assertFalse(result)
        verify(exactly = 0) { navController.popBackStack() }
    }

    @Test
    fun testPopBackStackSafelyReturnsFalseWhenPreviousBackStackEntryIsNull() {
        val navController = mockk<NavController>(relaxed = true)
        val currentEntry = mockk<NavBackStackEntry>(relaxed = true)
        every { navController.currentBackStackEntry } returns currentEntry
        every { currentEntry.lifecycle.currentState } returns Lifecycle.State.RESUMED
        every { navController.previousBackStackEntry } returns null

        val result = navController.popBackStackSafely()

        assertFalse(result)
        verify(exactly = 0) { navController.popBackStack() }
    }

    @Test
    fun testPopBackStackSafelyPopsBackStackWhenResumedAndPreviousEntryExists() {
        val navController = mockk<NavController>(relaxed = true)
        val currentEntry = mockk<NavBackStackEntry>(relaxed = true)
        val previousEntry = mockk<NavBackStackEntry>(relaxed = true)
        every { navController.currentBackStackEntry } returns currentEntry
        every { currentEntry.lifecycle.currentState } returns Lifecycle.State.RESUMED
        every { navController.previousBackStackEntry } returns previousEntry
        every { navController.popBackStack() } returns true

        val result = navController.popBackStackSafely()

        assertTrue(result)
        verify(exactly = 1) { navController.popBackStack() }
    }
}
