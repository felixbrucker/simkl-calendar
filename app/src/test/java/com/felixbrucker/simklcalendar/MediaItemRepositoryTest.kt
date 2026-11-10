package com.felixbrucker.simklcalendar

import com.felixbrucker.simklcalendar.data.database.CachedMediaItem
import com.felixbrucker.simklcalendar.data.database.CachedMediaItemDao
import com.felixbrucker.simklcalendar.data.database.MovieMetadata
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.TrendingDao
import com.felixbrucker.simklcalendar.data.database.TrendingEntry
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.WatchlistStatus
import com.felixbrucker.simklcalendar.data.network.AuthenticatedSimklApiService
import com.felixbrucker.simklcalendar.data.network.PublicSimklApiService
import com.felixbrucker.simklcalendar.data.network.SearchItemDto
import com.felixbrucker.simklcalendar.data.network.SimklIds
import com.felixbrucker.simklcalendar.data.network.SimklMovieDetailResponse
import com.felixbrucker.simklcalendar.data.network.SimklRatings
import com.felixbrucker.simklcalendar.data.network.SimklRating
import com.felixbrucker.simklcalendar.data.network.SyncAddToListResponse
import com.felixbrucker.simklcalendar.data.network.SyncHistoryResponse
import com.felixbrucker.simklcalendar.data.network.TrendingItemDto
import com.felixbrucker.simklcalendar.data.preferences.NotificationPreferences
import com.felixbrucker.simklcalendar.data.preferences.NotificationRepository
import com.felixbrucker.simklcalendar.data.preferences.SyncMetadataPreferences
import com.felixbrucker.simklcalendar.data.preferences.SyncMetadataRepository
import com.felixbrucker.simklcalendar.data.repository.MediaItemRepository
import com.felixbrucker.simklcalendar.receiver.notification.NotificationManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class MediaItemRepositoryTest {

    private lateinit var publicApiService: PublicSimklApiService
    private lateinit var authenticatedApiService: AuthenticatedSimklApiService
    private lateinit var cachedMediaItemDao: CachedMediaItemDao
    private lateinit var trendingDao: TrendingDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var syncMetadataRepo: SyncMetadataRepository
    private lateinit var notificationRepo: NotificationRepository
    private lateinit var notificationManager: NotificationManager
    private lateinit var repository: MediaItemRepository

    @Before
    fun setup() {
        publicApiService = mockk(relaxed = true)
        authenticatedApiService = mockk(relaxed = true)
        cachedMediaItemDao = mockk(relaxed = true)
        trendingDao = mockk(relaxed = true)
        watchlistDao = mockk(relaxed = true)
        syncMetadataRepo = mockk(relaxed = true)
        notificationRepo = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)

        every { syncMetadataRepo.preferencesFlow } returns flowOf(SyncMetadataPreferences())
        every { notificationRepo.preferencesFlow } returns flowOf(NotificationPreferences())

        repository = MediaItemRepository(
            publicSimklApiService = publicApiService,
            authenticatedSimklApiService = authenticatedApiService,
            cachedMediaItemDao = cachedMediaItemDao,
            trendingDao = trendingDao,
            watchlistDao = watchlistDao,
            syncMetadataRepo = syncMetadataRepo,
            notificationRepo = notificationRepo,
            notificationManager = notificationManager
        )
    }

    @Test
    fun testSyncTrendingSavesRankingAndUpsertsItems() = runTest {
        val trendingItems = listOf(
            TrendingItemDto(
                title = "Movie 1",
                ids = SimklIds(simkl = 11),
                ratings = SimklRatings(simkl = SimklRating(rating = 8.2)),
                overview = "Great movie"
            )
        )
        val headers = Headers.headersOf("Last-Modified", "Wed, 21 Oct 2026 07:28:00 GMT")
        coEvery { publicApiService.getTrendingToday("movies", any()) } returns Response.success(trendingItems, headers)
        coEvery { publicApiService.getTrendingToday("tv", any()) } returns Response.success(emptyList())
        coEvery { publicApiService.getTrendingToday("anime", any()) } returns Response.success(emptyList())
        coEvery { trendingDao.getAllTrendingSimklIds() } returns emptyList()
        coEvery { watchlistDao.getAllTrackedIds() } returns emptyList()
        coEvery { publicApiService.getMovieDetails(11) } returns SimklMovieDetailResponse(
            title = "Movie 1",
            overview = "Overview",
            ids = SimklIds(simkl = 11)
        )

        coEvery { cachedMediaItemDao.getCachedMediaItem(11) } returns null

        val newIds = repository.syncTrending(force = true)

        val entriesSlot = slot<List<TrendingEntry>>()
        coVerify { trendingDao.replaceTrendingEntriesForType(MediaType.MOVIE, capture(entriesSlot)) }
        val capturedEntries = entriesSlot.captured
        val firstEntry = capturedEntries[0]
        assertEquals(listOf(11), newIds)
        assertEquals(1, capturedEntries.size)
        assertEquals(1, firstEntry.rank)
        assertEquals(11, firstEntry.simklId)
        coVerify { cachedMediaItemDao.upsert(any()) }
    }

    @Test
    fun testSearchQueriesApi() = runTest {
        val searchDtos = listOf(
            SearchItemDto(
                title = "Search Title",
                ids = SimklIds(simkl = 99),
                endpointType = "movies",
                overview = "Description",
                ratings = SimklRatings(simkl = SimklRating(rating = 7.5))
            )
        )
        coEvery { publicApiService.search("all", "Search Title", "full", 1, 20) } returns searchDtos

        val results = repository.search("Search Title")

        assertEquals(1, results.size)
        assertEquals(99, results[0].ids.simkl)
    }

    @Test
    fun testUpdateWatchlistStatusUsesSyncHistoryWhenMarkingWatchedForExistingItem() = runTest {
        val item = CachedMediaItem(
            simklId = 55,
            mediaType = MediaType.MOVIE,
            title = "Watched Movie"
        )
        val existing = TrackedWatchlistItem(
            simklId = 55,
            type = MediaType.MOVIE,
            title = "Watched Movie",
            status = WatchlistStatus.PLAN_TO_WATCH
        )
        coEvery { watchlistDao.getItem(55) } returns existing
        coEvery { authenticatedApiService.markHistoryWatched(any()) } returns SyncHistoryResponse()

        val success = repository.updateWatchlistStatus(item, WatchlistStatus.COMPLETED)

        assertTrue(success)
        coVerify { authenticatedApiService.markHistoryWatched(any()) }
        coVerify(exactly = 0) { authenticatedApiService.addToList(any()) }
        coVerify { watchlistDao.updateItems(match { it[0].status == WatchlistStatus.COMPLETED }) }
    }
}
