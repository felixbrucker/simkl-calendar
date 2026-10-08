package com.felixbrucker.simklcalendar

import android.content.Context
import com.felixbrucker.simklcalendar.data.database.*
import com.felixbrucker.simklcalendar.data.model.*
import com.felixbrucker.simklcalendar.data.network.*
import com.felixbrucker.simklcalendar.data.preferences.*
import com.felixbrucker.simklcalendar.data.repository.*
import com.felixbrucker.simklcalendar.data.util.MediaStatusResolver
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SimklRepositoryDeepSyncTest {

    private lateinit var context: Context
    private lateinit var tokenDao: UserTokenDao
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var settingDao: NotificationSettingDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var watchedDao: WatchedEpisodeDao
    private lateinit var customSearchLinkDao: CustomSearchLinkDao
    private lateinit var itemDownloadSettingsDao: ItemDownloadSettingsDao
    private lateinit var publicApiService: PublicSimklApiService
    private lateinit var authenticatedApiService: AuthenticatedSimklApiService

    private lateinit var userRepository: UserRepository
    private lateinit var syncRepository: SyncRepository

    private lateinit var appSettingsRepo: AppSettingsRepository
    private lateinit var autoDownloadRepo: AutoDownloadRepository
    private lateinit var notificationRepo: NotificationRepository
    private lateinit var authRepo: AuthRepository
    private lateinit var syncMetadataRepo: SyncMetadataRepository
    private lateinit var uiRepo: UiRepository
    private val testScope = TestScope()

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.filesDir } returns File("/tmp")

        tokenDao = mockk(relaxed = true)
        calendarDao = mockk(relaxed = true)
        settingDao = mockk(relaxed = true)
        watchlistDao = mockk(relaxed = true)
        watchedDao = mockk(relaxed = true)
        customSearchLinkDao = mockk(relaxed = true)
        itemDownloadSettingsDao = mockk(relaxed = true)
        publicApiService = mockk(relaxed = true)
        authenticatedApiService = mockk(relaxed = true)

        appSettingsRepo = mockk(relaxed = true)
        autoDownloadRepo = mockk(relaxed = true)
        notificationRepo = mockk(relaxed = true)
        authRepo = mockk(relaxed = true)
        syncMetadataRepo = mockk(relaxed = true)
        uiRepo = mockk(relaxed = true)

        val mediaStatusResolver = MediaStatusResolver(autoDownloadRepo)

        userRepository = UserRepository(
            tokenDao = tokenDao,
            watchlistDao = watchlistDao,
            customSearchLinkDao = customSearchLinkDao,
            publicSimklApiService = publicApiService,
            authenticatedSimklApiService = authenticatedApiService,
            appSettingsRepo = appSettingsRepo,
            autoDownloadRepo = autoDownloadRepo,
            notificationRepo = notificationRepo,
            authRepo = authRepo,
            syncMetadataRepo = syncMetadataRepo,
            uiRepo = uiRepo,
            appScope = testScope,
        )

        syncRepository = SyncRepository(
            tokenDao = tokenDao,
            calendarDao = calendarDao,
            settingDao = settingDao,
            watchlistDao = watchlistDao,
            watchedDao = watchedDao,
            itemDownloadSettingsDao = itemDownloadSettingsDao,
            publicSimklApiService = publicApiService,
            authenticatedSimklApiService = authenticatedApiService,
            notificationRepo = notificationRepo,
            syncMetadataRepo = syncMetadataRepo,
            autoDownloadRepo = autoDownloadRepo,
            downloadRepository = mockk(relaxed = true),
            mediaStatusResolver = mediaStatusResolver,
            alarmScheduler = mockk(relaxed = true)
        )

        every { authRepo.preferencesFlow } returns flowOf(AuthPreferences())
        every { syncMetadataRepo.preferencesFlow } returns flowOf(SyncMetadataPreferences())
        every { notificationRepo.preferencesFlow } returns flowOf(NotificationPreferences())
        every { autoDownloadRepo.preferencesFlow } returns flowOf(AutoDownloadPreferences())
    }

    @Test
    fun testExchangeOAuthCodeSuccessAndFailure() = runTest {
        every { authRepo.preferencesFlow } returns flowOf(AuthPreferences(
            pkceState = "valid_state",
            pkceCodeVerifier = "verifier_123"
        ))

        coEvery { publicApiService.getAccessToken(any()) } returns OAuthTokenResponse(
            accessToken = "simkl_at_access_token_abc",
            tokenType = "Bearer",
            expiresIn = 604800,
            refreshToken = "simkl_rt_refresh_token_abc",
            scope = "media:read media:write"
        )
        coEvery { authenticatedApiService.getUserSettings() } returns UserSettingsResponse(UserProfile("SimklUser123"))

        val failureState = userRepository.exchangeOAuthCode("code", "wrong_state", "uri")
        assertFalse(failureState)

        every { authRepo.preferencesFlow } returns flowOf(AuthPreferences(
            pkceState = "valid_state",
            pkceCodeVerifier = null
        ))
        val failureVerifier = userRepository.exchangeOAuthCode("code", "valid_state", "uri")
        assertFalse(failureVerifier)
    }

    @Test
    fun testSyncWatchlistFullBranchExecution() = runTest {
        coEvery { tokenDao.getActiveToken() } returns UserToken(1, "token_123", "User")
        coEvery { authenticatedApiService.getSyncActivities() } returns SyncActivitiesResponse("2026-03-30T10:00:00Z")

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

        coEvery { authenticatedApiService.getSyncAllItems(any(), any(), any(), any(), any()) } returns syncResponse

        val existingTracked = listOf(
            TrackedWatchlistItem(simklId = 202, type = MediaType.ANIME, title = "Anime 1", poster = null)
        )
        coEvery { watchlistDao.getTrackedItemsBySimklIds(any()) } returns existingTracked

        val syncResult = syncRepository.syncWatchlist(forceFullSync = true)

        assertTrue(syncResult.hasWatchlistItemChanges)
        coVerify { watchlistDao.deleteItem(202) }
        coVerify { watchlistDao.insertItems(any()) }
        coVerify { watchedDao.insertWatchedEpisodes(any()) }
    }

    @Test
    fun testSyncCalendarJsons() = runTest {
        coEvery { tokenDao.getActiveToken() } returns UserToken(1, "token_123", "User")
        coEvery { watchlistDao.getAllTrackedIds() } returns listOf(101, 303)
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
        coEvery { publicApiService.getV2Calendar(any(), any(), any(), any()) } returns Response.success(v2CalendarResponse)

        val result = syncRepository.syncCalendarJsons(forceFullSync = true)

        val hasChanges = result.hasCalendarItemChanges
        assertTrue(hasChanges)
        coVerify { calendarDao.insertCalendarItems(any()) }
    }

    @Test
    fun testSyncCalendarJsonsUpdatesLastSyncedAtForTvButNotForAnime() = runTest {
        coEvery { tokenDao.getActiveToken() } returns UserToken(1, "token_123", "User")
        coEvery { watchlistDao.getAllTrackedIds() } returns listOf(101, 202)
        val existingTv = TrackedWatchlistItem(simklId = 101, type = MediaType.TV, title = "Old TV", lastSyncedAt = null)
        val existingAnime = TrackedWatchlistItem(simklId = 202, type = MediaType.ANIME, title = "Old Anime", lastSyncedAt = null)
        coEvery { watchlistDao.getTrackedItemsBySimklIds(listOf(101)) } returns listOf(existingTv)
        coEvery { watchlistDao.getTrackedItemsBySimklIds(listOf(202)) } returns listOf(existingAnime)
        coEvery { watchlistDao.getTrackedItemsBySimklIds(listOf(101, 202)) } returns listOf(existingTv, existingAnime)
        coEvery { watchlistDao.getTrackedIdsByTypes(listOf(MediaType.MOVIE)) } returns emptyList()

        val tvResponse = SimklV2CalendarResponse(
            calendar = listOf(
                SimklV2CalendarEntry(simklId = 101, date = "2026-04-10T20:00:00Z", episode = SimklV2Episode(season = 1, episode = 1, title = "Ep 1"))
            ),
            metadata = mapOf("101" to SimklV2Metadata(title = "New TV Title"))
        )
        val animeResponse = SimklV2CalendarResponse(
            calendar = listOf(
                SimklV2CalendarEntry(simklId = 202, date = "2026-04-10T20:00:00Z", episode = SimklV2Episode(season = 1, episode = 1, title = "Ep 1"))
            ),
            metadata = mapOf("202" to SimklV2Metadata(title = "New Anime Title"))
        )
        coEvery { publicApiService.getV2Calendar(any(), any(), eq("tv"), any()) } returns Response.success(tvResponse)
        coEvery { publicApiService.getV2Calendar(any(), any(), eq("anime"), any()) } returns Response.success(animeResponse)
        coEvery { publicApiService.getV2Calendar(any(), any(), eq("movie_release"), any()) } returns Response.success(SimklV2CalendarResponse(emptyList(), emptyMap()))

        val updatedItemsSlot = slot<List<TrackedWatchlistItem>>()
        coEvery { watchlistDao.updateItems(capture(updatedItemsSlot)) } returns Unit

        syncRepository.syncCalendarJsons(forceFullSync = true)

        val updatedItems = updatedItemsSlot.captured
        val tvUpdated = updatedItems.first { it.simklId == 101 }
        val animeUpdated = updatedItems.first { it.simklId == 202 }

        assertNotNull(tvUpdated.lastSyncedAt)
        assertNull(animeUpdated.lastSyncedAt)
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
        coEvery { publicApiService.getTvEpisodes(101) } returns episodes

        val result = syncRepository.backfillPastEpisodes(lastSyncTimestamp = 0L)

        assertTrue(result.hasCalendarItemChanges)
        coVerify { calendarDao.insertCalendarItems(any()) }
    }

    @Test
    fun testBackfillPastEpisodesCalculatesSeasonFinaleCorrectly() = runTest {
        val insertedSlot = slot<List<CalendarItem>>()
        coEvery { tokenDao.getActiveToken() } returns UserToken(1, "token_123", "User")
        val trackedShows = listOf(
            TrackedWatchlistItem(simklId = 101, type = MediaType.TV, title = "TV Show 1", poster = null)
        )
        coEvery { watchlistDao.getTrackedItemsByTypes(listOf(MediaType.TV, MediaType.ANIME)) } returns trackedShows
        val episodes = listOf(
            SimklEpisodeResponse(title = "Ep 1", season = 1, episode = 1, type = "episode", aired = true, date = "2026-02-01T20:00:00Z"),
            SimklEpisodeResponse(title = "Ep 2", season = 1, episode = 2, type = "episode", aired = true, date = "2026-02-08T20:00:00Z"),
            SimklEpisodeResponse(title = "Special 1", season = 1, episode = null, type = "special", aired = true, date = "2026-02-09T20:00:00Z"),
            SimklEpisodeResponse(title = "S2 Ep 1", season = 2, episode = 1, type = "episode", aired = true, date = "2026-02-15T20:00:00Z"),
            SimklEpisodeResponse(title = "S2 Ep 2", season = 2, episode = 2, type = "episode", aired = false, date = "2026-02-22T20:00:00Z")
        )
        coEvery { publicApiService.getTvEpisodes(101) } returns episodes
        coEvery { calendarDao.insertCalendarItems(capture(insertedSlot)) } returns Unit

        val result = syncRepository.backfillPastEpisodes(lastSyncTimestamp = 0L)

        assertTrue(result.hasCalendarItemChanges)
        val items = insertedSlot.captured
        val s1e1 = items.first { it.season == 1 && it.episodeNumber == 1 }
        val s1e2 = items.first { it.season == 1 && it.episodeNumber == 2 }
        val s2e1 = items.first { it.season == 2 && it.episodeNumber == 1 }
        assertFalse(s1e1.isSeasonFinale)
        assertTrue(s1e2.isSeasonFinale)
        assertFalse(s2e1.isSeasonFinale)
    }

    @Test
    fun testSyncCalendarUpdatesMovieDetailsAndReleaseDates() = runTest {
        coEvery { tokenDao.getActiveToken() } returns UserToken(1, "token_123", "User")
        val existingTracked = listOf(TrackedWatchlistItem(simklId = 303, type = MediaType.MOVIE, title = "Old Title", poster = "old.jpg", rating = 7.0))
        coEvery { watchlistDao.getTrackedIdsByTypes(listOf(MediaType.MOVIE)) } returns listOf(303)
        coEvery { watchlistDao.getTrackedItemsBySimklIds(listOf(303)) } returns existingTracked
        val movieDetail = SimklMovieDetailResponse(
            title = "New Movie Title",
            poster = "new_poster.jpg",
            released = "2026-03-01",
            releaseDates = listOf(
                SimklMovieReleaseDateCountry(
                    iso31661 = "US",
                    results = listOf(SimklMovieReleaseResult(type = 4, releaseDate = "2026-05-01"))
                )
            ),
            ratings = SimklRatings(simkl = SimklRating(rating = 8.5)),
            ids = SimklIds(simkl = 303)
        )
        coEvery { publicApiService.getMovieDetails(303) } returns movieDetail
        val updatedTrackedSlot = slot<List<TrackedWatchlistItem>>()
        coEvery { watchlistDao.updateItems(capture(updatedTrackedSlot)) } returns Unit

        syncRepository.syncCalendar(force = true)

        val updatedTrackedList = updatedTrackedSlot.captured
        val updatedMovie = updatedTrackedList.firstOrNull { it.simklId == 303 }
        val updatedTitle = updatedMovie?.title
        val updatedPoster = updatedMovie?.poster
        val updatedRating = updatedMovie?.rating
        coVerify { watchlistDao.updateLastSyncedAt(listOf(303), any()) }
        coVerify { calendarDao.insertCalendarItems(any()) }
        assertEquals("New Movie Title", updatedTitle)
        assertEquals("new_poster.jpg", updatedPoster)
        assertEquals(8.5, updatedRating!!, 0.01)
    }

    @Test
    fun testUpdateMovieDetailsThrottledWhenNoCandidatesNeedingSync() = runTest {
        coEvery { tokenDao.getActiveToken() } returns UserToken(1, "token_123", "User")
        coEvery { watchlistDao.getItemIdsNeedingSync(any(), any()) } returns emptyList()

        syncRepository.syncCalendar(force = false)

        coVerify(exactly = 0) { publicApiService.getMovieDetails(303) }
    }

    @Test
    fun testParallelMovieDetailsFetching() = runTest {
        coEvery { tokenDao.getActiveToken() } returns UserToken(1, "token_123", "User")
        val existingTracked = listOf(
            TrackedWatchlistItem(simklId = 303, type = MediaType.MOVIE, title = "Movie A", poster = "a.jpg", rating = 7.0),
            TrackedWatchlistItem(simklId = 304, type = MediaType.MOVIE, title = "Movie B", poster = "b.jpg", rating = 8.0)
        )
        coEvery { watchlistDao.getTrackedIdsByTypes(listOf(MediaType.MOVIE)) } returns listOf(303, 304)
        coEvery { watchlistDao.getTrackedItemsBySimklIds(listOf(303, 304)) } returns existingTracked
        val movieDetail303 = SimklMovieDetailResponse(
            title = "Movie A Updated",
            poster = "a_new.jpg",
            released = "2026-03-01",
            ratings = SimklRatings(simkl = SimklRating(rating = 8.1)),
            ids = SimklIds(simkl = 303)
        )
        val movieDetail304 = SimklMovieDetailResponse(
            title = "Movie B Updated",
            poster = "b_new.jpg",
            released = "2026-04-01",
            ratings = SimklRatings(simkl = SimklRating(rating = 8.2)),
            ids = SimklIds(simkl = 304)
        )
        coEvery { publicApiService.getMovieDetails(303) } returns movieDetail303
        coEvery { publicApiService.getMovieDetails(304) } returns movieDetail304
        val updatedTrackedSlot = slot<List<TrackedWatchlistItem>>()
        coEvery { watchlistDao.updateItems(capture(updatedTrackedSlot)) } returns Unit

        syncRepository.syncCalendar(force = true)

        val updatedTrackedList = updatedTrackedSlot.captured
        val size = updatedTrackedList.size
        coVerify { publicApiService.getMovieDetails(303) }
        coVerify { publicApiService.getMovieDetails(304) }
        assertEquals(2, size)
    }
}
