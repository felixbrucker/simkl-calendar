package com.felixbrucker.simklcalendar.receiver.alarm

import android.content.Context
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.NotificationSetting
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.preferences.*
import kotlinx.coroutines.flow.first

data class DefaultNotificationSettings(
    val itemAired: Boolean,
    val seasonFinished: Boolean,
    val movieIsInTheaters: Boolean,
    val movieIsReleasedOnDigital: Boolean,
) {
    companion object {
        suspend fun fromContext(context: Context): DefaultNotificationSettings {
            val repo = NotificationRepository(context.notificationDataStore)
            val prefs = repo.preferencesFlow.first()

            return DefaultNotificationSettings(
                itemAired = prefs.defaultNotifyAiring,
                seasonFinished = prefs.defaultNotifySeasonFinished,
                movieIsInTheaters = prefs.defaultNotifyMovieTheater,
                movieIsReleasedOnDigital = prefs.defaultNotifyMovieDigital
            )
        }
    }

    fun makeNotificationSettings(item: CalendarItemWithWatchlist): NotificationSetting {
        val isMovie = item.type == MediaType.MOVIE

        return NotificationSetting(
            simklId = item.simklId,
            notifyEveryEpisode = if (isMovie) movieIsInTheaters else itemAired,
            notifyAiredLastEpisode = if (isMovie) movieIsReleasedOnDigital else seasonFinished,
        )
    }
}
