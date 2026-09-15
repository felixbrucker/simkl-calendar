package com.felixbrucker.simklcalendar.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.CustomSearchLinkDao
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettingsDao
import com.felixbrucker.simklcalendar.data.database.NotificationSettingDao
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.database.UserTokenDao
import com.felixbrucker.simklcalendar.data.database.WatchedEpisodeDao
import com.felixbrucker.simklcalendar.data.database.WatchlistDao
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.network.SimklIds
import com.felixbrucker.simklcalendar.data.network.SimklMedia
import com.felixbrucker.simklcalendar.data.network.SyncMovieItem
import com.felixbrucker.simklcalendar.data.network.SyncShowItem
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SimklRepositoryTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var appDatabase: AppDatabase

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        appDatabase = mockk(relaxed = true)

        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences

        val userTokenDao = mockk<UserTokenDao>(relaxed = true)
        val calendarDao = mockk<CalendarItemDao>(relaxed = true)
        val settingDao = mockk<NotificationSettingDao>(relaxed = true)
        val watchlistDao = mockk<WatchlistDao>(relaxed = true)
        val watchedDao = mockk<WatchedEpisodeDao>(relaxed = true)
        val searchLinkDao = mockk<CustomSearchLinkDao>(relaxed = true)
        val itemDownloadSettingsDao = mockk<ItemDownloadSettingsDao>(relaxed = true)

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
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    @Test
    fun testDetermineStatusFutureDate() {
        val repo = SimklRepository(context)
        val futureDate = Instant.now().plusSeconds(3600)

        val status = repo.determineStatus(
            airDate = futureDate,
            settings = null,
            mediaType = MediaType.TV,
            isTheaterRelease = false,
            isWatched = false
        )

        assertEquals(MediaStatus.NOT_AIRED_YET, status)
    }

    @Test
    fun testDetermineStatusTheaterOrWatchedIgnored() {
        val repo = SimklRepository(context)
        val pastDate = Instant.now().minusSeconds(3600)

        val theaterStatus = repo.determineStatus(
            airDate = pastDate,
            settings = null,
            mediaType = MediaType.MOVIE,
            isTheaterRelease = true,
            isWatched = false
        )
        assertEquals(MediaStatus.IGNORED, theaterStatus)

        val watchedStatus = repo.determineStatus(
            airDate = pastDate,
            settings = null,
            mediaType = MediaType.TV,
            isTheaterRelease = false,
            isWatched = true
        )
        assertEquals(MediaStatus.IGNORED, watchedStatus)
    }

    @Test
    fun testDetermineStatusAutoDownloadUnwatched() {
        val repo = SimklRepository(context)
        val pastDate = Instant.now().minusSeconds(3600)

        every { sharedPreferences.getBoolean("auto_download_unwatched_tv", false) } returns true

        val wantedStatus = repo.determineStatus(
            airDate = pastDate,
            settings = null,
            mediaType = MediaType.TV,
            isTheaterRelease = false,
            isWatched = false
        )
        assertEquals(MediaStatus.WANTED, wantedStatus)

        val itemSettings = ItemDownloadSettings(simklId = 1, downloadUnwatched = false)
        val ignoredStatus = repo.determineStatus(
            airDate = pastDate,
            settings = itemSettings,
            mediaType = MediaType.TV,
            isTheaterRelease = false,
            isWatched = false
        )
        assertEquals(MediaStatus.IGNORED, ignoredStatus)
    }

    @Test
    fun testFromShowItemAndFromMovieItem() {
        val showItem = SyncShowItem(
            status = "watching",
            show = SimklMedia(
                title = "Naruto",
                poster = "naruto.jpg",
                ids = SimklIds(simkl = 123)
            )
        )
        val trackedShow = TrackedWatchlistItem.fromShowItem(showItem, MediaType.ANIME)
        assertEquals(123, trackedShow.simklId)
        assertEquals(MediaType.ANIME, trackedShow.type)
        assertEquals("Naruto", trackedShow.title)
        assertEquals("naruto.jpg", trackedShow.poster)

        val movieItem = SyncMovieItem(
            status = "watching",
            movie = SimklMedia(
                title = "Inception",
                poster = "inception.jpg",
                ids = SimklIds(simkl = 456)
            )
        )
        val trackedMovie = TrackedWatchlistItem.fromMovieItem(movieItem)
        assertEquals(456, trackedMovie.simklId)
        assertEquals(MediaType.MOVIE, trackedMovie.type)
        assertEquals("Inception", trackedMovie.title)
    }
}
