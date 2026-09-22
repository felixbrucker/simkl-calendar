package com.felixbrucker.simklcalendar.receiver.alarm

import android.content.Context
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.NotificationSetting
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.extensions.globalNotificationSettings

data class DefaultNotificationSettings(
    val itemAired: Boolean,
    val seasonFinished: Boolean,
    val movieIsInTheaters: Boolean,
    val movieIsReleasedOnDigital: Boolean,
) {
    companion object {
        fun fromContext(context: Context): DefaultNotificationSettings {
            val prefs = context.globalNotificationSettings

            return DefaultNotificationSettings(
                itemAired = prefs.getBoolean("default_notify_airing", false),
                seasonFinished = prefs.getBoolean("default_notify_season_finished", true),
                movieIsInTheaters = prefs.getBoolean("default_notify_movie_theater", false),
                movieIsReleasedOnDigital = prefs.getBoolean("default_notify_movie_digital", true)
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
