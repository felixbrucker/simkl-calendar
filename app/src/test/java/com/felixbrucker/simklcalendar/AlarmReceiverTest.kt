package com.felixbrucker.simklcalendar

import android.app.NotificationManager as AndroidNotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.LocalItemState
import com.felixbrucker.simklcalendar.data.database.NotificationSetting
import com.felixbrucker.simklcalendar.data.database.NotificationSettingDao
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import com.felixbrucker.simklcalendar.receiver.alarm.AlarmReceiver
import com.felixbrucker.simklcalendar.receiver.notification.NotificationManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class AlarmReceiverTest {

    private lateinit var context: Context
    private lateinit var appDatabase: AppDatabase
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var settingDao: NotificationSettingDao
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

        mockkObject(NotificationManager.Companion)
        coEvery { NotificationManager.showNotification(any(), any()) } returns Unit
        coEvery { NotificationManager.updateNotification(any(), any()) } returns Unit

        context = mockk(relaxed = true)
        appDatabase = mockk(relaxed = true)
        calendarDao = mockk(relaxed = true)
        settingDao = mockk(relaxed = true)
        itemDownloadSettingsDao = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        androidNotificationManager = mockk(relaxed = true)

        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns androidNotificationManager
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.getBoolean(any(), any()) } answers { secondArg() }

        every { appDatabase.calendarItemDao() } returns calendarDao
        every { appDatabase.notificationSettingDao() } returns settingDao
        every { appDatabase.itemDownloadSettingsDao() } returns itemDownloadSettingsDao

        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, appDatabase)
    }

    @After
    fun tearDown() {
        unmockkObject(TorrentServiceHelper.Companion)
        unmockkObject(NotificationManager.Companion)
        unmockkStatic(Log::class)
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

        receiver.onReceive(null, null)
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
        coVerify(timeout = 3000) { NotificationManager.showNotification(item, context) }
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
        coVerify(timeout = 3000) { NotificationManager.showNotification(item, context) }
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
        coVerify(timeout = 3000) { NotificationManager.showNotification(item, context) }
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

        coVerify(timeout = 3000) { NotificationManager.showNotification(itemNotAired, context) }
        coVerify(timeout = 3000) { calendarDao.updateMediaStatus("v2_100_1_1", MediaStatus.WANTED) }
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

        coVerify(timeout = 3000) { calendarDao.getUnwatchedDownloadableSeasonItems(100, 1) }
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
        coVerify { NotificationManager.updateNotification(itemFinal, context) }
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

        coVerify(exactly = 0) { NotificationManager.updateNotification(any(), any()) }
    }
}
