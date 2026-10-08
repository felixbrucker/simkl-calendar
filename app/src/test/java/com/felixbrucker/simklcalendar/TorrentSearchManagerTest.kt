package com.felixbrucker.simklcalendar.data.network

import com.felixbrucker.simklcalendar.data.database.*
import com.felixbrucker.simklcalendar.data.model.*
import com.felixbrucker.simklcalendar.data.preferences.*
import com.felixbrucker.torrent_search_api.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TorrentSearchManagerTest {

    private lateinit var calendarDao: CalendarItemDao
    private lateinit var dao: ItemDownloadSettingsDao
    private lateinit var autoDownloadRepository: AutoDownloadRepository
    private val preferencesStateFlow = MutableStateFlow(AutoDownloadPreferences())

    @Before
    fun setUp() {
        calendarDao = mockk(relaxed = true)
        dao = mockk(relaxed = true)
        autoDownloadRepository = mockk(relaxed = true)

        coEvery { dao.getSettings(any()) } returns null
        every { autoDownloadRepository.preferencesFlow } returns preferencesStateFlow
        preferencesStateFlow.value = AutoDownloadPreferences()

        mockkConstructor(NyaaProvider::class)
        mockkConstructor(TpbProvider::class)
        coEvery { anyConstructed<NyaaProvider>().search(any(), any(), any()) } returns Result.success(PaginatedSearchResult(emptyList(), 1, false))
        coEvery { anyConstructed<TpbProvider>().search(any(), any(), any()) } returns Result.success(PaginatedSearchResult(emptyList(), 1, false))
    }

    @After
    fun tearDown() {
        unmockkConstructor(NyaaProvider::class)
        unmockkConstructor(TpbProvider::class)
    }

    @Test
    fun testTorrentSearchManagerInitialization() {
        val manager = TorrentSearchManager(calendarDao, dao, autoDownloadRepository)

        assertNotNull(manager)
    }

    @Test
    fun testTorrentSearchTV() = runTest {
        val manager = TorrentSearchManager(calendarDao, dao, autoDownloadRepository)
        val calendarItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, true, false)
        val watchlistItem = TrackedWatchlistItem(100, MediaType.TV, "Test Show", null, null)
        val item = CalendarItemWithWatchlist(calendarItem, watchlistItem, null)

        val results = manager.search(item)

        assertTrue(results.isEmpty())
    }

    @Test
    fun testTorrentSearchAnimeWithSettingsOverrides() = runTest {
        val customSettings = ItemDownloadSettings(
            simklId = 200,
            titleOverride = "Custom Anime Title",
            seasonOverrides = mapOf(1 to 2),
            qualityOverride = "2160p",
            preferHevcOverride = false
        )
        coEvery { dao.getSettings(200) } returns customSettings
        val manager = TorrentSearchManager(calendarDao, dao, autoDownloadRepository)
        val calendarItem = CalendarItem("v2_200_1_5", 200, "Ep 5", 1, 5, Instant.now(), null, false, false)
        val watchlistItem = TrackedWatchlistItem(200, MediaType.ANIME, "Anime Show", "Anime Romaji", null)
        val item = CalendarItemWithWatchlist(calendarItem, watchlistItem, null)

        val results = manager.search(item)

        assertTrue(results.isEmpty())
    }

    @Test
    fun testTorrentSearchMovie() = runTest {
        val manager = TorrentSearchManager(calendarDao, dao, autoDownloadRepository)
        val calendarItem = CalendarItem("v2_300_theater", 300, null, null, null, Instant.now(), null, false, false)
        val watchlistItem = TrackedWatchlistItem(300, MediaType.MOVIE, "Test Movie", null, null)
        val item = CalendarItemWithWatchlist(calendarItem, watchlistItem, null)

        val results = manager.search(item)

        assertTrue(results.isEmpty())
    }

    @Test
    fun testTorrentSearchTVWithEpisodeSearchStyleOverride() = runTest {
        val customSettings = ItemDownloadSettings(
            simklId = 100,
            episodeSearchStyle = EpisodeSearchStyle.Episode
        )
        coEvery { dao.getSettings(100) } returns customSettings
        val manager = TorrentSearchManager(calendarDao, dao, autoDownloadRepository)
        val calendarItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, true, false)
        val watchlistItem = TrackedWatchlistItem(100, MediaType.TV, "Test Show", null, null)
        val item = CalendarItemWithWatchlist(calendarItem, watchlistItem, null)

        val results = manager.search(item)
        val isEmpty = results.isEmpty()

        assertTrue(isEmpty)
        coVerify { anyConstructed<TpbProvider>().search(term = "Test Show 01 1080p", category = any(), orderBy = any()) }
    }

    @Test
    fun testTorrentSearchAnimeWithSeasonAndEpisodeSearchStyleOverride() = runTest {
        val customSettings = ItemDownloadSettings(
            simklId = 200,
            episodeSearchStyle = EpisodeSearchStyle.SeasonAndEpisode
        )
        coEvery { dao.getSettings(200) } returns customSettings
        val manager = TorrentSearchManager(calendarDao, dao, autoDownloadRepository)
        val calendarItem = CalendarItem("v2_200_1_5", 200, "Ep 5", 1, 5, Instant.now(), null, false, false)
        val watchlistItem = TrackedWatchlistItem(200, MediaType.ANIME, "Anime Show", "Anime Romaji", null)
        val item = CalendarItemWithWatchlist(calendarItem, watchlistItem, null)

        val results = manager.search(item)
        val isEmpty = results.isEmpty()

        assertTrue(isEmpty)
        coVerify { anyConstructed<NyaaProvider>().search(term = "Anime Romaji S01E05 1080p", category = any(), orderBy = any()) }
    }

    @Test
    fun testTorrentSearchAnimeNormalizesTitle() = runTest {
        val manager = TorrentSearchManager(calendarDao, dao, autoDownloadRepository)
        val calendarItem = CalendarItem("v2_500_1_1", 500, "Ep 1", 1, 1, Instant.now(), null, false, false)
        val watchlistItem = TrackedWatchlistItem(500, MediaType.ANIME, "Welcome to Demon School! Iruma-kun", "Mairimashita! Iruma-kun", null)
        val item = CalendarItemWithWatchlist(calendarItem, watchlistItem, null)

        val results = manager.search(item)
        val isEmpty = results.isEmpty()

        assertTrue(isEmpty)
        coVerify { anyConstructed<NyaaProvider>().search(term = "Mairimashita Iruma-kun 01 1080p", category = any(), orderBy = any()) }
    }

    @Test
    fun testTorrentSearchKeywordsCaseSensitivityAndHevc() = runTest {
        preferencesStateFlow.value = AutoDownloadPreferences(
            preferredKeywords = listOf("SubsPlease", "Erai-raws"),
            ignoreKeywords = listOf("BAD_RELEASE"),
            preferHevc = true
        )
        val manager = TorrentSearchManager(calendarDao, dao, autoDownloadRepository)
        val now = Instant.now()
        val calendarItem = CalendarItem("v2_400_1_1", 400, "Ep 1", 1, 1, now, null, false, false)
        val watchlistItem = TrackedWatchlistItem(400, MediaType.TV, "Test Show", null, null)
        val item = CalendarItemWithWatchlist(calendarItem, watchlistItem, null)
        val itemIgnoredExact = mockk<SearchResultItem>()
        every { itemIgnoredExact.name } returns "Test Show S01E01 BAD_RELEASE"
        every { itemIgnoredExact.uploadedAt } returns now
        val itemIgnoredCaseMismatch = mockk<SearchResultItem>()
        every { itemIgnoredCaseMismatch.name } returns "Test Show S01E01 bad_release"
        every { itemIgnoredCaseMismatch.uploadedAt } returns now
        val itemPreferredCaseMismatch = mockk<SearchResultItem>()
        every { itemPreferredCaseMismatch.name } returns "Test Show S01E01 subsplease"
        every { itemPreferredCaseMismatch.uploadedAt } returns now
        val itemPreferredExact = mockk<SearchResultItem>()
        every { itemPreferredExact.name } returns "Test Show S01E01 SubsPlease"
        every { itemPreferredExact.uploadedAt } returns now
        val itemHevcLowerCase = mockk<SearchResultItem>()
        every { itemHevcLowerCase.name } returns "Test Show S01E01 hevc"
        every { itemHevcLowerCase.uploadedAt } returns now
        val itemHevcUpperCase = mockk<SearchResultItem>()
        every { itemHevcUpperCase.name } returns "Test Show S01E01 HEVC"
        every { itemHevcUpperCase.uploadedAt } returns now
        coEvery { anyConstructed<TpbProvider>().search(any(), any(), any()) } returns Result.success(
            PaginatedSearchResult(
                listOf(
                    itemIgnoredExact,
                    itemIgnoredCaseMismatch,
                    itemPreferredCaseMismatch,
                    itemPreferredExact,
                    itemHevcLowerCase,
                    itemHevcUpperCase
                ),
                1,
                false
            )
        )

        val results = manager.search(item)

        assertFalse(results.contains(itemIgnoredExact))
        assertTrue(results.contains(itemIgnoredCaseMismatch))
        assertEquals("Test Show S01E01 SubsPlease", results[0].name)
        assertEquals("Test Show S01E01 hevc", results[1].name)
        assertEquals("Test Show S01E01 HEVC", results[2].name)
    }

    @Test
    fun testTorrentSearchFiltersOutOldUploads() = runTest {
        val manager = TorrentSearchManager(calendarDao, dao, autoDownloadRepository)
        val releaseDate = Instant.parse("2024-10-01T12:00:00Z")
        val calendarItem = CalendarItem("v2_500_1_1", 500, "Ep 1", 1, 1, releaseDate, null, false, false)
        val watchlistItem = TrackedWatchlistItem(500, MediaType.TV, "Test Show", null, null)
        val item = CalendarItemWithWatchlist(calendarItem, watchlistItem, null)
        val oldUpload = mockk<SearchResultItem>()
        every { oldUpload.name } returns "Test Show S01E01 1080p Old"
        every { oldUpload.uploadedAt } returns Instant.parse("2024-05-01T00:00:00Z")
        val validUpload = mockk<SearchResultItem>()
        every { validUpload.name } returns "Test Show S01E01 1080p Valid"
        every { validUpload.uploadedAt } returns Instant.parse("2024-09-01T00:00:00Z")
        coEvery { anyConstructed<TpbProvider>().search(any(), any(), any()) } returns Result.success(
            PaginatedSearchResult(
                listOf(oldUpload, validUpload),
                1,
                false
            )
        )

        val results = manager.search(item)

        assertEquals(1, results.size)
        assertEquals("Test Show S01E01 1080p Valid", results[0].name)
    }

    @Test
    fun testTorrentSearchUploadDateBoundaryAndBuffer() = runTest {
        val manager = TorrentSearchManager(calendarDao, dao, autoDownloadRepository)
        val releaseDate = Instant.parse("2024-10-01T12:00:00Z")
        val calendarItem = CalendarItem("v2_600_theater", 600, null, null, null, releaseDate, null, false, false)
        val watchlistItem = TrackedWatchlistItem(600, MediaType.MOVIE, "Test Movie", null, null)
        val item = CalendarItemWithWatchlist(calendarItem, watchlistItem, null)
        val uploadWayBeforeBuffer = mockk<SearchResultItem>()
        every { uploadWayBeforeBuffer.name } returns "Test Movie 1080p Old"
        every { uploadWayBeforeBuffer.uploadedAt } returns Instant.parse("2024-06-30T23:59:59Z")
        val uploadExactBufferCutoff = mockk<SearchResultItem>()
        every { uploadExactBufferCutoff.name } returns "Test Movie 1080p Cutoff"
        every { uploadExactBufferCutoff.uploadedAt } returns Instant.parse("2024-07-01T12:00:00Z")
        val uploadAfterRelease = mockk<SearchResultItem>()
        every { uploadAfterRelease.name } returns "Test Movie 1080p New"
        every { uploadAfterRelease.uploadedAt } returns Instant.parse("2024-10-02T12:00:00Z")
        coEvery { anyConstructed<TpbProvider>().search(any(), any(), any()) } returns Result.success(
            PaginatedSearchResult(
                listOf(uploadWayBeforeBuffer, uploadExactBufferCutoff, uploadAfterRelease),
                1,
                false
            )
        )

        val results = manager.search(item)

        assertEquals(1, results.size)
        assertEquals("Test Movie 1080p New", results[0].name)
    }



    @Test
    fun testDetectAnimeTorrents() = runTest {
        val manager = TorrentSearchManager(calendarDao, dao, autoDownloadRepository)
        val now = Instant.now()
        val calendarItem = CalendarItem("v2_200_1_1", 200, "Ep 1", 1, 1, now, null, false, false)
        val watchlistItem = TrackedWatchlistItem(200, MediaType.ANIME, "Anime Show", null, null)
        val itemWithWatchlist = CalendarItemWithWatchlist(calendarItem, watchlistItem, null)
        coEvery { calendarDao.findFirstItemForSimklId(200) } returns itemWithWatchlist

        val mockResult = mockk<SearchResultItem>()
        every { mockResult.name } returns "[SubsPlease] Boku Hero Academia S4 - 01 (1080p)"
        every { mockResult.uploadedAt } returns now
        coEvery { anyConstructed<NyaaProvider>().search(any(), any(), any()) } returns Result.success(
            PaginatedSearchResult(listOf(mockResult), 1, false)
        )

        val results = manager.detectAnimeTorrents(simklId = 200, customTitle = "Boku Hero Academia (S4 | 4th | IV)")

        assertEquals(1, results.size)
        assertEquals(mockResult, results[0])
    }
}
