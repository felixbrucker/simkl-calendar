package com.felixbrucker.simklcalendar.data.database

import androidx.room.Embedded
import androidx.room.Relation
import androidx.compose.runtime.Immutable
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.extensions.toNormalizedAnimeTitle
import java.time.Instant

@Immutable
data class MediaItemWithDetails(
    @Embedded val mediaItem: MediaItem,
    @Relation(
        parentColumn = "simklId",
        entityColumn = "simklId"
    )
    val episodes: List<MediaEpisode> = emptyList(),
    @Relation(
        parentColumn = "simklId",
        entityColumn = "simklId"
    )
    val relations: List<RelatedMediaItem> = emptyList(),
    @Relation(
        parentColumn = "simklId",
        entityColumn = "simklId"
    )
    val similarItems: List<SimilarMediaItem> = emptyList(),
    @Relation(
        parentColumn = "simklId",
        entityColumn = "simklId"
    )
    val recommendedItems: List<RecommendedMediaItem> = emptyList()
) {
    val simklId: Int get() = mediaItem.simklId
    val mediaType: MediaType get() = mediaItem.mediaType
    val title: String get() = mediaItem.title
    val titleRomaji: String? get() = mediaItem.titleRomaji
    val year: Int? get() = mediaItem.year
    val description: String? get() = mediaItem.description
    val poster: String? get() = mediaItem.poster
    val genres: List<String> get() = mediaItem.genres
    val rating: Float? get() = mediaItem.rating
    val lastUpdatedAt: Instant get() = mediaItem.lastUpdatedAt
    val movieMetadata get() = mediaItem.movieMetadata
    val seriesMetadata get() = mediaItem.seriesMetadata
    val animeMetadata get() = mediaItem.animeMetadata

    fun getSearchTitle(settings: ItemDownloadSettings?): String {
        val titleOverride = settings?.titleOverride?.takeIf { it.isNotBlank() }

        return if (titleOverride != null) {
            titleOverride
        } else {
            val baseTitle = titleRomaji ?: title
            if (mediaItem.mediaType == MediaType.ANIME) {
                baseTitle.toNormalizedAnimeTitle()
            } else {
                baseTitle
            }
        }
    }
}
