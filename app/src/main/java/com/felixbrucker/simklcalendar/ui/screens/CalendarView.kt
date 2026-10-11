package com.felixbrucker.simklcalendar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.felixbrucker.simklcalendar.data.util.formattedAnimeSeasonLabel
import com.felixbrucker.simklcalendar.data.util.formattedEpisodeCardBadge
import com.felixbrucker.simklcalendar.extensions.toPosterUrl
import com.felixbrucker.simklcalendar.ui.theme.BlueAccent
import com.felixbrucker.simklcalendar.ui.theme.BlueLight
import com.felixbrucker.simklcalendar.ui.theme.ContainerBlue
import com.felixbrucker.simklcalendar.ui.theme.ErrorPink
import com.felixbrucker.simklcalendar.ui.theme.ErrorRed
import com.felixbrucker.simklcalendar.ui.theme.GreenSuccess
import com.felixbrucker.simklcalendar.ui.theme.OutlineVariant
import com.felixbrucker.simklcalendar.ui.theme.PurpleLight
import com.felixbrucker.simklcalendar.ui.theme.PurpleContainerLight
import com.felixbrucker.simklcalendar.ui.theme.SurfaceContainer
import com.felixbrucker.simklcalendar.ui.theme.SurfaceDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceGreen
import com.felixbrucker.simklcalendar.ui.theme.SurfaceGreyDark
import com.felixbrucker.simklcalendar.ui.theme.SurfacePurple
import com.felixbrucker.simklcalendar.ui.theme.SurfacePurpleVariant
import com.felixbrucker.simklcalendar.ui.theme.TextGreyLight
import com.felixbrucker.simklcalendar.ui.theme.TextGreyMuted
import com.felixbrucker.simklcalendar.ui.theme.TextPrimary
import com.felixbrucker.simklcalendar.ui.theme.TextSubtle
import com.felixbrucker.simklcalendar.ui.viewmodel.CalendarViewModel
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun CalendarView(
    viewModel: CalendarViewModel,
    onNavigateToShowDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
) {
    val items by viewModel.filteredCalendarItems.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val torrentDownloads by viewModel.torrentDownloads.collectAsState()
    val uiPreferences by viewModel.uiPreferences.collectAsState()

    CalendarContent(
        items = items,
        searchQuery = searchQuery,
        torrentDownloads = torrentDownloads,
        showEarlierReleases = uiPreferences.filterShowEarlier,
        onClearSearchQuery = { viewModel.clearSearchQuery() },
        onResetFilters = { viewModel.resetFilters() },
        onToggleShowEarlierReleases = { viewModel.toggleShowEarlierReleases() },
        onSetShowEarlierReleases = { viewModel.setShowEarlierReleases(it) },
        onNavigateToShowDetail = onNavigateToShowDetail,
        modifier = modifier,
        lazyListState = lazyListState
    )
}

@Immutable
data class CalendarItemListState(
    val lazyListState: LazyListState,
    val showEarlierReleases: Boolean,
    val earlierItems: List<CalendarItemWithWatchlist>,
    val earlierGrouped: Map<String, List<CalendarItemWithWatchlist>>,
    val upcomingGrouped: Map<String, List<CalendarItemWithWatchlist>>,
    val searchQuery: String,
    val torrentDownloads: Map<String, DownloadProgress>,
)

