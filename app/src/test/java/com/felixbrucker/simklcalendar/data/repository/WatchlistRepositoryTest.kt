package com.felixbrucker.simklcalendar.data.repository

import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.model.MediaType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistRepositoryTest {

    @Test
    fun testGetWatchlistItems() = runTest {
        val watchlistDao = mockk<WatchlistDao>(relaxed = true)
        val expected = listOf(TrackedWatchlistItem(100, MediaType.TV, "Show", null, null))
        every { watchlistDao.getAllTrackedItemsFlow() } returns flowOf(expected)
        val watchlistRepository = WatchlistRepository(watchlistDao)

        val items = watchlistRepository.watchlistItems.first()

        assertEquals(expected, items)
    }
}
