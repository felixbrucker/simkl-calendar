package com.felixbrucker.simklcalendar.ui.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.util.DateUtil
import com.felixbrucker.simklcalendar.data.util.DownloadProgress
import com.felixbrucker.simklcalendar.data.util.formattedEpisodeCode
import com.felixbrucker.simklcalendar.ui.theme.OutlineVariant
import com.felixbrucker.simklcalendar.ui.theme.PurpleContainerSoft
import com.felixbrucker.simklcalendar.ui.theme.PurpleLight
import com.felixbrucker.simklcalendar.ui.theme.SurfaceContainer
import com.felixbrucker.simklcalendar.ui.theme.SurfaceDark
import com.felixbrucker.simklcalendar.ui.theme.SurfacePrimaryVariant
import com.felixbrucker.simklcalendar.ui.theme.SurfacePurple
import com.felixbrucker.simklcalendar.ui.theme.SurfaceVariantGrey
import com.felixbrucker.simklcalendar.ui.theme.TextGreyLight
import com.felixbrucker.simklcalendar.ui.theme.TextPrimary

@Immutable
data class EpisodeListState(
    val items: List<CalendarItemWithWatchlist>,
    val mediaType: MediaType,
    val isSmallScreen: Boolean = false,
    val activeItemKey: String? = null,
    val updatingWatchKeys: Set<String> = emptySet(),
    val torrentDownloads: Map<String, DownloadProgress> = emptyMap(),
    val showSeasonHeaders: Boolean = true,
    val cardTitle: String? = null,
)

@Immutable
data class EpisodeListActions(
    val onItemClick: (CalendarItemWithWatchlist) -> Unit,
    val onWatchedStatusChange: (CalendarItemWithWatchlist, Boolean) -> Unit,
    val onMediaStatusChange: (String, MediaStatus) -> Unit,
    val onMarkSeasonWatched: ((season: Int) -> Unit)? = null,
    val onMarkSeasonUnwatched: ((season: Int) -> Unit)? = null,
    val onUpdateSeasonMediaStatus: ((season: Int, status: MediaStatus) -> Unit)? = null,
)

@Composable
fun EpisodeListCard(
    state: EpisodeListState,
    actions: EpisodeListActions,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            EpisodeListCardHeader(title = state.cardTitle, mediaType = state.mediaType, itemCount = state.items.size)
            Spacer(modifier = Modifier.height(10.dp))
            EpisodeListContent(
                state = state,
                actions = actions
            )
        }
    }
}

@Composable
private fun EpisodeListCardHeader(
    title: String?,
    mediaType: MediaType,
    itemCount: Int,
) {
    val titleText = title ?: if (mediaType == MediaType.MOVIE) {
        "Scheduled Releases ($itemCount)"
    } else {
        "Scheduled Episodes ($itemCount)"
    }
    Text(
        text = titleText,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        fontSize = 15.sp
    )
}

@Composable
fun EpisodeListContent(
    state: EpisodeListState,
    actions: EpisodeListActions,
    modifier: Modifier = Modifier,
) {
    if (state.mediaType == MediaType.MOVIE) {
        Column(modifier = modifier) {
            state.items.forEach { epItem ->
                EpisodeListRow(
                    epItem = epItem,
                    state = state,
                    actions = actions
                )
            }
        }
    } else {
        val grouped = remember(state.items) {
            state.items.groupBy { it.season ?: 1 }.toSortedMap()
        }
        Column(modifier = modifier) {
            grouped.forEach { (season, seasonEpisodes) ->
                if (state.showSeasonHeaders) {
                    SeasonSectionHeader(
                        season = season,
                        episodes = seasonEpisodes,
                        updatingWatchKeys = state.updatingWatchKeys,
                        onMarkSeasonWatched = actions.onMarkSeasonWatched,
                        onMarkSeasonUnwatched = actions.onMarkSeasonUnwatched,
                        onUpdateSeasonMediaStatus = actions.onUpdateSeasonMediaStatus
                    )
                }
                seasonEpisodes.forEach { epItem ->
                    EpisodeListRow(
                        epItem = epItem,
                        state = state,
                        actions = actions
                    )
                }
            }
        }
    }
}

@Composable
fun SeasonSectionHeader(
    season: Int,
    episodes: List<CalendarItemWithWatchlist>,
    updatingWatchKeys: Set<String>,
    modifier: Modifier = Modifier,
    onMarkSeasonWatched: ((season: Int) -> Unit)? = null,
    onMarkSeasonUnwatched: ((season: Int) -> Unit)? = null,
    onUpdateSeasonMediaStatus: ((season: Int, status: MediaStatus) -> Unit)? = null,
) {
    val commonStatus = remember(episodes) {
        val first = episodes.firstOrNull()?.mediaStatus
        if (first != null && episodes.all { it.mediaStatus == first }) first else null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Season $season (${episodes.size})",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = PurpleLight
        )

        if (onMarkSeasonWatched != null && onMarkSeasonUnwatched != null && onUpdateSeasonMediaStatus != null) {
            SeasonHeaderActions(
                season = season,
                episodes = episodes,
                commonStatus = commonStatus,
                updatingWatchKeys = updatingWatchKeys,
                onMarkSeasonWatched = onMarkSeasonWatched,
                onMarkSeasonUnwatched = onMarkSeasonUnwatched,
                onUpdateSeasonMediaStatus = onUpdateSeasonMediaStatus
            )
        }
    }
}

