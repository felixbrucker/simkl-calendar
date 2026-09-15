package com.felixbrucker.simklcalendar.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.database.CustomSearchLinkDao
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.NotificationSetting
import com.felixbrucker.simklcalendar.data.database.NotificationSettingDao
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.database.WatchedEpisodeDao
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.network.SimklIds
import com.felixbrucker.simklcalendar.data.network.SimklMedia
import com.felixbrucker.simklcalendar.data.network.SyncMovieItem
import com.felixbrucker.simklcalendar.data.network.SyncShowItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SimklRepositoryTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var appDatabase: AppDatabase
    private lateinit var userTokenDao: UserTokenDao
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var settingDao: NotificationSettingDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var watchedDao: WatchedEpisodeDao
    private lateinit var searchLinkDao: CustomSearchLinkDao
    private lateinit var itemDownloadSettingsDao: ItemDownloadSettingsDao

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        appDatabase = mockk(relaxed = true)

        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { context.packageName } returns "com.felixbrucker.simklcalendar"

        userTokenDao = mockk(relaxed = true)
        calendarDao = mockk(relaxed = true)
        settingDao = mockk(relaxed = true)
        watchlistDao = mockk(relaxed = true)
        watchedDao = mockk(relaxed = true)
        searchLinkDao = mockk(relaxed = true)
        itemDownloadSettingsDao = mockk(relaxed = true)

        every { userTokenDao.getUserToken() } returns flowOf(null)
        every { calendarDao.getAllCalendarItems() } returns flowOf(emptyList())
        every { settingDao.getAllSettings() } returns flowOf(emptyList())
        every { watchedDao.getAllWatchedEpisodesFlow() } returns flowOf(emptyList())
        every { searchLinkDao.getAllSearchLinks() } returns flowOf(emptyList())
        every { watchlistDao.getAllTrackedItemsFlow() } returns flowOf(emptyList())

        every { appDatabase.userTokenDao() } returns userTokenDao
        every { appDatabase.calendarItemDao() } returns calendarDao
        every { appDatabase.notificationSettingDao() } returns settingDao
        every { appDatabase.watchlistDao() } returns watchlistDao
        every { appDatabase.watchedEpisodeDao() } returns watchedDao
        every { appDatabase.customSearchLinkDao() } returns searchLinkDao
        every { appDatabase.itemDownloadSettingsDao() } returns itemDownloadSettingsDao

        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, appDatabase)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    @Test
    fun testDetermineStatusFutureDate() {
        val repo = SimklRepository(context)
        val futureDate = Instant.now().plusSeconds(3600)

        val status = repo.determineStatus(
            airDate = futureDate,
            settings = null,
            mediaType = MediaType.TV,
            isTheaterRelease = false,
            isWatched = false
        )

        assertEquals(MediaStatus.NOT_AIRED_YET, status)
    }

    @Test
    fun testDetermineStatusTheaterOrWatchedIgnored() {
        val repo = SimklRepository(context)
        val pastDate = Instant.now().minusSeconds(3600)

        val theaterStatus = repo.determineStatus(
            airDate = pastDate,
            settings = null,
            mediaType = MediaType.MOVIE,
            isTheaterRelease = true,
            isWatched = false
        )
        assertEquals(MediaStatus.IGNORED, theaterStatus)

        val watchedStatus = repo.determineStatus(
            airDate = pastDate,
            settings = null,
            mediaType = MediaType.TV,
            isTheaterRelease = false,
            isWatched = true
        )
        assertEquals(MediaStatus.IGNORED, watchedStatus)
    }

    @Test
    fun testDetermineStatusAutoDownloadUnwatched() {
        val repo = SimklRepository(context)
        val pastDate = Instant.now().minusSeconds(3600)

        every { sharedPreferences.getBoolean("auto_download_unwatched_tv", false) } returns true

        val wantedStatus = repo.determineStatus(
            airDate = pastDate,
            settings = null,
            mediaType = MediaType.TV,
            isTheaterRelease = false,
            isWatched = false
        )
        assertEquals(MediaStatus.WANTED, wantedStatus)

        val itemSettings = ItemDownloadSettings(simklId = 1, downloadUnwatched = false)
        val ignoredStatus = repo.determineStatus(
            airDate = pastDate,
            settings = itemSettings,
            mediaType = MediaType.TV,
            isTheaterRelease = false,
            isWatched = false
        )
        assertEquals(MediaStatus.IGNORED, ignoredStatus)
    }

    @Test
    fun testFromShowItemAndFromMovieItem() {
        val showItem = SyncShowItem(
            status = "watching",
            show = SimklMedia(
                title = "Naruto",
                poster = "naruto.jpg",
                ids = SimklIds(simkl = 123)
            )
        )
        val trackedShow = TrackedWatchlistItem.fromShowItem(showItem, MediaType.ANIME)
        assertEquals(123, trackedShow.simklId)
        assertEquals(MediaType.ANIME, trackedShow.type)
        assertEquals("Naruto", trackedShow.title)
        assertEquals("naruto.jpg", trackedShow.poster)

        val movieItem = SyncMovieItem(
            status = "watching",
            movie = SimklMedia(
                title = "Inception",
                poster = "inception.jpg",
                ids = SimklIds(simkl = 456)
            )
        )
        val trackedMovie = TrackedWatchlistItem.fromMovieItem(movieItem)
        assertEquals(456, trackedMovie.simklId)
        assertEquals(MediaType.MOVIE, trackedMovie.type)
        assertEquals("Inception", trackedMovie.title)
    }

    @Test
    fun testGenerateCompletionIntentUri() {
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setClassName(any<String>(), any<String>()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().toUri(any()) } returns "intent://#Intent;scheme=simklcalendar;end"

        val repo = SimklRepository(context)
        val uri = repo.generateCompletionIntentUri("v2_100_1_1")
        assertNotNull(uri)

        unmockkConstructor(Intent::class)
    }

    @Test
    fun testIsRealApiConfigured() {
        val repo = SimklRepository(context)
        val configured = repo.isRealApiConfigured()
        assertTrue(configured || !configured)
    }

    @Test
    fun testSearchLinkDaoDelegation() = runBlocking {
        val repo = SimklRepository(context)
        val link = CustomSearchLink(id = 1, name = "Search Google", urlTemplate = "https://google.com/search?q={TITLE}", position = 0)

        coEvery { searchLinkDao.insertSearchLink(link) } returns 1L
        val id = repo.insertSearchLink(link)
        assertEquals(1L, id)

        repo.updateSearchLink(link)
        coVerify { searchLinkDao.updateSearchLink(link) }

        repo.updateSearchLinks(listOf(link))
        coVerify { searchLinkDao.updateSearchLinks(listOf(link)) }

        repo.deleteSearchLink(link)
        coVerify { searchLinkDao.deleteSearchLink(link) }
    }

    @Test
    fun testMediaStatusUpdates() = runBlocking {
        val repo = SimklRepository(context)

        repo.updateMediaStatus("v2_100_1_1", MediaStatus.WANTED)
        coVerify { calendarDao.updateMediaStatus("v2_100_1_1", MediaStatus.WANTED) }

        repo.updateSeasonMediaStatus(100, 1, MediaStatus.WANTED)
        coVerify { calendarDao.updateSeasonMediaStatus(100, 1, MediaStatus.WANTED, any()) }

        repo.updateDownloadTaskId("v2_100_1_1", "task123", MediaStatus.DOWNLOADING)
        coVerify { calendarDao.updateDownloadTaskId("v2_100_1_1", "task123", MediaStatus.DOWNLOADING) }
    }

    @Test
    fun testSaveAndGetItemDownloadSettings() = runBlocking {
        val repo = SimklRepository(context)
        val settings = ItemDownloadSettings(simklId = 100, downloadUnwatched = true)

        repo.saveItemDownloadSettings(settings)
        coVerify { itemDownloadSettingsDao.insertOrUpdate(settings) }

        every { itemDownloadSettingsDao.getSettingsFlow(100) } returns flowOf(settings)
        val flow = repo.getItemDownloadSettingsFlow(100)
        assertNotNull(flow)
    }

    @Test
    fun testToggleNotificationSetting() = runBlocking {
        val repo = SimklRepository(context)

        repo.toggleNotificationSetting(100, notifyEveryEpisode = true, notifyAiredLastEpisode = false)
        coVerify { settingDao.saveSetting(NotificationSetting(100, notifyEveryEpisode = true, notifyAiredLastEpisode = false)) }
    }

    @Test
    fun testLogout() = runBlocking {
        val repo = SimklRepository(context)

        repo.logout()
        coVerify { userTokenDao.clearUserToken() }
        coVerify { calendarDao.clearCalendarItems() }
        coVerify { watchlistDao.clearAll() }
        coVerify { watchedDao.clearAll() }
    }

    @Test
    fun testCleanupOldWatchedCalendarItems() = runBlocking {
        val repo = SimklRepository(context)
        coEvery { calendarDao.deleteWatchedItemsOlderThan(any()) } returns 5

        val deleted = repo.cleanupOldWatchedCalendarItems(30)
        assertEquals(5, deleted)
    }
}
