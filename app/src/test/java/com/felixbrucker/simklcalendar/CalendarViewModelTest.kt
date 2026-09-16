package com.felixbrucker.simklcalendar.ui.viewmodel

import android.app.Application
import android.content.SharedPreferences
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CustomSearchLinkDao
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.NotificationSettingDao
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.database.WatchedEpisodeDao
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.model.MediaType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var application: Application
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var appDatabase: AppDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        appDatabase = mockk(relaxed = true)

        every { application.applicationContext } returns application
        every { application.getSharedPreferences(any(), any()) } returns sharedPreferences

        every { sharedPreferences.getString(any(), any()) } answers { secondArg() ?: "" }
        every { sharedPreferences.getString("view_mode", any()) } returns MainViewMode.CALENDAR.name
        every { sharedPreferences.getBoolean(any(), any()) } answers { secondArg() }

        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { sharedPreferences.edit() } returns editor

        val userTokenDao = mockk<UserTokenDao>(relaxed = true)
        val calendarDao = mockk<CalendarItemDao>(relaxed = true)
        val settingDao = mockk<NotificationSettingDao>(relaxed = true)
        val watchlistDao = mockk<WatchlistDao>(relaxed = true)
        val watchedDao = mockk<WatchedEpisodeDao>(relaxed = true)
        val searchLinkDao = mockk<CustomSearchLinkDao>(relaxed = true)
        val itemDownloadSettingsDao = mockk<ItemDownloadSettingsDao>(relaxed = true)

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
        Dispatchers.resetMain()

        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    @Test
    fun testViewModeToggle() {
        val viewModel = CalendarViewModel(application)
        assertEquals(MainViewMode.CALENDAR, viewModel.viewMode.value)

        viewModel.setViewMode(MainViewMode.TABLE)
        assertEquals(MainViewMode.TABLE, viewModel.viewMode.value)
    }

    @Test
    fun testTableSortToggle() {
        val viewModel = CalendarViewModel(application)
        assertEquals(TableSortField.NAME, viewModel.tableSortField.value)
        assertEquals(SortDirection.ASCENDING, viewModel.tableSortDirection.value)

        viewModel.toggleTableSort(TableSortField.NAME)
        assertEquals(SortDirection.DESCENDING, viewModel.tableSortDirection.value)

        viewModel.toggleTableSort(TableSortField.LAST_EP)
        assertEquals(TableSortField.LAST_EP, viewModel.tableSortField.value)
        assertEquals(SortDirection.ASCENDING, viewModel.tableSortDirection.value)
    }

    @Test
    fun testFiltersAndSearch() {
        val viewModel = CalendarViewModel(application)

        viewModel.toggleShowTv()
        assertFalse(viewModel.showTv.value)

        viewModel.toggleShowAnime()
        assertFalse(viewModel.showAnime.value)

        viewModel.toggleShowMovies()
        assertFalse(viewModel.showMovies.value)

        viewModel.resetFilters()
        assertTrue(viewModel.showTv.value)
        assertTrue(viewModel.showAnime.value)
        assertTrue(viewModel.showMovies.value)

        viewModel.setSearchQuery("Naruto")
        assertEquals("Naruto", viewModel.searchQuery.value)

        viewModel.clearSearchQuery()
        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun testAutoDownloadPreferences() {
        val viewModel = CalendarViewModel(application)

        viewModel.updateAutoDownloadQuality("720p")
        assertEquals("720p", viewModel.autoDownloadQuality.value)

        viewModel.updateAutoDownloadPreferHevc(false)
        assertFalse(viewModel.autoDownloadPreferHevc.value)

        viewModel.addPreferredKeyword("SubsPlease")
        assertTrue(viewModel.autoDownloadPreferredKeywords.value.contains("SubsPlease"))

        viewModel.removePreferredKeyword("SubsPlease")
        assertFalse(viewModel.autoDownloadPreferredKeywords.value.contains("SubsPlease"))
    }

    @Test
    fun testWatchlistTableItemProgress() {
        val watchlistItem = TrackedWatchlistItem(
            simklId = 1,
            type = MediaType.TV,
            title = "Test Show",
            titleRomaji = null,
            poster = "poster.jpg"
        )

        val item = WatchlistTableItem(
            watchlistItem = watchlistItem,
            hasUnwatched = true,
            hasUnwatchedReleased = true,
            nextEpisodeDate = null,
            lastAiredDate = Instant.now(),
            watchedReleasedCount = 5,
            totalReleasedCount = 10,
            downloadedReleasedCount = 3,
            totalDownloadableReleasedCount = 6
        )

        assertEquals(0.5, item.watchedProgress, 0.001)
        assertEquals(0.5, item.downloadedProgress, 0.001)
    }
}
