package com.felixbrucker.simklcalendar

import android.content.Context
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CustomSearchLinkDao
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.NotificationSettingDao
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.database.WatchedEpisodeDao
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import com.felixbrucker.simklcalendar.data.repository.CalendarRepository
import com.felixbrucker.simklcalendar.data.repository.CustomSearchLinkRepository
import com.felixbrucker.simklcalendar.data.repository.DownloadRepository
import com.felixbrucker.simklcalendar.data.repository.NotificationSettingRepository
import com.felixbrucker.simklcalendar.data.repository.SyncRepository
import com.felixbrucker.simklcalendar.data.repository.UserRepository
import com.felixbrucker.simklcalendar.data.repository.WatchlistRepository
import com.felixbrucker.simklcalendar.di.DatabaseModule
import com.felixbrucker.simklcalendar.di.NetworkModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class DiModulesTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var tokenDao: UserTokenDao
    private lateinit var calendarDao: CalendarItemDao
    private lateinit var settingDao: NotificationSettingDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var watchedDao: WatchedEpisodeDao
    private lateinit var searchLinkDao: CustomSearchLinkDao
    private lateinit var itemDownloadSettingsDao: ItemDownloadSettingsDao
    private lateinit var autoDownloadRepo: AutoDownloadRepository

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        db = mockk(relaxed = true)
        tokenDao = mockk(relaxed = true)
        calendarDao = mockk(relaxed = true)
        settingDao = mockk(relaxed = true)
        watchlistDao = mockk(relaxed = true)
        watchedDao = mockk(relaxed = true)
        searchLinkDao = mockk(relaxed = true)
        itemDownloadSettingsDao = mockk(relaxed = true)
        autoDownloadRepo = mockk(relaxed = true)
        every { db.userTokenDao() } returns tokenDao
        every { db.calendarItemDao() } returns calendarDao
        every { db.notificationSettingDao() } returns settingDao
        every { db.watchlistDao() } returns watchlistDao
        every { db.watchedEpisodeDao() } returns watchedDao
        every { db.customSearchLinkDao() } returns searchLinkDao
        every { db.itemDownloadSettingsDao() } returns itemDownloadSettingsDao
        mockkObject(AppDatabase.Companion)
        every { AppDatabase.makeDatabase(context) } returns db
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testDatabaseModuleProviders() {
        val providedDb = DatabaseModule.provideAppDatabase(context)
        val userTokenDao = DatabaseModule.provideUserTokenDao(db)
        val calendarItemDao = DatabaseModule.provideCalendarItemDao(db)
        val notificationSettingDao = DatabaseModule.provideNotificationSettingDao(db)
        val watchlistDaoRes = DatabaseModule.provideWatchlistDao(db)
        val watchedEpisodeDao = DatabaseModule.provideWatchedEpisodeDao(db)
        val customSearchLinkDao = DatabaseModule.provideCustomSearchLinkDao(db)
        val itemDownloadSettingsDaoRes = DatabaseModule.provideItemDownloadSettingsDao(db)

        assertNotNull(providedDb)
        assertNotNull(userTokenDao)
        assertNotNull(calendarItemDao)
        assertNotNull(notificationSettingDao)
        assertNotNull(watchlistDaoRes)
        assertNotNull(watchedEpisodeDao)
        assertNotNull(customSearchLinkDao)
        assertNotNull(itemDownloadSettingsDaoRes)
    }

    @Test
    fun testNetworkModuleProviders() {
        val moshi = NetworkModule.provideMoshi()
        val apiService = NetworkModule.providePublicSimklApiService(moshi)

        assertNotNull(moshi)
        assertNotNull(apiService)
    }

    @Test
    fun testRepositoryInstantiations() {
        val userRepo = UserRepository(
            tokenDao = tokenDao,
            calendarDao = calendarDao,
            watchlistDao = watchlistDao,
            watchedDao = watchedDao,
            publicSimklApiService = mockk(relaxed = true),
            authenticatedSimklApiService = mockk(relaxed = true),
            appSettingsRepo = mockk(relaxed = true),
            autoDownloadRepo = autoDownloadRepo,
            notificationRepo = mockk(relaxed = true),
            authRepo = mockk(relaxed = true),
            syncMetadataRepo = mockk(relaxed = true),
            uiRepo = mockk(relaxed = true)
        )
        val calendarRepo = CalendarRepository(
            calendarDao = calendarDao,
            watchedDao = watchedDao,
            itemDownloadSettingsDao = itemDownloadSettingsDao,
            authenticatedSimklApiService = mockk(relaxed = true),
            autoDownloadRepo = autoDownloadRepo
        )
        val watchlistRepo = WatchlistRepository(watchlistDao)
        val downloadRepo = DownloadRepository(
            context = context,
            itemDownloadSettingsDao = itemDownloadSettingsDao,
            calendarDao = calendarDao,
            calendarRepository = calendarRepo,
            torrentSearchManager = mockk(relaxed = true),
            torrentServiceHelper = mockk(relaxed = true)
        )
        val customSearchLinkRepo = CustomSearchLinkRepository(searchLinkDao)
        val notificationSettingRepo = NotificationSettingRepository(settingDao)
        val syncRepo = SyncRepository(
            tokenDao = tokenDao,
            calendarDao = calendarDao,
            settingDao = settingDao,
            watchlistDao = watchlistDao,
            watchedDao = watchedDao,
            itemDownloadSettingsDao = itemDownloadSettingsDao,
            publicSimklApiService = mockk(relaxed = true),
            authenticatedSimklApiService = mockk(relaxed = true),
            syncMetadataRepo = mockk(relaxed = true),
            notificationRepo = mockk(relaxed = true),
            calendarRepository = calendarRepo,
            downloadRepository = downloadRepo,
            alarmScheduler = mockk(relaxed = true)
        )

        assertNotNull(userRepo)
        assertNotNull(calendarRepo)
        assertNotNull(watchlistRepo)
        assertNotNull(downloadRepo)
        assertNotNull(customSearchLinkRepo)
        assertNotNull(notificationSettingRepo)
        assertNotNull(syncRepo)
    }
}
