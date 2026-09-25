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
    private lateinit var userRepositoryMock: UserRepository
    private lateinit var calendarRepositoryMock: CalendarRepository
    private lateinit var syncRepositoryMock: SyncRepository
    private lateinit var watchHistoryRepositoryMock: WatchHistoryRepository
    private lateinit var downloadRepositoryMock: DownloadRepository
    private lateinit var customSearchLinkRepositoryMock: CustomSearchLinkRepository
    private lateinit var notificationSettingRepositoryMock: NotificationSettingRepository

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
        userRepositoryMock = mockk(relaxed = true)
        calendarRepositoryMock = mockk(relaxed = true)
        syncRepositoryMock = mockk(relaxed = true)
        watchHistoryRepositoryMock = mockk(relaxed = true)
        downloadRepositoryMock = mockk(relaxed = true)
        customSearchLinkRepositoryMock = mockk(relaxed = true)
        notificationSettingRepositoryMock = mockk(relaxed = true)

        uiRepo = mockk(relaxed = true)
        notificationRepo = mockk(relaxed = true)
        autoDownloadRepo = mockk(relaxed = true)
        torrentServiceHelper = mockk(relaxed = true)

        every { uiRepo.preferencesFlow } returns uiPreferencesFlow
        every { notificationRepo.preferencesFlow } returns notificationPreferencesFlow
        every { autoDownloadRepo.preferencesFlow } returns flowOf(AutoDownloadPreferences())

        coEvery { syncRepositoryMock.syncCalendar(any()) } returns Unit
        coEvery { syncRepositoryMock.syncCalendar() } returns Unit
        coEvery { userRepositoryMock.getActiveUserToken() } returns null

        every { application.applicationContext } returns application

        every { calendarRepositoryMock.calendarItems } returns calendarItemsFlow
        every { userRepositoryMock.activeUserToken } returns userTokenFlow
        every { calendarRepositoryMock.watchlistItems } returns watchlistItemsFlow
        every { customSearchLinkRepositoryMock.customSearchLinks } returns customSearchLinksFlow
        every { notificationSettingRepositoryMock.notificationSettings } returns flowOf(emptyList())
        every { watchHistoryRepositoryMock.watchedEpisodes } returns flowOf(emptyList())
        every { torrentServiceHelper.downloads } returns MutableStateFlow(emptyMap())
        every { torrentServiceHelper.isBound } returns MutableStateFlow(false)
        every { torrentServiceHelper.isInstalled } returns MutableStateFlow(false)
        coEvery { downloadRepositoryMock.searchAndDownloadEpisode(any()) } returns Result.failure(Exception("No torrents"))
    }

    private fun createViewModel(): CalendarViewModel {
        userTokenFlow.value = null
        return CalendarViewModel(
            userRepository = userRepositoryMock,
            calendarRepository = calendarRepositoryMock,
            syncRepository = syncRepositoryMock,
            watchHistoryRepository = watchHistoryRepositoryMock,
            downloadRepository = downloadRepositoryMock,
            customSearchLinkRepository = customSearchLinkRepositoryMock,
            notificationSettingRepository = notificationSettingRepositoryMock,
            autoDownloadRepo = autoDownloadRepo,
            notificationRepo = notificationRepo,
            uiRepo = uiRepo,
            torrentServiceHelper = torrentServiceHelper,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Dispatchers::class)
        unmockkStatic(Environment::class)
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
    fun testItemDownloadSettings() = runTest {
        val viewModel = createViewModel()
        val settings = ItemDownloadSettings(simklId = 10, qualityOverride = "1080p")
        every { downloadRepositoryMock.getItemDownloadSettingsFlow(10) } returns flowOf(settings)

        viewModel.saveItemDownloadSettings(settings)
        advanceUntilIdle()
        val retrievedFlow = viewModel.getItemDownloadSettingsFlow(10)
        val retrievedSettings = retrievedFlow.first()

        coVerify { downloadRepositoryMock.saveItemDownloadSettings(settings) }
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
        coEvery { watchHistoryRepositoryMock.markEpisodeWatched(1, 1, 2, MediaType.TV) } returns Result.success(Unit)

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
        coEvery { watchHistoryRepositoryMock.markEpisodeWatched(1, 1, 2, MediaType.TV) } returns Result.failure(Exception("Failed to mark episode as watched"))
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
        coEvery { watchHistoryRepositoryMock.markSeasonWatched(1, 1, MediaType.TV) } returns Result.success(false)

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
        coEvery { watchHistoryRepositoryMock.markSeasonWatched(1, 1, MediaType.TV) } returns Result.failure(Exception("Failed to mark season as watched"))
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
        coEvery { watchHistoryRepositoryMock.markMovieWatched(1) } returns Result.success(Unit)

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
        coEvery { watchHistoryRepositoryMock.markMovieWatched(1) } returns Result.failure(Exception("Failed to mark movie as watched"))
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
        coEvery { watchHistoryRepositoryMock.markEpisodeUnwatched(1, 1, 2, MediaType.TV) } returns Result.success(Unit)

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
        coEvery { watchHistoryRepositoryMock.markEpisodeUnwatched(1, 1, 2, MediaType.TV) } returns Result.failure(Exception("Failed to mark episode as unwatched"))
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
        coEvery { watchHistoryRepositoryMock.markSeasonUnwatched(1, 1, MediaType.TV) } returns Result.success(Unit)

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
        coEvery { watchHistoryRepositoryMock.markSeasonUnwatched(1, 1, MediaType.TV) } returns Result.failure(Exception("Failed to mark season as unwatched"))
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
        coEvery { watchHistoryRepositoryMock.markMovieUnwatched(1) } returns Result.success(Unit)

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
        coEvery { watchHistoryRepositoryMock.markMovieUnwatched(1) } returns Result.failure(Exception("Failed to mark movie as unwatched"))
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
        coEvery { userRepositoryMock.getActiveUserToken() } returns null

        viewModel.syncLocalCalendar()
        advanceUntilIdle()

        val isSyncing = viewModel.isSyncing.value
        assertFalse(isSyncing)
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

        coVerify { calendarRepositoryMock.updateMediaStatus("v2_1_1_1", MediaStatus.WANTED) }
        coVerify { calendarRepositoryMock.updateSeasonMediaStatus(1, 1, MediaStatus.WANTED) }
        coVerify { calendarRepositoryMock.updateSeasonMediaStatus(1, 1, MediaStatus.IGNORED) }
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
        coEvery { downloadRepositoryMock.searchAndDownloadEpisode(itemWithWatchlist) } returns Result.failure(Exception("No torrent results found"))

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

        coVerify { downloadRepositoryMock.searchAndDownloadWantedItems(any(), any()) }
    }

    @Test
    fun testNotificationToggle() = runTest {
        val viewModel = createViewModel()

        viewModel.toggleNotification(1, notifyEpisode = true, notifySeasonFinished = false)
        advanceUntilIdle()

        coVerify { notificationSettingRepositoryMock.toggleNotificationSetting(1, true, false) }
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
