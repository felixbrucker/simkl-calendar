package com.felixbrucker.simklcalendar

import android.content.Context
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.database.CustomSearchLinkDao
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.LocalItemState
import com.felixbrucker.simklcalendar.data.database.NotificationSetting
import com.felixbrucker.simklcalendar.data.database.NotificationSettingDao
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.database.WatchedEpisodeDao
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
class RepositoryAdditionalCoverageTest {

    private lateinit var context: Context
    private lateinit var appDatabase: AppDatabase
    private lateinit var tokenDao: UserTokenDao
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var settingDao: NotificationSettingDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var watchedDao: WatchedEpisodeDao
    private lateinit var searchLinkDao: CustomSearchLinkDao
    private lateinit var itemDownloadSettingsDao: ItemDownloadSettingsDao

    private lateinit var repository: SimklRepository

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        appDatabase = mockk(relaxed = true)
        tokenDao = mockk(relaxed = true)
        calendarDao = mockk(relaxed = true)
        settingDao = mockk(relaxed = true)
        watchlistDao = mockk(relaxed = true)
        watchedDao = mockk(relaxed = true)
        searchLinkDao = mockk(relaxed = true)
        itemDownloadSettingsDao = mockk(relaxed = true)

        everyAppDatabase()

        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, appDatabase)

        repository = SimklRepository(context)
    }

    private fun everyAppDatabase() {
        coEvery { appDatabase.userTokenDao() } returns tokenDao
        coEvery { appDatabase.calendarItemDao() } returns calendarDao
        coEvery { appDatabase.notificationSettingDao() } returns settingDao
        coEvery { appDatabase.watchlistDao() } returns watchlistDao
        coEvery { appDatabase.watchedEpisodeDao() } returns watchedDao
        coEvery { appDatabase.customSearchLinkDao() } returns searchLinkDao
        coEvery { appDatabase.itemDownloadSettingsDao() } returns itemDownloadSettingsDao
    }

    @After
    fun tearDown() {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    @Test
    fun testSaveAndGetItemDownloadSettings() = runTest {
        val settings = ItemDownloadSettings(simklId = 55, downloadUnwatched = true, qualityOverride = "1080p")
        coEvery { itemDownloadSettingsDao.getSettingsFlow(55) } returns flowOf(settings)

        repository.saveItemDownloadSettings(settings)
        val retrieved = repository.getItemDownloadSettingsFlow(55).first()

        coVerify { itemDownloadSettingsDao.insertOrUpdate(settings) }
        assertEquals("1080p", retrieved?.qualityOverride)
    }

    @Test
    fun testSearchLinkDatabaseOperations() = runTest {
        val link1 = CustomSearchLink(id = 1L, name = "Link 1", urlTemplate = "http://test1.com", position = 0)
        val link2 = CustomSearchLink(id = 2L, name = "Link 2", urlTemplate = "http://test2.com", position = 1)
        coEvery { searchLinkDao.insertSearchLink(link1) } returns 1L

        val id = repository.insertSearchLink(link1)
        repository.updateSearchLink(link1)
        repository.updateSearchLinks(listOf(link1, link2))
        repository.deleteSearchLink(link1)

        assertEquals(1L, id)
        coVerify { searchLinkDao.insertSearchLink(link1) }
        coVerify { searchLinkDao.updateSearchLink(link1) }
        coVerify { searchLinkDao.updateSearchLinks(listOf(link1, link2)) }
        coVerify { searchLinkDao.deleteSearchLink(link1) }
    }

    @Test
    fun testUpdateMediaStatusMethods() = runTest {
        repository.updateMediaStatus("v2_10_1_1", MediaStatus.WANTED)
        repository.updateSeasonMediaStatus(10, 1, MediaStatus.IGNORED)
        repository.updateDownloadTaskId("v2_10_1_1", "task_123", MediaStatus.DOWNLOADING)

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

        repository.updateItemAiredStatus(itemWithWatchlist)

        coVerify { calendarDao.updateMediaStatus("v2_10_1_1", any()) }
    }

    @Test
    fun testToggleNotificationSetting() = runTest {
        repository.toggleNotificationSetting(simklId = 77, notifyEveryEpisode = true, notifyAiredLastEpisode = false)

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
