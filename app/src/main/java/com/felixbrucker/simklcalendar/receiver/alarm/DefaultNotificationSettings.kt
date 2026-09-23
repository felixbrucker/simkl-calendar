package com.felixbrucker.simklcalendar.receiver.alarm

import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.NotificationSetting
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.preferences.NotificationPreferences

fun NotificationPreferences.toDefaultNotificationSettings(): DefaultNotificationSettings {
    return DefaultNotificationSettings(
        itemAired = defaultNotifyAiring,
        seasonFinished = defaultNotifySeasonFinished,
        movieIsInTheaters = defaultNotifyMovieTheater,
        movieIsReleasedOnDigital = defaultNotifyMovieDigital
    )
}

data class DefaultNotificationSettings(
    val itemAired: Boolean,
    val seasonFinished: Boolean,
    val movieIsInTheaters: Boolean,
    val movieIsReleasedOnDigital: Boolean,
) {
    fun makeNotificationSettings(item: CalendarItemWithWatchlist): NotificationSetting {
        val isMovie = item.type == MediaType.MOVIE

        return NotificationSetting(
            simklId = item.simklId,
            notifyEveryEpisode = if (isMovie) movieIsInTheaters else itemAired,
            notifyAiredLastEpisode = if (isMovie) movieIsReleasedOnDigital else seasonFinished,
        )
    }
}
