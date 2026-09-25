package com.felixbrucker.simklcalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.LocalItemState
import com.felixbrucker.simklcalendar.data.database.NotificationSettingDao
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.UserToken
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.database.WatchedEpisodeDao
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.repository.CalendarRepository
import com.felixbrucker.simklcalendar.data.repository.DownloadRepository
import com.felixbrucker.simklcalendar.data.repository.WatchHistoryRepository
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import com.felixbrucker.simklcalendar.receiver.notification.NotificationActionReceiver
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

class NotificationActionReceiverTest {

    private lateinit var context: Context
    private lateinit var tokenDao: UserTokenDao
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var settingDao: NotificationSettingDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var watchedDao: WatchedEpisodeDao
    private lateinit var itemDownloadSettingsDao: ItemDownloadSettingsDao
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var androidNotificationManager: android.app.NotificationManager
    private lateinit var torrentServiceHelper: TorrentServiceHelper
    private lateinit var notificationManagerMock: NotificationManager
    private lateinit var repositoryMock: CalendarRepository
    private lateinit var downloadRepositoryMock: DownloadRepository
    private lateinit var watchHistoryRepositoryMock: WatchHistoryRepository

    @Before
    fun setUp() {
        torrentServiceHelper = mockk(relaxed = true)

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(Toast::class)
        val toastMock = mockk<Toast>(relaxed = true)
        every { Toast.makeText(any(), any<CharSequence>(), any()) } returns toastMock

        notificationManagerMock = mockk(relaxed = true)
        coEvery { notificationManagerMock.updateNotification(any()) } returns Unit
        coEvery { notificationManagerMock.showNotification(any()) } returns Unit
        coEvery { notificationManagerMock.dismissNotification(any<CalendarItemWithWatchlist>()) } returns Unit

        repositoryMock = mockk(relaxed = true)
        downloadRepositoryMock = mockk(relaxed = true)
        watchHistoryRepositoryMock = mockk(relaxed = true)

        coEvery { watchHistoryRepositoryMock.markEpisodeWatched(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { watchHistoryRepositoryMock.markSeasonWatched(any(), any(), any()) } returns Result.success(true)
        coEvery { repositoryMock.updateMediaStatus(any(), any()) } returns Unit
        coEvery { downloadRepositoryMock.searchAndDownloadEpisode(any()) } returns Result.success("task1")
        coEvery { downloadRepositoryMock.searchAndDownloadWantedItems() } returns Unit

        val mockInjector = mockk<com.felixbrucker.simklcalendar.receiver.notification.NotificationActionReceiver_GeneratedInjector>(relaxed = true)
        every { mockInjector.injectNotificationActionReceiver(any()) } answers {
            val rec = firstArg<NotificationActionReceiver>()
            rec.repo = repositoryMock
            rec.downloadRepository = downloadRepositoryMock
            rec.watchHistoryRepository = watchHistoryRepositoryMock
            rec.calendarItemDao = calendarDao
            rec.torrentServiceHelper = torrentServiceHelper
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
        tokenDao = mockk(relaxed = true)
        calendarDao = mockk(relaxed = true)
        settingDao = mockk(relaxed = true)
        watchlistDao = mockk(relaxed = true)
        watchedDao = mockk(relaxed = true)
        itemDownloadSettingsDao = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        androidNotificationManager = mockk(relaxed = true)

        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns androidNotificationManager
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.getBoolean(any(), any()) } answers { secondArg() }

        coEvery { tokenDao.getActiveToken() } returns UserToken(1, "token123", "User")
    }

    @After
    fun tearDown() {
        unmockkStatic(Toast::class)
        unmockkStatic(Log::class)
    }

    @Test
    fun testConstants() {
        val actionMarkWatched = NotificationActionReceiver.ACTION_MARK_ITEM_WATCHED
        val actionMarkSeasonWatched = NotificationActionReceiver.ACTION_MARK_SEASON_WATCHED
        val actionDownloadItem = NotificationActionReceiver.ACTION_DOWNLOAD_ITEM
        val actionDownloadSeason = NotificationActionReceiver.ACTION_DOWNLOAD_SEASON_MISSING_EPISODES
        val actionDismissed = NotificationActionReceiver.ACTION_NOTIFICATION_DISMISSED
        val extraKey = NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY

        assertEquals("com.felixbrucker.simklcalendar.ACTION_MARK_ITEM_WATCHED", actionMarkWatched)
        assertEquals("com.felixbrucker.simklcalendar.ACTION_MARK_SEASON_WATCHED", actionMarkSeasonWatched)
        assertEquals("com.felixbrucker.simklcalendar.ACTION_DOWNLOAD_ITEM", actionDownloadItem)
        assertEquals("com.felixbrucker.simklcalendar.ACTION_DOWNLOAD_SEASON_MISSING_EPISODES", actionDownloadSeason)
        assertEquals("com.felixbrucker.simklcalendar.ACTION_NOTIFICATION_DISMISSED", actionDismissed)
        assertEquals("extra_item_primary_key", extraKey)
    }

    @Test
    fun testOnReceiveNullContextOrIntent() {
        val receiver = NotificationActionReceiver()

        try {
            receiver.onReceive(null, null)
        } catch (_: Exception) {}
        receiver.onReceive(context, null)

        coVerify(exactly = 0) { calendarDao.findItem(any()) }
    }

    @Test
    fun testOnReceiveInvalidAction() {
        val receiver = NotificationActionReceiver()
        val invalidIntent = mockk<Intent>()
        every { invalidIntent.action } returns "INVALID_ACTION"

        receiver.onReceive(context, invalidIntent)

        coVerify(exactly = 0) { calendarDao.findItem(any()) }
    }

    @Test
    fun testOnReceiveMissingPrimaryKey() {
        val receiver = NotificationActionReceiver()
        val intent = mockk<Intent>()
        every { intent.action } returns NotificationActionReceiver.ACTION_MARK_ITEM_WATCHED
        every { intent.getStringExtra(NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns null

        receiver.onReceive(context, intent)

        coVerify(exactly = 0) { calendarDao.findItem(any()) }
    }

    @Test
    fun testOnReceiveMarkItemWatched() {
        val receiver = spyk(NotificationActionReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns NotificationActionReceiver.ACTION_MARK_ITEM_WATCHED
        every { intent.getStringExtra(NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_100_1_1"
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, false, false, false, null)
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.DOWNLOADED))
        coEvery { calendarDao.findItem("v2_100_1_1") } returns item

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify(timeout = 3000) { watchHistoryRepositoryMock.markEpisodeWatched(100, 1, 1, MediaType.TV) }
        coVerify(timeout = 3000) { notificationManagerMock.dismissNotification(item) }
    }

    @Test
    fun testOnReceiveMarkMovieWatched() {
        val receiver = spyk(NotificationActionReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns NotificationActionReceiver.ACTION_MARK_ITEM_WATCHED
        every { intent.getStringExtra(NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_200_theater"
        val calItem = CalendarItem("v2_200_theater", 200, "Movie", null, null, Instant.now(), null, false, false, false, null)
        val watchItem = TrackedWatchlistItem(200, MediaType.MOVIE, "Movie Title", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_200_theater", MediaStatus.DOWNLOADED))
        coEvery { calendarDao.findItem("v2_200_theater") } returns item
        coEvery { watchHistoryRepositoryMock.markMovieWatched(200) } returns Result.success(Unit)

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify(timeout = 3000) { watchHistoryRepositoryMock.markMovieWatched(200) }
        coVerify(timeout = 3000) { notificationManagerMock.dismissNotification(item) }
    }

    @Test
    fun testOnReceiveMarkSeasonWatched() {
        val receiver = spyk(NotificationActionReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns NotificationActionReceiver.ACTION_MARK_SEASON_WATCHED
        every { intent.getStringExtra(NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_100_1_1"
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, false, false, false, null)
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.DOWNLOADED))
        coEvery { calendarDao.findItem("v2_100_1_1") } returns item

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify(timeout = 3000) { watchHistoryRepositoryMock.markSeasonWatched(100, 1, MediaType.TV) }
        coVerify(timeout = 3000) { notificationManagerMock.dismissNotification(item) }
    }

    @Test
    fun testOnReceiveDownloadItem() {
        val receiver = spyk(NotificationActionReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns NotificationActionReceiver.ACTION_DOWNLOAD_ITEM
        every { intent.getStringExtra(NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_100_1_1"
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, false, false, false, null)
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.IGNORED))
        coEvery { calendarDao.findItem("v2_100_1_1") } returns item

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify(timeout = 3000) { repositoryMock.updateMediaStatus("v2_100_1_1", MediaStatus.WANTED) }
        verify(timeout = 3000) { torrentServiceHelper.unbind() }
    }

    @Test
    fun testOnReceiveNotificationDismissed() {
        val receiver = spyk(NotificationActionReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns NotificationActionReceiver.ACTION_NOTIFICATION_DISMISSED
        every { intent.getStringExtra(NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_100_1_1"

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify(timeout = 3000) { notificationManagerMock.removeActiveNotification("v2_100_1_1") }
    }

    @Test
    fun testOnReceiveDownloadSeasonMissingEpisodes() {
        val receiver = spyk(NotificationActionReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns NotificationActionReceiver.ACTION_DOWNLOAD_SEASON_MISSING_EPISODES
        every { intent.getStringExtra(NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_100_1_1"
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, false, false, false, null)
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.IGNORED))
        coEvery { calendarDao.findItem("v2_100_1_1") } returns item
        coEvery { calendarDao.getItemsInSeasonOrRelatedItems(100, 1) } returns listOf(item)

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify(timeout = 3000) { repositoryMock.updateMediaStatus("v2_100_1_1", MediaStatus.WANTED) }
        verify(timeout = 3000) { torrentServiceHelper.unbind() }
    }
}
