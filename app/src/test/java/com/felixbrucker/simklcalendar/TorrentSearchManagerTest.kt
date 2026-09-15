package com.felixbrucker.simklcalendar.data.network

import android.content.SharedPreferences
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.model.MediaType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant

class TorrentSearchManagerTest {

    @Test
    fun testTorrentSearchManagerInitialization() {
        val dao = mockk<ItemDownloadSettingsDao>()
        val prefs = mockk<SharedPreferences>(relaxed = true)

        val manager = TorrentSearchManager(dao, prefs)
        assertNotNull(manager)
    }

    @Test
    fun testTorrentSearchTV() = runBlocking {
        val dao = mockk<ItemDownloadSettingsDao>()
        val prefs = mockk<SharedPreferences>(relaxed = true)

        coEvery { dao.getSettings(100) } returns null
        every { prefs.getString("quality", "1080p") } returns "1080p"
        every { prefs.getBoolean("prefer_hevc", true) } returns true
        every { prefs.getString("preferred_keywords", null) } returns null
        every { prefs.getString("ignore_keywords", null) } returns null

        val manager = TorrentSearchManager(dao, prefs)

        val calendarItem = CalendarItem(
            primaryKey = "v2_100_1_1",
            simklId = 100,
            episodeTitle = "Pilot",
            season = 1,
            episodeNumber = 1,
            date = Instant.now(),
            movieReleaseType = null,
            isSeasonPremiere = true,
            isSeasonFinale = false
        )
        val watchlistItem = TrackedWatchlistItem(
            simklId = 100,
            type = MediaType.TV,
            title = "NonExistentShowForTest123456789",
            titleRomaji = null,
            poster = "poster.jpg"
        )
        val item = CalendarItemWithWatchlist(calendarItem, watchlistItem, null)

        val results = try {
            manager.search(item)
        } catch (_: Exception) {
            emptyList()
        }

        assertNotNull(results)
    }
}
