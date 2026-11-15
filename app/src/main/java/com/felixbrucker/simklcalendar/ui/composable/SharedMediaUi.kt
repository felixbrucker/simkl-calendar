package com.felixbrucker.simklcalendar.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.WatchlistStatus
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
