package com.felixbrucker.simklcalendar.ui.viewmodel

import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.preferences.*
import com.felixbrucker.simklcalendar.data.repository.CustomSearchLinkRepository
import com.felixbrucker.simklcalendar.data.repository.SyncRepository
import com.felixbrucker.simklcalendar.data.repository.UserRepository
import com.felixbrucker.simklcalendar.data.sentry.SentryManager
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import com.felixbrucker.simklcalendar.receiver.alarm.AlarmScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val userRepositoryMock: UserRepository = mockk(relaxed = true)
    private val customSearchLinkRepositoryMock: CustomSearchLinkRepository = mockk(relaxed = true)
    private val syncRepositoryMock: SyncRepository = mockk(relaxed = true)
    private val appSettingsRepo: AppSettingsRepository = mockk(relaxed = true)
    private val autoDownloadRepo: AutoDownloadRepository = mockk(relaxed = true)
    private val notificationRepo: NotificationRepository = mockk(relaxed = true)
    private val torrentServiceHelper: TorrentServiceHelper = mockk(relaxed = true)
    private val alarmScheduler: AlarmScheduler = mockk(relaxed = true)
    private val sentryManager: SentryManager = mockk(relaxed = true)

    private val userTokenFlow = MutableStateFlow<UserToken?>(null)
    private val customSearchLinksFlow = MutableStateFlow<List<CustomSearchLink>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        userTokenFlow.value = null
        customSearchLinksFlow.value = emptyList()

        every { userRepositoryMock.activeUserToken } returns userTokenFlow
        every { customSearchLinkRepositoryMock.customSearchLinks } returns customSearchLinksFlow
        every { notificationRepo.preferencesFlow } returns flowOf(NotificationPreferences())
        every { appSettingsRepo.preferencesFlow } returns flowOf(AppSettingsPreferences())
        every { autoDownloadRepo.preferencesFlow } returns flowOf(AutoDownloadPreferences())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSettingsUpdates() = runTest {
        every { sentryManager.isDsnConfigured } returns true
        val viewModel = SettingsViewModel(
            userRepository = userRepositoryMock,
            customSearchLinkRepository = customSearchLinkRepositoryMock,
            syncRepository = syncRepositoryMock,
            appSettingsRepo = appSettingsRepo,
            autoDownloadRepo = autoDownloadRepo,
            notificationRepo = notificationRepo,
            torrentServiceHelper = torrentServiceHelper,
            alarmScheduler = alarmScheduler,
            sentryManager = sentryManager
        )

        viewModel.logoutUser()
        viewModel.updateSyncInterval(6)
        viewModel.updateSentryEnabled(false)
        val isConfigured = viewModel.isSentryConfigured
        viewModel.updateSearchInterval(4)
        viewModel.updateUseExactAlarms(true)
        viewModel.updateDefaultNotifyAiring(true)
        viewModel.updateDefaultNotifySeasonFinished(true)
        viewModel.updateDefaultNotifyMovieTheater(true)
        viewModel.updateDefaultNotifyMovieDigital(true)
        viewModel.scheduleAllItemsAiredAlarms()
        advanceUntilIdle()

        assertTrue(isConfigured)
        coVerify { userRepositoryMock.logout() }
        coVerify { appSettingsRepo.setSyncIntervalHours(6) }
        coVerify { appSettingsRepo.setIsSentryEnabled(false) }
        coVerify { autoDownloadRepo.setSearchIntervalHours(4) }
        coVerify { notificationRepo.setUseExactAlarms(true) }
        coVerify { notificationRepo.setDefaultNotifyAiring(true) }
        coVerify { notificationRepo.setDefaultNotifySeasonFinished(true) }
        coVerify { notificationRepo.setDefaultNotifyMovieTheater(true) }
        coVerify { notificationRepo.setDefaultNotifyMovieDigital(true) }
        coVerify { alarmScheduler.scheduleAllItemsAiredAlarms() }
    }

    @Test
    fun testAutoDownloadSettingsAndKeywords() = runTest {
        val viewModel = SettingsViewModel(
            userRepository = userRepositoryMock,
            customSearchLinkRepository = customSearchLinkRepositoryMock,
            syncRepository = syncRepositoryMock,
            appSettingsRepo = appSettingsRepo,
            autoDownloadRepo = autoDownloadRepo,
            notificationRepo = notificationRepo,
            torrentServiceHelper = torrentServiceHelper,
            alarmScheduler = alarmScheduler,
            sentryManager = sentryManager
        )

        viewModel.updateAutoDownloadQuality("1080p")
        viewModel.updateAutoDownloadPreferHevc(true)
        viewModel.updateAutoDownloadUnwatchedTv(true)
        viewModel.updateAutoDownloadUnwatchedAnime(true)
        viewModel.updateAutoDownloadUnwatchedMovie(true)
        viewModel.updateAutoDownloadSeasonUnwatchedTv(true)
        viewModel.updateAutoDownloadSeasonUnwatchedAnime(true)

        viewModel.addPreferredKeyword("Tag1")
        viewModel.removePreferredKeyword("Tag1")
        viewModel.updatePreferredKeywordsOrder(listOf("Tag2", "Tag1"))

        viewModel.addIgnoreKeyword("Skip1")
        viewModel.removeIgnoreKeyword("Skip1")
        viewModel.updateIgnoreKeywordsOrder(listOf("Skip2", "Skip1"))
        advanceUntilIdle()

        coVerify { autoDownloadRepo.setQuality("1080p") }
        coVerify { autoDownloadRepo.setPreferHevc(true) }
        coVerify { autoDownloadRepo.setAutoDownloadUnwatchedTv(true) }
        coVerify { autoDownloadRepo.setAutoDownloadUnwatchedAnime(true) }
        coVerify { autoDownloadRepo.setAutoDownloadUnwatchedMovie(true) }
        coVerify { autoDownloadRepo.setAutoDownloadSeasonUnwatchedTv(true) }
        coVerify { autoDownloadRepo.setAutoDownloadSeasonUnwatchedAnime(true) }
        coVerify { autoDownloadRepo.setPreferredKeywords(any()) }
        coVerify { autoDownloadRepo.setIgnoreKeywords(any()) }
    }

    @Test
    fun testCustomSearchLinkOperations() = runTest {
        val viewModel = SettingsViewModel(
            userRepository = userRepositoryMock,
            customSearchLinkRepository = customSearchLinkRepositoryMock,
            syncRepository = syncRepositoryMock,
            appSettingsRepo = appSettingsRepo,
            autoDownloadRepo = autoDownloadRepo,
            notificationRepo = notificationRepo,
            torrentServiceHelper = torrentServiceHelper,
            alarmScheduler = alarmScheduler,
            sentryManager = sentryManager
        )
        val link1 = CustomSearchLink(id = 0L, name = "Link 1", urlTemplate = "https://test.com/{query}", position = 0)
        val link2 = CustomSearchLink(id = 2L, name = "Link 2", urlTemplate = "https://test2.com/{query}", position = 1)

        viewModel.saveCustomSearchLink(link1)
        viewModel.saveCustomSearchLink(link2)
        viewModel.updateSearchLinksOrder(listOf(link2, link1))
        viewModel.deleteCustomSearchLink(link2)
        advanceUntilIdle()

        coVerify { customSearchLinkRepositoryMock.insertSearchLink(any()) }
        coVerify { customSearchLinkRepositoryMock.updateSearchLink(link2) }
        coVerify { customSearchLinkRepositoryMock.updateSearchLinks(any()) }
        coVerify { customSearchLinkRepositoryMock.deleteSearchLink(link2) }
    }

    @Test
    fun testForceWatchlistResyncNoTokenAndSuccess() = runTest {
        coEvery { userRepositoryMock.getActiveUserToken() } returns null
        val viewModel = SettingsViewModel(
            userRepository = userRepositoryMock,
            customSearchLinkRepository = customSearchLinkRepositoryMock,
            syncRepository = syncRepositoryMock,
            appSettingsRepo = appSettingsRepo,
            autoDownloadRepo = autoDownloadRepo,
            notificationRepo = notificationRepo,
            torrentServiceHelper = torrentServiceHelper,
            alarmScheduler = alarmScheduler,
            sentryManager = sentryManager
        )

        var noTokenOk = true
        var noTokenMsg = ""
        viewModel.forceWatchlistResync { ok, msg ->
            noTokenOk = ok
            noTokenMsg = msg
        }
        advanceUntilIdle()

        coEvery { userRepositoryMock.getActiveUserToken() } returns UserToken(accessToken = "valid_token", username = "user")
        var successOk = false
        var successMsg = ""
        viewModel.forceWatchlistResync { ok, msg ->
            successOk = ok
            successMsg = msg
        }
        advanceUntilIdle()

        assertFalse(noTokenOk)
        assertEquals("User is not logged in", noTokenMsg)
        assertTrue(successOk)
        assertEquals("Watchlist re-synced successfully", successMsg)
    }

    @Test
    fun testIsSentryRunningReturnsTrueWhenRunning() = runTest {
        every { sentryManager.isSentryRunning } returns true
        val viewModel = SettingsViewModel(
            userRepository = userRepositoryMock,
            customSearchLinkRepository = customSearchLinkRepositoryMock,
            syncRepository = syncRepositoryMock,
            appSettingsRepo = appSettingsRepo,
            autoDownloadRepo = autoDownloadRepo,
            notificationRepo = notificationRepo,
            torrentServiceHelper = torrentServiceHelper,
            alarmScheduler = alarmScheduler,
            sentryManager = sentryManager
        )

        val isRunning = viewModel.isSentryRunning

        assertTrue(isRunning)
    }

    @Test
    fun testIsSentryRunningReturnsFalseWhenNotRunning() = runTest {
        every { sentryManager.isSentryRunning } returns false
        val viewModel = SettingsViewModel(
            userRepository = userRepositoryMock,
            customSearchLinkRepository = customSearchLinkRepositoryMock,
            syncRepository = syncRepositoryMock,
            appSettingsRepo = appSettingsRepo,
            autoDownloadRepo = autoDownloadRepo,
            notificationRepo = notificationRepo,
            torrentServiceHelper = torrentServiceHelper,
            alarmScheduler = alarmScheduler,
            sentryManager = sentryManager
        )

        val isRunning = viewModel.isSentryRunning

        assertFalse(isRunning)
    }
}
