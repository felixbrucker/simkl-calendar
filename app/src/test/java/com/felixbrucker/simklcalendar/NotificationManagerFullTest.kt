package com.felixbrucker.simklcalendar

import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
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
import io.mockk.mockkObject
import io.mockk.mockkStatic
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

        context = mockk(relaxed = true)
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
        assertEquals("New Episode Released", title)
        assertNotNull(msg)

        assertNotNull(item.makeOpenReleaseDetailViewIntent(context))
        assertNotNull(item.makeMarkWatchedIntent(context))
        assertNotNull(item.makeMarkSeasonWatchedIntent(context))
        assertNotNull(item.makeDownloadItemIntent(context))
        assertNotNull(item.makeDownloadSeasonMissingEpisodesIntent(context))
    }

    @Test
    fun testCreateNotificationChannel() {
        NotificationManager.createNotificationChannel(context)
        verify { androidNotificationManager.createNotificationChannel(any()) }
    }
}
