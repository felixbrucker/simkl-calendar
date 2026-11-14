package com.felixbrucker.simklcalendar.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.felixbrucker.simklcalendar.data.model.MediaType
import java.time.Instant
import java.time.LocalDate

enum class AnimeType(val key: String) {
    TV("tv"),
    MOVIE("movie"),
    OVA("ova"),
    ONA("ona"),
    SPECIAL("special"),
    MUSIC("music"),
    UNKNOWN("unknown");

    companion object {
        fun fromKey(key: String?): AnimeType {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

enum class SeriesStatus(val key: String) {
    TBA("tba"),
    ENDED("ended"),
    AIRING("airing"),
    UNKNOWN("unknown");

    companion object {
        fun fromKey(key: String?): SeriesStatus {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

data class MovieMetadata(
    val releaseDate: LocalDate? = null,
    val dvdDate: LocalDate? = null,
    val budget: Long? = null,
    val boxOffice: Long? = null
)

data class SeriesMetadata(
    val endYear: Int? = null,
    val seasonsCount: Int? = null,
    val totalEpisodes: Int? = null,
    val status: SeriesStatus? = null
)

data class AnimeMetadata(
    val animeType: AnimeType? = null,
    val endYear: Int? = null,
    val totalEpisodes: Int? = null,
    val seasonYear: Int? = null,
    val seasonQuarter: String? = null,
    val status: SeriesStatus? = null
)

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["simklId"], unique = true),
        Index(value = ["lastUpdatedAt"])
    ]
)
data class MediaItem(
    @PrimaryKey val simklId: Int,
    val mediaType: MediaType,
    val title: String,
    val titleRomaji: String? = null,
    val year: Int,
    val description: String? = null,
    val poster: String? = null,
    val genres: List<String> = emptyList(),
    val rating: Float? = null,
    val lastUpdatedAt: Instant = Instant.now(),
    @Embedded(prefix = "movie_") val movieMetadata: MovieMetadata? = null,
    @Embedded(prefix = "series_") val seriesMetadata: SeriesMetadata? = null,
    @Embedded(prefix = "anime_") val animeMetadata: AnimeMetadata? = null
) {
    val simklUrl: String get() = "https://simkl.com/${mediaType.key}/$simklId"
    val hasDvd: Boolean get() = movieMetadata?.dvdDate != null
}
