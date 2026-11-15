package com.felixbrucker.simklcalendar.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.felixbrucker.simklcalendar.data.database.EpisodeType
import com.felixbrucker.simklcalendar.data.database.MediaEpisode
import com.felixbrucker.simklcalendar.data.database.MediaItem
import com.felixbrucker.simklcalendar.data.database.MediaItemWithDetails
import com.felixbrucker.simklcalendar.data.database.WatchlistItemDao
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.network.AuthenticatedSimklApiService
import com.felixbrucker.simklcalendar.data.preferences.UiPreferences
import com.felixbrucker.simklcalendar.data.preferences.UiRepository
import com.felixbrucker.simklcalendar.data.repository.CalendarRepository
import com.felixbrucker.simklcalendar.data.repository.CustomSearchLinkRepository
import com.felixbrucker.simklcalendar.data.repository.MediaRepository
import com.felixbrucker.simklcalendar.data.repository.SyncRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MediaItemDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val syncRepository: SyncRepository = mockk(relaxed = true)
    private val authenticatedSimklApiService: AuthenticatedSimklApiService = mockk(relaxed = true)
    private val calendarRepository: CalendarRepository = mockk(relaxed = true)
    private val customSearchLinkRepository: CustomSearchLinkRepository = mockk(relaxed = true)
    private val watchlistItemDao: WatchlistItemDao = mockk(relaxed = true)
    private val uiRepository: UiRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { customSearchLinkRepository.customSearchLinks } returns flowOf(emptyList())
        every { uiRepository.preferencesFlow } returns flowOf(UiPreferences())
        every { calendarRepository.getWatchlistItemFlow(any()) } returns flowOf(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun episodeListUiState_transformsEpisodesAndFiltersSpecials() = runTest {
        val simklId = 123
        val savedStateHandle = SavedStateHandle(mapOf("simklId" to simklId, "mediaType" to "tv"))

        val mediaItem = MediaItem(
            simklId = simklId,
            mediaType = MediaType.TV,
            title = "Test Show",
            lastUpdatedAt = Instant.now(),
        )
        val episodes = listOf(
            MediaEpisode(simklId = simklId, season = 0, episodeNumber = 1, title = "Special 1", type = EpisodeType.SPECIAL),
            MediaEpisode(simklId = simklId, season = 1, episodeNumber = 1, title = "Pilot", type = EpisodeType.EPISODE),
            MediaEpisode(simklId = simklId, season = 1, episodeNumber = 2, title = "Episode 2", type = EpisodeType.EPISODE),
            MediaEpisode(simklId = simklId, season = 2, episodeNumber = 1, title = "S2 Pilot", type = EpisodeType.EPISODE)
        )
        val details = MediaItemWithDetails(
            mediaItem = mediaItem,
            episodes = episodes
        )

        every { mediaRepository.getMediaItemStream(simklId, MediaType.TV) } returns flowOf(details)

        val viewModel = MediaItemDetailViewModel(
            mediaRepository = mediaRepository,
            syncRepository = syncRepository,
            authenticatedSimklApiService = authenticatedSimklApiService,
            calendarRepository = calendarRepository,
            customSearchLinkRepository = customSearchLinkRepository,
            watchlistItemDao = watchlistItemDao,
            uiRepository = uiRepository,
            savedStateHandle = savedStateHandle,
            defaultDispatcher = testDispatcher
        )

        val job = launch(testDispatcher) { viewModel.episodeListUiState.collect() }

        val state = viewModel.episodeListUiState.value
        assertEquals(3, state.totalCount)

        // Since there are 2 seasons, season headers should be present
        assertTrue(state.items.any { (it is EpisodeListItemUiModel.SeasonHeader) && (it.season == 1) })
        assertTrue(state.items.any { (it is EpisodeListItemUiModel.SeasonHeader) && (it.season == 2) })

        val epRows = state.items.filterIsInstance<EpisodeListItemUiModel.EpisodeRow>()
        assertEquals(3, epRows.size)
        assertEquals("Pilot", epRows[0].title)
        assertEquals("S01E01", epRows[0].episodeCode)
        assertEquals("Episode 2", epRows[1].title)
        assertEquals("S01E02", epRows[1].episodeCode)
        assertEquals("S2 Pilot", epRows[2].title)
        assertEquals("S02E01", epRows[2].episodeCode)

        job.cancel()
    }

    @Test
    fun episodeListUiState_singleSeason_doesNotIncludeSeasonHeaders() = runTest {
        val simklId = 456
        val savedStateHandle = SavedStateHandle(mapOf("simklId" to simklId, "mediaType" to "anime"))

        val mediaItem = MediaItem(
            simklId = simklId,
            mediaType = MediaType.ANIME,
            title = "Test Anime",
            lastUpdatedAt = Instant.now(),
        )
        val episodes = listOf(
            MediaEpisode(simklId = simklId, season = 1, episodeNumber = 1, title = "Anime Ep 1", type = EpisodeType.EPISODE),
            MediaEpisode(simklId = simklId, season = 1, episodeNumber = 2, title = "Anime Ep 2", type = EpisodeType.EPISODE)
        )
        val details = MediaItemWithDetails(
            mediaItem = mediaItem,
            episodes = episodes
        )

        every { mediaRepository.getMediaItemStream(simklId, MediaType.ANIME) } returns flowOf(details)

        val viewModel = MediaItemDetailViewModel(
            mediaRepository = mediaRepository,
            syncRepository = syncRepository,
            authenticatedSimklApiService = authenticatedSimklApiService,
            calendarRepository = calendarRepository,
            customSearchLinkRepository = customSearchLinkRepository,
            watchlistItemDao = watchlistItemDao,
            uiRepository = uiRepository,
            savedStateHandle = savedStateHandle,
            defaultDispatcher = testDispatcher
        )

        val job = launch(testDispatcher) { viewModel.episodeListUiState.collect() }

        val state = viewModel.episodeListUiState.value
        assertEquals(2, state.totalCount)
        assertTrue(state.items.none { it is EpisodeListItemUiModel.SeasonHeader })

        val epRows = state.items.filterIsInstance<EpisodeListItemUiModel.EpisodeRow>()
        assertEquals(2, epRows.size)
        assertEquals("E01", epRows[0].episodeCode)
        assertEquals("E02", epRows[1].episodeCode)

        job.cancel()
    }
}
