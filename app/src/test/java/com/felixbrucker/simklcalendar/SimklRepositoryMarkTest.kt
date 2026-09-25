package com.felixbrucker.simklcalendar

import android.content.Context
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.database.WatchedEpisodeDao
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.network.AuthenticatedSimklApiService
import com.felixbrucker.simklcalendar.data.network.PublicSimklApiService
import com.felixbrucker.simklcalendar.data.repository.WatchHistoryRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SimklRepositoryMarkTest {

    private lateinit var context: Context
    private lateinit var tokenDao: UserTokenDao
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var watchedDao: WatchedEpisodeDao
    private lateinit var publicApiService: PublicSimklApiService
    private lateinit var authenticatedApiService: AuthenticatedSimklApiService
    private lateinit var repository: WatchHistoryRepository

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        tokenDao = mockk(relaxed = true)
        calendarDao = mockk(relaxed = true)
        watchedDao = mockk(relaxed = true)
        publicApiService = mockk(relaxed = true)
        authenticatedApiService = mockk(relaxed = true)

        coEvery { tokenDao.getActiveToken() } returns UserToken(1, "simkl_at_test_token", "User")
        coEvery { authenticatedApiService.markHistoryWatched(any()) } returns mockk(relaxed = true)
        coEvery { authenticatedApiService.markHistoryUnwatched(any()) } returns mockk(relaxed = true)

        repository = WatchHistoryRepository(
            calendarDao = calendarDao,
            watchedDao = watchedDao,
            authenticatedSimklApiService = authenticatedApiService
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testMarkEpisodeWatchedSuccess() = runTest {
        coEvery { watchedDao.insertWatchedEpisodes(any()) } returns Unit
        coEvery { calendarDao.markEpisodeWatched(any(), any(), any(), any()) } returns Unit

        val result = repository.markEpisodeWatched(100, 1, 1, MediaType.TV)
        val isSuccess = result.isSuccess

        assertTrue(isSuccess)
    }

    @Test
    fun testMarkEpisodeWatchedAnimeSuccess() = runTest {
        coEvery { watchedDao.insertWatchedEpisodes(any()) } returns Unit
        coEvery { calendarDao.markEpisodeWatched(any(), any(), any(), any()) } returns Unit

        val result = repository.markEpisodeWatched(100, 1, 1, MediaType.ANIME)
        val isSuccess = result.isSuccess

        assertTrue(isSuccess)
    }

    @Test
    fun testMarkMovieWatchedSuccess() = runTest {
        coEvery { calendarDao.markMovieWatched(any(), any()) } returns Unit

        val result = repository.markMovieWatched(200)
        val isSuccess = result.isSuccess

        assertTrue(isSuccess)
    }

    @Test
    fun testMarkSeasonWatchedSuccess() = runTest {
        coEvery { calendarDao.getItemsForSimklId(100) } returns emptyList()
        coEvery { watchedDao.getWatchedEpisodesForShow(100) } returns emptyList()
        coEvery { calendarDao.markSeasonWatched(any(), any(), any()) } returns Unit

        val result = repository.markSeasonWatched(100, 1, MediaType.TV)
        val isSuccess = result.isSuccess

        assertTrue(isSuccess)
    }

    @Test
    fun testMarkSeasonWatchedAnimeSuccess() = runTest {
        coEvery { calendarDao.getItemsForSimklId(100) } returns emptyList()
        coEvery { watchedDao.getWatchedEpisodesForShow(100) } returns emptyList()
        coEvery { calendarDao.markSeasonWatched(any(), any(), any()) } returns Unit

        val result = repository.markSeasonWatched(100, 1, MediaType.ANIME)
        val isSuccess = result.isSuccess

        assertTrue(isSuccess)
    }

    @Test
    fun testMarkEpisodeUnwatchedSuccess() = runTest {
        coEvery { watchedDao.deleteWatchedEpisode(any(), any(), any()) } returns Unit
        coEvery { calendarDao.markEpisodeWatched(any(), any(), any(), any()) } returns Unit

        val result = repository.markEpisodeUnwatched(100, 1, 1, MediaType.TV)
        val isSuccess = result.isSuccess

        assertTrue(isSuccess)
    }

    @Test
    fun testMarkEpisodeUnwatchedAnimeSuccess() = runTest {
        coEvery { watchedDao.deleteWatchedEpisode(any(), any(), any()) } returns Unit
        coEvery { calendarDao.markEpisodeWatched(any(), any(), any(), any()) } returns Unit

        val result = repository.markEpisodeUnwatched(100, 1, 1, MediaType.ANIME)
        val isSuccess = result.isSuccess

        assertTrue(isSuccess)
    }

    @Test
    fun testMarkSeasonUnwatchedSuccess() = runTest {
        coEvery { watchedDao.deleteWatchedSeason(any(), any()) } returns Unit
        coEvery { calendarDao.markSeasonWatched(any(), any(), any()) } returns Unit

        val result = repository.markSeasonUnwatched(100, 1, MediaType.TV)
        val isSuccess = result.isSuccess

        assertTrue(isSuccess)
    }

    @Test
    fun testMarkSeasonUnwatchedAnimeSuccess() = runTest {
        coEvery { watchedDao.deleteWatchedSeason(any(), any()) } returns Unit
        coEvery { calendarDao.markSeasonWatched(any(), any(), any()) } returns Unit

        val result = repository.markSeasonUnwatched(100, 1, MediaType.ANIME)
        val isSuccess = result.isSuccess

        assertTrue(isSuccess)
    }

    @Test
    fun testMarkMovieUnwatchedSuccess() = runTest {
        coEvery { calendarDao.markMovieWatched(any(), any()) } returns Unit

        val result = repository.markMovieUnwatched(200)
        val isSuccess = result.isSuccess

        assertTrue(isSuccess)
    }
}
