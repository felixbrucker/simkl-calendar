package com.felixbrucker.simklcalendar

import com.felixbrucker.simklcalendar.data.database.AnimeType
import com.felixbrucker.simklcalendar.data.database.MediaItem
import com.felixbrucker.simklcalendar.data.database.MediaItemDao
import com.felixbrucker.simklcalendar.data.database.MediaItemWithDetails
import com.felixbrucker.simklcalendar.data.database.MovieMetadata
import com.felixbrucker.simklcalendar.data.database.SeriesStatus
import com.felixbrucker.simklcalendar.data.database.TrendingEntryDao
import com.felixbrucker.simklcalendar.data.database.WatchlistItemDao
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.network.PublicSimklApiService
import com.felixbrucker.simklcalendar.data.network.SimklAnimeDetailResponse
import com.felixbrucker.simklcalendar.data.network.SimklEpisodeResponse
import com.felixbrucker.simklcalendar.data.network.SimklIds
import com.felixbrucker.simklcalendar.data.network.SimklMovieDetailResponse
import com.felixbrucker.simklcalendar.data.network.SimklRating
import com.felixbrucker.simklcalendar.data.network.SimklRatings
import com.felixbrucker.simklcalendar.data.network.SimklTvDetailResponse
import com.felixbrucker.simklcalendar.data.repository.MediaRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MediaRepositoryTest {

    private lateinit var mediaItemDao: MediaItemDao
    private lateinit var trendingEntryDao: TrendingEntryDao
    private lateinit var watchlistItemDao: WatchlistItemDao
    private lateinit var publicSimklApiService: PublicSimklApiService
    private lateinit var testScope: TestScope
    private lateinit var mediaRepository: MediaRepository

    @Before
    fun setUp() {
        io.mockk.mockkStatic(android.text.Html::class)
        io.mockk.every { android.text.Html.fromHtml(any(), any()) } answers {
            val text = firstArg<String>()
            val spanned = mockk<android.text.Spanned>(relaxed = true)
            io.mockk.every { spanned.toString() } returns text
            spanned
        }
        mediaItemDao = mockk(relaxed = true)
        trendingEntryDao = mockk(relaxed = true)
        watchlistItemDao = mockk(relaxed = true)
        publicSimklApiService = mockk(relaxed = true)
        testScope = TestScope()

        mediaRepository = MediaRepository(
            mediaItemDao = mediaItemDao,
            trendingEntryDao = trendingEntryDao,
            watchlistItemDao = watchlistItemDao,
            publicSimklApiService = publicSimklApiService,
            externalScope = testScope
        )
    }

    @org.junit.After
    fun tearDown() {
        io.mockk.unmockkStatic(android.text.Html::class)
    }

    @Test
    fun testMediaItemComputedProperties() {
        val item = MediaItem(
            simklId = 12345,
            mediaType = MediaType.MOVIE,
            title = "Test Movie",
            year = 2023,
            movieMetadata = MovieMetadata(dvdDate = LocalDate.of(2023, 5, 1))
        )

        assertEquals(true, item.hasDvd)
    }

    @Test
    fun testFetchMovieFromApiMapsCorrectly() = runTest {
        coEvery { publicSimklApiService.getMovieDetails(100) } returns SimklMovieDetailResponse(
            title = "Inception",
            year = 2010,
            overview = "A thief who steals corporate secrets...",
            poster = "inception.jpg",
            genres = listOf("Action", "Sci-Fi"),
            budget = 160000000L,
            revenue = 829895144L,
            released = "2010-07-16",
            ratings = SimklRatings(simkl = SimklRating(rating = 8.8)),
            ids = SimklIds(simkl = 100)
        )

        val mediaItem = mediaRepository.fetchMediaItemFromApi(simklId = 100, mediaType = MediaType.MOVIE)

        assertEquals(100, mediaItem.simklId)
        assertEquals(MediaType.MOVIE, mediaItem.mediaType)
        assertEquals("Inception", mediaItem.title)
        assertEquals(2010, mediaItem.year)
        assertEquals("A thief who steals corporate secrets...", mediaItem.description)
        assertEquals("inception.jpg", mediaItem.poster)
        assertEquals(listOf("Action", "Sci-Fi"), mediaItem.genres)
        assertEquals(8.8f, mediaItem.rating!!, 0.01f)
        assertNotNull(mediaItem.movieMetadata)
        assertEquals(LocalDate.of(2010, 7, 16), mediaItem.movieMetadata?.releaseDate)
        assertEquals(160000000L, mediaItem.movieMetadata?.budget)
        assertEquals(829895144L, mediaItem.movieMetadata?.boxOffice)
    }

    @Test
    fun testFetchTvSeriesFromApiMapsCorrectlyAndCalculatesSeasons() = runTest {
        coEvery { publicSimklApiService.getTvDetails(200) } returns SimklTvDetailResponse(
            title = "Breaking Bad",
            year = 2008,
            overview = "A chemistry teacher turned drug lord...",
            poster = "bb.jpg",
            genres = listOf("Drama", "Crime"),
            status = "ended",
            totalEpisodes = 62,
            yearStartEnd = "2008-2013",
            ratings = SimklRatings(simkl = SimklRating(rating = 9.5)),
            ids = SimklIds(simkl = 200)
        )
        coEvery { publicSimklApiService.getTvEpisodes(200) } returns listOf(
            SimklEpisodeResponse(title = "Specials", type = "special", season = 0, episode = 1, aired = true),
            SimklEpisodeResponse(title = "Pilot", type = "episode", season = 1, episode = 1, aired = true),
            SimklEpisodeResponse(title = "Seven Thirty-Seven", type = "episode", season = 2, episode = 1, aired = true)
        )

        val mediaItem = mediaRepository.fetchMediaItemFromApi(simklId = 200, mediaType = MediaType.TV)

        assertEquals(200, mediaItem.simklId)
        assertEquals(MediaType.TV, mediaItem.mediaType)
        assertEquals("Breaking Bad", mediaItem.title)
        assertEquals(2008, mediaItem.year)
        assertNotNull(mediaItem.seriesMetadata)
        assertEquals(2013, mediaItem.seriesMetadata?.endYear)
        assertEquals(2, mediaItem.seriesMetadata?.seasonsCount)
        assertEquals(62, mediaItem.seriesMetadata?.totalEpisodes)
        assertEquals(SeriesStatus.ENDED, mediaItem.seriesMetadata?.status)
    }

    @Test
    fun testFetchAnimeFromApiMapsEnglishTitleAndRomajiTitle() = runTest {
        coEvery { publicSimklApiService.getAnimeDetails(300) } returns SimklAnimeDetailResponse(
            title = "Shingeki no Kyojin",
            enTitle = "Attack on Titan",
            year = 2013,
            overview = "Humanity lives inside cities surrounded by enormous walls...",
            poster = "aot.jpg",
            genres = listOf("Action", "Fantasy"),
            animeType = "tv",
            totalEpisodes = 25,
            yearStartEnd = "2013-2013",
            seasonNameYear = "Spring 2013",
            ratings = SimklRatings(simkl = SimklRating(rating = 9.0)),
            ids = SimklIds(simkl = 300)
        )
        coEvery { publicSimklApiService.getAnimeEpisodes(300) } returns listOf(
            SimklEpisodeResponse(title = "To You, in 2000 Years", type = "episode", season = 1, episode = 1, aired = true)
        )

        val mediaItem = mediaRepository.fetchMediaItemFromApi(simklId = 300, mediaType = MediaType.ANIME)

        assertEquals(300, mediaItem.simklId)
        assertEquals(MediaType.ANIME, mediaItem.mediaType)
        assertEquals("Attack on Titan", mediaItem.title)
        assertEquals("Shingeki no Kyojin", mediaItem.titleRomaji)
        assertEquals(2013, mediaItem.year)
        assertNotNull(mediaItem.animeMetadata)
        assertEquals(AnimeType.TV, mediaItem.animeMetadata?.animeType)
        assertEquals(2013, mediaItem.animeMetadata?.endYear)
        assertEquals(2013, mediaItem.animeMetadata?.seasonYear)
        assertEquals("Spring", mediaItem.animeMetadata?.seasonQuarter)
    }

    @Test
    fun testGetMediaItemStreamReturnsCachedDbFlowAndTriggersRefreshIfExpired() = runTest {
        val simklId = 500
        val expiredItem = MediaItem(
            simklId = simklId,
            mediaType = MediaType.MOVIE,
            title = "Old Cached Movie",
            year = 2020,
            lastUpdatedAt = Instant.now().minusSeconds(100000) // > 24 hours
        )
        coEvery { mediaItemDao.getMediaItem(simklId) } returns expiredItem
        coEvery { mediaItemDao.getMediaItemWithDetailsStream(simklId) } returns flowOf(MediaItemWithDetails(mediaItem = expiredItem))
        coEvery { publicSimklApiService.getMovieDetails(simklId) } returns SimklMovieDetailResponse(
            title = "Refreshed Movie Title",
            year = 2020,
            overview = "New overview",
            ids = SimklIds(simkl = simklId)
        )

        val flow = mediaRepository.getMediaItemStream(simklId = simklId, mediaType = MediaType.MOVIE)
        val initialResult = flow.first()

        assertEquals("Old Cached Movie", initialResult?.title)
        coVerify(timeout = 2000) { mediaItemDao.upsertMediaItem(any()) }
    }

    @Test
    fun testPruneExpiredMediaItemsDeletesExpiredEntities() = runTest {
        coEvery { mediaItemDao.deleteExpiredMediaItems(any()) } returns 5

        val deletedCount = mediaRepository.pruneExpiredMediaItems(months = 3)

        assertEquals(5, deletedCount)
        coVerify { mediaItemDao.deleteExpiredMediaItems(any()) }
    }
}
