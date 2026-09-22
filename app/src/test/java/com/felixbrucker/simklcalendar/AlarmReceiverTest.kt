package com.felixbrucker.simklcalendar

import android.app.NotificationManager as AndroidNotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.felixbrucker.simklcalendar.data.database.*
import com.felixbrucker.simklcalendar.data.model.*
import com.felixbrucker.simklcalendar.data.preferences.*
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import com.felixbrucker.simklcalendar.receiver.alarm.AlarmReceiver
import com.felixbrucker.simklcalendar.receiver.notification.NotificationManager as AppNotificationManager
import com.felixbrucker.torrent_search_api.PaginatedSearchResult
import com.felixbrucker.torrent_search_api.TpbProvider
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

class AlarmReceiverTest {

    private lateinit var context: Context
    private lateinit var appDatabase: AppDatabase
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var settingDao: NotificationSettingDao
    private lateinit var itemDownloadSettingsDao: ItemDownloadSettingsDao
    private lateinit var androidNotificationManager: AndroidNotificationManager
    private lateinit var torrentServiceHelper: TorrentServiceHelper
    private lateinit var notificationManager: AppNotificationManager
    private lateinit var repositoryMock: SimklRepository

    private lateinit var autoDownloadRepo: AutoDownloadRepository
    private lateinit var notificationRepo: NotificationRepository

    @Before
    fun setUp() {
        torrentServiceHelper = mockk(relaxed = true)
        mockkConstructor(TpbProvider::class)
        coEvery { anyConstructed<TpbProvider>().search(any(), any(), any()) } returns Result.success(PaginatedSearchResult(results = emptyList(), page = 1, hasNextPage = false))


        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        notificationManager = mockk(relaxed = true)
        coEvery { notificationManager.showNotification(any()) } returns Unit
        coEvery { notificationManager.updateNotification(any()) } returns Unit

        val mockInjector = mockk<com.felixbrucker.simklcalendar.receiver.alarm.AlarmReceiver_GeneratedInjector>(relaxed = true)
        every { mockInjector.injectAlarmReceiver(any()) } answers {
            val rec = firstArg<AlarmReceiver>()
            rec.repo = repositoryMock
            rec.calendarItemDao = calendarDao
            rec.notificationSettingDao = settingDao
            rec.itemDownloadSettingsDao = itemDownloadSettingsDao
            rec.autoDownloadRepo = autoDownloadRepo
            rec.notificationRepo = notificationRepo
            rec.torrentServiceHelper = torrentServiceHelper
            rec.notificationManager = notificationManager
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
        every { context.filesDir } returns File("/tmp")
        appDatabase = mockk(relaxed = true)
        calendarDao = mockk(relaxed = true)
        settingDao = mockk(relaxed = true)
        itemDownloadSettingsDao = mockk(relaxed = true)
        androidNotificationManager = mockk(relaxed = true)

        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns androidNotificationManager

        every { appDatabase.calendarItemDao() } returns calendarDao
        every { appDatabase.notificationSettingDao() } returns settingDao
        every { appDatabase.itemDownloadSettingsDao() } returns itemDownloadSettingsDao

        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, appDatabase)

        repositoryMock = mockk(relaxed = true)
        mockkConstructor(SimklRepository::class)

        autoDownloadRepo = mockk(relaxed = true)
        notificationRepo = mockk(relaxed = true)

        coEvery { anyConstructed<SimklRepository>().updateItemAiredStatus(any()) } returns Unit
        coEvery { anyConstructed<SimklRepository>().searchAndDownloadEpisode(any()) } returns Result.success("taskId")
        coEvery { anyConstructed<SimklRepository>().searchAndDownloadSeason(any(), any()) } returns Unit

        every { autoDownloadRepo.preferencesFlow } returns flowOf(AutoDownloadPreferences())
        every { notificationRepo.preferencesFlow } returns flowOf(NotificationPreferences())
    }

