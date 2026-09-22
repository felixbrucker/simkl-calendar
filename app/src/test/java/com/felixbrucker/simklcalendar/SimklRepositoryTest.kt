package com.felixbrucker.simklcalendar.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.File
import com.felixbrucker.simklcalendar.data.database.*
import com.felixbrucker.simklcalendar.data.model.*
import com.felixbrucker.simklcalendar.data.preferences.*
import com.felixbrucker.simklcalendar.data.network.*
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SimklRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: SimklRepository
    private lateinit var appDatabase: AppDatabase
    private lateinit var tokenDao: UserTokenDao
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var settingDao: NotificationSettingDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var watchedDao: WatchedEpisodeDao
    private lateinit var itemDownloadSettingsDao: ItemDownloadSettingsDao
    private lateinit var apiService: SimklApiService
    private lateinit var torrentServiceHelper: TorrentServiceHelper

    private lateinit var appSettingsRepo: AppSettingsRepository
    private lateinit var autoDownloadRepo: AutoDownloadRepository
    private lateinit var notificationRepo: NotificationRepository
    private lateinit var authRepo: AuthRepository
    private lateinit var syncMetadataRepo: SyncMetadataRepository
    private lateinit var uiRepo: UiRepository

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.filesDir } returns File("/tmp")
        appDatabase = mockk(relaxed = true)
        tokenDao = mockk(relaxed = true)
        calendarDao = mockk(relaxed = true)
        settingDao = mockk(relaxed = true)
        watchlistDao = mockk(relaxed = true)
        watchedDao = mockk(relaxed = true)
        itemDownloadSettingsDao = mockk(relaxed = true)
        apiService = mockk(relaxed = true)
        torrentServiceHelper = mockk(relaxed = true)


        appSettingsRepo = mockk(relaxed = true)
        autoDownloadRepo = mockk(relaxed = true)
        notificationRepo = mockk(relaxed = true)
        authRepo = mockk(relaxed = true)
        syncMetadataRepo = mockk(relaxed = true)
        uiRepo = mockk(relaxed = true)

        mockkStatic(Uri::class)
        val mockUri = mockk<Uri>(relaxed = true)
        every { Uri.parse(any()) } returns mockUri
        every { mockUri.toString() } returns "https://mock.uri"

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().toUri(any()) } returns "intent://mock"
        every { anyConstructed<Intent>().setClassName(any<String>(), any()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } returns mockk(relaxed = true)

        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } returns "base64"
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        every { appDatabase.userTokenDao() } returns tokenDao
        every { appDatabase.calendarItemDao() } returns calendarDao
        every { appDatabase.notificationSettingDao() } returns settingDao
        every { appDatabase.watchlistDao() } returns watchlistDao
        every { appDatabase.watchedEpisodeDao() } returns watchedDao
        every { appDatabase.customSearchLinkDao() } returns mockk(relaxed = true)
        every { appDatabase.itemDownloadSettingsDao() } returns itemDownloadSettingsDao

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
            searchLinkDao = mockk(relaxed = true),
            itemDownloadSettingsDao = itemDownloadSettingsDao,
            apiService = apiService,
            appSettingsRepo = appSettingsRepo,
            autoDownloadRepo = autoDownloadRepo,
            notificationRepo = notificationRepo,
            authRepo = authRepo,
            syncMetadataRepo = syncMetadataRepo,
            uiRepo = uiRepo,
            torrentServiceHelper = torrentServiceHelper,
            torrentSearchManager = TorrentSearchManager(itemDownloadSettingsDao, autoDownloadRepo),
            alarmScheduler = mockk(relaxed = true)
        )

        coEvery { torrentServiceHelper.addTorrent(any(), any(), any(), any(), any(), any(), any()) } returns Result.success("taskId")

        val apiField = SimklRepository::class.java.getDeclaredField("apiService")
        apiField.isAccessible = true
        apiField.set(repository, apiService)

        every { autoDownloadRepo.preferencesFlow } returns flowOf(AutoDownloadPreferences())
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        unmockkConstructor(Intent::class)
        unmockkStatic(Base64::class)
        unmockkStatic(Log::class)
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    @Test
    fun testDetermineStatusFutureNotAiredYet() = runTest {
        val future = Instant.now().plusSeconds(3600)

        val status = repository.determineStatus(future, null, MediaType.TV, false, false)

        assertEquals(MediaStatus.NOT_AIRED_YET, status)
    }

    @Test
    fun testDetermineStatusTheaterIgnored() = runTest {
        val past = Instant.now().minusSeconds(3600)

        val status = repository.determineStatus(past, null, MediaType.MOVIE, true, false)

        assertEquals(MediaStatus.IGNORED, status)
    }

    @Test
    fun testSearchAndDownloadSeason() = runTest {
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val calItem = CalendarItem("v2_100_1_2", 100, "Ep 2", 1, 2, Instant.now().minusSeconds(3600), null, false, false, false, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_2", MediaStatus.IGNORED))
        coEvery { calendarDao.getUnwatchedDownloadableSeasonItems(100, 1) } returns listOf(item)
        coEvery { calendarDao.findItem("v2_100_1_2") } returns item

        repository.searchAndDownloadSeason(100, 1)

        coVerify { calendarDao.updateMediaStatus("v2_100_1_2", MediaStatus.WANTED) }
    }


    @Test
    fun testDetermineStatusWatchedIgnored() = runTest {
        val past = Instant.now().minusSeconds(3600)

        val status = repository.determineStatus(past, null, MediaType.TV, false, true)

        assertEquals(MediaStatus.IGNORED, status)
    }

    @Test
    fun testDetermineStatusGlobalAutoDownloadWanted() = runTest {
        val past = Instant.now().minusSeconds(3600)
        every { autoDownloadRepo.preferencesFlow } returns flowOf(AutoDownloadPreferences(autoDownloadUnwatchedTv = true))

        val status = repository.determineStatus(past, null, MediaType.TV, false, false)

        assertEquals(MediaStatus.WANTED, status)
    }

    @Test
    fun testDetermineStatusSpecificSettingOverridesGlobal() = runTest {
        val past = Instant.now().minusSeconds(3600)
        val settings = ItemDownloadSettings(simklId = 100, downloadUnwatched = false)

        val status = repository.determineStatus(past, settings, MediaType.TV, false, false)

        assertEquals(MediaStatus.IGNORED, status)
    }

    @Test
    fun testLogoutAndTokenManagement() = runTest {
        coEvery { tokenDao.getActiveToken() } returns UserToken(1, "token123", "User")

        val token = repository.getActiveUserToken()
        repository.logout()

        assertNotNull(token)
        assertEquals("token123", token?.accessToken)
        coVerify { tokenDao.clearUserToken() }
        coVerify { calendarDao.clearCalendarItems() }
        coVerify { watchlistDao.clearAll() }
        coVerify { watchedDao.clearAll() }
    }
}
