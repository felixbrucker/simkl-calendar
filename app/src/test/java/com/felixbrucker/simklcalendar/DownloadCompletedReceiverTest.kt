package com.felixbrucker.simklcalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.LocalItemState
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadPreferences
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import com.felixbrucker.simklcalendar.data.repository.CalendarRepository
import com.felixbrucker.simklcalendar.receiver.download.DownloadCompletedReceiver
import com.felixbrucker.simklcalendar.receiver.notification.NotificationManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

class DownloadCompletedReceiverTest {

    private lateinit var context: Context
    private lateinit var repositoryMock: CalendarRepository
    private lateinit var notificationManagerMock: NotificationManager
    private lateinit var autoDownloadRepositoryMock: AutoDownloadRepository
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var tokenDao: UserTokenDao
    private lateinit var itemDownloadSettingsDao: ItemDownloadSettingsDao

    @Before
    fun setUp() {
        itemDownloadSettingsDao = mockk(relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        notificationManagerMock = mockk(relaxed = true)
        coEvery { notificationManagerMock.updateNotification(any()) } returns Unit
        autoDownloadRepositoryMock = mockk(relaxed = true)
        every { autoDownloadRepositoryMock.preferencesFlow } returns flowOf(AutoDownloadPreferences())

        repositoryMock = mockk(relaxed = true)
        watchlistDao = mockk(relaxed = true)
        val mockInjector = mockk<com.felixbrucker.simklcalendar.receiver.download.DownloadCompletedReceiver_GeneratedInjector>(relaxed = true)
        every { mockInjector.injectDownloadCompletedReceiver(any()) } answers {
            val rec = firstArg<DownloadCompletedReceiver>()
            rec.calendarRepository = repositoryMock
            rec.calendarItemDao = calendarDao
            rec.watchlistDao = watchlistDao
            rec.itemDownloadSettingsDao = itemDownloadSettingsDao
            rec.notificationManager = notificationManagerMock
            rec.autoDownloadRepository = autoDownloadRepositoryMock
        }
        val mockComponentManager = mockk<dagger.hilt.internal.GeneratedComponentManager<Any>>(relaxed = true)
        every { mockComponentManager.generatedComponent() } returns mockInjector

        val mockApp = mockk<android.app.Application>(
            moreInterfaces = arrayOf(
                dagger.hilt.internal.GeneratedComponentManagerHolder::class,
                dagger.hilt.internal.GeneratedComponentManager::class
            ),
            relaxed = true
        )
        every { (mockApp as dagger.hilt.internal.GeneratedComponentManagerHolder).componentManager() } returns mockComponentManager
        every { (mockApp as dagger.hilt.internal.GeneratedComponentManager<*>).generatedComponent() } returns mockInjector

        context = mockk(relaxed = true)
        every { context.applicationContext } returns mockApp
        calendarDao = mockk(relaxed = true)
        tokenDao = mockk(relaxed = true)

        coEvery { tokenDao.getActiveToken() } returns UserToken(1, "token123", "User")
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun testConstants() {
        val action = DownloadCompletedReceiver.ACTION_DOWNLOAD_COMPLETED
        val extra = DownloadCompletedReceiver.EXTRA_ITEM_PRIMARY_KEY

        assertEquals("com.felixbrucker.simklcalendar.ACTION_DOWNLOAD_COMPLETED", action)
        assertEquals("extra_item_primary_key", extra)
    }

    @Test
    fun testOnReceiveNullOrInvalidAction() {
        val receiver = DownloadCompletedReceiver()
        val intent = mockk<Intent>()
        every { intent.action } returns "INVALID_ACTION"

        try {
            receiver.onReceive(null, null)
        } catch (_: Exception) {}
        receiver.onReceive(context, null)
        receiver.onReceive(context, intent)

        coVerify(exactly = 0) { calendarDao.updateDownloadTaskId(any(), any(), any()) }
    }

    @Test
    fun testOnReceiveMissingExtra() {
        val receiver = DownloadCompletedReceiver()
        val intent = mockk<Intent>()
        every { intent.action } returns DownloadCompletedReceiver.ACTION_DOWNLOAD_COMPLETED
        every { intent.getStringExtra(DownloadCompletedReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns null

        receiver.onReceive(context, intent)

        coVerify(exactly = 0) { calendarDao.updateDownloadTaskId(any(), any(), any()) }
    }

    @Test
    fun testOnReceiveValidActionItemFound() {
        val receiver = spyk(DownloadCompletedReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns DownloadCompletedReceiver.ACTION_DOWNLOAD_COMPLETED
        every { intent.getStringExtra(DownloadCompletedReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_100_1_1"
        every { intent.getStringArrayListExtra(DownloadCompletedReceiver.EXTRA_FILE_PATHS) } returns null
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, false, false, false, null)
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.DOWNLOADED))
        val finaleCalItem = CalendarItem("v2_100_1_10", 100, "Finale", 1, 10, Instant.now(), null, false, true, false, null)
        val finaleItem = CalendarItemWithWatchlist(finaleCalItem, watchItem, LocalItemState("v2_100_1_10", MediaStatus.DOWNLOADED))
        coEvery { calendarDao.findItem("v2_100_1_1") } returns item
        coEvery { calendarDao.getSeasonFinaleItem(100, 1) } returns finaleItem

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify(timeout = 3000) {
            repositoryMock.updateDownloadCompleted(
                "v2_100_1_1",
                null,
                MediaStatus.DOWNLOADED,
                null
            )
        }
    }

    @Test
    fun testOnReceiveWithPostProcessingRenamesFile() {
        val receiver = spyk(DownloadCompletedReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns DownloadCompletedReceiver.ACTION_DOWNLOAD_COMPLETED
        every { intent.getStringExtra(DownloadCompletedReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_100_1_1"

        val tempDir = File.createTempFile("test_dir", "").apply {
            delete()
            mkdir()
        }
        val sourceFile = File(tempDir, "The.Bear.S02E05.1080p.WEB-DL.DDP5.1.Atmos.H.264-FLUX.mkv")
        sourceFile.writeText("sample")
        val filePathList = arrayListOf(sourceFile.absolutePath)
        every { intent.getStringArrayListExtra(DownloadCompletedReceiver.EXTRA_FILE_PATHS) } returns filePathList

        val calItem = CalendarItem("v2_100_1_1", 100, "Episode 5", 2, 5, Instant.now(), null, false, false, false, null)
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "The Bear", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.DOWNLOADED))
        coEvery { calendarDao.findItem("v2_100_1_1") } returns item
        coEvery { calendarDao.getSeasonFinaleItem(100, 2) } returns null

        val postProcessPrefs = AutoDownloadPreferences(
            postProcessTvEnabled = true,
            postProcessReplaceSpacesWithDotsTv = true,
            postProcessTemplateTv = "{title}.{episode_slug}.{resolution}.{source}-{release_group}"
        )
        every { autoDownloadRepositoryMock.preferencesFlow } returns flowOf(postProcessPrefs)

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify(timeout = 3000) {
            repositoryMock.updateDownloadCompleted(
                "v2_100_1_1",
                null,
                MediaStatus.DOWNLOADED,
                match { it != null && it.endsWith("The.Bear.S02E05.1080p.WEB-DL-FLUX.mkv") }
            )
        }

        tempDir.deleteRecursively()
    }
}
