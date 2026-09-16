package com.felixbrucker.simklcalendar.data.network

import android.content.SharedPreferences
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.torrent_search_api.NyaaProvider
import com.felixbrucker.torrent_search_api.PaginatedSearchResult
import com.felixbrucker.torrent_search_api.TpbProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TorrentSearchManagerTest {

    private lateinit var dao: ItemDownloadSettingsDao
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        prefs = mockk(relaxed = true)

        coEvery { dao.getSettings(any()) } returns null
        every { prefs.getString("quality", "1080p") } returns "1080p"
        every { prefs.getBoolean("prefer_hevc", true) } returns true
        every { prefs.getString("preferred_keywords", null) } returns null
        every { prefs.getString("ignore_keywords", null) } returns null

        mockkConstructor(NyaaProvider::class)
        mockkConstructor(TpbProvider::class)
        coEvery { anyConstructed<NyaaProvider>().search(any(), any(), any()) } returns Result.success(PaginatedSearchResult(emptyList(), 1, false))
        coEvery { anyConstructed<TpbProvider>().search(any(), any(), any()) } returns Result.success(PaginatedSearchResult(emptyList(), 1, false))
    }

    @After
    fun tearDown() {
        unmockkConstructor(NyaaProvider::class)
        unmockkConstructor(TpbProvider::class)
    }

    @Test
    fun testTorrentSearchManagerInitialization() {
        val manager = TorrentSearchManager(dao, prefs)

        assertNotNull(manager)
    }

    @Test
    fun testTorrentSearchTV() = runTest {
        val manager = TorrentSearchManager(dao, prefs)
        val calendarItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, true, false)
        val watchlistItem = TrackedWatchlistItem(100, MediaType.TV, "Test Show", null, null)
        val item = CalendarItemWithWatchlist(calendarItem, watchlistItem, null)

        val results = manager.search(item)

        assertTrue(results.isEmpty())
    }

    @Test
    fun testTorrentSearchAnimeWithSettingsOverrides() = runTest {
        val customSettings = ItemDownloadSettings(
            simklId = 200,
            titleOverride = "Custom Anime Title",
            seasonOverrides = mapOf(1 to 2),
            qualityOverride = "2160p",
            preferHevcOverride = false
        )
        coEvery { dao.getSettings(200) } returns customSettings
        val manager = TorrentSearchManager(dao, prefs)
        val calendarItem = CalendarItem("v2_200_1_5", 200, "Ep 5", 1, 5, Instant.now(), null, false, false)
        val watchlistItem = TrackedWatchlistItem(200, MediaType.ANIME, "Anime Show", "Anime Romaji", null)
        val item = CalendarItemWithWatchlist(calendarItem, watchlistItem, null)

        val results = manager.search(item)

        assertTrue(results.isEmpty())
    }

    @Test
    fun testTorrentSearchMovie() = runTest {
        val manager = TorrentSearchManager(dao, prefs)
        val calendarItem = CalendarItem("v2_300_theater", 300, null, null, null, Instant.now(), null, false, false)
        val watchlistItem = TrackedWatchlistItem(300, MediaType.MOVIE, "Test Movie", null, null)
        val item = CalendarItemWithWatchlist(calendarItem, watchlistItem, null)

        val results = manager.search(item)

        assertTrue(results.isEmpty())
    }
}
