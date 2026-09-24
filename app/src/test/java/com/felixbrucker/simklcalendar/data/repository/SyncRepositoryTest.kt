package com.felixbrucker.simklcalendar.data.repository

import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.NotificationSettingDao
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.database.WatchedEpisodeDao
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.network.AuthenticatedSimklApiService
import com.felixbrucker.simklcalendar.data.network.PublicSimklApiService
import com.felixbrucker.simklcalendar.data.network.SimklEpisodeResponse
import com.felixbrucker.simklcalendar.data.network.SimklIds
import com.felixbrucker.simklcalendar.data.network.SimklMedia
import com.felixbrucker.simklcalendar.data.network.SimklMovieDetailResponse
import com.felixbrucker.simklcalendar.data.network.SimklMovieReleaseDateCountry
import com.felixbrucker.simklcalendar.data.network.SimklMovieReleaseResult
import com.felixbrucker.simklcalendar.data.network.SimklV2CalendarEntry
import com.felixbrucker.simklcalendar.data.network.SimklV2CalendarResponse
import com.felixbrucker.simklcalendar.data.network.SimklV2Episode
import com.felixbrucker.simklcalendar.data.network.SimklV2Metadata
import com.felixbrucker.simklcalendar.data.network.SyncActivitiesResponse
import com.felixbrucker.simklcalendar.data.network.SyncAllItemsResponse
import com.felixbrucker.simklcalendar.data.network.SyncEpisodeItem
import com.felixbrucker.simklcalendar.data.network.SyncMovieItem
import com.felixbrucker.simklcalendar.data.network.SyncSeasonItem
import com.felixbrucker.simklcalendar.data.network.SyncShowItem
import com.felixbrucker.simklcalendar.data.preferences.NotificationPreferences
import com.felixbrucker.simklcalendar.data.preferences.NotificationRepository
import com.felixbrucker.simklcalendar.data.preferences.SyncMetadataPreferences
import com.felixbrucker.simklcalendar.data.preferences.SyncMetadataRepository
import com.felixbrucker.simklcalendar.receiver.alarm.AlarmScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SyncRepositoryTest {

    private lateinit var tokenDao: UserTokenDao
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var settingDao: NotificationSettingDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var watchedDao: WatchedEpisodeDao
    private lateinit var itemDownloadSettingsDao: ItemDownloadSettingsDao
    private lateinit var publicApiService: PublicSimklApiService
    private lateinit var authenticatedApiService: AuthenticatedSimklApiService
    private lateinit var syncMetadataRepo: SyncMetadataRepository
    private lateinit var notificationRepo: NotificationRepository
    private lateinit var calendarRepository: CalendarRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var syncRepository: SyncRepository

    @Before
    fun setUp() {
        tokenDao = mockk(relaxed = true)
        calendarDao = mockk(relaxed = true)
        settingDao = mockk(relaxed = true)
        watchlistDao = mockk(relaxed = true)
        watchedDao = mockk(relaxed = true)
        itemDownloadSettingsDao = mockk(relaxed = true)
        publicApiService = mockk(relaxed = true)
        authenticatedApiService = mockk(relaxed = true)
        syncMetadataRepo = mockk(relaxed = true)
        notificationRepo = mockk(relaxed = true)
        calendarRepository = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        alarmScheduler = mockk(relaxed = true)

        syncRepository = SyncRepository(
            tokenDao = tokenDao,
            calendarDao = calendarDao,
            settingDao = settingDao,
            watchlistDao = watchlistDao,
            watchedDao = watchedDao,
            itemDownloadSettingsDao = itemDownloadSettingsDao,
            publicSimklApiService = publicApiService,
            authenticatedSimklApiService = authenticatedApiService,
            syncMetadataRepo = syncMetadataRepo,
            notificationRepo = notificationRepo,
            calendarRepository = calendarRepository,
            downloadRepository = downloadRepository,
            alarmScheduler = alarmScheduler
        )

        every { syncMetadataRepo.preferencesFlow } returns flowOf(SyncMetadataPreferences())
        every { notificationRepo.preferencesFlow } returns flowOf(NotificationPreferences())
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

        coEvery { publicApiService.getV2Calendar(any(), any(), any(), any()) } returns Response.success(v2CalendarResponse)

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

        coEvery { publicApiService.getMovieDetails(303) } returns movieDetail

        val result = syncRepository.syncCalendarJsons(forceFullSync = true)

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
}
