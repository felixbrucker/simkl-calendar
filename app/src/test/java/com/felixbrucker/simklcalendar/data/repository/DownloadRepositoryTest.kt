package com.felixbrucker.simklcalendar.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.LocalItemState
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.network.TorrentSearchManager
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadRepositoryTest {

    private lateinit var context: Context
    private lateinit var itemDownloadSettingsDao: ItemDownloadSettingsDao
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var calendarRepository: CalendarRepository
    private lateinit var torrentSearchManager: TorrentSearchManager
    private lateinit var torrentServiceHelper: TorrentServiceHelper
    private lateinit var downloadRepository: DownloadRepository

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        itemDownloadSettingsDao = mockk(relaxed = true)
        calendarDao = mockk(relaxed = true)
        calendarRepository = mockk(relaxed = true)
        torrentSearchManager = mockk(relaxed = true)
        torrentServiceHelper = mockk(relaxed = true)

        mockkStatic(Uri::class)
        val mockUri = mockk<Uri>(relaxed = true)
        every { Uri.parse(any()) } returns mockUri
        every { mockUri.toString() } returns "https://mock.uri"

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().toUri(any()) } returns "intent://mock"
        every { anyConstructed<Intent>().setClassName(any<String>(), any()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } returns mockk(relaxed = true)

        downloadRepository = DownloadRepository(
            context = context,
            itemDownloadSettingsDao = itemDownloadSettingsDao,
            calendarDao = calendarDao,
            calendarRepository = calendarRepository,
            torrentSearchManager = torrentSearchManager,
            torrentServiceHelper = torrentServiceHelper
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        unmockkConstructor(Intent::class)
    }

    @Test
    fun testSaveAndGetItemDownloadSettings() = runTest {
        val settings = ItemDownloadSettings(simklId = 55, downloadUnwatched = true, qualityOverride = "1080p")
        coEvery { itemDownloadSettingsDao.getSettingsFlow(55) } returns flowOf(settings)

        downloadRepository.saveItemDownloadSettings(settings)
        val retrieved = downloadRepository.getItemDownloadSettingsFlow(55).first()

        coVerify { itemDownloadSettingsDao.insertOrUpdate(settings) }
        assertEquals("1080p", retrieved?.qualityOverride)
    }

    @Test
    fun testSearchAndDownloadSeason() = runTest {
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val calItem = CalendarItem("v2_100_1_2", 100, "Ep 2", 1, 2, Instant.now().minusSeconds(3600), null, false, false, false, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_2", MediaStatus.IGNORED))
        coEvery { calendarDao.getUnwatchedDownloadableSeasonItems(100, 1) } returns listOf(item)
        coEvery { calendarDao.findItem("v2_100_1_2") } returns item

        downloadRepository.searchAndDownloadSeason(100, 1)

        coVerify { calendarRepository.updateMediaStatus("v2_100_1_2", MediaStatus.WANTED) }
    }
}
