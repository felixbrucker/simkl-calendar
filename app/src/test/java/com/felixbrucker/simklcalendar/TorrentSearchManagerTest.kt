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

    private lateinit var dao: ItemDownloadSettingsDao
    private lateinit var autoDownloadRepository: AutoDownloadRepository
    private val preferencesStateFlow = MutableStateFlow(AutoDownloadPreferences())

    @Before
    fun setUp() {
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
        val manager = TorrentSearchManager(dao, autoDownloadRepository)

        assertNotNull(manager)
    }

    @Test
    fun testTorrentSearchTV() = runTest {
        val manager = TorrentSearchManager(dao, autoDownloadRepository)
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
        val manager = TorrentSearchManager(dao, autoDownloadRepository)
        val calendarItem = CalendarItem("v2_200_1_5", 200, "Ep 5", 1, 5, Instant.now(), null, false, false)
        val watchlistItem = TrackedWatchlistItem(200, MediaType.ANIME, "Anime Show", "Anime Romaji", null)
        val item = CalendarItemWithWatchlist(calendarItem, watchlistItem, null)

        val results = manager.search(item)

        assertTrue(results.isEmpty())
    }

    @Test
    fun testTorrentSearchMovie() = runTest {
        val manager = TorrentSearchManager(dao, autoDownloadRepository)
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
        val manager = TorrentSearchManager(dao, autoDownloadRepository)
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
        val manager = TorrentSearchManager(dao, autoDownloadRepository)
        val calendarItem = CalendarItem("v2_200_1_5", 200, "Ep 5", 1, 5, Instant.now(), null, false, false)
        val watchlistItem = TrackedWatchlistItem(200, MediaType.ANIME, "Anime Show", "Anime Romaji", null)
        val item = CalendarItemWithWatchlist(calendarItem, watchlistItem, null)

        val results = manager.search(item)
        val isEmpty = results.isEmpty()

        assertTrue(isEmpty)
        coVerify { anyConstructed<NyaaProvider>().search(term = "Anime Romaji S01E05 1080p", category = any(), orderBy = any()) }
    }

    @Test
    fun testTorrentSearchKeywordsCaseSensitivityAndHevc() = runTest {
        preferencesStateFlow.value = AutoDownloadPreferences(
            preferredKeywords = listOf("SubsPlease", "Erai-raws"),
            ignoreKeywords = listOf("BAD_RELEASE"),
            preferHevc = true
        )
        val manager = TorrentSearchManager(dao, autoDownloadRepository)
        val calendarItem = CalendarItem("v2_400_1_1", 400, "Ep 1", 1, 1, Instant.now(), null, false, false)
        val watchlistItem = TrackedWatchlistItem(400, MediaType.TV, "Test Show", null, null)
        val item = CalendarItemWithWatchlist(calendarItem, watchlistItem, null)
        val itemIgnoredExact = mockk<SearchResultItem>()
        every { itemIgnoredExact.name } returns "Test Show S01E01 BAD_RELEASE"
        val itemIgnoredCaseMismatch = mockk<SearchResultItem>()
        every { itemIgnoredCaseMismatch.name } returns "Test Show S01E01 bad_release"
        val itemPreferredCaseMismatch = mockk<SearchResultItem>()
        every { itemPreferredCaseMismatch.name } returns "Test Show S01E01 subsplease"
        val itemPreferredExact = mockk<SearchResultItem>()
        every { itemPreferredExact.name } returns "Test Show S01E01 SubsPlease"
        val itemHevcLowerCase = mockk<SearchResultItem>()
        every { itemHevcLowerCase.name } returns "Test Show S01E01 hevc"
        val itemHevcUpperCase = mockk<SearchResultItem>()
        every { itemHevcUpperCase.name } returns "Test Show S01E01 HEVC"
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
        val containsIgnoredExact = results.contains(itemIgnoredExact)
        val containsIgnoredCaseMismatch = results.contains(itemIgnoredCaseMismatch)
        val firstResultName = results[0].name
        val secondResultName = results[1].name
        val thirdResultName = results[2].name

        assertFalse(containsIgnoredExact)
        assertTrue(containsIgnoredCaseMismatch)
        assertEquals("Test Show S01E01 SubsPlease", firstResultName)
        assertEquals("Test Show S01E01 hevc", secondResultName)
        assertEquals("Test Show S01E01 HEVC", thirdResultName)
    }
}