@Composable
fun CalendarContent(
    items: List<CalendarItemWithWatchlist>,
    searchQuery: String,
    torrentDownloads: Map<String, DownloadProgress>,
    showEarlierReleases: Boolean,
    onClearSearchQuery: () -> Unit,
    onResetFilters: () -> Unit,
    onToggleShowEarlierReleases: () -> Unit,
    onSetShowEarlierReleases: (Boolean) -> Unit,
    onNavigateToShowDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
) {
    val (earlierItems, earlierGrouped, upcomingGrouped) = remember(items) {
        groupCalendarItems(items)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(8.dp))

        if (items.isEmpty()) {
            CalendarEmptyState(
                searchQuery = searchQuery,
                onClearSearchQuery = onClearSearchQuery,
                onResetFilters = onResetFilters
            )
        } else {
            CalendarItemList(
                state = CalendarItemListState(
                    lazyListState = lazyListState,
                    showEarlierReleases = showEarlierReleases,
                    earlierItems = earlierItems,
                    earlierGrouped = earlierGrouped,
                    upcomingGrouped = upcomingGrouped,
                    searchQuery = searchQuery,
                    torrentDownloads = torrentDownloads
                ),
                onToggleShowEarlierReleases = onToggleShowEarlierReleases,
                onSetShowEarlierReleases = onSetShowEarlierReleases,
                onNavigateToShowDetail = onNavigateToShowDetail,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private typealias GroupedCalendarItems = Triple<
    List<CalendarItemWithWatchlist>,
    Map<String, List<CalendarItemWithWatchlist>>,
    Map<String, List<CalendarItemWithWatchlist>>
    >

private fun groupCalendarItems(
    items: List<CalendarItemWithWatchlist>
): GroupedCalendarItems {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    val earlierList = mutableListOf<CalendarItemWithWatchlist>()
    val earlierMap = LinkedHashMap<LocalDate, MutableList<CalendarItemWithWatchlist>>()
    val upcomingMap = LinkedHashMap<LocalDate, MutableList<CalendarItemWithWatchlist>>()

    for (item in items) {
        val localDate = item.date.atZone(zone).toLocalDate()
        if (localDate.isBefore(today)) {
            earlierList.add(item)
            earlierMap.getOrPut(localDate) { mutableListOf() }.add(item)
        } else {
            upcomingMap.getOrPut(localDate) { mutableListOf() }.add(item)
        }
    }

    val formattedEarlier = earlierMap.mapKeys { (localDate, _) ->
        DateUtil.formatAiringDateHeader(localDate, zone, today)
    }
    val formattedUpcoming = upcomingMap.mapKeys { (localDate, _) ->
        DateUtil.formatAiringDateHeader(localDate, zone, today)
    }

    return Triple(earlierList, formattedEarlier, formattedUpcoming)
}

@Composable
private fun CalendarItemList(
    state: CalendarItemListState,
    onToggleShowEarlierReleases: () -> Unit,
    onSetShowEarlierReleases: (Boolean) -> Unit,
    onNavigateToShowDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = state.lazyListState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        if (state.earlierItems.isNotEmpty()) {
            item(key = "earlier_releases_toggle_card") {
                CalendarEarlierReleasesHeader(
                    showEarlierReleases = state.showEarlierReleases,
                    earlierCount = state.earlierItems.size,
                    searchQuery = state.searchQuery,
                    onToggle = onToggleShowEarlierReleases
                )
            }

            if (state.showEarlierReleases) {
                renderGroupedCalendarItems(
                    groupedItems = state.earlierGrouped,
                    keyPrefix = "earlier",
                    headerColor = TextGreyMuted,
                    torrentDownloads = state.torrentDownloads,
                    onNavigateToShowDetail = onNavigateToShowDetail
                )
            }
        }

        if (state.upcomingGrouped.isNotEmpty()) {
            renderGroupedCalendarItems(
                groupedItems = state.upcomingGrouped,
                keyPrefix = "upcoming",
                headerColor = TextSubtle,
                torrentDownloads = state.torrentDownloads,
                onNavigateToShowDetail = onNavigateToShowDetail
            )
        } else if (state.earlierItems.isNotEmpty() && !state.showEarlierReleases) {
            item(key = "no_upcoming_prompt") {
                CalendarNoUpcomingReleasesNotice(
                    searchQuery = state.searchQuery,
                    earlierCount = state.earlierItems.size,
                    onShowEarlierReleases = { onSetShowEarlierReleases(true) }
                )
            }
        }
    }
}

private fun LazyListScope.renderGroupedCalendarItems(
    groupedItems: Map<String, List<CalendarItemWithWatchlist>>,
    keyPrefix: String,
    headerColor: Color,
    torrentDownloads: Map<String, DownloadProgress>,
    onNavigateToShowDetail: (String) -> Unit
) {
    groupedItems.forEach { (dateHeader, dayItems) ->
        stickyHeader(key = "${keyPrefix}_header_$dateHeader") {
            DateGroupHeader(dateHeader = dateHeader, textColor = headerColor)
        }

        items(dayItems, key = { if (keyPrefix == "earlier") "earlier_${it.primaryKey}" else it.primaryKey }) { item ->
            CalendarItemCard(
                item = item,
                onNavigateToShowDetail = onNavigateToShowDetail,
                downloadProgress = torrentDownloads[item.downloadTaskId],
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun DateGroupHeader(
    dateHeader: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = dateHeader,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun CalendarNoUpcomingReleasesNotice(
    searchQuery: String,
    earlierCount: Int,
    onShowEarlierReleases: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.EventAvailable,
                contentDescription = null,
                tint = SurfacePurpleVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                if (searchQuery.isNotBlank()) {
                    "No upcoming releases matching \"$searchQuery\""
                } else {
                    "No upcoming releases for active filters"
                },
                color = TextGreyLight,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onShowEarlierReleases) {
                Text(
                    if (searchQuery.isNotBlank()) {
                        "View $earlierCount Matching Earlier Releases"
                    } else {
                        "View $earlierCount Earlier Releases"
                    },
                    color = PurpleLight
                )
            }
        }
    }
}

@Composable
fun CalendarEmptyState(
    searchQuery: String,
    onClearSearchQuery: () -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (searchQuery.isNotBlank()) Icons.Default.SearchOff else Icons.Default.CalendarToday,
                contentDescription = null,
                tint = SurfacePurpleVariant,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (searchQuery.isNotBlank()) {
                    "No releases found matching \"$searchQuery\""
                } else {
                    "No releases found matching filters"
                },
                color = TextGreyLight,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (searchQuery.isNotBlank()) {
                TextButton(
                    onClick = {
                        onClearSearchQuery()
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                ) {
                    Text("Clear Search Query", color = PurpleLight)
                }
            } else {
                TextButton(
                    onClick = onResetFilters
                ) {
                    Text("Reset Active Filters", color = PurpleLight)
                }
            }
        }
    }
}

@Composable
fun CalendarEarlierReleasesHeader(
    showEarlierReleases: Boolean,
    earlierCount: Int,
    searchQuery: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (showEarlierReleases) SurfacePurple.copy(alpha = 0.5f) else SurfaceContainer
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (showEarlierReleases) PurpleLight else OutlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onToggle() }
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
                    tint = PurpleLight,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = if (showEarlierReleases) "Hide Earlier Releases" else "Show Earlier Releases",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            "$earlierCount matching past ${if (earlierCount == 1) "release" else "releases"}"
                        } else {
                            "$earlierCount past ${if (earlierCount == 1) "release" else "releases"} hidden by default"
                        },
                        fontSize = 12.sp,
                        color = TextSubtle
                    )
                }
            }
            Icon(
                imageVector = if (showEarlierReleases) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (showEarlierReleases) "Collapse earlier releases" else "Expand earlier releases",
                tint = PurpleLight,
                modifier = Modifier.size(24.dp)
            )
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
        MediaType.ANIME -> PurpleLight
        MediaType.MOVIE -> ErrorPink
        MediaType.TV -> BlueAccent
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceContainer
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.mediaStatus == MediaStatus.DOWNLOADING) ContainerBlue else OutlineVariant
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
                        .background(SurfaceGreyDark)
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                containerColor = ContainerBlue,
                                contentColor = BlueLight
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
                                        tint = BlueLight
                                    )
                                    Text(
                                        text = "DOWNLOADING",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BlueLight
                                    )
                                }
                            }
                        }

                        // Downloaded badge
                        if (item.mediaStatus == MediaStatus.DOWNLOADED) {
                            Badge(
                                containerColor = SurfaceGreen,
                                contentColor = GreenSuccess
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
                                        tint = GreenSuccess
                                    )
                                    Text(
                                        text = "DOWNLOADED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GreenSuccess
                                    )
                                }
                            }
                        }

                        // Premiere badge
                        if (item.isSeasonPremiere) {
                            Badge(containerColor = PurpleContainerLight, contentColor = SurfacePurpleVariant) {
                                Text("SEASON PREMIERE", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                            }
                        }

                        // Finale badge
                        if (item.isSeasonFinale) {
                            Badge(containerColor = ErrorRed, contentColor = Color.White) {
                                Text("SEASON FINALE", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                            }
                        }

                        // Anime Season badge
                        val animeSeasonLabel = item.formattedAnimeSeasonLabel
                        if (animeSeasonLabel != null) {
                            Badge(containerColor = PurpleContainerLight, contentColor = SurfacePurpleVariant) {
                                Text(
                                    text = animeSeasonLabel.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (item.hasUniqueTitleRomaji) {
                        Text(
                            text = item.titleRomaji!!,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = TextSubtle,
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
                                color = TextSubtle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        MediaType.MOVIE -> {
                            Text(
                                text = item.movieReleaseType?.displayName ?: "Movie Release",
                                fontSize = 13.sp,
                                color = ErrorPink
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
                                    tint = PurpleLight
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = releaseTime,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = PurpleLight
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
                        .background(ContainerBlue.copy(alpha = 0.1f))
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
                            color = BlueLight,
                            trackColor = ContainerBlue.copy(alpha = 0.3f)
                        )
                        Text(
                            text = "${percentage}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BlueLight
                        )
                        Text(
                            text = "$downloaded / $total",
                            fontSize = 10.sp,
                            color = BlueLight.copy(alpha = 0.7f)
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
                            color = BlueLight
                        )
                    }
                }
            }
        }
    }
}