@Composable
private fun SeasonHeaderActions(
    season: Int,
    episodes: List<CalendarItemWithWatchlist>,
    commonStatus: MediaStatus?,
    updatingWatchKeys: Set<String>,
    onMarkSeasonWatched: (season: Int) -> Unit,
    onMarkSeasonUnwatched: (season: Int) -> Unit,
    onUpdateSeasonMediaStatus: (season: Int, status: MediaStatus) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val isWatched = episodes.all { it.isWatched }
        val isLoading = episodes.any { updatingWatchKeys.contains(it.primaryKey) }

        WatchedStatusDropdown(
            isWatched = isWatched,
            onStatusChange = { watched ->
                if (watched) {
                    onMarkSeasonWatched(season)
                } else {
                    onMarkSeasonUnwatched(season)
                }
            },
            isLoading = isLoading
        )

        MediaStatusDropdown(
            currentStatus = commonStatus ?: MediaStatus.IGNORED,
            onStatusChange = { onUpdateSeasonMediaStatus(season, it) }
        )
    }
}

@Composable
fun EpisodeListRow(
    epItem: CalendarItemWithWatchlist,
    state: EpisodeListState,
    actions: EpisodeListActions,
    modifier: Modifier = Modifier
) {
    val isSelected = epItem.primaryKey == state.activeItemKey
    val downloadProgress = state.torrentDownloads[epItem.downloadTaskId]

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) SurfacePrimaryVariant.copy(alpha = 0.4f) else SurfaceDark,
        border = BorderStroke(
            1.dp,
            if (isSelected) PurpleLight else SurfaceVariantGrey
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { actions.onItemClick(epItem) }
    ) {
        EpisodeListRowContent(
            epItem = epItem,
            state = state,
            actions = actions,
            isSelected = isSelected,
            downloadProgress = downloadProgress
        )
    }
}

@Composable
private fun EpisodeListRowContent(
    epItem: CalendarItemWithWatchlist,
    state: EpisodeListState,
    actions: EpisodeListActions,
    isSelected: Boolean,
    downloadProgress: DownloadProgress?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            EpisodeListTagBadge(epItem = epItem, isSelected = isSelected)
            EpisodeListTitleAndProgress(
                epItem = epItem,
                isSelected = isSelected,
                downloadProgress = downloadProgress
            )
        }

        if (state.mediaType != MediaType.MOVIE) {
            EpisodeListRowDropdowns(
                epItem = epItem,
                isSmallScreen = state.isSmallScreen,
                updatingWatchKeys = state.updatingWatchKeys,
                onWatchedStatusChange = actions.onWatchedStatusChange,
                onMediaStatusChange = actions.onMediaStatusChange
            )
        }

        if (isSelected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Currently Selected",
                tint = PurpleLight,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun EpisodeListTagBadge(
    epItem: CalendarItemWithWatchlist,
    isSelected: Boolean
) {
    val epTag = if (epItem.type == MediaType.MOVIE) {
        if (epItem.movieReleaseType == MovieReleaseType.THEATER) "THEATER" else "DIGITAL / DVD"
    } else {
        epItem.formattedEpisodeCode
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (isSelected) PurpleLight else SurfacePurple,
        contentColor = if (isSelected) SurfacePurple else PurpleContainerSoft
    ) {
        Text(
            text = epTag,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EpisodeListTitleAndProgress(
    epItem: CalendarItemWithWatchlist,
    isSelected: Boolean,
    downloadProgress: DownloadProgress?
) {
    val epDate = if (epItem.type == MediaType.MOVIE) {
        DateUtil.formatDisplayDate(epItem.date)
    } else {
        DateUtil.formatDisplayDateTime(epItem.date)
    }
    val titleText = if (epItem.type == MediaType.MOVIE) {
        epItem.movieReleaseType?.displayName ?: "Movie Release"
    } else {
        epItem.episodeTitle?.takeIf { it.isNotBlank() } ?: "TBA"
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(modifier = Modifier.align(Alignment.CenterVertically)) {
            Text(
                text = titleText,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = epDate,
                color = TextGreyLight,
                fontSize = 11.sp
            )
        }
        if (epItem.mediaStatus == MediaStatus.DOWNLOADING && downloadProgress != null) {
            EpisodeDownloadProgress(
                downloadProgress = downloadProgress,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}

@Composable
private fun EpisodeListRowDropdowns(
    epItem: CalendarItemWithWatchlist,
    isSmallScreen: Boolean,
    updatingWatchKeys: Set<String>,
    onWatchedStatusChange: (CalendarItemWithWatchlist, Boolean) -> Unit,
    onMediaStatusChange: (String, MediaStatus) -> Unit
) {
    if (isSmallScreen) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.End
        ) {
            ItemWatchedStatusDropdown(
                item = epItem,
                onWatchedStatusChange = { watched -> onWatchedStatusChange(epItem, watched) },
                updatingWatchKeys = updatingWatchKeys
            )
            ItemMediaStatusDropdown(
                item = epItem,
                onStatusChange = { newStatus -> onMediaStatusChange(epItem.primaryKey, newStatus) }
            )
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ItemWatchedStatusDropdown(
                item = epItem,
                onWatchedStatusChange = { watched -> onWatchedStatusChange(epItem, watched) },
                updatingWatchKeys = updatingWatchKeys
            )
            ItemMediaStatusDropdown(
                item = epItem,
                onStatusChange = { newStatus -> onMediaStatusChange(epItem.primaryKey, newStatus) }
            )
        }
    }
}
