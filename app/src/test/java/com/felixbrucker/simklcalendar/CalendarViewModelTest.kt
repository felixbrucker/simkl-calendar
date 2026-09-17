package com.felixbrucker.simklcalendar.ui.viewmodel

import android.app.Application
import android.content.SharedPreferences
import android.os.Environment
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.database.CustomSearchLinkDao
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.LocalItemState
import com.felixbrucker.simklcalendar.data.database.NotificationSettingDao
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.database.WatchedEpisodeDao
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import com.felixbrucker.simklcalendar.data.util.DownloadProgress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var application: Application
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var autoDownloadPrefs: SharedPreferences
    private lateinit var appDatabase: AppDatabase
    private lateinit var userTokenDao: UserTokenDao
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var settingDao: NotificationSettingDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var watchedDao: WatchedEpisodeDao
    private lateinit var searchLinkDao: CustomSearchLinkDao
    private lateinit var itemDownloadSettingsDao: ItemDownloadSettingsDao
    private lateinit var repositoryMock: SimklRepository
    private val calendarItemsFlow = MutableStateFlow<List<CalendarItemWithWatchlist>>(emptyList())
    private val userTokenFlow = MutableStateFlow<UserToken?>(null)
    private val watchlistItemsFlow = MutableStateFlow<List<TrackedWatchlistItem>>(emptyList())
    private val customSearchLinksFlow = MutableStateFlow<List<CustomSearchLink>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns testDispatcher

        mockkStatic(Environment::class)
        every { Environment.getExternalStoragePublicDirectory(any<String>()) } returns File("/non_existent_dir_for_test")

        userTokenFlow.value = null
        calendarItemsFlow.value = emptyList()
        watchlistItemsFlow.value = emptyList()
        customSearchLinksFlow.value = emptyList()

        application = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        autoDownloadPrefs = mockk(relaxed = true)
        appDatabase = mockk(relaxed = true)
        repositoryMock = mockk(relaxed = true)

        coEvery { repositoryMock.syncCalendar(any()) } returns Unit
        coEvery { repositoryMock.syncCalendar() } returns Unit
        coEvery { repositoryMock.getActiveUserToken() } returns null

        every { application.applicationContext } returns application
        every { application.getSharedPreferences("ui_prefs", any()) } returns sharedPreferences
        every { application.getSharedPreferences("auto_download_prefs", any()) } returns autoDownloadPrefs
        every { application.getSharedPreferences(any(), any()) } returns sharedPreferences

        every { sharedPreferences.getString("view_mode", any()) } returns MainViewMode.CALENDAR.name
        every { sharedPreferences.getString(any(), any()) } answers { secondArg() ?: "" }
        every { sharedPreferences.getBoolean("filter_show_tv", true) } returns true
        every { sharedPreferences.getBoolean("filter_show_anime", true) } returns true
        every { sharedPreferences.getBoolean("filter_show_movies", true) } returns true
        every { sharedPreferences.getBoolean("filter_only_unwatched", true) } returns true
        every { sharedPreferences.getBoolean("filter_only_premieres", false) } returns false
        every { sharedPreferences.getBoolean("filter_only_finales", false) } returns false
        every { sharedPreferences.getBoolean("filter_only_digital_dvd", false) } returns false
        every { sharedPreferences.getBoolean("filter_show_earlier", false) } returns false
        every { sharedPreferences.getBoolean(any(), any()) } answers { secondArg() as Boolean }

        every { autoDownloadPrefs.getString("quality", any()) } returns "1080p"
        every { autoDownloadPrefs.getBoolean("prefer_hevc", true) } returns true
        every { autoDownloadPrefs.getBoolean(any(), any()) } answers { secondArg() as Boolean }
        every { autoDownloadPrefs.getString(any(), any()) } answers { secondArg() ?: "" }

        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { sharedPreferences.edit() } returns editor
        every { autoDownloadPrefs.edit() } returns editor

        userTokenDao = mockk(relaxed = true)
        calendarDao = mockk(relaxed = true)
        settingDao = mockk(relaxed = true)
        watchlistDao = mockk(relaxed = true)
        watchedDao = mockk(relaxed = true)
        searchLinkDao = mockk(relaxed = true)
        itemDownloadSettingsDao = mockk(relaxed = true)

        every { userTokenDao.getUserToken() } returns userTokenFlow
        every { calendarDao.getAllCalendarItems() } returns calendarItemsFlow
        every { settingDao.getAllSettings() } returns flowOf(emptyList())
        every { watchedDao.getAllWatchedEpisodesFlow() } returns flowOf(emptyList())
        every { searchLinkDao.getAllSearchLinks() } returns customSearchLinksFlow
        every { watchlistDao.getAllTrackedItemsFlow() } returns watchlistItemsFlow

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

        every { repositoryMock.calendarItems } returns calendarItemsFlow
        every { repositoryMock.activeUserToken } returns userTokenFlow
        every { repositoryMock.watchlistItems } returns watchlistItemsFlow
        every { repositoryMock.customSearchLinks } returns customSearchLinksFlow
        every { repositoryMock.notificationSettings } returns flowOf(emptyList())
        every { repositoryMock.watchedEpisodes } returns flowOf(emptyList())
        every { repositoryMock.torrentServiceHelper.downloads } returns MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
        every { repositoryMock.torrentServiceHelper.isBound } returns MutableStateFlow(false)
        every { repositoryMock.torrentServiceHelper.isInstalled } returns MutableStateFlow(false)
        coEvery { repositoryMock.searchAndDownloadEpisode(any()) } returns Result.failure(Exception("No torrents"))
    }

    private fun createViewModel(): CalendarViewModel {
        userTokenFlow.value = null
        val viewModel = CalendarViewModel(application)
        val field = CalendarViewModel::class.java.getDeclaredField("repository")
        field.isAccessible = true
        field.set(viewModel, repositoryMock)
        return viewModel
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Dispatchers::class)
        unmockkStatic(Environment::class)

        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    @Test
    fun testViewModeToggle() = runTest {
        val viewModel = createViewModel()

        viewModel.setViewMode(MainViewMode.TABLE)
        advanceUntilIdle()

        val mode = viewModel.viewMode.value
        assertEquals(MainViewMode.TABLE, mode)
    }

    @Test
    fun testTableSortToggle() = runTest {
        val viewModel = createViewModel()

        viewModel.toggleTableSort(TableSortField.NAME)
        val initialDir = viewModel.tableSortDirection.value
        viewModel.toggleTableSort(TableSortField.LAST_EP)
        val field = viewModel.tableSortField.value
        val newDir = viewModel.tableSortDirection.value

        assertEquals(SortDirection.DESCENDING, initialDir)
        assertEquals(TableSortField.LAST_EP, field)
        assertEquals(SortDirection.ASCENDING, newDir)
    }

    @Test
    fun testFiltersAndSearch() = runTest {
        val viewModel = createViewModel()

        viewModel.toggleShowTv()
        val tv = viewModel.showTv.value
        viewModel.toggleShowAnime()
        val anime = viewModel.showAnime.value
        viewModel.toggleShowMovies()
        val movies = viewModel.showMovies.value
        viewModel.toggleShowOnlyUnwatchedReleased()
        val unwatched = viewModel.showOnlyUnwatchedReleased.value
        viewModel.toggleOnlySeasonPremieres()
        val premieres = viewModel.onlySeasonPremieres.value
        viewModel.toggleOnlySeasonFinales()
        val finales = viewModel.onlySeasonFinales.value
        viewModel.toggleOnlyDigitalDvd()
        val digitalDvd = viewModel.onlyDigitalDvd.value
        viewModel.toggleShowEarlierReleases()
        val showEarlier = viewModel.showEarlierReleases.value
        viewModel.setShowEarlierReleases(true)
        val setEarlier = viewModel.showEarlierReleases.value
        viewModel.resetFilters()
        val resetTv = viewModel.showTv.value
        val resetAnime = viewModel.showAnime.value
        val resetMovies = viewModel.showMovies.value
        viewModel.setSearchQuery("Naruto")
        val query = viewModel.searchQuery.value
        viewModel.clearSearchQuery()
        val clearedQuery = viewModel.searchQuery.value

        assertFalse(tv)
        assertFalse(anime)
        assertFalse(movies)
        assertFalse(unwatched)
        assertTrue(premieres)
        assertTrue(finales)
        assertTrue(digitalDvd)
        assertTrue(showEarlier)
        assertTrue(setEarlier)
        assertTrue(resetTv)
        assertTrue(resetAnime)
        assertTrue(resetMovies)
        assertEquals("Naruto", query)
        assertEquals("", clearedQuery)
    }

    @Test
    fun testAutoDownloadPreferences() = runTest {
        val viewModel = createViewModel()

        viewModel.updateAutoDownloadQuality("720p")
        val quality = viewModel.autoDownloadQuality.value
        viewModel.updateAutoDownloadPreferHevc(false)
        val preferHevc = viewModel.autoDownloadPreferHevc.value
        viewModel.updateAutoDownloadUnwatchedTv(true)
        val unwatchedTv = viewModel.autoDownloadUnwatchedTv.value
        viewModel.updateAutoDownloadUnwatchedAnime(true)
        val unwatchedAnime = viewModel.autoDownloadUnwatchedAnime.value
        viewModel.updateAutoDownloadUnwatchedMovie(true)
        val unwatchedMovie = viewModel.autoDownloadUnwatchedMovie.value
        viewModel.updateAutoDownloadSeasonUnwatchedTv(true)
        val seasonUnwatchedTv = viewModel.autoDownloadSeasonUnwatchedTv.value
        viewModel.updateAutoDownloadSeasonUnwatchedAnime(true)
        val seasonUnwatchedAnime = viewModel.autoDownloadSeasonUnwatchedAnime.value
        viewModel.addPreferredKeyword("SubsPlease")
        val keywordsAfterAdd = viewModel.autoDownloadPreferredKeywords.value
        viewModel.addPreferredKeyword("SubsPlease")
        val keywordsAfterDuplicateAdd = viewModel.autoDownloadPreferredKeywords.value
        viewModel.removePreferredKeyword("SubsPlease")
        val keywordsAfterRemove = viewModel.autoDownloadPreferredKeywords.value
        viewModel.removePreferredKeyword("NonExistent")
        val keywordsAfterRemoveNonExistent = viewModel.autoDownloadPreferredKeywords.value
        viewModel.updatePreferredKeywordsOrder(listOf("B", "A"))
        val reorderedKeywords = viewModel.autoDownloadPreferredKeywords.value
        viewModel.addIgnoreKeyword("RAW")
        val ignoreAfterAdd = viewModel.autoDownloadIgnoreKeywords.value
        viewModel.addIgnoreKeyword("RAW")
        val ignoreAfterDuplicateAdd = viewModel.autoDownloadIgnoreKeywords.value
        viewModel.removeIgnoreKeyword("RAW")
        val ignoreAfterRemove = viewModel.autoDownloadIgnoreKeywords.value
        viewModel.removeIgnoreKeyword("NonExistent")
        val ignoreAfterRemoveNonExistent = viewModel.autoDownloadIgnoreKeywords.value
        viewModel.updateIgnoreKeywordsOrder(listOf("Y", "X"))
        val reorderedIgnore = viewModel.autoDownloadIgnoreKeywords.value

        assertEquals("720p", quality)
        assertFalse(preferHevc)
        assertTrue(unwatchedTv)
        assertTrue(unwatchedAnime)
        assertTrue(unwatchedMovie)
        assertTrue(seasonUnwatchedTv)
        assertTrue(seasonUnwatchedAnime)
        assertTrue(keywordsAfterAdd.contains("SubsPlease"))
        assertEquals(1, keywordsAfterDuplicateAdd.size)
        assertFalse(keywordsAfterRemove.contains("SubsPlease"))
        assertEquals(0, keywordsAfterRemoveNonExistent.size)
        assertEquals(listOf("B", "A"), reorderedKeywords)
        assertTrue(ignoreAfterAdd.contains("RAW"))
        assertEquals(1, ignoreAfterDuplicateAdd.size)
        assertFalse(ignoreAfterRemove.contains("RAW"))
        assertEquals(0, ignoreAfterRemoveNonExistent.size)
        assertEquals(listOf("Y", "X"), reorderedIgnore)
    }

    @Test
    fun testPendingDetailKey() = runTest {
        val viewModel = createViewModel()

        viewModel.setPendingDetailKey("key123")
        val key = viewModel.pendingDetailKey.value
        viewModel.clearPendingDetailKey()
        val clearedKey = viewModel.pendingDetailKey.value

        assertEquals("key123", key)
        assertNull(clearedKey)
    }

    @Test
    fun testItemDownloadSettings() = runTest {
        val viewModel = createViewModel()
        val settings = ItemDownloadSettings(simklId = 10, qualityOverride = "1080p")
        every { repositoryMock.getItemDownloadSettingsFlow(10) } returns flowOf(settings)

        viewModel.saveItemDownloadSettings(settings)
        advanceUntilIdle()
        val retrievedFlow = viewModel.getItemDownloadSettingsFlow(10)
        val retrievedSettings = retrievedFlow.first()

        coVerify { repositoryMock.saveItemDownloadSettings(settings) }
        assertEquals("1080p", retrievedSettings?.qualityOverride)
    }

    @Test
    fun testRefreshDownloadSubdirectories() = runTest {
        val viewModel = createViewModel()

        viewModel.refreshDownloadSubdirectories()
        advanceUntilIdle()

        val subdirs = viewModel.downloadSubdirectories.value
        assertEquals(emptyList<String>(), subdirs)
    }

    @Test
    fun testMarkEpisodeWatchedSuccessAndFailure() = runTest {
        val viewModel = createViewModel()
        coEvery { repositoryMock.markEpisodeWatched(1, 1, 2, MediaType.TV) } returns Result.success(Unit)

        var successCalled = false
        var successMsg = ""
        viewModel.markEpisodeWatched(
            simklId = 1,
            season = 1,
            episodeNumber = 2,
            mediaType = MediaType.TV,
            primaryKey = "v2_1_1_2",
            showTitle = "Show Title"
        ) { ok, msg ->
            successCalled = ok
            successMsg = msg
        }
        advanceUntilIdle()

        var failureCalled = true
        var failureMsg = ""
        coEvery { repositoryMock.markEpisodeWatched(1, 1, 2, MediaType.TV) } returns Result.failure(Exception("Failed to mark episode as watched"))
        viewModel.markEpisodeWatched(
            simklId = 1,
            season = 1,
            episodeNumber = 2,
            mediaType = MediaType.TV,
            primaryKey = "v2_1_1_2",
            showTitle = "Show Title"
        ) { ok, msg ->
            failureCalled = ok
            failureMsg = msg
        }
        advanceUntilIdle()

        assertTrue(successCalled)
        assertEquals("Marked Show Title S01E02 as watched", successMsg)
        assertFalse(failureCalled)
        assertEquals("Failed to mark episode as watched", failureMsg)
    }

    @Test
    fun testMarkSeasonWatchedSuccessAndFailure() = runTest {
        val viewModel = createViewModel()
        coEvery { repositoryMock.markSeasonWatched(1, 1, MediaType.TV) } returns Result.success(false)

        var successCalled = false
        var successMsg = ""
        viewModel.markSeasonWatched(
            simklId = 1,
            season = 1,
            mediaType = MediaType.TV,
            showTitle = "Show Title"
        ) { ok, msg ->
            successCalled = ok
            successMsg = msg
        }
        advanceUntilIdle()

        var failureCalled = true
        var failureMsg = ""
        coEvery { repositoryMock.markSeasonWatched(1, 1, MediaType.TV) } returns Result.failure(Exception("Failed to mark season as watched"))
        viewModel.markSeasonWatched(
            simklId = 1,
            season = 1,
            mediaType = MediaType.TV,
            showTitle = "Show Title"
        ) { ok, msg ->
            failureCalled = ok
            failureMsg = msg
        }
        advanceUntilIdle()

        assertTrue(successCalled)
        assertEquals("Marked Show Title Season 1 as watched", successMsg)
        assertFalse(failureCalled)
        assertEquals("Failed to mark season as watched", failureMsg)
    }

    @Test
    fun testMarkMovieWatchedSuccessAndFailure() = runTest {
        val viewModel = createViewModel()
        coEvery { repositoryMock.markMovieWatched(1) } returns Result.success(Unit)

        var successCalled = false
        var successMsg = ""
        viewModel.markMovieWatched(
            simklId = 1,
            primaryKey = "v2_1_theater",
            showTitle = "Movie Title"
        ) { ok, msg ->
            successCalled = ok
            successMsg = msg
        }
        advanceUntilIdle()

        var failureCalled = true
        var failureMsg = ""
        coEvery { repositoryMock.markMovieWatched(1) } returns Result.failure(Exception("Failed to mark movie as watched"))
        viewModel.markMovieWatched(
            simklId = 1,
            primaryKey = "v2_1_theater",
            showTitle = "Movie Title"
        ) { ok, msg ->
            failureCalled = ok
            failureMsg = msg
        }
        advanceUntilIdle()

        assertTrue(successCalled)
        assertEquals("Marked Movie Title as watched", successMsg)
        assertFalse(failureCalled)
        assertEquals("Failed to mark movie as watched", failureMsg)
    }

    @Test
    fun testMarkEpisodeUnwatchedSuccessAndFailure() = runTest {
        val viewModel = createViewModel()
        coEvery { repositoryMock.markEpisodeUnwatched(1, 1, 2, MediaType.TV) } returns Result.success(Unit)

        var successCalled = false
        var successMsg = ""
        viewModel.markEpisodeUnwatched(
            simklId = 1,
            season = 1,
            episodeNumber = 2,
            mediaType = MediaType.TV,
            primaryKey = "v2_1_1_2",
            showTitle = "Show Title"
        ) { ok, msg ->
            successCalled = ok
            successMsg = msg
        }
        advanceUntilIdle()

        var failureCalled = true
        var failureMsg = ""
        coEvery { repositoryMock.markEpisodeUnwatched(1, 1, 2, MediaType.TV) } returns Result.failure(Exception("Failed to mark episode as unwatched"))
        viewModel.markEpisodeUnwatched(
            simklId = 1,
            season = 1,
            episodeNumber = 2,
            mediaType = MediaType.TV,
            primaryKey = "v2_1_1_2",
            showTitle = "Show Title"
        ) { ok, msg ->
            failureCalled = ok
            failureMsg = msg
        }
        advanceUntilIdle()

        assertTrue(successCalled)
        assertEquals("Marked Show Title S01E02 as unwatched", successMsg)
        assertFalse(failureCalled)
        assertEquals("Failed to mark episode as unwatched", failureMsg)
    }

    @Test
    fun testMarkSeasonUnwatchedSuccessAndFailure() = runTest {
        val viewModel = createViewModel()
        coEvery { repositoryMock.markSeasonUnwatched(1, 1, MediaType.TV) } returns Result.success(Unit)

        var successCalled = false
        var successMsg = ""
        viewModel.markSeasonUnwatched(
            simklId = 1,
            season = 1,
            mediaType = MediaType.TV,
            showTitle = "Show Title"
        ) { ok, msg ->
            successCalled = ok
            successMsg = msg
        }
        advanceUntilIdle()

        var failureCalled = true
        var failureMsg = ""
        coEvery { repositoryMock.markSeasonUnwatched(1, 1, MediaType.TV) } returns Result.failure(Exception("Failed to mark season as unwatched"))
        viewModel.markSeasonUnwatched(
            simklId = 1,
            season = 1,
            mediaType = MediaType.TV,
            showTitle = "Show Title"
        ) { ok, msg ->
            failureCalled = ok
            failureMsg = msg
        }
        advanceUntilIdle()

        assertTrue(successCalled)
        assertEquals("Marked Show Title Season 1 as unwatched", successMsg)
        assertFalse(failureCalled)
        assertEquals("Failed to mark season as unwatched", failureMsg)
    }

    @Test
    fun testMarkMovieUnwatchedSuccessAndFailure() = runTest {
        val viewModel = createViewModel()
        coEvery { repositoryMock.markMovieUnwatched(1) } returns Result.success(Unit)

        var successCalled = false
        var successMsg = ""
        viewModel.markMovieUnwatched(
            simklId = 1,
            primaryKey = "v2_1_theater",
            showTitle = "Movie Title"
        ) { ok, msg ->
            successCalled = ok
            successMsg = msg
        }
        advanceUntilIdle()

        var failureCalled = true
        var failureMsg = ""
        coEvery { repositoryMock.markMovieUnwatched(1) } returns Result.failure(Exception("Failed to mark movie as unwatched"))
        viewModel.markMovieUnwatched(
            simklId = 1,
            primaryKey = "v2_1_theater",
            showTitle = "Movie Title"
        ) { ok, msg ->
            failureCalled = ok
            failureMsg = msg
        }
        advanceUntilIdle()

        assertTrue(successCalled)
        assertEquals("Marked Movie Title as unwatched", successMsg)
        assertFalse(failureCalled)
        assertEquals("Failed to mark movie as unwatched", failureMsg)
    }

    @Test
    fun testSyncLocalCalendarNoToken() = runTest {
        val viewModel = createViewModel()
        coEvery { repositoryMock.getActiveUserToken() } returns null

        viewModel.syncLocalCalendar()
        advanceUntilIdle()

        val isSyncing = viewModel.isSyncing.value
        assertFalse(isSyncing)
    }

    @Test
    fun testForceWatchlistResyncNoTokenAndSuccess() = runTest {
        val viewModel = createViewModel()
        coEvery { repositoryMock.getActiveUserToken() } returns null

        var noTokenOk = true
        var noTokenMsg = ""
        viewModel.forceWatchlistResync { ok, msg ->
            noTokenOk = ok
            noTokenMsg = msg
        }
        advanceUntilIdle()

        coEvery { repositoryMock.getActiveUserToken() } returns UserToken(accessToken = "valid_token", username = "user")
        var successOk = false
        var successMsg = ""
        viewModel.forceWatchlistResync { ok, msg ->
            successOk = ok
            successMsg = msg
        }
        advanceUntilIdle()

        assertFalse(noTokenOk)
        assertEquals("User is not logged in", noTokenMsg)
        assertTrue(successOk)
        assertEquals("Watchlist re-synced successfully", successMsg)
    }

    @Test
    fun testCustomSearchLinkOperations() = runTest {
        val viewModel = createViewModel()
        val link1 = CustomSearchLink(id = 0L, name = "Link 1", urlTemplate = "https://test.com/{query}", position = 0)
        val link2 = CustomSearchLink(id = 2L, name = "Link 2", urlTemplate = "https://test2.com/{query}", position = 1)

        viewModel.saveCustomSearchLink(link1)
        viewModel.saveCustomSearchLink(link2)
        viewModel.updateSearchLinksOrder(listOf(link2, link1))
        viewModel.deleteCustomSearchLink(link2)
        advanceUntilIdle()

        coVerify { repositoryMock.insertSearchLink(any()) }
        coVerify { repositoryMock.updateSearchLink(link2) }
        coVerify { repositoryMock.updateSearchLinks(any()) }
        coVerify { repositoryMock.deleteSearchLink(link2) }
    }

    @Test
    fun testUpdateMediaStatusAndSeasonStatus() = runTest {
        val viewModel = createViewModel()
        val calendarItem = CalendarItem(
            primaryKey = "v2_1_1_1",
            simklId = 1,
            episodeTitle = "Ep 1",
            season = 1,
            episodeNumber = 1,
            date = Instant.now().minusSeconds(3600),
            movieReleaseType = null,
            isSeasonPremiere = true,
            isSeasonFinale = false
        )
        val watchlistItem = TrackedWatchlistItem(
            simklId = 1,
            type = MediaType.TV,
            title = "Test Show",
            titleRomaji = null,
            poster = "poster.jpg"
        )
        val itemWithWatchlist = CalendarItemWithWatchlist(
            calendarItem = calendarItem,
            watchlistItem = watchlistItem,
            localState = LocalItemState(primaryKey = "v2_1_1_1", mediaStatus = MediaStatus.WANTED),
            downloadSettings = null
        )
        calendarItemsFlow.value = listOf(itemWithWatchlist)

        viewModel.updateMediaStatus("v2_1_1_1", MediaStatus.WANTED)
        viewModel.updateSeasonMediaStatus(1, 1, MediaStatus.WANTED)
        viewModel.updateSeasonMediaStatus(1, 1, MediaStatus.IGNORED)
        advanceUntilIdle()

        coVerify { repositoryMock.updateMediaStatus("v2_1_1_1", MediaStatus.WANTED) }
        coVerify { repositoryMock.updateSeasonMediaStatus(1, 1, MediaStatus.WANTED) }
        coVerify { repositoryMock.updateSeasonMediaStatus(1, 1, MediaStatus.IGNORED) }
    }

    @Test
    fun testSearchAndDownloadEpisodeAndSeason() = runTest {
        val viewModel = createViewModel()
        val calendarItem = CalendarItem(
            primaryKey = "v2_1_1_1",
            simklId = 1,
            episodeTitle = "Ep 1",
            season = 1,
            episodeNumber = 1,
            date = Instant.now().minusSeconds(3600),
            movieReleaseType = null,
            isSeasonPremiere = true,
            isSeasonFinale = false
        )
        val watchlistItem = TrackedWatchlistItem(
            simklId = 1,
            type = MediaType.TV,
            title = "Test Show",
            titleRomaji = null,
            poster = "poster.jpg"
        )
        val itemWithWatchlist = CalendarItemWithWatchlist(
            calendarItem = calendarItem,
            watchlistItem = watchlistItem,
            downloadSettings = null
        )
        calendarItemsFlow.value = listOf(itemWithWatchlist)
        coEvery { repositoryMock.searchAndDownloadEpisode(itemWithWatchlist) } returns Result.failure(Exception("No torrent results found"))

        var episodeResultOk = true
        var episodeResultMsg = ""
        viewModel.searchAndDownloadEpisode(itemWithWatchlist) { ok, msg ->
            episodeResultOk = ok
            episodeResultMsg = msg
        }

        viewModel.searchAndDownloadSeason(1, 1)
        advanceUntilIdle()

        assertFalse(episodeResultOk)
        assertEquals("No torrent results found", episodeResultMsg)
    }

    @Test
    fun testRunAutoDownloadManualNoWantedItems() = runTest {
        val viewModel = createViewModel()
        calendarItemsFlow.value = emptyList()

        viewModel.runAutoDownloadManual()
        advanceUntilIdle()

        val status = viewModel.autoDownloadStatus.value
        assertEquals("", status)
    }

    @Test
    fun testRunAutoDownloadManualWithWantedItems() = runTest {
        val viewModel = createViewModel()
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, false, false, false, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.WANTED))
        calendarItemsFlow.value = listOf(item)

        viewModel.runAutoDownloadManual()
        advanceUntilIdle()

        coVerify { repositoryMock.searchAndDownloadWantedItems(any(), any()) }
    }

    @Test
    fun testOAuthAndLogoutAndNotificationToggle() = runTest {
        val viewModel = createViewModel()
        every { repositoryMock.createAuthorizationUrl(any()) } returns "https://simkl.com/auth"

        val authUrl = viewModel.createAuthorizationUrl()
        var exchangeSuccess = false
        viewModel.exchangeOAuthCode("code", "state", "simklcalendar://auth", onSuccess = {
            exchangeSuccess = true
        }, onFailure = {})

        viewModel.logoutUser()
        viewModel.toggleNotification(1, notifyEpisode = true, notifySeasonFinished = false)
        advanceUntilIdle()

        assertEquals("https://simkl.com/auth", authUrl)
        assertFalse(exchangeSuccess)
        coVerify { repositoryMock.logout() }
        coVerify { repositoryMock.toggleNotificationSetting(1, true, false) }
    }

    @Test
    fun testExchangeOAuthCodeSuccessCallback() = runTest {
        val viewModel = createViewModel()
        coEvery { repositoryMock.exchangeOAuthCode("code123", "state123", "simklcalendar://auth") } returns true

        var successCalled = false
        var failureCalled = false
        viewModel.exchangeOAuthCode("code123", "state123", "simklcalendar://auth", onSuccess = {
            successCalled = true
        }, onFailure = {
            failureCalled = true
        })
        advanceUntilIdle()

        assertTrue(successCalled)
        assertFalse(failureCalled)
    }

    @Test
    fun testIsTorrentServiceInstalled() = runTest {
        val viewModel = createViewModel()
        every { repositoryMock.torrentServiceHelper.isServiceInstalled() } returns true

        val installed = viewModel.isTorrentServiceInstalled()

        assertTrue(installed)
    }

    @Test
    fun testWatchlistTableItemsFilteringAndSorting() = runTest {
        val watchlistItem1 = TrackedWatchlistItem(
            simklId = 1,
            type = MediaType.TV,
            title = "Alpha Show",
            titleRomaji = "Romaji Alpha",
            poster = "poster1.jpg"
        )
        val watchlistItem2 = TrackedWatchlistItem(
            simklId = 2,
            type = MediaType.ANIME,
            title = "Beta Anime",
            titleRomaji = null,
            poster = "poster2.jpg"
        )
        val watchlistItem3 = TrackedWatchlistItem(
            simklId = 3,
            type = MediaType.MOVIE,
            title = "Charlie Movie",
            titleRomaji = null,
            poster = "poster3.jpg"
        )
        watchlistItemsFlow.value = listOf(watchlistItem1, watchlistItem2, watchlistItem3)

        val now = Instant.now()
        val calItem1 = CalendarItemWithWatchlist(
            calendarItem = CalendarItem(
                primaryKey = "v2_1_1_1",
                simklId = 1,
                episodeTitle = "Ep 1",
                season = 1,
                episodeNumber = 1,
                date = now.minusSeconds(7200),
                movieReleaseType = null,
                isSeasonPremiere = true,
                isSeasonFinale = false,
                watchedAt = null
            ),
            watchlistItem = watchlistItem1,
            localState = LocalItemState(primaryKey = "v2_1_1_1", mediaStatus = MediaStatus.DOWNLOADED)
        )
        val calItem2 = CalendarItemWithWatchlist(
            calendarItem = CalendarItem(
                primaryKey = "v2_2_1_1",
                simklId = 2,
                episodeTitle = "Ep 1",
                season = 1,
                episodeNumber = 1,
                date = now.plusSeconds(7200),
                movieReleaseType = null,
                isSeasonPremiere = true,
                isSeasonFinale = false,
                watchedAt = null
            ),
            watchlistItem = watchlistItem2,
            localState = LocalItemState(primaryKey = "v2_2_1_1", mediaStatus = MediaStatus.WANTED)
        )
        calendarItemsFlow.value = listOf(calItem1, calItem2)

        val viewModel = createViewModel()

        val items = viewModel.watchlistTableItems.first()
        viewModel.toggleTableSort(TableSortField.LAST_EP)
        val itemsSortLastEp = viewModel.watchlistTableItems.first()
        viewModel.toggleTableSort(TableSortField.NEXT_EP)
        val itemsSortNextEp = viewModel.watchlistTableItems.first()
        viewModel.toggleTableSort(TableSortField.WATCHED)
        val itemsSortWatched = viewModel.watchlistTableItems.first()
        viewModel.toggleTableSort(TableSortField.DOWNLOADED)
        val itemsSortDownloaded = viewModel.watchlistTableItems.first()

        assertTrue(items.isNotEmpty())
        assertTrue(itemsSortLastEp.isNotEmpty())
        assertTrue(itemsSortNextEp.isNotEmpty())
        assertTrue(itemsSortWatched.isNotEmpty())
        assertTrue(itemsSortDownloaded.isNotEmpty())
    }

    @Test
    fun testFilteredCalendarItems() = runTest {
        val now = Instant.now()
        val watchlistItem = TrackedWatchlistItem(
            simklId = 1,
            type = MediaType.TV,
            title = "Super Show",
            titleRomaji = "Super Romaji",
            poster = "poster.jpg"
        )
        val calItem1 = CalendarItemWithWatchlist(
            calendarItem = CalendarItem(
                primaryKey = "v2_1_1_1",
                simklId = 1,
                episodeTitle = "Special Premiere",
                season = 1,
                episodeNumber = 1,
                date = now.minusSeconds(3600),
                movieReleaseType = null,
                isSeasonPremiere = true,
                isSeasonFinale = false,
                watchedAt = null
            ),
            watchlistItem = watchlistItem,
            localState = LocalItemState(primaryKey = "v2_1_1_1", mediaStatus = MediaStatus.WANTED)
        )
        val calItem2 = CalendarItemWithWatchlist(
            calendarItem = CalendarItem(
                primaryKey = "v2_1_1_10",
                simklId = 1,
                episodeTitle = "Grand Finale",
                season = 1,
                episodeNumber = 10,
                date = now.plusSeconds(3600),
                movieReleaseType = null,
                isSeasonPremiere = false,
                isSeasonFinale = true,
                watchedAt = null
            ),
            watchlistItem = watchlistItem,
            localState = LocalItemState(primaryKey = "v2_1_1_10", mediaStatus = MediaStatus.WANTED)
        )
        calendarItemsFlow.value = listOf(calItem1, calItem2)

        val viewModel = createViewModel()

        val allItems = viewModel.filteredCalendarItems.first()
        viewModel.setSearchQuery("Grand")
        val searchItems = viewModel.filteredCalendarItems.first()
        viewModel.setSearchQuery("Super Romaji")
        val romajiItems = viewModel.filteredCalendarItems.first()

        assertEquals(2, allItems.size)
        assertEquals(1, searchItems.size)
        assertEquals(2, romajiItems.size)
    }
}
