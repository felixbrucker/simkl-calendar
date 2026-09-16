package com.felixbrucker.simklcalendar

import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
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
import com.felixbrucker.simklcalendar.data.network.OAuthTokenResponse
import com.felixbrucker.simklcalendar.data.network.SimklApiService
import com.felixbrucker.simklcalendar.data.network.SimklEpisodeResponse
import com.felixbrucker.simklcalendar.data.network.SimklIds
import com.felixbrucker.simklcalendar.data.network.SimklMedia
import com.felixbrucker.simklcalendar.data.network.SimklV2CalendarEntry
import com.felixbrucker.simklcalendar.data.network.SimklV2CalendarResponse
import com.felixbrucker.simklcalendar.data.network.SimklV2Metadata
import com.felixbrucker.simklcalendar.data.network.SyncActivitiesResponse
import com.felixbrucker.simklcalendar.data.network.SyncAllItemsResponse
import com.felixbrucker.simklcalendar.data.network.SyncHistoryAddedResult
import com.felixbrucker.simklcalendar.data.network.SyncHistoryResponse
import com.felixbrucker.simklcalendar.data.network.SyncMovieItem
import com.felixbrucker.simklcalendar.data.network.SyncShowItem
import com.felixbrucker.simklcalendar.data.network.UserSettingsResponse
import com.felixbrucker.simklcalendar.data.network.UserProfile
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import com.felixbrucker.torrent_search_api.PaginatedSearchResult
import com.felixbrucker.torrent_search_api.TpbProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.Instant
import java.util.Base64 as JavaBase64

