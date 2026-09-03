package com.felixbrucker.simklcalendar.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.util.DateUtil
import com.felixbrucker.simklcalendar.data.util.MediaFormatter
import com.felixbrucker.simklcalendar.data.util.PosterSize
import com.felixbrucker.simklcalendar.data.util.toPosterUrl
import com.felixbrucker.simklcalendar.data.util.formattedEpisodeCode
import com.felixbrucker.simklcalendar.ui.viewmodel.CalendarViewModel
import com.felixbrucker.simklcalendar.ui.viewmodel.WatchlistTableItem
import kotlinx.coroutines.launch

private object TableWeights {
    const val NAME = 4f
    const val LAST_EP = 1.8f
    const val NEXT_EP = 1.8f
    const val WATCHED = 1.2f
    const val DOWNLOADED = 1.2f
}

@Composable
fun TrackedWatchlistTableView(
    viewModel: CalendarViewModel,
    onNavigateToEpisode: (String) -> Unit,
    onNavigateToSeriesDetail: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items by viewModel.watchlistTableItems.collectAsState()
    val sortField by viewModel.tableSortField.collectAsState()
    val sortDirection by viewModel.tableSortDirection.collectAsState()
    val isDownloaderInstalled by viewModel.isTorrentServiceInstalled.collectAsState()

    val animeItems = remember(items) { items.filter { it.watchlistItem.type == MediaType.ANIME } }
    val tvItems = remember(items) { items.filter { it.watchlistItem.type == MediaType.TV } }
    val movieItems = remember(items) { items.filter { it.watchlistItem.type == MediaType.MOVIE } }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (items.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No items found matching the selected filters.", color = Color(0xFFCAC4D0))
            }
        } else {
            WatchlistTableHeader(
                sortField = sortField,
                sortDirection = sortDirection,
                onSortToggle = { viewModel.toggleTableSort(it) },
                isDownloaderInstalled = isDownloaderInstalled
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (tvItems.isNotEmpty()) {
                    item(key = "header_tv") { WatchlistTableSectionHeader("TV Shows", tvItems.size) }
                    items(tvItems, key = { it.watchlistItem.simklId }) { item ->
                        WatchlistTableItemRow(
                            item = item,
                            viewModel = viewModel,
                            isDownloaderInstalled = isDownloaderInstalled,
                            onRowClick = { onNavigateToSeriesDetail(item.watchlistItem.simklId) }
                        )
                    }
                }
                if (animeItems.isNotEmpty()) {
                    item(key = "header_anime") { WatchlistTableSectionHeader("Anime", animeItems.size) }
                    items(animeItems, key = { it.watchlistItem.simklId }) { item ->
                        WatchlistTableItemRow(
                            item = item,
                            viewModel = viewModel,
                            isDownloaderInstalled = isDownloaderInstalled,
                            onRowClick = { onNavigateToSeriesDetail(item.watchlistItem.simklId) }
                        )
                    }
                }
                if (movieItems.isNotEmpty()) {
                    item(key = "header_movies") { WatchlistTableSectionHeader("Movies", movieItems.size) }
                    items(movieItems, key = { it.watchlistItem.simklId }) { item ->
                        WatchlistTableItemRow(
                            item = item,
                            viewModel = viewModel,
                            isDownloaderInstalled = isDownloaderInstalled,
                            onRowClick = { onNavigateToSeriesDetail(item.watchlistItem.simklId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WatchlistTableHeader(
    sortField: com.felixbrucker.simklcalendar.ui.viewmodel.TableSortField,
    sortDirection: com.felixbrucker.simklcalendar.ui.viewmodel.SortDirection,
    onSortToggle: (com.felixbrucker.simklcalendar.ui.viewmodel.TableSortField) -> Unit,
    isDownloaderInstalled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(16.dp)) // Indicator space

        SortableHeaderItem("Name", com.felixbrucker.simklcalendar.ui.viewmodel.TableSortField.NAME, sortField, sortDirection, onSortToggle, Modifier.weight(TableWeights.NAME))
        SortableHeaderItem("Last Ep", com.felixbrucker.simklcalendar.ui.viewmodel.TableSortField.LAST_EP, sortField, sortDirection, onSortToggle, Modifier.weight(TableWeights.LAST_EP))
        SortableHeaderItem("Next Ep", com.felixbrucker.simklcalendar.ui.viewmodel.TableSortField.NEXT_EP, sortField, sortDirection, onSortToggle, Modifier.weight(TableWeights.NEXT_EP))
        SortableHeaderItem("Watched", com.felixbrucker.simklcalendar.ui.viewmodel.TableSortField.WATCHED, sortField, sortDirection, onSortToggle, Modifier.weight(TableWeights.WATCHED))
        if (isDownloaderInstalled) {
            SortableHeaderItem("Downloaded", com.felixbrucker.simklcalendar.ui.viewmodel.TableSortField.DOWNLOADED, sortField, sortDirection, onSortToggle, Modifier.weight(TableWeights.DOWNLOADED))
        }
        Spacer(modifier = Modifier.width(32.dp)) // Expand icon space
    }
}

@Composable
fun RowScope.SortableHeaderItem(
    label: String,
    field: com.felixbrucker.simklcalendar.ui.viewmodel.TableSortField,
    currentSortField: com.felixbrucker.simklcalendar.ui.viewmodel.TableSortField,
    currentDirection: com.felixbrucker.simklcalendar.ui.viewmodel.SortDirection,
    onSortToggle: (com.felixbrucker.simklcalendar.ui.viewmodel.TableSortField) -> Unit,
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
            color = if (isSelected) Color(0xFFD0BCFF) else Color(0xFFCAC4D0),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isSelected) {
            Icon(
                imageVector = if (currentDirection == com.felixbrucker.simklcalendar.ui.viewmodel.SortDirection.ASCENDING) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = Color(0xFFD0BCFF),
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
            color = Color(0xFFD0BCFF).copy(alpha = 0.8f)
        )
        HorizontalDivider(color = Color(0xFF49454F).copy(alpha = 0.5f), thickness = 0.5.dp)
    }
}

@Composable
fun WatchlistTableItemRow(
    item: WatchlistTableItem,
    viewModel: CalendarViewModel,
    isDownloaderInstalled: Boolean,
    onRowClick: () -> Unit
) {
    val categoryColor = when (item.watchlistItem.type) {
        MediaType.ANIME -> Color(0xFFD0BCFF)
        MediaType.MOVIE -> Color(0xFFF2B8B5)
        MediaType.TV -> Color(0xFFBAC3FF)
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRowClick() }
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

                // Name
                Text(
                    text = item.watchlistItem.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(TableWeights.NAME)
                )

                // Last Ep
                Text(
                    text = item.lastAiredDate?.let { DateUtil.formatDisplayDateTime(it) } ?: "-",
                    fontSize = 12.sp,
                    color = Color(0xFFE6E1E5),
                    modifier = Modifier.weight(TableWeights.LAST_EP)
                )

                // Next Ep
                Text(
                    text = item.nextEpisodeDate?.let { DateUtil.formatDisplayDateTime(it) } ?: "-",
                    fontSize = 12.sp,
                    color = Color(0xFFD0BCFF),
                    modifier = Modifier.weight(TableWeights.NEXT_EP)
                )

                // Watched
                val watchedColor = getTableItemColor(item.watchedReleasedCount, item.totalReleasedCount)
                Text(
                    text = "${item.watchedReleasedCount}/${item.totalReleasedCount}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = watchedColor,
                    modifier = Modifier.weight(TableWeights.WATCHED)
                )

                // Downloaded
                if (isDownloaderInstalled) {
                    val downloadedColor = getTableItemColor(item.downloadedReleasedCount, item.totalDownloadableReleasedCount)
                    Text(
                        text = "${item.downloadedReleasedCount}/${item.totalDownloadableReleasedCount}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = downloadedColor,
                        modifier = Modifier.weight(TableWeights.DOWNLOADED)
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFFCAC4D0),
                    modifier = Modifier.size(24.dp).padding(end = 8.dp)
                )
            }
        }
    }
}
