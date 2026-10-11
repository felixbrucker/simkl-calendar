package com.felixbrucker.simklcalendar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.util.DateUtil
import com.felixbrucker.simklcalendar.data.util.formattedAnimeSeasonLabel
import com.felixbrucker.simklcalendar.ui.composable.getTableItemColor
import com.felixbrucker.simklcalendar.ui.theme.BlueAccent
import com.felixbrucker.simklcalendar.ui.theme.ErrorPink
import com.felixbrucker.simklcalendar.ui.theme.OutlineVariant
import com.felixbrucker.simklcalendar.ui.theme.PurpleLight
import com.felixbrucker.simklcalendar.ui.theme.TextPrimary
import com.felixbrucker.simklcalendar.ui.theme.TextSubtle
import com.felixbrucker.simklcalendar.ui.viewmodel.CalendarViewModel
import com.felixbrucker.simklcalendar.ui.viewmodel.SortDirection
import com.felixbrucker.simklcalendar.ui.viewmodel.TableSortField
import com.felixbrucker.simklcalendar.ui.viewmodel.WatchlistTableItem

private object TableWeights {
    const val NAME = 5f
    const val LAST_EP = 2f
    const val NEXT_EP = 2f
    const val WATCHED = 1f
    const val DOWNLOADED = 1f
}

private object SmallTableWeights {
    const val NAME = 4f
    const val WATCHED = 1.2f
    const val DOWNLOADED = 1.3f
}

@Composable
fun TrackedWatchlistTableView(
    viewModel: CalendarViewModel,
    onNavigateToSeriesDetail: (Int) -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
) {
    val items by viewModel.watchlistTableItems.collectAsState()
    val sortField by viewModel.tableSortField.collectAsState()
    val sortDirection by viewModel.tableSortDirection.collectAsState()
    val isDownloaderInstalled by viewModel.isTorrentServiceInstalled.collectAsState()

    TrackedWatchlistTableContent(
        items = items,
        sortField = sortField,
        sortDirection = sortDirection,
        isDownloaderInstalled = isDownloaderInstalled,
        onSortToggle = { viewModel.toggleTableSort(it) },
        onNavigateToSeriesDetail = onNavigateToSeriesDetail,
        modifier = modifier,
        lazyListState = lazyListState
    )
}

