package com.felixbrucker.simklcalendar.data.util

import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadPreferences
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStatusResolver @Inject constructor(
    private val autoDownloadRepo: AutoDownloadRepository
) {
    suspend fun resolve(
        item: CalendarItemWithWatchlist,
        autoDownloadSettings: AutoDownloadPreferences? = null
    ): MediaStatus {
        return resolve(
            airDate = item.date,
            settings = item.downloadSettings,
            mediaType = item.type,
            isTheaterRelease = item.movieReleaseType == MovieReleaseType.THEATER,
            isWatched = item.isWatched,
            autoDownloadSettings = autoDownloadSettings,
        )
    }

    suspend fun resolve(
        airDate: Instant,
        settings: ItemDownloadSettings?,
        mediaType: MediaType,
        isTheaterRelease: Boolean,
        isWatched: Boolean,
        autoDownloadSettings: AutoDownloadPreferences? = null,
    ): MediaStatus {
        if (airDate.isAfter(Instant.now())) return MediaStatus.NOT_AIRED_YET
        if (isTheaterRelease || isWatched) return MediaStatus.IGNORED

        val autoPrefs = autoDownloadSettings ?: autoDownloadRepo.preferencesFlow.first()
        val globalIsAutoDownloadUnwatched = when (mediaType) {
            MediaType.TV -> autoPrefs.autoDownloadUnwatchedTv
            MediaType.ANIME -> autoPrefs.autoDownloadUnwatchedAnime
            MediaType.MOVIE -> autoPrefs.autoDownloadUnwatchedMovie
        }

        val isAutoDownloadUnwatched = settings?.downloadUnwatched ?: globalIsAutoDownloadUnwatched
        return if (isAutoDownloadUnwatched) MediaStatus.WANTED else MediaStatus.IGNORED
    }
}
