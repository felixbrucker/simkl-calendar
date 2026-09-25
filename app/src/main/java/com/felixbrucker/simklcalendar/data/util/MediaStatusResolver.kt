package com.felixbrucker.simklcalendar.data.util

import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
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
        airDate: Instant,
        settings: ItemDownloadSettings?,
        mediaType: MediaType,
        isTheaterRelease: Boolean,
        isWatched: Boolean,
    ): MediaStatus {
        if (airDate.isAfter(Instant.now())) return MediaStatus.NOT_AIRED_YET
        if (isTheaterRelease || isWatched) return MediaStatus.IGNORED

        val autoDownloadSettings = autoDownloadRepo.preferencesFlow.first()
        val globalIsAutoDownloadUnwatched = when (mediaType) {
            MediaType.TV -> autoDownloadSettings.autoDownloadUnwatchedTv
            MediaType.ANIME -> autoDownloadSettings.autoDownloadUnwatchedAnime
            MediaType.MOVIE -> autoDownloadSettings.autoDownloadUnwatchedMovie
        }

        val isAutoDownloadUnwatched = settings?.downloadUnwatched ?: globalIsAutoDownloadUnwatched
        return if (isAutoDownloadUnwatched) MediaStatus.WANTED else MediaStatus.IGNORED
    }
}
