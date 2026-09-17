package com.felixbrucker.simklcalendar

import android.app.Notification
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.CustomSearchLinkDao
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.LocalItemState
import com.felixbrucker.simklcalendar.data.database.NotificationSettingDao
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.database.WatchedEpisodeDao
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import com.felixbrucker.simklcalendar.receiver.notification.NotificationManager
import com.felixbrucker.simklcalendar.receiver.notification.formatNotificationContent
import com.felixbrucker.simklcalendar.receiver.notification.makeDownloadItemIntent
import com.felixbrucker.simklcalendar.receiver.notification.makeDownloadSeasonMissingEpisodesIntent
import com.felixbrucker.simklcalendar.receiver.notification.makeMarkSeasonWatchedIntent
import com.felixbrucker.simklcalendar.receiver.notification.makeMarkWatchedIntent
import com.felixbrucker.simklcalendar.receiver.notification.makeOpenReleaseDetailViewIntent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationManagerFullTest {

    private lateinit var context: Context
    private lateinit var androidNotificationManager: AndroidNotificationManager
    private lateinit var packageManager: PackageManager
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var appDatabase: AppDatabase
    private lateinit var userTokenDao: UserTokenDao
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var settingDao: NotificationSettingDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var watchedDao: WatchedEpisodeDao
    private lateinit var searchLinkDao: CustomSearchLinkDao
    private lateinit var itemDownloadSettingsDao: ItemDownloadSettingsDao
    private lateinit var torrentServiceHelper: TorrentServiceHelper

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(Toast::class)
        val toastMock = mockk<Toast>(relaxed = true)
        every { Toast.makeText(any(), any<CharSequence>(), any()) } returns toastMock

        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED

        mockkStatic(Uri::class)
        val uriMock = mockk<Uri>(relaxed = true)
        every { Uri.parse(any()) } returns uriMock

        mockkStatic(PendingIntent::class)
        val pendingIntentMock = mockk<PendingIntent>(relaxed = true)
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns pendingIntentMock
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns pendingIntentMock

        mockkConstructor(NotificationCompat.Builder::class)
        every { anyConstructed<NotificationCompat.Builder>().build() } returns mockk(relaxed = true)

        context = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)
        androidNotificationManager = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        appDatabase = mockk(relaxed = true)
        userTokenDao = mockk(relaxed = true)
        calendarDao = mockk(relaxed = true)
        settingDao = mockk(relaxed = true)
        watchlistDao = mockk(relaxed = true)
        watchedDao = mockk(relaxed = true)
        searchLinkDao = mockk(relaxed = true)
        itemDownloadSettingsDao = mockk(relaxed = true)
        torrentServiceHelper = mockk(relaxed = true)

        every { context.applicationContext } returns context
        every { context.packageManager } returns packageManager
        every { context.applicationInfo } returns ApplicationInfo()
        every { context.resources } returns mockk(relaxed = true)
        every { packageManager.getPackageInfo(any<String>(), any<Int>()) } returns PackageInfo()

        every { userTokenDao.getUserToken() } returns flowOf(null)
        every { calendarDao.getAllCalendarItems() } returns flowOf(emptyList())
        every { settingDao.getAllSettings() } returns flowOf(emptyList())
        every { watchedDao.getAllWatchedEpisodesFlow() } returns flowOf(emptyList())
        every { searchLinkDao.getAllSearchLinks() } returns flowOf(emptyList())
        every { watchlistDao.getAllTrackedItemsFlow() } returns flowOf(emptyList())

        every { torrentServiceHelper.isInstalled } returns MutableStateFlow(false)
        mockkObject(TorrentServiceHelper.Companion)
        every { TorrentServiceHelper.getInstance(any()) } returns torrentServiceHelper

        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns androidNotificationManager
        every { context.packageName } returns "com.felixbrucker.simklcalendar"
        every { context.checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_GRANTED
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences

        coEvery { calendarDao.getItemsInSeasonOrRelatedItems(any(), any()) } returns emptyList()

        every { appDatabase.userTokenDao() } returns userTokenDao
        every { appDatabase.calendarItemDao() } returns calendarDao
        every { appDatabase.notificationSettingDao() } returns settingDao
        every { appDatabase.watchlistDao() } returns watchlistDao
        every { appDatabase.watchedEpisodeDao() } returns watchedDao
        every { appDatabase.customSearchLinkDao() } returns searchLinkDao
        every { appDatabase.itemDownloadSettingsDao() } returns itemDownloadSettingsDao

        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, appDatabase)
    }

    @After
    fun tearDown() {
        unmockkConstructor(NotificationCompat.Builder::class)
        unmockkStatic(PendingIntent::class)
        unmockkStatic(Uri::class)
        unmockkStatic(ContextCompat::class)
        unmockkStatic(Toast::class)
        unmockkStatic(Log::class)
        unmockkObject(TorrentServiceHelper.Companion)
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    @Test
    fun testNotificationIntentsAndFormatting() {
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, false, false, false, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.WANTED))

        val (title, msg) = item.formatNotificationContent(10)
        val openIntent = item.makeOpenReleaseDetailViewIntent(context)
        val markWatchedIntent = item.makeMarkWatchedIntent(context)
        val markSeasonWatchedIntent = item.makeMarkSeasonWatchedIntent(context)
        val downloadItemIntent = item.makeDownloadItemIntent(context)
        val downloadSeasonIntent = item.makeDownloadSeasonMissingEpisodesIntent(context)

        assertEquals("New Episode Released", title)
        assertNotNull(msg)
        assertNotNull(openIntent)
        assertNotNull(markWatchedIntent)
        assertNotNull(markSeasonWatchedIntent)
        assertNotNull(downloadItemIntent)
        assertNotNull(downloadSeasonIntent)
    }

    @Test
    fun testCreateNotificationChannel() {
        NotificationManager.createNotificationChannel(context)

        verify { androidNotificationManager.createNotificationChannel(any()) }
    }

    @Test
    fun testShowNotificationWhenPermissionGranted() = runTest {
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, false, false, false, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.WANTED))

        NotificationManager.showNotification(item, context)

        verify { androidNotificationManager.notify(item.notificationId, any()) }
    }

    @Test
    fun testUpdateNotificationWhenActive() = runTest {
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, false, false, false, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.WANTED))
        val activeNotif = mockk<StatusBarNotification>()
        every { activeNotif.id } returns item.notificationId
        every { androidNotificationManager.activeNotifications } returns arrayOf(activeNotif)

        NotificationManager.updateNotification(item, context)

        verify { androidNotificationManager.notify(item.notificationId, any()) }
    }

    @Test
    fun testUpdateNotificationWhenInactive() = runTest {
        val watchItem = TrackedWatchlistItem(100, MediaType.TV, "Show", null, null)
        val calItem = CalendarItem("v2_100_1_1", 100, "Pilot", 1, 1, Instant.now(), null, false, false, false, null)
        val item = CalendarItemWithWatchlist(calItem, watchItem, LocalItemState("v2_100_1_1", MediaStatus.WANTED))
        every { androidNotificationManager.activeNotifications } returns arrayOf()

        NotificationManager.updateNotification(item, context)

        verify(exactly = 0) { androidNotificationManager.notify(any(), any<Notification>()) }
    }

    @Test
    fun testShowNotificationMovieAndFinaleWithTorrentService() = runTest {
        every { torrentServiceHelper.isInstalled } returns MutableStateFlow(true)
        val watchMovie = TrackedWatchlistItem(200, MediaType.MOVIE, "Movie", null, null)
        val calMovie = CalendarItem("v2_200_theater", 200, "Movie", null, null, Instant.now(), MovieReleaseType.THEATER, false, false, false, null)
        val movieItem = CalendarItemWithWatchlist(calMovie, watchMovie, LocalItemState("v2_200_theater", MediaStatus.IGNORED))

        val watchFinale = TrackedWatchlistItem(300, MediaType.TV, "Finale Show", null, null)
        val calFinale = CalendarItem("v2_300_1_10", 300, "Finale Ep", 1, 10, Instant.now(), null, false, true, false, null)
        val finaleItem = CalendarItemWithWatchlist(calFinale, watchFinale, LocalItemState("v2_300_1_10", MediaStatus.IGNORED))

        coEvery { calendarDao.getItemsInSeasonOrRelatedItems(200, null) } returns listOf(movieItem)
        coEvery { calendarDao.getItemsInSeasonOrRelatedItems(300, 1) } returns listOf(finaleItem)

        NotificationManager.showNotification(movieItem, context)
        NotificationManager.showNotification(finaleItem, context)

        verify { androidNotificationManager.notify(movieItem.notificationId, any()) }
        verify { androidNotificationManager.notify(finaleItem.notificationId, any()) }
    }

    @Test
    fun testShowNotificationWantedStatusWithTorrentService() = runTest {
        every { torrentServiceHelper.isInstalled } returns MutableStateFlow(true)
        val watchMovie = TrackedWatchlistItem(201, MediaType.MOVIE, "Movie 2", null, null)
        val calMovieDigital = CalendarItem("v2_201_digital", 201, "Movie 2", null, null, Instant.now(), MovieReleaseType.DIGITAL, false, false, false, null)
        val movieDigitalItem = CalendarItemWithWatchlist(calMovieDigital, watchMovie, LocalItemState("v2_201_digital", MediaStatus.WANTED))
        val watchEpisode = TrackedWatchlistItem(301, MediaType.TV, "Regular Show", null, null)
        val calEpisode = CalendarItem("v2_301_1_2", 301, "Ep 2", 1, 2, Instant.now(), null, false, false, false, null)
        val episodeItem = CalendarItemWithWatchlist(calEpisode, watchEpisode, LocalItemState("v2_301_1_2", MediaStatus.WANTED))
        val watchFinale = TrackedWatchlistItem(401, MediaType.TV, "Finale Show 2", null, null)
        val calFinale = CalendarItem("v2_401_1_10", 401, "Finale Ep 2", 1, 10, Instant.now(), null, false, true, false, null)
        val finaleItem = CalendarItemWithWatchlist(calFinale, watchFinale, LocalItemState("v2_401_1_10", MediaStatus.WANTED))
        coEvery { calendarDao.getItemsInSeasonOrRelatedItems(201, null) } returns listOf(movieDigitalItem)
        coEvery { calendarDao.getItemsInSeasonOrRelatedItems(301, 1) } returns listOf(episodeItem)
        coEvery { calendarDao.getItemsInSeasonOrRelatedItems(401, 1) } returns listOf(finaleItem)

        NotificationManager.showNotification(movieDigitalItem, context)
        NotificationManager.showNotification(episodeItem, context)
        NotificationManager.showNotification(finaleItem, context)

        verify { androidNotificationManager.notify(movieDigitalItem.notificationId, any()) }
        verify { androidNotificationManager.notify(episodeItem.notificationId, any()) }
        verify { androidNotificationManager.notify(finaleItem.notificationId, any()) }
        verify { anyConstructed<NotificationCompat.Builder>().addAction(R.drawable.ic_download, "Download", any()) }
        verify { anyConstructed<NotificationCompat.Builder>().addAction(R.drawable.ic_download, "Download missing episodes", any()) }
    }
}