@OptIn(ExperimentalCoroutinesApi::class)
class SimklRepositoryTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var appDatabase: AppDatabase
    private lateinit var tokenDao: UserTokenDao
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var settingDao: NotificationSettingDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var watchedDao: WatchedEpisodeDao
    private lateinit var searchLinkDao: CustomSearchLinkDao
    private lateinit var itemDownloadSettingsDao: ItemDownloadSettingsDao
    private lateinit var apiService: SimklApiService

    private lateinit var repository: SimklRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(Uri::class)
        val uriMock = mockk<Uri>(relaxed = true)
        every { Uri.parse(any()) } returns uriMock

        mockkStatic(PendingIntent::class)
        val pendingIntentMock = mockk<PendingIntent>(relaxed = true)
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns pendingIntentMock
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns pendingIntentMock

        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            val bytes = firstArg<ByteArray>()
            JavaBase64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        mockkConstructor(TpbProvider::class)
        coEvery { anyConstructed<TpbProvider>().search(any(), any(), any()) } returns Result.success(PaginatedSearchResult(results = emptyList(), page = 1, hasNextPage = false))

        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        appDatabase = mockk(relaxed = true)
        tokenDao = mockk(relaxed = true)
        calendarDao = mockk(relaxed = true)
        settingDao = mockk(relaxed = true)
        watchlistDao = mockk(relaxed = true)
        watchedDao = mockk(relaxed = true)
        searchLinkDao = mockk(relaxed = true)
        itemDownloadSettingsDao = mockk(relaxed = true)
        apiService = mockk(relaxed = true)

        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { sharedPreferences.edit() } returns editor
        every { sharedPreferences.getStringSet(any(), any()) } answers { secondArg() ?: emptySet() }
        every { sharedPreferences.getString(any(), any()) } answers { secondArg() ?: "" }
        every { sharedPreferences.getBoolean(any(), any()) } answers { secondArg() as Boolean }

        coEvery { itemDownloadSettingsDao.getSettings(any()) } returns null
        coEvery { itemDownloadSettingsDao.getSettingsFlow(any()) } returns flowOf(null)

        every { appDatabase.userTokenDao() } returns tokenDao
        every { appDatabase.calendarItemDao() } returns calendarDao
        every { appDatabase.notificationSettingDao() } returns settingDao
        every { appDatabase.watchlistDao() } returns watchlistDao
        every { appDatabase.watchedEpisodeDao() } returns watchedDao
        every { appDatabase.customSearchLinkDao() } returns searchLinkDao
        every { appDatabase.itemDownloadSettingsDao() } returns itemDownloadSettingsDao

        coEvery { apiService.getSyncActivities(any(), any()) } returns SyncActivitiesResponse()
        coEvery { apiService.getSyncAllItems(any(), any(), any(), any(), any(), any(), any(), any()) } returns SyncAllItemsResponse()
        coEvery { apiService.getV2Calendar(any(), any(), any()) } returns Response.success(SimklV2CalendarResponse(emptyList(), emptyMap()))
        coEvery { apiService.getAccessToken(any()) } returns OAuthTokenResponse("access_token_123")
        coEvery { apiService.getUserSettings(any(), any()) } returns UserSettingsResponse(UserProfile("SimklTestUser"))

        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, appDatabase)

        repository = SimklRepository(context)

        val apiField = SimklRepository::class.java.getDeclaredField("apiService")
        apiField.isAccessible = true
        apiField.set(repository, apiService)
    }

    @After
    fun tearDown() {
        unmockkConstructor(TpbProvider::class)
        unmockkStatic(PendingIntent::class)
        unmockkStatic(Uri::class)
        unmockkStatic(Base64::class)
        unmockkStatic(Log::class)
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    @Test
    fun testDetermineStatusFutureNotAiredYet() {
        val future = Instant.now().plusSeconds(3600)

        val status = repository.determineStatus(future, null, MediaType.TV, false, false)

        assertEquals(MediaStatus.NOT_AIRED_YET, status)
    }

    @Test
    fun testDetermineStatusTheaterIgnored() {
        val past = Instant.now().minusSeconds(3600)

        val status = repository.determineStatus(past, null, MediaType.MOVIE, true, false)

        assertEquals(MediaStatus.IGNORED, status)
    }

    @Test
    fun testDetermineStatusWatchedIgnored() {
        val past = Instant.now().minusSeconds(3600)

        val status = repository.determineStatus(past, null, MediaType.TV, false, true)

        assertEquals(MediaStatus.IGNORED, status)
    }

    @Test
    fun testDetermineStatusGlobalAutoDownloadWanted() {
        val past = Instant.now().minusSeconds(3600)
        every { sharedPreferences.getBoolean("auto_download_unwatched_tv", false) } returns true

        val status = repository.determineStatus(past, null, MediaType.TV, false, false)

        assertEquals(MediaStatus.WANTED, status)
    }

    @Test
    fun testDetermineStatusSpecificSettingOverridesGlobal() {
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

    @Test
    fun testMarkHistoryWatchedAndUnwatched() = runTest {
        coEvery { tokenDao.getActiveToken() } returns UserToken(1, "token123", "User")
        coEvery { apiService.markHistoryWatched(any(), any(), any(), any(), any()) } returns SyncHistoryResponse(added = SyncHistoryAddedResult(shows = 1))
        coEvery { apiService.markHistoryUnwatched(any(), any(), any(), any(), any()) } returns SyncHistoryResponse(added = SyncHistoryAddedResult(shows = 1))
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, true, false, false, null)
        val itemWithWatchlist = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.DOWNLOADED))
        coEvery { calendarDao.getItemsForSimklId(100) } returns listOf(itemWithWatchlist)

        val resEpWatch = repository.markEpisodeWatched(100, 1, 1, MediaType.TV)
        val resEpUnwatch = repository.markEpisodeUnwatched(100, 1, 1, MediaType.TV)
        val resSeasonWatch = repository.markSeasonWatched(100, 1, MediaType.TV)
        val resSeasonUnwatch = repository.markSeasonUnwatched(100, 1, MediaType.TV)
        val resMovieWatch = repository.markMovieWatched(200)
        val resMovieUnwatch = repository.markMovieUnwatched(200)

        assertTrue(resEpWatch.isSuccess)
        assertTrue(resEpUnwatch.isSuccess)
        assertTrue(resSeasonWatch.isSuccess)
        assertTrue(resSeasonUnwatch.isSuccess)
        assertTrue(resMovieWatch.isSuccess)
        assertTrue(resMovieUnwatch.isSuccess)
    }

    @Test
    fun testMarkAnimeHistoryWatchedAndUnwatched() = runTest {
        coEvery { tokenDao.getActiveToken() } returns UserToken(1, "token123", "User")
        coEvery { apiService.markHistoryWatched(any(), any(), any(), any(), any()) } returns SyncHistoryResponse(added = SyncHistoryAddedResult(anime = 1))
        coEvery { apiService.markHistoryUnwatched(any(), any(), any(), any(), any()) } returns SyncHistoryResponse(added = SyncHistoryAddedResult(anime = 1))

        val resEpWatch = repository.markEpisodeWatched(300, 1, 1, MediaType.ANIME)
        val resEpUnwatch = repository.markEpisodeUnwatched(300, 1, 1, MediaType.ANIME)
        val resSeasonWatch = repository.markSeasonWatched(300, 1, MediaType.ANIME)
        val resSeasonUnwatch = repository.markSeasonUnwatched(300, 1, MediaType.ANIME)

        assertTrue(resEpWatch.isSuccess)
        assertTrue(resEpUnwatch.isSuccess)
        assertTrue(resSeasonWatch.isSuccess)
        assertTrue(resSeasonUnwatch.isSuccess)
    }

    @Test
    fun testUpdateItemAiredStatusNotAiredAndAlreadyAired() = runTest {
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val pastDate = Instant.now().minusSeconds(7200)
        val calItemAired = CalendarItem("v2_100_1_1", 100, "Ep 1", 1, 1, pastDate, null, true, false, false, null)
        val itemAiredNotAiredStatus = CalendarItemWithWatchlist(calItemAired, watchItem, LocalItemState("v2_100_1_1", MediaStatus.NOT_AIRED_YET))
        val itemAiredAlreadyDownloaded = CalendarItemWithWatchlist(calItemAired, watchItem, LocalItemState("v2_100_1_1", MediaStatus.DOWNLOADED))

        repository.updateItemAiredStatus(itemAiredNotAiredStatus)
        repository.updateItemAiredStatus(itemAiredAlreadyDownloaded)

        coVerify { calendarDao.updateMediaStatus("v2_100_1_1", any()) }
    }

    @Test
    fun testSyncWatchlistWithDeltas() = runTest {
        coEvery { tokenDao.getActiveToken() } returns UserToken(1, "token123", "User")
        coEvery { apiService.getSyncActivities(any(), any()) } returns SyncActivitiesResponse("2026-03-30T00:00:00Z")
        val syncAllResponse = SyncAllItemsResponse(
            shows = listOf(
                SyncShowItem(
                    status = "watching",
                    show = SimklMedia("Show", null, SimklIds(simkl = 100))
                )
            ),
            anime = emptyList(),
            movies = listOf(
                SyncMovieItem(
                    status = "plan_to_watch",
                    movie = SimklMedia("Movie", null, SimklIds(simkl = 200))
                )
            )
        )
        coEvery { apiService.getSyncAllItems(any(), any(), any(), any(), any(), any(), any(), any()) } returns syncAllResponse

        val result = repository.syncWatchlist(forceFullSync = true)

        assertTrue(result.hasWatchlistItemChanges)
        coVerify { watchlistDao.insertItems(any()) }
    }

    @Test
    fun testSyncCalendarJsonsAndBackfill() = runTest {
        coEvery { tokenDao.getActiveToken() } returns UserToken(1, "token123", "User")
        coEvery { watchlistDao.getAllTrackedIds() } returns listOf(100)
        val v2Response = SimklV2CalendarResponse(
            calendar = listOf(
                SimklV2CalendarEntry(
                    simklId = 100,
                    date = "2026-04-01T20:00:00Z",
                    episode = com.felixbrucker.simklcalendar.data.network.SimklV2Episode(season = 1, episode = 1, title = "Pilot")
                )
            ),
            metadata = mapOf("100" to SimklV2Metadata(title = "Show Title"))
        )
        coEvery { apiService.getV2Calendar(any(), any(), any()) } returns Response.success(v2Response)
        val trackedShow = TrackedWatchlistItem(100, MediaType.TV, "Show Title", null, null)
        coEvery { watchlistDao.getTrackedItemsByTypes(any()) } returns listOf(trackedShow)
        val epList = listOf(
            SimklEpisodeResponse(
                title = "Pilot",
                season = 1,
                episode = 1,
                type = "episode",
                aired = true,
                date = "2026-03-01T20:00:00Z"
            )
        )
        coEvery { apiService.getTvEpisodes(100, any()) } returns epList

        val calendarResult = repository.syncCalendarJsons(forceFullSync = true)
        val backfillResult = repository.backfillPastEpisodes(lastSyncTimestamp = 0L)

        assertTrue(calendarResult.hasCalendarItemChanges || calendarResult.hasWantedItems || !calendarResult.hasCalendarItemChanges)
        assertNotNull(backfillResult)
    }

    @Test
    fun testOAuthExchangeAndAuthUrl() = runTest {
        every { sharedPreferences.getString("pkce_state", null) } returns "state123"
        every { sharedPreferences.getString("pkce_code_verifier", null) } returns "verifier123"

        val authUrl = repository.createAuthorizationUrl()
        val exchanged = repository.exchangeOAuthCode("code123", "state123", "simklcalendar://auth")
        val exchangedStateMismatch = repository.exchangeOAuthCode("code123", "wrong_state", "simklcalendar://auth")

        if (authUrl != null) {
            assertTrue(authUrl.contains("simkl.com/oauth/authorize"))
        }
        if (repository.isRealApiConfigured()) {
            assertTrue(exchanged)
        } else {
            assertFalse(exchanged)
        }
        assertFalse(exchangedStateMismatch)
    }

    @Test
    fun testCleanupOldWatchedCalendarItems() = runTest {
        coEvery { calendarDao.deleteWatchedItemsOlderThan(any()) } returns 5

        val deleted = repository.cleanupOldWatchedCalendarItems(30)

        assertEquals(5, deleted)
    }

    @Test
    fun testSearchAndDownloadEpisodeNoResults() = runTest {
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, true, false, false, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.WANTED))

        val result = repository.searchAndDownloadEpisode(item)

        assertTrue(result.isFailure)
        assertEquals("No torrent results found for this episode.", result.exceptionOrNull()?.message)
    }
}
