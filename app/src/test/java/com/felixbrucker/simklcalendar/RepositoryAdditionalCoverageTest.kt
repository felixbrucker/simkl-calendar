package com.felixbrucker.simklcalendar

import android.content.Context
import com.felixbrucker.simklcalendar.data.database.*
import com.felixbrucker.simklcalendar.data.model.*
import com.felixbrucker.simklcalendar.data.preferences.*
import com.felixbrucker.simklcalendar.data.repository.*
import io.mockk.*
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
    
    private lateinit var appSettingsRepo: AppSettingsRepository
    private lateinit var autoDownloadRepo: AutoDownloadRepository
    private lateinit var notificationRepo: NotificationRepository
    private lateinit var authRepo: AuthRepository
    private lateinit var syncMetadataRepo: SyncMetadataRepository
    private lateinit var uiRepo: UiRepository

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

        appSettingsRepo = mockk(relaxed = true)
        autoDownloadRepo = mockk(relaxed = true)
        notificationRepo = mockk(relaxed = true)
        authRepo = mockk(relaxed = true)
        syncMetadataRepo = mockk(relaxed = true)
        uiRepo = mockk(relaxed = true)

        everyAppDatabase()

        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, appDatabase)

        repository = SimklRepository(
            context = context,
            tokenDao = tokenDao,
            calendarDao = calendarDao,
            settingDao = settingDao,
            watchlistDao = watchlistDao,
            watchedDao = watchedDao,
            searchLinkDao = searchLinkDao,
            itemDownloadSettingsDao = itemDownloadSettingsDao,
            apiService = mockk(relaxed = true),
            appSettingsRepo = appSettingsRepo,
            autoDownloadRepo = autoDownloadRepo,
            notificationRepo = notificationRepo,
            authRepo = authRepo,
            syncMetadataRepo = syncMetadataRepo,
            uiRepo = uiRepo,
            torrentServiceHelper = mockk(relaxed = true),
            torrentSearchManager = mockk(relaxed = true)
        )
        
        every { autoDownloadRepo.preferencesFlow } returns flowOf(AutoDownloadPreferences())
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
