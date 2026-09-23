package com.felixbrucker.simklcalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.LocalItemState
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class DownloadCompletedReceiverTest {

    private lateinit var context: Context
    private lateinit var repositoryMock: SimklRepository
    private lateinit var notificationManagerMock: NotificationManager
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var tokenDao: UserTokenDao

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        notificationManagerMock = mockk(relaxed = true)
        coEvery { notificationManagerMock.updateNotification(any()) } returns Unit

        repositoryMock = mockk(relaxed = true)
        val mockInjector = mockk<com.felixbrucker.simklcalendar.receiver.download.DownloadCompletedReceiver_GeneratedInjector>(relaxed = true)
        every { mockInjector.injectDownloadCompletedReceiver(any()) } answers {
            val rec = firstArg<DownloadCompletedReceiver>()
            rec.repo = repositoryMock
            rec.calendarItemDao = calendarDao
            rec.notificationManager = notificationManagerMock
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
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, false, false, false, null)
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.DOWNLOADED))
        val finaleCalItem = CalendarItem("v2_100_1_10", 100, "Finale", 1, 10, Instant.now(), null, false, true, false, null)
        val finaleItem = CalendarItemWithWatchlist(finaleCalItem, watchItem, LocalItemState("v2_100_1_10", MediaStatus.DOWNLOADED))
        coEvery { calendarDao.findItem("v2_100_1_1") } returns item
        coEvery { calendarDao.getSeasonFinaleItem(100, 1) } returns finaleItem

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify(timeout = 3000) { repositoryMock.updateDownloadTaskId("v2_100_1_1", null, MediaStatus.DOWNLOADED) }
    }
}
