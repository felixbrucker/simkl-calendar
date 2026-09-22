package com.felixbrucker.simklcalendar.extensions

import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.model.MediaType

fun MediaType.subdirectoryName(): String {
    return when(this) {
        MediaType.MOVIE -> "movies"
        MediaType.TV -> "series"
        MediaType.ANIME -> "anime"
    }
}

fun CalendarItemWithWatchlist.destinationSubdirectory(): String {
    downloadSettings?.downloadSubdirectoryOverride?.let { return it }
    return defaultDestinationSubdirectory()
}

fun CalendarItemWithWatchlist.defaultDestinationSubdirectory(): String {
    return watchlistItem?.defaultDestinationSubdirectory() ?: throw IllegalArgumentException("No watchlist item found for simklId $simklId")
}

fun TrackedWatchlistItem.defaultDestinationSubdirectory(): String {
    val baseSubdirectory = type.subdirectoryName()
    if (type == MediaType.MOVIE) {
        return baseSubdirectory
    }
    val titlePath = (titleRomaji ?: title).take(127).cleanedForUseAsPath()

    return "$baseSubdirectory/$titlePath"
}
