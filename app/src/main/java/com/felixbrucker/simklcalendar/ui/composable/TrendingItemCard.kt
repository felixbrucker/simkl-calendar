package com.felixbrucker.simklcalendar.ui.composable

import android.R.drawable.ic_menu_gallery
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.felixbrucker.simklcalendar.data.database.TrendingMediaItem
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.util.MediaFormatter
import com.felixbrucker.simklcalendar.data.util.displayTitle
import com.felixbrucker.simklcalendar.data.util.PosterSize
import com.felixbrucker.simklcalendar.extensions.toPosterUrl
import com.felixbrucker.simklcalendar.ui.theme.*
import java.util.Locale

@Composable
fun TrendingItemCard(
    trendingItem: TrendingMediaItem,
    onItemClick: (Int, MediaType) -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val mediaItem = trendingItem.mediaItem
    val simklId = trendingItem.trendingEntry.simklId
    val mediaType = trendingItem.trendingEntry.mediaType
    val rank = trendingItem.trendingEntry.rank

    val categoryColor = mediaType.categoryColor
    val density = LocalDensity.current
    val isTablet = with(density) { LocalWindowInfo.current.containerSize.width.toDp() >= 600.dp }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant),
        modifier = modifier
            .sharedBoundsItem(
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                key = "container_$simklId"
            )
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onItemClick(simklId, mediaType) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Big centered ranking number in front of the poster
            if (isTablet) {
                Box(
                    modifier = Modifier.size(width = 40.dp, height = 130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$rank",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PurpleLight
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))
            }

            // Unified Poster Column: Image Top + Bottom Media Type Banner extending poster
            Column(
                modifier = Modifier
                    .sharedBoundsItem(
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        key = "poster_$simklId"
                    )
                    .width(75.dp)
                    .clip(RoundedCornerShape(8.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = mediaItem?.poster?.toPosterUrl(PosterSize.COMPACT),
                    contentDescription = "${mediaItem?.title} Poster",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = painterResource(id = ic_menu_gallery)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(categoryColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mediaType.displayName.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = mediaType.badgeTextColor,
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title
                Text(
                    text = mediaItem?.displayTitle ?: mediaItem?.title ?: "Loading...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.sharedElementItem(
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        key = "title_$simklId"
                    )
                )

                if (!mediaItem?.titleRomaji.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = mediaItem.titleRomaji,
                        fontSize = 13.sp,
                        color = TextSubtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Subtitle string
                if (mediaItem != null) {
                    val subtitle = MediaFormatter.formatMediaSubtitle(mediaItem)
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            color = TextSubtle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Genres
                if (!mediaItem?.genres.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = mediaItem.genres.joinToString(" • "),
                        fontSize = 10.sp,
                        color = PurpleLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Summary / Description (ellipsed)
                if (!mediaItem?.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = mediaItem.description,
                        fontSize = 12.sp,
                        color = TextGrey,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Footer with Rating
                if (mediaItem?.rating != null && mediaItem.rating > 0f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = YellowAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f", mediaItem.rating),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
