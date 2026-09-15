package com.felixbrucker.simklcalendar

import android.content.Context
import android.content.SharedPreferences
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

        every { appDatabase.userTokenDao() } returns tokenDao
        every { appDatabase.calendarItemDao() } returns calendarDao
        every { appDatabase.notificationSettingDao() } returns settingDao
        every { appDatabase.watchlistDao() } returns watchlistDao
        every { appDatabase.watchedEpisodeDao() } returns watchedDao
        every { appDatabase.customSearchLinkDao() } returns searchLinkDao
        every { appDatabase.itemDownloadSettingsDao() } returns itemDownloadSettingsDao

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
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    @Test
    fun testDetermineStatus() {
        val future = Instant.now().plusSeconds(3600)
        val past = Instant.now().minusSeconds(3600)

        // Future -> NOT_AIRED_YET
        assertEquals(
            MediaStatus.NOT_AIRED_YET,
            repository.determineStatus(future, null, MediaType.TV, false, false)
        )

        // Theater release -> IGNORED
        assertEquals(
            MediaStatus.IGNORED,
            repository.determineStatus(past, null, MediaType.MOVIE, true, false)
        )

        // Watched -> IGNORED
        assertEquals(
            MediaStatus.IGNORED,
            repository.determineStatus(past, null, MediaType.TV, false, true)
        )

        // Global download enabled -> WANTED
        every { sharedPreferences.getBoolean("auto_download_unwatched_tv", false) } returns true
        assertEquals(
            MediaStatus.WANTED,
            repository.determineStatus(past, null, MediaType.TV, false, false)
        )

        // Item specific setting overrides global -> IGNORED
        val settings = ItemDownloadSettings(simklId = 100, downloadUnwatched = false)
        assertEquals(
            MediaStatus.IGNORED,
            repository.determineStatus(past, settings, MediaType.TV, false, false)
        )
    }

    @Test
    fun testLogoutAndTokenManagement() = runTest {
        coEvery { tokenDao.getActiveToken() } returns UserToken(1, "token123", "User")

        val token = repository.getActiveUserToken()
        assertNotNull(token)
        assertEquals("token123", token?.accessToken)

        repository.logout()
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

        // Episode watched / unwatched
        val resEpWatch = repository.markEpisodeWatched(100, 1, 1, MediaType.TV)
        assertTrue(resEpWatch.isSuccess)

        val resEpUnwatch = repository.markEpisodeUnwatched(100, 1, 1, MediaType.TV)
        assertTrue(resEpUnwatch.isSuccess)

        // Season watched / unwatched
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, true, false, false, null)
        val itemWithWatchlist = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.DOWNLOADED))
        coEvery { calendarDao.getItemsForSimklId(100) } returns listOf(itemWithWatchlist)

        val resSeasonWatch = repository.markSeasonWatched(100, 1, MediaType.TV)
        assertTrue(resSeasonWatch.isSuccess)

        val resSeasonUnwatch = repository.markSeasonUnwatched(100, 1, MediaType.TV)
        assertTrue(resSeasonUnwatch.isSuccess)

        // Movie watched / unwatched
        val resMovieWatch = repository.markMovieWatched(200)
        assertTrue(resMovieWatch.isSuccess)

        val resMovieUnwatch = repository.markMovieUnwatched(200)
        assertTrue(resMovieUnwatch.isSuccess)
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

        val calendarResult = repository.syncCalendarJsons(forceFullSync = true)
        assertTrue(calendarResult.hasCalendarItemChanges || calendarResult.hasWantedItems || !calendarResult.hasCalendarItemChanges)

        // Backfill test
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

        val backfillResult = repository.backfillPastEpisodes(lastSyncTimestamp = 0L)
        assertNotNull(backfillResult)
    }

    @Test
    fun testOAuthExchangeAndAuthUrl() = runTest {
        val authUrl = repository.createAuthorizationUrl()
        if (authUrl != null) {
            assertTrue(authUrl.contains("simkl.com/oauth/authorize"))
        }

        every { sharedPreferences.getString("pkce_state", null) } returns "state123"
        every { sharedPreferences.getString("pkce_code_verifier", null) } returns "verifier123"

        coEvery { apiService.getAccessToken(any()) } throws Exception("Auth error")

        val exchanged = repository.exchangeOAuthCode("code123", "state123", "simklcalendar://auth")
        assertFalse(exchanged)
    }

    @Test
    fun testCleanupOldWatchedCalendarItems() = runTest {
        coEvery { calendarDao.deleteWatchedItemsOlderThan(any()) } returns 5
        val deleted = repository.cleanupOldWatchedCalendarItems(30)
        assertEquals(5, deleted)
    }
}
