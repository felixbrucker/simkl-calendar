package com.felixbrucker.simklcalendar.ui.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.felixbrucker.simklcalendar.data.database.CachedMediaItem
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.WatchlistStatus
import com.felixbrucker.simklcalendar.data.util.PosterSize
import com.felixbrucker.simklcalendar.extensions.toPosterUrl
import com.felixbrucker.simklcalendar.ui.theme.ContainerBlue
import com.felixbrucker.simklcalendar.ui.theme.ErrorPink
import com.felixbrucker.simklcalendar.ui.theme.GreenSuccess
import com.felixbrucker.simklcalendar.ui.theme.OutlineVariant
import com.felixbrucker.simklcalendar.ui.theme.PurpleLight
import com.felixbrucker.simklcalendar.ui.theme.PurplePrimaryDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceContainer
import com.felixbrucker.simklcalendar.ui.theme.SurfaceDark
import com.felixbrucker.simklcalendar.ui.theme.TextGrey
import com.felixbrucker.simklcalendar.ui.theme.TextPrimary
import com.felixbrucker.simklcalendar.ui.theme.TextSubtle
import com.felixbrucker.simklcalendar.ui.theme.YellowAccent

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaItemCard(
    item: CachedMediaItem,
    listStatus: WatchlistStatus?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Poster left
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.poster.toPosterUrl(PosterSize.COMPACT))
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(80.dp)
                    .height(115.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
            )

            // Details section right
            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Title
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Meta row: Release date (movie) / Start-End year (TV/anime) + Total episodes
                val releaseInfo = buildReleaseInfo(item)
                if (releaseInfo.isNotBlank()) {
                    Text(
                        text = releaseInfo,
                        color = TextSubtle,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Financials (movie budget & box office)
                if (item.mediaType == MediaType.MOVIE) {
                    val budget = item.movieMetadata?.budget
                    val boxOffice = item.movieMetadata?.boxOffice
                    if (budget != null || boxOffice != null) {
                        val financeParts = mutableListOf<String>()
                        if (budget != null && budget > 0) financeParts.add("Budget: $${formatCurrency(budget)}")
                        if (boxOffice != null && boxOffice > 0) financeParts.add("Box Office: $${formatCurrency(boxOffice)}")
                        if (financeParts.isNotEmpty()) {
                            Text(
                                text = financeParts.joinToString(" • "),
                                color = TextGrey,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Ellipsed description / summary
                if (!item.description.isNullOrBlank()) {
                    Text(
                        text = item.description,
                        color = TextSubtle,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                }

                // Genres list
                if (item.genres.isNotEmpty()) {
                    Text(
                        text = item.genres.take(3).joinToString(", "),
                        color = PurpleLight,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Bottom row: DVD badge + Rating + List Status Badge
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (item.mediaType == MediaType.MOVIE && item.hasDvd) {
                        StatusChip(
                            text = "DVD / Digital",
                            color = ContainerBlue,
                            textColor = Color.White
                        )
                    }

                    if (item.rating != null && item.rating > 0f) {
                        RatingChip(rating = item.rating)
                    }

                    if (listStatus != null) {
                        WatchlistStatusChip(status = listStatus)
                    }
                }
            }
        }
    }
}

@Composable
fun RatingChip(
    rating: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, OutlineVariant),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = YellowAccent,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = String.format("%.1f", rating),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun WatchlistStatusChip(
    status: WatchlistStatus,
    modifier: Modifier = Modifier
) {
    val (label, bg, fg) = when (status) {
        WatchlistStatus.WATCHING -> Triple("Watching", PurplePrimaryDark, Color.White)
        WatchlistStatus.PLAN_TO_WATCH -> Triple("Plan to Watch", ContainerBlue, Color.White)
        WatchlistStatus.COMPLETED -> Triple("Completed", GreenSuccess, Color.Black)
        WatchlistStatus.HOLD -> Triple("On Hold", YellowAccent, Color.Black)
        WatchlistStatus.DROPPED -> Triple("Dropped", ErrorPink, Color.Black)
    }

    StatusChip(text = label, color = bg, textColor = fg, modifier = modifier)
}

@Composable
fun StatusChip(
    text: String,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color,
        modifier = modifier
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun buildReleaseInfo(item: CachedMediaItem): String {
    return when (item.mediaType) {
        MediaType.MOVIE -> {
            val date = item.movieMetadata?.releaseDate?.toString() ?: item.year?.toString()
            date?.let { "Released: $it" } ?: ""
        }
        MediaType.TV -> {
            val startYr = item.year?.toString() ?: ""
            val endYr = item.seriesMetadata?.endYear?.toString() ?: ""
            val yearSpan = when {
                startYr.isNotEmpty() && endYr.isNotEmpty() && startYr != endYr -> "$startYr - $endYr"
                startYr.isNotEmpty() -> startYr
                else -> ""
            }
            val epCount = item.seriesMetadata?.totalEpisodes?.let { "$it eps" }
            listOfNotNull(yearSpan.takeIf { it.isNotEmpty() }, epCount).joinToString(" • ")
        }
        MediaType.ANIME -> {
            val startYr = item.year?.toString() ?: ""
            val endYr = item.animeMetadata?.endYear?.toString() ?: ""
            val yearSpan = when {
                startYr.isNotEmpty() && endYr.isNotEmpty() && startYr != endYr -> "$startYr - $endYr"
                startYr.isNotEmpty() -> startYr
                else -> ""
            }
            val epCount = item.animeMetadata?.totalEpisodes?.let { "$it eps" }
            listOfNotNull(yearSpan.takeIf { it.isNotEmpty() }, epCount).joinToString(" • ")
        }
    }
}

private fun formatCurrency(amount: Long): String {
    return when {
        amount >= 1_000_000_000 -> String.format("%.1fB", amount / 1_000_000_000.0)
        amount >= 1_000_000 -> String.format("%.1fM", amount / 1_000_000.0)
        amount >= 1_000 -> String.format("%.1fK", amount / 1_000.0)
        else -> amount.toString()
    }
}