    @After
    fun tearDown() {
        unmockkConstructor(TpbProvider::class)
        unmockkStatic(Log::class)
        unmockkConstructor(SimklRepository::class)
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    @Test
    fun testConstants() {
        val extraKey = AlarmReceiver.EXTRA_ITEM_PRIMARY_KEY
        val action = AlarmReceiver.ACTION_ITEM_AIRED_ALARM

        assertEquals("extra_item_primary_key", extraKey)
        assertEquals("com.felixbrucker.simklcalendar.ACTION_ITEM_AIRED_ALARM", action)
    }

    @Test
    fun testOnReceiveNullContextOrIntent() {
        val receiver = AlarmReceiver()

        try {
            receiver.onReceive(null, null)
        } catch (_: Exception) {}
        receiver.onReceive(context, null)

        coVerify(exactly = 0) { calendarDao.findItem(any()) }
    }

    @Test
    fun testOnReceiveInvalidAction() {
        val receiver = AlarmReceiver()
        val invalidIntent = mockk<Intent>()
        every { invalidIntent.action } returns "INVALID_ACTION"

        receiver.onReceive(context, invalidIntent)

        coVerify(exactly = 0) { calendarDao.findItem(any()) }
    }

    @Test
    fun testOnReceiveMissingPrimaryKey() {
        val receiver = AlarmReceiver()
        val missingKeyIntent = mockk<Intent>()
        every { missingKeyIntent.action } returns AlarmReceiver.ACTION_ITEM_AIRED_ALARM
        every { missingKeyIntent.getStringExtra(AlarmReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns null

        receiver.onReceive(context, missingKeyIntent)

        coVerify(exactly = 0) { calendarDao.findItem(any()) }
    }

    @Test
    fun testOnReceiveItemNotFoundInDatabase() {
        val receiver = spyk(AlarmReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns AlarmReceiver.ACTION_ITEM_AIRED_ALARM
        every { intent.getStringExtra(AlarmReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_999_1_1"
        coEvery { calendarDao.findItem("v2_999_1_1") } returns null

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify(exactly = 0) { calendarDao.markItemAsNotified(any()) }
    }

    @Test
    fun testOnReceiveItemAlreadyNotified() {
        val receiver = spyk(AlarmReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns AlarmReceiver.ACTION_ITEM_AIRED_ALARM
        every { intent.getStringExtra(AlarmReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_100_1_1"
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, false, false, true, null)
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.DOWNLOADED))
        coEvery { calendarDao.findItem("v2_100_1_1") } returns item

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify(exactly = 0) { calendarDao.markItemAsNotified(any()) }
    }

    @Test
    fun testOnReceiveMovieTheaterNotificationPostedWhenEnabled() {
        val receiver = spyk(AlarmReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns AlarmReceiver.ACTION_ITEM_AIRED_ALARM
        every { intent.getStringExtra(AlarmReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_200_theater"
        val calItem = CalendarItem("v2_200_theater", 200, null, null, null, Instant.now(), MovieReleaseType.THEATER, false, false, false, null)
        val watchItem = TrackedWatchlistItem(200, MediaType.MOVIE, "Movie", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_200_theater", MediaStatus.DOWNLOADED))
        coEvery { calendarDao.findItem("v2_200_theater") } returns item
        coEvery { settingDao.getSettingForShow(200) } returns NotificationSetting(200, notifyEveryEpisode = true, notifyAiredLastEpisode = false)

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify(timeout = 3000) { notificationManager.showNotification(item) }
        coVerify(timeout = 3000) { calendarDao.markItemAsNotified("v2_200_theater") }
    }

    @Test
    fun testOnReceiveMovieTheaterNotificationNotPostedWhenDisabled() {
        val receiver = spyk(AlarmReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns AlarmReceiver.ACTION_ITEM_AIRED_ALARM
        every { intent.getStringExtra(AlarmReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_200_theater"
        val calItem = CalendarItem("v2_200_theater", 200, null, null, null, Instant.now(), MovieReleaseType.THEATER, false, false, false, null)
        val watchItem = TrackedWatchlistItem(200, MediaType.MOVIE, "Movie", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_200_theater", MediaStatus.DOWNLOADED))
        coEvery { calendarDao.findItem("v2_200_theater") } returns item
        coEvery { settingDao.getSettingForShow(200) } returns NotificationSetting(200, notifyEveryEpisode = false, notifyAiredLastEpisode = true)

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify(exactly = 0) { calendarDao.markItemAsNotified(any()) }
    }

    @Test
    fun testOnReceiveMovieDigitalNotificationPostedWhenEnabled() {
        val receiver = spyk(AlarmReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns AlarmReceiver.ACTION_ITEM_AIRED_ALARM
        every { intent.getStringExtra(AlarmReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_200_digital"
        val calItem = CalendarItem("v2_200_digital", 200, null, null, null, Instant.now(), MovieReleaseType.DIGITAL, false, false, false, null)
        val watchItem = TrackedWatchlistItem(200, MediaType.MOVIE, "Movie", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_200_digital", MediaStatus.DOWNLOADED))
        coEvery { calendarDao.findItem("v2_200_digital") } returns item
        coEvery { settingDao.getSettingForShow(200) } returns NotificationSetting(200, notifyEveryEpisode = false, notifyAiredLastEpisode = true)

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify(timeout = 3000) { notificationManager.showNotification(item) }
        coVerify(timeout = 3000) { calendarDao.markItemAsNotified("v2_200_digital") }
    }

    @Test
    fun testOnReceiveSeasonFinaleNotificationPostedWhenEnabled() {
        val receiver = spyk(AlarmReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns AlarmReceiver.ACTION_ITEM_AIRED_ALARM
        every { intent.getStringExtra(AlarmReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_100_1_10"
        val calItem = CalendarItem("v2_100_1_10", 100, "Finale", 1, 10, Instant.now(), null, false, true, false, null)
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_10", MediaStatus.DOWNLOADED))
        coEvery { calendarDao.findItem("v2_100_1_10") } returns item
        coEvery { settingDao.getSettingForShow(100) } returns NotificationSetting(100, notifyEveryEpisode = false, notifyAiredLastEpisode = true)

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify(timeout = 3000) { notificationManager.showNotification(item) }
        coVerify(timeout = 3000) { calendarDao.markItemAsNotified("v2_100_1_10") }
    }

    @Test
    fun testOnReceiveTvEpisodeNotificationDisabled() {
        val receiver = spyk(AlarmReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns AlarmReceiver.ACTION_ITEM_AIRED_ALARM
        every { intent.getStringExtra(AlarmReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_100_1_2"
        val calItem = CalendarItem("v2_100_1_2", 100, "Episode 2", 1, 2, Instant.now(), null, false, false, false, null)
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_2", MediaStatus.DOWNLOADED))
        coEvery { calendarDao.findItem("v2_100_1_2") } returns item
        coEvery { settingDao.getSettingForShow(100) } returns NotificationSetting(100, notifyEveryEpisode = false, notifyAiredLastEpisode = true)

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify(exactly = 0) { calendarDao.markItemAsNotified(any()) }
    }

    @Test
    fun testOnReceiveUnbindsTorrentService() {
        val receiver = spyk(AlarmReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns AlarmReceiver.ACTION_ITEM_AIRED_ALARM
        every { intent.getStringExtra(AlarmReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_100_1_1"
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, false, false, false, null)
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.NOT_AIRED_YET))
        coEvery { calendarDao.findItem("v2_100_1_1") } returns item
        coEvery { settingDao.getSettingForShow(100) } returns NotificationSetting(100, notifyEveryEpisode = true, notifyAiredLastEpisode = false)

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { torrentServiceHelper.unbind() }
    }

    @Test
    fun testOnReceiveSearchesTorrentsWhenStatusIsWanted() {
        val receiver = spyk(AlarmReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns AlarmReceiver.ACTION_ITEM_AIRED_ALARM
        every { intent.getStringExtra(AlarmReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_100_1_1"
        val pastDate = Instant.now().minusSeconds(7200)
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, pastDate, null, false, false, false, null)
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val itemNotAired = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.NOT_AIRED_YET))
        val itemWanted = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.WANTED))
        val settings = ItemDownloadSettings(simklId = 100, downloadUnwatched = true)
        coEvery { itemDownloadSettingsDao.getSettings(100) } returns settings
        coEvery { calendarDao.findItem("v2_100_1_1") } returns itemNotAired andThen itemWanted
        coEvery { settingDao.getSettingForShow(100) } returns NotificationSetting(100, notifyEveryEpisode = true, notifyAiredLastEpisode = false)

        receiver.onReceive(context, intent)

        coVerify(timeout = 3000) { repositoryMock.updateItemAiredStatus(any()) }
        coVerify(timeout = 3000) { notificationManager.showNotification(any()) }
    }

    @Test
    fun testOnReceiveSeasonFinaleTriggersSeasonUnwatchedDownloads() {
        val receiver = spyk(AlarmReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns AlarmReceiver.ACTION_ITEM_AIRED_ALARM
        every { intent.getStringExtra(AlarmReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_100_1_12"
        val pastDate = Instant.now().minusSeconds(7200)
        val calItemFinale = CalendarItem("v2_100_1_12", 100, "Finale", 1, 12, pastDate, null, false, true, false, null)
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val itemFinale = CalendarItemWithWatchlist(calItemFinale, watchItem, LocalItemState("v2_100_1_12", MediaStatus.NOT_AIRED_YET))
        val settings = ItemDownloadSettings(simklId = 100, downloadSeasonUnwatched = true)
        coEvery { itemDownloadSettingsDao.getSettings(100) } returns settings
        coEvery { calendarDao.findItem("v2_100_1_12") } returns itemFinale
        coEvery { calendarDao.getUnwatchedDownloadableSeasonItems(100, 1) } returns emptyList()

        receiver.onReceive(context, intent)

        coVerify(timeout = 3000) { repositoryMock.searchAndDownloadSeason(100, 1) }
    }

    @Test
    fun testOnReceiveUpdatesNotificationAfterSearchAndDownload() {
        val receiver = spyk(AlarmReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns AlarmReceiver.ACTION_ITEM_AIRED_ALARM
        every { intent.getStringExtra(AlarmReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_100_1_1"
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, false, false, false, null)
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val itemInitial = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.WANTED))
        val itemFinal = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.DOWNLOADING))
        coEvery { calendarDao.findItem("v2_100_1_1") } returns itemInitial andThen itemInitial andThen itemFinal
        coEvery { settingDao.getSettingForShow(100) } returns NotificationSetting(100, notifyEveryEpisode = true, notifyAiredLastEpisode = false)

        receiver.onReceive(context, intent)

        verify(timeout = 3000) { pendingResult.finish() }
        coVerify { notificationManager.updateNotification(itemFinal) }
    }

    @Test
    fun testOnReceiveDoesNotUpdateNotificationWhenNoSearchOrDownloadOccurred() {
        val receiver = spyk(AlarmReceiver())
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        every { receiver.goAsync() } returns pendingResult
        val intent = mockk<Intent>()
        every { intent.action } returns AlarmReceiver.ACTION_ITEM_AIRED_ALARM
        every { intent.getStringExtra(AlarmReceiver.EXTRA_ITEM_PRIMARY_KEY) } returns "v2_100_1_1"
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, false, false, false, null)
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val itemIgnored = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.IGNORED))
        coEvery { calendarDao.findItem("v2_100_1_1") } returns itemIgnored
        coEvery { settingDao.getSettingForShow(100) } returns NotificationSetting(100, notifyEveryEpisode = true, notifyAiredLastEpisode = false)

        receiver.onReceive(context, intent)

        coVerify(exactly = 0) { notificationManager.updateNotification(any()) }
    }
}
