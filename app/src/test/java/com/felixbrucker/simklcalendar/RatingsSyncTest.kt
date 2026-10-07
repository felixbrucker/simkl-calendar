package com.felixbrucker.simklcalendar

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
import com.felixbrucker.simklcalendar.data.network.SimklAnimeDetailResponse
import com.felixbrucker.simklcalendar.data.network.SimklIds
import com.felixbrucker.simklcalendar.data.network.SimklMovieDetailResponse
import com.felixbrucker.simklcalendar.data.network.SimklRating
import com.felixbrucker.simklcalendar.data.network.SimklRatings
import com.felixbrucker.simklcalendar.data.network.SimklTvDetailResponse
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadPreferences
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import com.felixbrucker.simklcalendar.data.preferences.NotificationPreferences
import com.felixbrucker.simklcalendar.data.preferences.NotificationRepository
import com.felixbrucker.simklcalendar.data.preferences.SyncMetadataPreferences
import com.felixbrucker.simklcalendar.data.preferences.SyncMetadataRepository
import com.felixbrucker.simklcalendar.data.repository.DownloadRepository
import com.felixbrucker.simklcalendar.data.repository.SyncRepository
import com.felixbrucker.simklcalendar.data.util.MediaStatusResolver
import com.felixbrucker.simklcalendar.receiver.alarm.AlarmScheduler
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RatingsSyncTest {

    private lateinit var tokenDao: UserTokenDao
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var settingDao: NotificationSettingDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var watchedDao: WatchedEpisodeDao
    private lateinit var itemDownloadSettingsDao: ItemDownloadSettingsDao
    private lateinit var publicApiService: PublicSimklApiService
    private lateinit var authenticatedApiService: AuthenticatedSimklApiService
    private lateinit var notificationRepo: NotificationRepository
    private lateinit var syncMetadataRepo: SyncMetadataRepository
    private lateinit var autoDownloadRepo: AutoDownloadRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var mediaStatusResolver: MediaStatusResolver
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
        notificationRepo = mockk(relaxed = true)
        syncMetadataRepo = mockk(relaxed = true)
        autoDownloadRepo = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        mediaStatusResolver = mockk(relaxed = true)
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
            notificationRepo = notificationRepo,
            syncMetadataRepo = syncMetadataRepo,
            autoDownloadRepo = autoDownloadRepo,
            downloadRepository = downloadRepository,
            mediaStatusResolver = mediaStatusResolver,
            alarmScheduler = alarmScheduler
        )

        every { syncMetadataRepo.preferencesFlow } returns flowOf(SyncMetadataPreferences())
        every { notificationRepo.preferencesFlow } returns flowOf(NotificationPreferences())
        every { autoDownloadRepo.preferencesFlow } returns flowOf(AutoDownloadPreferences())
    }

    @Test
    fun testTrackedWatchlistItemUpdatedWithRating() {
        val original = TrackedWatchlistItem(
            simklId = 1,
            type = MediaType.TV,
            title = "Test Show",
            poster = "poster.jpg",
            rating = 7.5
        )
        val newItemWithNewRating = TrackedWatchlistItem(
            simklId = 1,
            type = MediaType.TV,
            title = "Test Show",
            rating = 8.2
        )
        val newItemWithoutRating = TrackedWatchlistItem(
            simklId = 1,
            type = MediaType.TV,
            title = "Test Show",
            rating = null
        )

        val updatedWithRating = original.updatedWith(newItemWithNewRating)
        val updatedWithoutRating = original.updatedWith(newItemWithoutRating)

        val rating1 = updatedWithRating.rating
        val rating2 = updatedWithoutRating.rating

        assertEquals(8.2, rating1!!, 0.01)
        assertEquals(7.5, rating2!!, 0.01)
    }

    @Test
    fun testSyncWatchlistItemDetailsFetchesCandidatesAndUpdatesDetails() = runTest {
        coEvery { watchlistDao.getCandidateIdsByType(MediaType.MOVIE, any()) } returns listOf(10)
        coEvery { watchlistDao.getCandidateIdsByType(MediaType.TV, any()) } returns listOf(20)
        coEvery { watchlistDao.getCandidateIdsByType(MediaType.ANIME, any()) } returns listOf(30)
        val movieItem = TrackedWatchlistItem(simklId = 10, type = MediaType.MOVIE, title = "Movie", rating = null)
        val tvItem = TrackedWatchlistItem(simklId = 20, type = MediaType.TV, title = "TV Show", rating = null)
        val animeItem = TrackedWatchlistItem(simklId = 30, type = MediaType.ANIME, title = "Anime", rating = null)
        coEvery { watchlistDao.getTrackedItemsBySimklIds(listOf(10)) } returns listOf(movieItem)
        coEvery { watchlistDao.getTrackedItemsBySimklIds(listOf(20)) } returns listOf(tvItem)
        coEvery { watchlistDao.getTrackedItemsBySimklIds(listOf(30)) } returns listOf(animeItem)
        coEvery { publicApiService.getMovieDetails(10) } returns SimklMovieDetailResponse(
            title = "Movie Updated",
            ratings = SimklRatings(simkl = SimklRating(rating = 8.4)),
            ids = SimklIds(simkl = 10)
        )
        coEvery { publicApiService.getTvDetails(20) } returns SimklTvDetailResponse(
            title = "TV Show Updated",
            ratings = SimklRatings(simkl = SimklRating(rating = 9.1)),
            ids = SimklIds(simkl = 20)
        )
        coEvery { publicApiService.getAnimeDetails(30) } returns SimklAnimeDetailResponse(
            title = "Anime Updated",
            ratings = SimklRatings(simkl = SimklRating(rating = 7.8)),
            ids = SimklIds(simkl = 30)
        )
        val updatedSlot = slot<List<TrackedWatchlistItem>>()
        coEvery { watchlistDao.updateItems(capture(updatedSlot)) } returns Unit

        val syncResult = syncRepository.syncWatchlistItemDetails(force = false)

        val updatedList = updatedSlot.captured
        val size = updatedList.size
        val movieRating = updatedList.find { it.simklId == 10 }?.rating
        val tvRating = updatedList.find { it.simklId == 20 }?.rating
        val animeRating = updatedList.find { it.simklId == 30 }?.rating
        val hasChanges = syncResult.hasWatchlistItemChanges
        assertEquals(3, size)
        assertEquals(8.4, movieRating!!, 0.01)
        assertEquals(9.1, tvRating!!, 0.01)
        assertEquals(7.8, animeRating!!, 0.01)
        assertEquals(true, hasChanges)
        coVerify { watchlistDao.updateLastSyncedAt(listOf(20, 30, 10), any()) }
    }

    @Test
    fun testSyncWatchlistItemDetailsForAnimeWithEnTitle() = runTest {
        coEvery { watchlistDao.getCandidateIdsByType(MediaType.TV, any()) } returns emptyList()
        coEvery { watchlistDao.getCandidateIdsByType(MediaType.ANIME, any()) } returns listOf(30)
        coEvery { watchlistDao.getCandidateIdsByType(MediaType.MOVIE, any()) } returns emptyList()
        val animeItem = TrackedWatchlistItem(simklId = 30, type = MediaType.ANIME, title = "Shingeki no Kyojin", rating = null)
        coEvery { watchlistDao.getTrackedItemsBySimklIds(listOf(30)) } returns listOf(animeItem)
        coEvery { publicApiService.getAnimeDetails(30) } returns SimklAnimeDetailResponse(
            title = "Shingeki no Kyojin",
            enTitle = "Attack on Titan",
            ratings = SimklRatings(simkl = SimklRating(rating = 9.0)),
            ids = SimklIds(simkl = 30)
        )
        val updatedSlot = slot<List<TrackedWatchlistItem>>()
        coEvery { watchlistDao.updateItems(capture(updatedSlot)) } returns Unit

        val syncResult = syncRepository.syncWatchlistItemDetails(force = false)

        val updatedList = updatedSlot.captured
        val updatedAnime = updatedList.first()
        val title = updatedAnime.title
        val titleRomaji = updatedAnime.titleRomaji
        val rating = updatedAnime.rating
        val hasChanges = syncResult.hasWatchlistItemChanges
        assertEquals("Attack on Titan", title)
        assertEquals("Shingeki no Kyojin", titleRomaji)
        assertEquals(9.0, rating!!, 0.01)
        assertEquals(true, hasChanges)
    }

    @Test
    fun testSyncWatchlistItemDetailsThrottledWhenNoCandidates() = runTest {
        coEvery { watchlistDao.getCandidateIdsByType(any(), any()) } returns emptyList()

        val syncResult = syncRepository.syncWatchlistItemDetails(force = false)

        val hasChanges = syncResult.hasWatchlistItemChanges
        assertEquals(false, hasChanges)
        coVerify(exactly = 0) { watchlistDao.updateItems(any()) }
        coVerify(exactly = 0) { watchlistDao.updateLastSyncedAt(any(), any()) }
    }

    @Test
    fun testMoshiParsesSimklRatings() {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(SimklMovieDetailResponse::class.java)

        val jsonString = """
            {
                "title": "Inception",
                "ratings": {
                    "simkl": { "rating": 8.8 },
                    "imdb": { "rating": 8.8 }
                },
                "ids": { "simkl": 123 }
            }
        """.trimIndent()

        val parsed = adapter.fromJson(jsonString)
        val rating = parsed?.ratings?.simkl?.rating

        assertEquals(8.8, rating!!, 0.01)
    }
}
