package com.felixbrucker.simklcalendar.data.repository

import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.LocalItemState
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.database.WatchedEpisodeDao
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.network.AuthenticatedSimklApiService
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadPreferences
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarRepositoryTest {

    private lateinit var calendarDao: CalendarItemDao
    private lateinit var watchedDao: WatchedEpisodeDao
    private lateinit var authenticatedApiService: AuthenticatedSimklApiService
    private lateinit var autoDownloadRepo: AutoDownloadRepository
    private lateinit var calendarRepository: CalendarRepository

    @Before
    fun setUp() {
        calendarDao = mockk(relaxed = true)
        watchedDao = mockk(relaxed = true)
        authenticatedApiService = mockk(relaxed = true)
        autoDownloadRepo = mockk(relaxed = true)

        calendarRepository = CalendarRepository(
            calendarDao = calendarDao,
            watchedDao = watchedDao,
            itemDownloadSettingsDao = mockk(relaxed = true),
            authenticatedSimklApiService = authenticatedApiService,
            autoDownloadRepo = autoDownloadRepo
        )

        every { autoDownloadRepo.preferencesFlow } returns flowOf(AutoDownloadPreferences())
    }

    @Test
    fun testDetermineStatusFutureNotAiredYet() = runTest {
        val future = Instant.now().plusSeconds(3600)

        val status = calendarRepository.determineStatus(future, null, MediaType.TV, false, false)

        assertEquals(MediaStatus.NOT_AIRED_YET, status)
    }

    @Test
    fun testDetermineStatusTheaterIgnored() = runTest {
        val past = Instant.now().minusSeconds(3600)

        val status = calendarRepository.determineStatus(past, null, MediaType.MOVIE, true, false)

        assertEquals(MediaStatus.IGNORED, status)
    }

    @Test
    fun testDetermineStatusWatchedIgnored() = runTest {
        val past = Instant.now().minusSeconds(3600)

        val status = calendarRepository.determineStatus(past, null, MediaType.TV, false, true)

        assertEquals(MediaStatus.IGNORED, status)
    }

    @Test
    fun testDetermineStatusGlobalAutoDownloadWanted() = runTest {
        val past = Instant.now().minusSeconds(3600)
        every { autoDownloadRepo.preferencesFlow } returns flowOf(AutoDownloadPreferences(autoDownloadUnwatchedTv = true))

        val status = calendarRepository.determineStatus(past, null, MediaType.TV, false, false)

        assertEquals(MediaStatus.WANTED, status)
    }

    @Test
    fun testDetermineStatusSpecificSettingOverridesGlobal() = runTest {
        val past = Instant.now().minusSeconds(3600)
        val settings = ItemDownloadSettings(simklId = 100, downloadUnwatched = false)

        val status = calendarRepository.determineStatus(past, settings, MediaType.TV, false, false)

        assertEquals(MediaStatus.IGNORED, status)
    }

    @Test
    fun testMarkEpisodeWatchedSuccess() = runTest {
        coEvery { authenticatedApiService.markHistoryWatched(any()) } returns mockk(relaxed = true)

        val result = calendarRepository.markEpisodeWatched(100, 1, 1, MediaType.TV)
        val isSuccess = result.isSuccess

        assertTrue(isSuccess)
    }

    @Test
    fun testMarkSeasonWatchedSuccess() = runTest {
        coEvery { authenticatedApiService.markHistoryWatched(any()) } returns mockk(relaxed = true)

        val result = calendarRepository.markSeasonWatched(100, 1, MediaType.TV)
        val isSuccess = result.isSuccess

        assertTrue(isSuccess)
    }

    @Test
    fun testMarkMovieWatchedSuccess() = runTest {
        coEvery { authenticatedApiService.markHistoryWatched(any()) } returns mockk(relaxed = true)

        val result = calendarRepository.markMovieWatched(200)
        val isSuccess = result.isSuccess

        assertTrue(isSuccess)
    }

    @Test
    fun testUpdateMediaStatusMethods() = runTest {
        calendarRepository.updateMediaStatus("v2_10_1_1", MediaStatus.WANTED)
        calendarRepository.updateSeasonMediaStatus(10, 1, MediaStatus.IGNORED)
        calendarRepository.updateDownloadTaskId("v2_10_1_1", "task_123", MediaStatus.DOWNLOADING)

        coVerify { calendarDao.updateMediaStatus("v2_10_1_1", MediaStatus.WANTED) }
        coVerify { calendarDao.updateSeasonMediaStatus(10, 1, MediaStatus.IGNORED, any()) }
        coVerify { calendarDao.updateDownloadTaskId("v2_10_1_1", "task_123", MediaStatus.DOWNLOADING) }
    }
}
