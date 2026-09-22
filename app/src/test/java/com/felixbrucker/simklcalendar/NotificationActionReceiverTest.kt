package com.felixbrucker.simklcalendar

import android.app.NotificationManager as AndroidNotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import com.felixbrucker.simklcalendar.data.database.AppDatabase
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
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import com.felixbrucker.simklcalendar.receiver.notification.NotificationActionReceiver
import com.felixbrucker.simklcalendar.receiver.notification.NotificationManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class NotificationActionReceiverTest {

    private lateinit var context: Context
    private lateinit var appDatabase: AppDatabase
    private lateinit var tokenDao: UserTokenDao
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var settingDao: NotificationSettingDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var watchedDao: WatchedEpisodeDao
    private lateinit var itemDownloadSettingsDao: ItemDownloadSettingsDao
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var androidNotificationManager: AndroidNotificationManager
    private lateinit var torrentServiceHelper: TorrentServiceHelper

    @Before
    fun setUp() {
        torrentServiceHelper = mockk(relaxed = true)
        mockkObject(TorrentServiceHelper.Companion)
        every { TorrentServiceHelper.getInstance(any()) } returns torrentServiceHelper

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(Toast::class)
        val toastMock = mockk<Toast>(relaxed = true)
        every { Toast.makeText(any(), any<CharSequence>(), any()) } returns toastMock

        mockkObject(NotificationManager)
        coEvery { NotificationManager.updateNotification(any(), any()) } returns Unit
        coEvery { NotificationManager.showNotification(any(), any()) } returns Unit
        coEvery { NotificationManager.dismissNotification(any<CalendarItemWithWatchlist>(), any()) } returns Unit

        mockkConstructor(SimklRepository::class)
        coEvery { anyConstructed<SimklRepository>().markEpisodeWatched(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { anyConstructed<SimklRepository>().markSeasonWatched(any(), any(), any()) } returns Result.success(true)
        coEvery { anyConstructed<SimklRepository>().updateMediaStatus(any(), any()) } returns Unit
        coEvery { anyConstructed<SimklRepository>().searchAndDownloadEpisode(any()) } returns Result.success("task1")
        coEvery { anyConstructed<SimklRepository>().searchAndDownloadWantedItems() } returns Unit

        context = mockk(relaxed = true)
        appDatabase = mockk(relaxed = true)
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

        every { appDatabase.userTokenDao() } returns tokenDao
        every { appDatabase.calendarItemDao() } returns calendarDao
        every { appDatabase.notificationSettingDao() } returns settingDao
        every { appDatabase.watchlistDao() } returns watchlistDao
        every { appDatabase.watchedEpisodeDao() } returns watchedDao
        every { appDatabase.itemDownloadSettingsDao() } returns itemDownloadSettingsDao

        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, appDatabase)
    }

    @After
    fun tearDown() {
        unmockkObject(TorrentServiceHelper.Companion)
        unmockkConstructor(SimklRepository::class)
        unmockkObject(NotificationManager)
        unmockkStatic(Toast::class)
        unmockkStatic(Log::class)
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
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

        receiver.onReceive(null, null)
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
        coVerify(timeout = 3000) { anyConstructed<SimklRepository>().markEpisodeWatched(100, 1, 1, MediaType.TV) }
        coVerify(timeout = 3000) { NotificationManager.dismissNotification(item, context) }
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
        coEvery { anyConstructed<SimklRepository>().markMovieWatched(200) } returns Result.success(Unit)

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify(timeout = 3000) { anyConstructed<SimklRepository>().markMovieWatched(200) }
        coVerify(timeout = 3000) { NotificationManager.dismissNotification(item, context) }
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
        coVerify(timeout = 3000) { anyConstructed<SimklRepository>().markSeasonWatched(100, 1, MediaType.TV) }
        coVerify(timeout = 3000) { NotificationManager.dismissNotification(item, context) }
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
        coVerify(timeout = 3000) { anyConstructed<SimklRepository>().updateMediaStatus("v2_100_1_1", MediaStatus.WANTED) }
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
        coVerify(timeout = 3000) { NotificationManager.removeActiveNotification(context, "v2_100_1_1") }
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
        coVerify(timeout = 3000) { anyConstructed<SimklRepository>().updateMediaStatus("v2_100_1_1", MediaStatus.WANTED) }
        verify(timeout = 3000) { torrentServiceHelper.unbind() }
    }
}
