package com.felixbrucker.simklcalendar.ui.composable

import android.R.drawable.ic_menu_gallery
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.WatchlistStatus
import com.felixbrucker.simklcalendar.data.util.PosterSize
import com.felixbrucker.simklcalendar.extensions.toPosterUrl
import com.felixbrucker.simklcalendar.ui.theme.BlueAccent
import com.felixbrucker.simklcalendar.ui.theme.ErrorPink
import com.felixbrucker.simklcalendar.ui.theme.PurpleLight
import com.felixbrucker.simklcalendar.ui.theme.SurfaceDarkBlue
import com.felixbrucker.simklcalendar.ui.theme.SurfaceRedDeep
import com.felixbrucker.simklcalendar.ui.theme.SurfaceVariantDark

val MediaType.categoryColor: Color
    get() = when (this) {
        MediaType.ANIME -> PurpleLight
        MediaType.MOVIE -> ErrorPink
        MediaType.TV -> BlueAccent
    }

val MediaType.badgeTextColor: Color
    get() = when (this) {
        MediaType.ANIME -> SurfaceVariantDark
        MediaType.MOVIE -> SurfaceRedDeep
        MediaType.TV -> SurfaceDarkBlue
    }

val WatchlistStatus?.overlayIcon: ImageVector?
    get() = when (this) {
        WatchlistStatus.COMPLETED -> Icons.Default.CheckCircle
        WatchlistStatus.HOLD -> Icons.Default.Pause
        WatchlistStatus.DROPPED -> Icons.Default.Cancel
        WatchlistStatus.WATCHING -> Icons.Default.Visibility
        WatchlistStatus.PLAN_TO_WATCH -> Icons.Default.Bookmark
        else -> null
    }

val WatchlistStatus?.posterAlpha: Float
    get() = if (this != null) 0.6f else 1.0f

@Composable
fun MediaTypeBadge(
    mediaType: MediaType,
    modifier: Modifier = Modifier,
    text: String = mediaType.displayName.uppercase(),
    fontSize: TextUnit = 9.sp,
    cornerRadius: Dp = 3.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(mediaType.categoryColor)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = mediaType.badgeTextColor,
            lineHeight = fontSize
        )
    }
}

@Composable
fun WatchlistOverlayPoster(
    posterFragment: String?,
    contentDescription: String?,
    watchlistStatus: WatchlistStatus?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    iconSize: Dp = 24.dp,
    applyAlpha: Boolean = true
) {
    val alphaVal = if (applyAlpha) watchlistStatus.posterAlpha else 1.0f
    Box(
        modifier = modifier.alpha(alphaVal),
        contentAlignment = Alignment.Center
    ) {
        PosterAsyncImage(posterFragment = posterFragment, contentDescription = contentDescription, contentScale = contentScale)
        PosterOverlayIcon(watchlistStatus = watchlistStatus, iconSize = iconSize)
    }
}

@Composable
private fun PosterAsyncImage(
    posterFragment: String?,
    contentDescription: String?,
    contentScale: ContentScale
) {
    val context = LocalContext.current
    val posterUrl = remember(posterFragment) { posterFragment?.toPosterUrl(PosterSize.COMPACT) }
    AsyncImage(
        model = ImageRequest.Builder(context).data(posterUrl).crossfade(enable = true).build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = Modifier.fillMaxSize(),
        error = painterResource(id = ic_menu_gallery)
    )
}

@Composable
private fun PosterOverlayIcon(
    watchlistStatus: WatchlistStatus?,
    iconSize: Dp
) {
    val isFaded = watchlistStatus != null
    val overlayIcon = watchlistStatus.overlayIcon
    if (isFaded && overlayIcon != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = overlayIcon,
                contentDescription = watchlistStatus.displayName,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun WatchlistOverlayPosterWithBadge(
    posterFragment: String?,
    contentDescription: String?,
    mediaType: MediaType,
    watchlistStatus: WatchlistStatus?,
    modifier: Modifier = Modifier,
    posterHeight: Dp = 110.dp,
    iconSize: Dp = 24.dp
) {
    val alphaVal = watchlistStatus.posterAlpha
    Column(
        modifier = modifier.alpha(alphaVal),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PosterWithBadgeImageSection(
            posterFragment = posterFragment,
            contentDescription = contentDescription,
            watchlistStatus = watchlistStatus,
            posterHeight = posterHeight,
            iconSize = iconSize
        )
        PosterCategoryBadge(mediaType = mediaType)
    }
}

@Composable
private fun PosterWithBadgeImageSection(
    posterFragment: String?,
    contentDescription: String?,
    watchlistStatus: WatchlistStatus?,
    posterHeight: Dp,
    iconSize: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(posterHeight),
        contentAlignment = Alignment.Center
    ) {
        WatchlistOverlayPoster(
            posterFragment = posterFragment,
            contentDescription = contentDescription,
            watchlistStatus = watchlistStatus,
            modifier = Modifier.fillMaxSize(),
            iconSize = iconSize,
            applyAlpha = false
        )
    }
}

@Composable
private fun PosterCategoryBadge(mediaType: MediaType) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(mediaType.categoryColor),
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
