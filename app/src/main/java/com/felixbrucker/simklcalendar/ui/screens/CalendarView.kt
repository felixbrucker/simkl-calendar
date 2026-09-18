package com.felixbrucker.simklcalendar.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.util.DateUtil
import com.felixbrucker.simklcalendar.data.util.DownloadProgress
import com.felixbrucker.simklcalendar.data.util.PosterSize
import com.felixbrucker.simklcalendar.data.util.formattedEpisodeCardBadge
import com.felixbrucker.simklcalendar.data.util.toPosterUrl
import com.felixbrucker.simklcalendar.ui.viewmodel.CalendarViewModel
import kotlin.collections.get

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarView(
    items: List<CalendarItemWithWatchlist>,
    earlierItems: List<CalendarItemWithWatchlist>,
    upcomingGrouped: Map<String, List<CalendarItemWithWatchlist>>,
    earlierGrouped: Map<String, List<CalendarItemWithWatchlist>>,
    showEarlierReleases: Boolean,
    searchQuery: String,
    torrentDownloads: Map<String, DownloadProgress>,
    viewModel: CalendarViewModel,
    onNavigateToShowDetail: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(8.dp))

        // Calendar Group list
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (searchQuery.isNotBlank()) Icons.Default.SearchOff else Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = Color(0xFF3E3D4F),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (searchQuery.isNotBlank()) {
                            "No releases found matching \"$searchQuery\""
                        } else {
                            "No releases found matching filters"
                        },
                        color = Color(0xFFA5A3B1),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (searchQuery.isNotBlank()) {
                        TextButton(
                            onClick = {
                                viewModel.clearSearchQuery()
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        ) {
                            Text("Clear Search Query", color = Color(0xFFD0BCFF))
                        }
                    } else {
                        TextButton(
                            onClick = {
                                viewModel.resetFilters()
                            },
                        ) {
                            Text("Reset Active Filters", color = Color(0xFFD0BCFF))
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Earlier Releases Expandable Header Card
                if (earlierItems.isNotEmpty()) {
                    item(key = "earlier_releases_toggle_card") {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (showEarlierReleases) Color(0xFF381E72).copy(alpha = 0.5f) else Color(0xFF2B2930)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (showEarlierReleases) Color(0xFFD0BCFF) else Color(0xFF49454F)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { viewModel.toggleShowEarlierReleases() }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = Color(0xFFD0BCFF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = if (showEarlierReleases) "Hide Earlier Releases" else "Show Earlier Releases",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = Color(0xFFE6E1E5)
                                        )
                                        Text(
                                            text = if (searchQuery.isNotBlank()) {
                                                "${earlierItems.size} matching past ${if (earlierItems.size == 1) "release" else "releases"}"
                                            } else {
                                                "${earlierItems.size} past ${if (earlierItems.size == 1) "release" else "releases"} hidden by default"
                                            },
                                            fontSize = 12.sp,
                                            color = Color(0xFFCAC4D0)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (showEarlierReleases) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (showEarlierReleases) "Collapse earlier releases" else "Expand earlier releases",
                                    tint = Color(0xFFD0BCFF),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // When expanded, render earlier day groups
                    if (showEarlierReleases) {
                        earlierGrouped.forEach { (dateHeader, dayItems) ->
                            stickyHeader(key = "earlier_header_$dateHeader") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1C1B1F))
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = dateHeader,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF9E9AA3),
                                        letterSpacing = 1.sp
                                    )
                                }
                            }

                            items(dayItems, key = { "earlier_${it.primaryKey}" }) { item ->
                                CalendarItemCard(
                                    item = item,
                                    onNavigateToShowDetail = onNavigateToShowDetail,
                                    downloadProgress = torrentDownloads[item.downloadTaskId],
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }

                // Upcoming releases (today and future dates)
                if (upcomingGrouped.isNotEmpty()) {
                    upcomingGrouped.forEach { (dateHeader, dayItems) ->
                        stickyHeader(key = "upcoming_header_$dateHeader") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1C1B1F))
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = dateHeader,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCAC4D0),
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        items(dayItems, key = { it.primaryKey }) { item ->
                            CalendarItemCard(
                                item = item,
                                onNavigateToShowDetail = onNavigateToShowDetail,
                                downloadProgress = torrentDownloads[item.downloadTaskId],
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                } else if (earlierItems.isNotEmpty() && !showEarlierReleases) {
                    // Notice when upcoming is empty but earlier items exist
                    item(key = "no_upcoming_prompt") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.EventAvailable,
                                    contentDescription = null,
                                    tint = Color(0xFF3E3D4F),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    if (searchQuery.isNotBlank()) {
                                        "No upcoming releases matching \"$searchQuery\""
                                    } else {
                                        "No upcoming releases for active filters"
                                    },
                                    color = Color(0xFFA5A3B1),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { viewModel.setShowEarlierReleases(true) }) {
                                    Text(
                                        if (searchQuery.isNotBlank()) {
                                            "View ${earlierItems.size} Matching Earlier Releases"
                                        } else {
                                            "View ${earlierItems.size} Earlier Releases"
                                        },
                                        color = Color(0xFFD0BCFF)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarItemCard(
    item: CalendarItemWithWatchlist,
    onNavigateToShowDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    downloadProgress: DownloadProgress? = null
) {
    val categoryColor = when (item.type) {
        MediaType.ANIME -> Color(0xFFD0BCFF)
        MediaType.MOVIE -> Color(0xFFF2B8B5)
        MediaType.TV -> Color(0xFFBAC3FF)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2B2930)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.mediaStatus == MediaStatus.DOWNLOADING) Color(0xFF004A77) else Color(0xFF49454F)
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onNavigateToShowDetail(item.primaryKey) }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Coil Async Image loading cropped poster
                Box(
                    modifier = Modifier
                        .size(width = 60.dp, height = 90.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF313033))
                ) {
                    AsyncImage(
                        model = item.poster.toPosterUrl(PosterSize.COMPACT),
                        contentDescription = "${item.title} Poster",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = painterResource(id = android.R.drawable.ic_menu_gallery)
                    )

                    // Slim type overlay bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(categoryColor)
                            .align(Alignment.BottomStart)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Details Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Type Badge
                        Badge(
                            containerColor = categoryColor.copy(alpha = 0.2f),
                            contentColor = categoryColor
                        ) {
                            Text(item.type.displayName.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                        }

                        // Downloading badge
                        if (item.mediaStatus == MediaStatus.DOWNLOADING) {
                            Badge(
                                containerColor = Color(0xFF004A77),
                                contentColor = Color(0xFFC2E8FF)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Downloading",
                                        modifier = Modifier.size(10.dp),
                                        tint = Color(0xFFC2E8FF)
                                    )
                                    Text(
                                        text = "DOWNLOADING",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFC2E8FF)
                                    )
                                }
                            }
                        }

                        // Downloaded badge
                        if (item.mediaStatus == MediaStatus.DOWNLOADED) {
                            Badge(
                                containerColor = Color(0xFF1E3A2B),
                                contentColor = Color(0xFF7CE49F)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Downloaded",
                                        modifier = Modifier.size(10.dp),
                                        tint = Color(0xFF7CE49F)
                                    )
                                    Text(
                                        text = "DOWNLOADED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF7CE49F)
                                    )
                                }
                            }
                        }

                        // Premiere badge
                        if (item.isSeasonPremiere) {
                            Badge(containerColor = Color(0xFFE8DEF8), contentColor = Color(0xFF1D192B)) {
                                Text("SEASON PREMIERE", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                            }
                        }

                        // Finale badge
                        if (item.isSeasonFinale) {
                            Badge(containerColor = Color(0xFFB3261E), contentColor = Color.White) {
                                Text("SEASON FINALE", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color(0xFFE6E1E5),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val romaji = item.titleRomaji
                    if (item.type == MediaType.ANIME && !romaji.isNullOrBlank()) {
                        Text(
                            text = romaji,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFFCAC4D0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    when (item.type) {
                        MediaType.ANIME, MediaType.TV -> {
                            val epLabel = item.formattedEpisodeCardBadge
                            val epTitle = item.episodeTitle?.takeIf { it.isNotBlank() } ?: "TBA"
                            Text(
                                text = "$epLabel: $epTitle",
                                fontSize = 13.sp,
                                color = Color(0xFFCAC4D0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        MediaType.MOVIE -> {
                            Text(
                                text = item.movieReleaseType?.displayName ?: "Movie Release",
                                fontSize = 13.sp,
                                color = Color(0xFFF2B8B5)
                            )
                        }
                    }

                    if (item.type != MediaType.MOVIE) {
                        val releaseTime = DateUtil.formatLocalizedTime(item.date)
                        if (releaseTime != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Air Time",
                                    modifier = Modifier.size(13.dp),
                                    tint = Color(0xFFD0BCFF)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = releaseTime,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFD0BCFF)
                                )
                            }
                        }
                    }
                }
            }

            // Progress bar and stats for downloading items
            if (item.mediaStatus == MediaStatus.DOWNLOADING && downloadProgress != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF004A77).copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    val progress = if (downloadProgress.totalBytes > 0) downloadProgress.bytesDownloaded.toFloat() / downloadProgress.totalBytes else 0f
                    val percentage = (progress * 100).toInt()
                    val downloaded = android.text.format.Formatter.formatFileSize(LocalContext.current, downloadProgress.bytesDownloaded)
                    val total = android.text.format.Formatter.formatFileSize(LocalContext.current, downloadProgress.totalBytes)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .width(100.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFFC2E8FF),
                            trackColor = Color(0xFF004A77).copy(alpha = 0.3f)
                        )
                        Text(
                            text = "${percentage}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC2E8FF)
                        )
                        Text(
                            text = "$downloaded / $total",
                            fontSize = 10.sp,
                            color = Color(0xFFC2E8FF).copy(alpha = 0.7f)
                        )
                    }

                    if (downloadProgress.downloadSpeed > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val speedStr = android.text.format.Formatter.formatFileSize(LocalContext.current, downloadProgress.downloadSpeed.toLong()) + "/s"
                        val remainingBytes = downloadProgress.totalBytes - downloadProgress.bytesDownloaded
                        val remainingSeconds = (remainingBytes / downloadProgress.downloadSpeed).toLong()
                        val eta = DateUtil.formatDuration(remainingSeconds)

                        Text(
                            text = "$speedStr • ETA: $eta",
                            fontSize = 11.sp,
                            color = Color(0xFFC2E8FF)
                        )
                    }
                }
            }
        }
    }
}
