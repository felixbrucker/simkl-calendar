package com.felixbrucker.simklcalendar.data.util

import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.model.MediaType

fun MediaType.subdirectoryName(): String {
    return when(this) {
        MediaType.MOVIE -> "movies"
        MediaType.TV -> "series"
        MediaType.ANIME -> "anime"
    }
}

fun CalendarItemWithWatchlist.destinationSubdirectory(): String {
    val baseSubdirectory = type.subdirectoryName()
    if (type == MediaType.MOVIE) {
        return baseSubdirectory
    }
    val title = titleRomaji ?: title

    return "$baseSubdirectory/$title"
}
