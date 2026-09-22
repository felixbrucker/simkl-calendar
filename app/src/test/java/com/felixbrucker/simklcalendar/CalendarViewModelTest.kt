package com.felixbrucker.simklcalendar.ui.viewmodel

import android.app.Application
import android.os.Environment
import com.felixbrucker.simklcalendar.data.database.*
import com.felixbrucker.simklcalendar.data.model.*
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import com.felixbrucker.simklcalendar.data.preferences.*
import com.felixbrucker.simklcalendar.data.repository.*
import com.felixbrucker.simklcalendar.data.preferences.ViewMode
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var application: Application
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

    private val uiPreferencesFlow = MutableStateFlow(UiPreferences())
    private val notificationPreferencesFlow = MutableStateFlow(NotificationPreferences())
    private val appSettingsPreferencesFlow = MutableStateFlow(AppSettingsPreferences())
    private val authPreferencesFlow = MutableStateFlow(AuthPreferences())

    private lateinit var uiRepo: UiRepository
    private lateinit var notificationRepo: NotificationRepository
    private lateinit var appSettingsRepo: AppSettingsRepository
    private lateinit var authRepo: AuthRepository
    private lateinit var autoDownloadRepo: AutoDownloadRepository
    private lateinit var torrentServiceHelper: TorrentServiceHelper

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
        uiPreferencesFlow.value = UiPreferences()
        notificationPreferencesFlow.value = NotificationPreferences()
        appSettingsPreferencesFlow.value = AppSettingsPreferences()
        authPreferencesFlow.value = AuthPreferences()

        application = mockk(relaxed = true)
        appDatabase = mockk(relaxed = true)
        repositoryMock = mockk(relaxed = true)

        uiRepo = mockk(relaxed = true)
        notificationRepo = mockk(relaxed = true)
        appSettingsRepo = mockk(relaxed = true)
        authRepo = mockk(relaxed = true)
        autoDownloadRepo = mockk(relaxed = true)
        torrentServiceHelper = mockk(relaxed = true)

        every { uiRepo.preferencesFlow } returns uiPreferencesFlow
        every { notificationRepo.preferencesFlow } returns notificationPreferencesFlow
        every { appSettingsRepo.preferencesFlow } returns appSettingsPreferencesFlow
        every { authRepo.preferencesFlow } returns authPreferencesFlow
        every { autoDownloadRepo.preferencesFlow } returns flowOf(AutoDownloadPreferences())

        coEvery { repositoryMock.syncCalendar(any()) } returns Unit
        coEvery { repositoryMock.syncCalendar() } returns Unit
        coEvery { repositoryMock.getActiveUserToken() } returns null

        every { application.applicationContext } returns application

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
        every { torrentServiceHelper.downloads } returns MutableStateFlow(emptyMap())
        every { torrentServiceHelper.isBound } returns MutableStateFlow(false)
        every { torrentServiceHelper.isInstalled } returns MutableStateFlow(false)
        coEvery { repositoryMock.searchAndDownloadEpisode(any()) } returns Result.failure(Exception("No torrents"))
    }

    private fun createViewModel(): CalendarViewModel {
        userTokenFlow.value = null
        return CalendarViewModel(
            application = application,
            repository = repositoryMock,
            appSettingsRepo = appSettingsRepo,
            autoDownloadRepo = autoDownloadRepo,
            notificationRepo = notificationRepo,
            authRepo = authRepo,
            syncMetadataRepo = mockk(relaxed = true),
            uiRepo = uiRepo,
            torrentServiceHelper = torrentServiceHelper
        )
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

        viewModel.setViewMode(ViewMode.TABLE)
        advanceUntilIdle()

        coVerify { uiRepo.setViewMode(ViewMode.TABLE) }
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
        viewModel.toggleShowAnime()
        viewModel.toggleShowMovies()
        viewModel.toggleShowOnlyUnwatchedReleased()
        viewModel.toggleOnlySeasonPremieres()
        viewModel.toggleOnlySeasonFinales()
        viewModel.toggleOnlyDigitalDvd()
        viewModel.toggleShowEarlierReleases()
        viewModel.setShowEarlierReleases(true)
        viewModel.resetFilters()

        viewModel.setSearchQuery("Naruto")
        val query = viewModel.searchQuery.value
        viewModel.clearSearchQuery()
        val clearedQuery = viewModel.searchQuery.value

        coVerify { uiRepo.updateFilters(any()) }
        assertEquals("Naruto", query)
        assertEquals("", clearedQuery)
    }

    @Test
    fun testAutoDownloadPreferences() = runTest {
        val viewModel = createViewModel()

        viewModel.updateAutoDownloadQuality("720p")
        viewModel.updateAutoDownloadPreferHevc(false)
        viewModel.updateAutoDownloadUnwatchedTv(true)
        viewModel.updateAutoDownloadUnwatchedAnime(true)
        viewModel.updateAutoDownloadUnwatchedMovie(true)
        viewModel.updateAutoDownloadSeasonUnwatchedTv(true)
        viewModel.updateAutoDownloadSeasonUnwatchedAnime(true)

        viewModel.addPreferredKeyword("SubsPlease")
        viewModel.removePreferredKeyword("SubsPlease")
        viewModel.updatePreferredKeywordsOrder(listOf("B", "A"))

        viewModel.addIgnoreKeyword("RAW")
        viewModel.removeIgnoreKeyword("RAW")
        viewModel.updateIgnoreKeywordsOrder(listOf("Y", "X"))

        coVerify { autoDownloadRepo.setQuality("720p") }
        coVerify { autoDownloadRepo.setPreferHevc(false) }
        coVerify { autoDownloadRepo.setAutoDownloadUnwatchedTv(true) }
        coVerify { autoDownloadRepo.setAutoDownloadUnwatchedAnime(true) }
        coVerify { autoDownloadRepo.setAutoDownloadUnwatchedMovie(true) }
        coVerify { autoDownloadRepo.setAutoDownloadSeasonUnwatchedTv(true) }
        coVerify { autoDownloadRepo.setAutoDownloadSeasonUnwatchedAnime(true) }
        coVerify { autoDownloadRepo.setPreferredKeywords(any()) }
        coVerify { autoDownloadRepo.setIgnoreKeywords(any()) }
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
        every { torrentServiceHelper.isServiceInstalled() } returns true

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
