package com.felixbrucker.simklcalendar

import android.content.Context
import com.felixbrucker.simklcalendar.data.database.*
import com.felixbrucker.simklcalendar.data.model.*
import com.felixbrucker.simklcalendar.data.preferences.*
import com.felixbrucker.simklcalendar.data.repository.*
import com.felixbrucker.simklcalendar.data.util.MediaStatusResolver
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryAdditionalCoverageTest {

    private lateinit var context: Context
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var settingDao: NotificationSettingDao
    private lateinit var searchLinkDao: CustomSearchLinkDao
    private lateinit var itemDownloadSettingsDao: ItemDownloadSettingsDao

    private lateinit var downloadRepository: DownloadRepository
    private lateinit var customSearchLinkRepository: CustomSearchLinkRepository
    private lateinit var calendarRepository: CalendarRepository
    private lateinit var notificationSettingRepository: NotificationSettingRepository

    private lateinit var autoDownloadRepo: AutoDownloadRepository

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        calendarDao = mockk(relaxed = true)
        settingDao = mockk(relaxed = true)
        searchLinkDao = mockk(relaxed = true)
        itemDownloadSettingsDao = mockk(relaxed = true)
        autoDownloadRepo = mockk(relaxed = true)

        val mediaStatusResolver = MediaStatusResolver(autoDownloadRepo)

        downloadRepository = DownloadRepository(
            context = context,
            calendarDao = calendarDao,
            itemDownloadSettingsDao = itemDownloadSettingsDao,
            torrentSearchManager = mockk(relaxed = true),
            torrentServiceHelper = mockk(relaxed = true)
        )

        customSearchLinkRepository = CustomSearchLinkRepository(searchLinkDao)

        calendarRepository = CalendarRepository(
            calendarDao = calendarDao,
            watchlistDao = mockk(relaxed = true),
            mediaStatusResolver = mediaStatusResolver
        )

        notificationSettingRepository = NotificationSettingRepository(settingDao)

        every { autoDownloadRepo.preferencesFlow } returns flowOf(AutoDownloadPreferences())
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
    fun testSearchLinkDatabaseOperations() = runTest {
        val link1 = CustomSearchLink(id = 1L, name = "Link 1", urlTemplate = "http://test1.com", position = 0)
        val link2 = CustomSearchLink(id = 2L, name = "Link 2", urlTemplate = "http://test2.com", position = 1)
        coEvery { searchLinkDao.insertSearchLink(link1) } returns 1L

        val id = customSearchLinkRepository.insertSearchLink(link1)
        customSearchLinkRepository.updateSearchLink(link1)
        customSearchLinkRepository.updateSearchLinks(listOf(link1, link2))
        customSearchLinkRepository.deleteSearchLink(link1)

        assertEquals(1L, id)
        coVerify { searchLinkDao.insertSearchLink(link1) }
        coVerify { searchLinkDao.updateSearchLink(link1) }
        coVerify { searchLinkDao.updateSearchLinks(listOf(link1, link2)) }
        coVerify { searchLinkDao.deleteSearchLink(link1) }
    }

    @Test
    fun testUpdateMediaStatusMethods() = runTest {
        calendarRepository.updateMediaStatus("v2_10_1_1", MediaStatus.WANTED)
        calendarRepository.updateSeasonMediaStatus(10, 1, MediaStatus.IGNORED)
        calendarRepository.updateDownloadTaskId("v2_10_1_1", "task_123", MediaStatus.DOWNLOADING)

        coVerify { calendarDao.updateMediaStatus("v2_10_1_1", MediaStatus.WANTED) }
        coVerify { calendarDao.updateSeasonMediaStatus(10, 1, MediaStatus.IGNORED, any()) }
        coVerify { calendarDao.updateDownloadTaskId("v2_10_1_1", "task_123", MediaStatus.DOWNLOADING) }
    }

    @Test
    fun testUpdateItemAiredStatusNotAiredYetToWanted() = runTest {
        val past = Instant.now().minusSeconds(3600)
        val calItem = CalendarItem("v2_10_1_1", 10, "Ep Title", 1, 1, past, null, true, false, false, null)
        val watchItem = TrackedWatchlistItem(10, MediaType.TV, "Show Title", null, null)
        val localState = LocalItemState("v2_10_1_1", MediaStatus.NOT_AIRED_YET)
        val itemWithWatchlist = CalendarItemWithWatchlist(calItem, watchItem, localState)

        coEvery { calendarDao.findItem("v2_10_1_1") } returns itemWithWatchlist

        calendarRepository.updateItemAiredStatus(itemWithWatchlist)

        coVerify { calendarDao.updateMediaStatus("v2_10_1_1", any()) }
    }

    @Test
    fun testToggleNotificationSetting() = runTest {
        notificationSettingRepository.toggleNotificationSetting(simklId = 77, notifyEveryEpisode = true, notifyAiredLastEpisode = false)

        coVerify {
            settingDao.saveSetting(
                NotificationSetting(
                    simklId = 77,
                    notifyEveryEpisode = true,
                    notifyAiredLastEpisode = false
                )
            )
        }
    }
}