@Composable
fun TrackedWatchlistTableContent(
    items: List<WatchlistTableItem>,
    sortField: TableSortField,
    sortDirection: SortDirection,
    isDownloaderInstalled: Boolean,
    onSortToggle: (TableSortField) -> Unit,
    onNavigateToSeriesDetail: (Int) -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val isSmallScreen = with(density) { windowInfo.containerSize.width.toDp() } < 800.dp

    // Single-pass segregation reduces collection traversals from O(3N) to O(N)
    val (tvItems, animeItems, movieItems) = remember(items) {
        val tv = ArrayList<WatchlistTableItem>()
        val anime = ArrayList<WatchlistTableItem>()
        val movie = ArrayList<WatchlistTableItem>()
        for (item in items) {
            when (item.watchlistItem.type) {
                MediaType.TV -> tv.add(item)
                MediaType.ANIME -> anime.add(item)
                MediaType.MOVIE -> movie.add(item)
            }
        }
        Triple(tv, anime, movie)
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (items.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No items found matching the selected filters.", color = TextSubtle)
            }
        } else {
            WatchlistTableHeader(
                sortField = sortField,
                sortDirection = sortDirection,
                onSortToggle = onSortToggle,
                isDownloaderInstalled = isDownloaderInstalled,
                isSmallScreen = isSmallScreen
            )

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (tvItems.isNotEmpty()) {
                    item(key = "header_tv") { WatchlistTableSectionHeader("TV Shows", tvItems.size) }
                    items(tvItems, key = { it.watchlistItem.simklId }) { item ->
                        WatchlistTableItemRow(
                            item = item,
                            isDownloaderInstalled = isDownloaderInstalled,
                            isSmallScreen = isSmallScreen,
                            onNavigateToSeriesDetail = onNavigateToSeriesDetail
                        )
                    }
                }
                if (animeItems.isNotEmpty()) {
                    item(key = "header_anime") { WatchlistTableSectionHeader("Anime", animeItems.size) }
                    items(animeItems, key = { it.watchlistItem.simklId }) { item ->
                        WatchlistTableItemRow(
                            item = item,
                            isDownloaderInstalled = isDownloaderInstalled,
                            isSmallScreen = isSmallScreen,
                            onNavigateToSeriesDetail = onNavigateToSeriesDetail
                        )
                    }
                }
                if (movieItems.isNotEmpty()) {
                    item(key = "header_movies") { WatchlistTableSectionHeader("Movies", movieItems.size) }
                    items(movieItems, key = { it.watchlistItem.simklId }) { item ->
                        WatchlistTableItemRow(
                            item = item,
                            isDownloaderInstalled = isDownloaderInstalled,
                            isSmallScreen = isSmallScreen,
                            onNavigateToSeriesDetail = onNavigateToSeriesDetail
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WatchlistTableHeader(
    sortField: TableSortField,
    sortDirection: SortDirection,
    onSortToggle: (TableSortField) -> Unit,
    isDownloaderInstalled: Boolean,
    isSmallScreen: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(16.dp)) // Indicator space

        SortableHeaderItem(
            "Name",
            TableSortField.NAME,
            sortField,
            sortDirection,
            onSortToggle,
            Modifier.weight(if (isSmallScreen) SmallTableWeights.NAME else TableWeights.NAME)
        )
        if (!isSmallScreen) {
            SortableHeaderItem(
                "Last Ep",
                TableSortField.LAST_EP,
                sortField,
                sortDirection,
                onSortToggle,
                Modifier.weight(TableWeights.LAST_EP)
            )
            SortableHeaderItem(
                "Next Ep",
                TableSortField.NEXT_EP,
                sortField,
                sortDirection,
                onSortToggle,
                Modifier.weight(TableWeights.NEXT_EP)
            )
        }
        SortableHeaderItem(
            "Watched",
            TableSortField.WATCHED,
            sortField,
            sortDirection,
            onSortToggle,
            Modifier.weight(if (isSmallScreen) SmallTableWeights.WATCHED else TableWeights.WATCHED)
        )
        if (isDownloaderInstalled) {
            SortableHeaderItem(
                "Downloaded",
                TableSortField.DOWNLOADED,
                sortField,
                sortDirection,
                onSortToggle,
                Modifier.weight(if (isSmallScreen) SmallTableWeights.DOWNLOADED else TableWeights.DOWNLOADED)
            )
        }
        Spacer(modifier = Modifier.width(32.dp)) // Expand icon space
    }
}

@Composable
fun SortableHeaderItem(
    label: String,
    field: TableSortField,
    currentSortField: TableSortField,
    currentDirection: SortDirection,
    onSortToggle: (TableSortField) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = currentSortField == field
    Row(
        modifier = modifier
            .clickable { onSortToggle(field) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) PurpleLight else TextSubtle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isSelected) {
            Icon(
                imageVector = if (currentDirection == SortDirection.ASCENDING) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = PurpleLight,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun WatchlistTableSectionHeader(title: String, count: Int) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Text(
            text = "$title ($count)",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = PurpleLight.copy(alpha = 0.8f)
        )
        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
    }
}

@Composable
fun WatchlistTableItemRow(
    item: WatchlistTableItem,
    isDownloaderInstalled: Boolean,
    isSmallScreen: Boolean,
    onNavigateToSeriesDetail: (Int) -> Unit,
) {
    val categoryColor = when (item.watchlistItem.type) {
        MediaType.ANIME -> PurpleLight
        MediaType.MOVIE -> ErrorPink
        MediaType.TV -> BlueAccent
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToSeriesDetail(item.watchlistItem.simklId) }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Indicator
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(4.dp, 16.dp)
                        .background(categoryColor, RoundedCornerShape(2.dp))
                )

                val animeSeasonLabel = item.watchlistItem.formattedAnimeSeasonLabel
                val displayName = if (animeSeasonLabel != null) {
                    "${item.watchlistItem.title} ($animeSeasonLabel)"
                } else {
                    item.watchlistItem.title
                }

                // Name
                Text(
                    text = displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(if (isSmallScreen) SmallTableWeights.NAME else TableWeights.NAME)
                )

                if (!isSmallScreen) {
                    // Last Ep
                    Text(
                        text = item.lastAiredDate?.let { DateUtil.formatDisplayDateTime(it) } ?: "-",
                        fontSize = 12.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(TableWeights.LAST_EP)
                    )

                    // Next Ep
                    Text(
                        text = item.nextEpisodeDate?.let { DateUtil.formatDisplayDateTime(it) } ?: "-",
                        fontSize = 12.sp,
                        color = PurpleLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(TableWeights.NEXT_EP)
                    )
                }

                // Watched
                val watchedColor =
                    getTableItemColor(item.watchedReleasedCount, item.totalReleasedCount)
                Text(
                    text = "${item.watchedReleasedCount}/${item.totalReleasedCount}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = watchedColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(if (isSmallScreen) SmallTableWeights.WATCHED else TableWeights.WATCHED)
                )

                // Downloaded
                if (isDownloaderInstalled) {
                    val downloadedColor = getTableItemColor(
                        item.downloadedReleasedCount,
                        item.totalDownloadableReleasedCount
                    )
                    Text(
                        text = "${item.downloadedReleasedCount}/${item.totalDownloadableReleasedCount}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = downloadedColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(if (isSmallScreen) SmallTableWeights.DOWNLOADED else TableWeights.DOWNLOADED)
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextSubtle,
                    modifier = Modifier.size(24.dp).padding(end = 8.dp)
                )
            }
        }
    }
}
