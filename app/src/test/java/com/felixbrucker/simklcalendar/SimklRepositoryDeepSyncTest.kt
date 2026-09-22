package com.felixbrucker.simklcalendar

import android.content.Context
import com.felixbrucker.simklcalendar.data.database.*
import com.felixbrucker.simklcalendar.data.model.*
import com.felixbrucker.simklcalendar.data.network.*
import com.felixbrucker.simklcalendar.data.preferences.*
import com.felixbrucker.simklcalendar.data.repository.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SimklRepositoryDeepSyncTest {

    private lateinit var context: Context
    private lateinit var appDatabase: AppDatabase
    private lateinit var tokenDao: UserTokenDao
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var settingDao: NotificationSettingDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var watchedDao: WatchedEpisodeDao
    private lateinit var itemDownloadSettingsDao: ItemDownloadSettingsDao
    private lateinit var apiService: SimklApiService

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
        every { context.filesDir } returns File("/tmp")
        
        appDatabase = mockk(relaxed = true)
        tokenDao = mockk(relaxed = true)
        calendarDao = mockk(relaxed = true)
        settingDao = mockk(relaxed = true)
        watchlistDao = mockk(relaxed = true)
        watchedDao = mockk(relaxed = true)
        itemDownloadSettingsDao = mockk(relaxed = true)
        apiService = mockk(relaxed = true)

        appSettingsRepo = mockk(relaxed = true)
        autoDownloadRepo = mockk(relaxed = true)
        notificationRepo = mockk(relaxed = true)
        authRepo = mockk(relaxed = true)
        syncMetadataRepo = mockk(relaxed = true)
        uiRepo = mockk(relaxed = true)

        coEvery { appDatabase.userTokenDao() } returns tokenDao
        coEvery { appDatabase.calendarItemDao() } returns calendarDao
        coEvery { appDatabase.notificationSettingDao() } returns settingDao
        coEvery { appDatabase.watchlistDao() } returns watchlistDao
        coEvery { appDatabase.watchedEpisodeDao() } returns watchedDao
        coEvery { appDatabase.itemDownloadSettingsDao() } returns itemDownloadSettingsDao
        every { appDatabase.customSearchLinkDao() } returns mockk(relaxed = true)

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
            torrentServiceHelper = mockk(relaxed = true),
            torrentSearchManager = mockk(relaxed = true),
            alarmScheduler = mockk(relaxed = true)
        )

        val apiField = SimklRepository::class.java.getDeclaredField("apiService")
        apiField.isAccessible = true
        apiField.set(repository, apiService)
        
        every { authRepo.preferencesFlow } returns flowOf(AuthPreferences())
        every { syncMetadataRepo.preferencesFlow } returns flowOf(SyncMetadataPreferences())
        every { notificationRepo.preferencesFlow } returns flowOf(NotificationPreferences())
        every { autoDownloadRepo.preferencesFlow } returns flowOf(AutoDownloadPreferences())
    }

    @After
    fun tearDown() {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    @Test
    fun testExchangeOAuthCodeSuccessAndFailure() = runTest {
        every { authRepo.preferencesFlow } returns flowOf(AuthPreferences(
            pkceState = "valid_state",
            pkceCodeVerifier = "verifier_123"
        ))

        coEvery { apiService.getAccessToken(any()) } returns OAuthTokenResponse(
            accessToken = "simkl_at_access_token_abc",
            tokenType = "Bearer",
            expiresIn = 604800,
            refreshToken = "simkl_rt_refresh_token_abc",
            scope = "media:read media:write"
        )
        coEvery { apiService.getUserSettings() } returns UserSettingsResponse(UserProfile("SimklUser123"))

        val failureState = repository.exchangeOAuthCode("code", "wrong_state", "uri")
        assertFalse(failureState)

        every { authRepo.preferencesFlow } returns flowOf(AuthPreferences(
            pkceState = "valid_state",
            pkceCodeVerifier = null
        ))
        val failureVerifier = repository.exchangeOAuthCode("code", "valid_state", "uri")
        assertFalse(failureVerifier)
    }

    @Test
    fun testSyncWatchlistFullBranchExecution() = runTest {
        coEvery { tokenDao.getActiveToken() } returns UserToken(1, "token_123", "User")
        coEvery { apiService.getSyncActivities() } returns SyncActivitiesResponse("2026-03-30T10:00:00Z")

        val syncResponse = SyncAllItemsResponse(
            shows = listOf(
                SyncShowItem(
                    status = "watching",
                    show = SimklMedia("TV Show 1", "poster1.jpg", SimklIds(simkl = 101)),
                    seasons = listOf(
                        SyncSeasonItem(
                            number = 1,
                            episodes = listOf(
                                SyncEpisodeItem(number = 1, watchedAt = "2026-03-01T12:00:00Z"),
                                SyncEpisodeItem(number = 2, watchedAt = "2026-03-02T12:00:00Z")
                            )
                        )
                    )
                )
            ),
            anime = listOf(
                SyncShowItem(
                    status = "completed",
                    show = SimklMedia("Anime 1", "poster2.jpg", SimklIds(simkl = 202))
                )
            ),
            movies = listOf(
                SyncMovieItem(
                    status = "plan_to_watch",
                    movie = SimklMedia("Movie 1", "poster3.jpg", SimklIds(simkl = 303))
                )
            )
        )

        coEvery { apiService.getSyncAllItems(any(), any(), any(), any(), any()) } returns syncResponse

        val existingTracked = listOf(
            TrackedWatchlistItem(simklId = 202, type = MediaType.ANIME, title = "Anime 1", poster = null)
        )
        coEvery { watchlistDao.getTrackedItemsBySimklIds(any()) } returns existingTracked

        val syncResult = repository.syncWatchlist(forceFullSync = true)

        assertTrue(syncResult.hasWatchlistItemChanges)
        coVerify { watchlistDao.deleteItem(202) }
        coVerify { watchlistDao.insertItems(any()) }
        coVerify { watchedDao.insertWatchedEpisodes(any()) }
    }

    @Test
    fun testSyncCalendarJsonsWithMovieDetails() = runTest {
        coEvery { tokenDao.getActiveToken() } returns UserToken(1, "token_123", "User")
        coEvery { watchlistDao.getAllTrackedIds() } returns listOf(101, 303)
        coEvery { watchlistDao.getTrackedIdsByTypes(listOf(MediaType.MOVIE)) } returns listOf(303)

        val v2CalendarResponse = SimklV2CalendarResponse(
            calendar = listOf(
                SimklV2CalendarEntry(
                    simklId = 101,
                    date = "2026-04-10T20:00:00Z",
                    episode = SimklV2Episode(season = 1, episode = 3, title = "Ep 3")
                )
            ),
            metadata = mapOf(
                "101" to SimklV2Metadata(title = "TV Show 1")
            )
        )

        coEvery { apiService.getV2Calendar(any(), any(), any(), any()) } returns Response.success(v2CalendarResponse)

        val movieDetail = SimklMovieDetailResponse(
            title = "Movie 1",
            poster = "movie_poster.jpg",
            released = "2026-03-01",
            releaseDates = listOf(
                SimklMovieReleaseDateCountry(
                    iso31661 = "US",
                    results = listOf(
                        SimklMovieReleaseResult(type = 4, releaseDate = "2026-04-15")
                    )
                )
            ),
            ids = SimklIds(simkl = 303)
        )

        coEvery { apiService.getMovieDetails(303) } returns movieDetail

        val result = repository.syncCalendarJsons(forceFullSync = true)

        assertTrue(result.hasCalendarItemChanges)
        coVerify { calendarDao.insertCalendarItems(any()) }
    }

    @Test
    fun testBackfillPastEpisodesExecution() = runTest {
        coEvery { tokenDao.getActiveToken() } returns UserToken(1, "token_123", "User")
        val trackedShows = listOf(
            TrackedWatchlistItem(simklId = 101, type = MediaType.TV, title = "TV Show 1", poster = null)
        )
        coEvery { watchlistDao.getTrackedItemsByTypes(listOf(MediaType.TV, MediaType.ANIME)) } returns trackedShows

        val episodes = listOf(
            SimklEpisodeResponse(title = "Ep 1", season = 1, episode = 1, type = "episode", aired = true, date = "2026-02-01T20:00:00Z"),
            SimklEpisodeResponse(title = "Ep 2", season = 1, episode = 2, type = "episode", aired = true, date = "2026-02-08T20:00:00Z")
        )
        coEvery { apiService.getTvEpisodes(101) } returns episodes

        val result = repository.backfillPastEpisodes(lastSyncTimestamp = 0L)

        assertTrue(result.hasCalendarItemChanges)
        coVerify { calendarDao.insertCalendarItems(any()) }
    }
}
