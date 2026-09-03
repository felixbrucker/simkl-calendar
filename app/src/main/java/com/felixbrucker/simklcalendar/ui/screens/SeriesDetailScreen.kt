package com.felixbrucker.simklcalendar.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.util.DateUtil
import com.felixbrucker.simklcalendar.data.util.PosterSize
import com.felixbrucker.simklcalendar.data.util.toPosterUrl
import com.felixbrucker.simklcalendar.data.util.formattedEpisodeCode
import com.felixbrucker.simklcalendar.ui.viewmodel.CalendarViewModel
import com.felixbrucker.simklcalendar.ui.viewmodel.WatchlistTableItem
import com.felixbrucker.simklcalendar.ui.composable.Table
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SeriesDetailScreen(
    viewModel: CalendarViewModel,
    simklId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToEpisode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allCalendarItems by viewModel.allCalendarItems.collectAsState()
    val watchlistItems by viewModel.repository.watchlistItems.collectAsState(initial = emptyList())
    val tableItems by viewModel.watchlistTableItems.collectAsState()
    val updatingWatchKeys by viewModel.updatingWatchStatusKeys.collectAsState()
    val torrentDownloads by viewModel.torrentDownloads.collectAsState()

    val seriesItem = remember(watchlistItems, simklId) {
        watchlistItems.find { it.simklId == simklId }
    }

    val tableItem = remember(tableItems, simklId) {
        tableItems.find { it.watchlistItem.simklId == simklId }
    }

    val episodes = remember(allCalendarItems, simklId) {
        allCalendarItems.filter { it.simklId == simklId }
            .sortedWith(compareBy<CalendarItemWithWatchlist> { it.season ?: 0 }
                .thenBy { it.episodeNumber ?: 0 }
                .thenBy { it.date })
    }

    val seasons = remember(episodes) {
        episodes.groupBy { it.season ?: 1 }
    }

    val isMovie = seriesItem?.type == MediaType.MOVIE
    val isAnimeSeasonOneOnly = seriesItem?.type == MediaType.ANIME && seasons.size == 1 && seasons.containsKey(1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(seriesItem?.title ?: "Details", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1C1B1F))
            )
        },
        containerColor = Color(0xFF1C1B1F)
    ) { innerPadding ->
        if (seriesItem == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // 1. Header with Poster & Title
                item {
                    SeriesHeader(seriesItem)
                }

                // 2. Summary Stats Bar (Hidden for movies)
                if (!isMovie) {
                    item {
                        SeriesSummaryStats(
                            item = tableItem,
                            viewModel = viewModel,
                            simklId = simklId,
                            episodes = episodes,
                            isAnimeSeasonOneOnly = isAnimeSeasonOneOnly
                        )
                    }
                } else {
                    // Movie shared actions
                    item {
                        val digitalRelease = episodes.find { it.movieReleaseType != MovieReleaseType.THEATER } ?: episodes.firstOrNull()
                        val mainKey = digitalRelease?.primaryKey ?: seriesItem.simklId.toString()

                        if (digitalRelease != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .background(Color(0xFF2B2930), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                WatchedStatusDropdown(
                                    isWatched = digitalRelease.isWatched,
                                    onStatusChange = { watched ->
                                        if (watched) {
                                            viewModel.markMovieWatched(digitalRelease.simklId, digitalRelease.primaryKey, digitalRelease.title) { _, _ -> }
                                        } else {
                                            viewModel.markMovieUnwatched(digitalRelease.simklId, digitalRelease.primaryKey, digitalRelease.title) { _, _ -> }
                                        }
                                    },
                                    isLoading = updatingWatchKeys.contains(digitalRelease.primaryKey)
                                )

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    MediaStatusDropdown(
                                        currentStatus = digitalRelease.mediaStatus,
                                        onStatusChange = { viewModel.updateMediaStatus(digitalRelease.primaryKey, it) }
                                    )

                                    if (digitalRelease.date.isBefore(java.time.Instant.now())) {
                                        IconButton(
                                            onClick = {
                                                viewModel.searchAndDownloadEpisode(digitalRelease) { success, message ->
                                                    if (!success) {
                                                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFFD0BCFF), modifier = Modifier.size(22.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Content Card (Episode list or Movie Releases)
                item {
                    Card(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                        border = BorderStroke(1.dp, Color(0xFF49454F))
                    ) {
                        Column {
                            if (isMovie) {
                                Box(modifier = Modifier.padding(16.dp)) {
                                    Text("Release Information", fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF), fontSize = 16.sp)
                                }
                                MovieReleasesTable(
                                    releases = episodes,
                                    torrentDownloads = torrentDownloads,
                                    onNavigateToEpisode = onNavigateToEpisode
                                )
                            } else {
                                val showSeasonHeaders = !isAnimeSeasonOneOnly
                                val sortedSeasons = seasons.keys.sorted()
                                sortedSeasons.forEach { season ->
                                    val seasonEpisodes = seasons[season] ?: emptyList()
                                    if (showSeasonHeaders) {
                                        SeasonSectionHeader(
                                            season = season,
                                            count = seasonEpisodes.size,
                                            viewModel = viewModel,
                                            simklId = simklId,
                                            episodes = seasonEpisodes
                                        )
                                    }

                                    EpisodesTable(
                                        episodes = seasonEpisodes,
                                        viewModel = viewModel,
                                        updatingWatchKeys = updatingWatchKeys,
                                        torrentDownloads = torrentDownloads,
                                        onNavigateToEpisode = onNavigateToEpisode
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Download Settings (Moved to bottom)
                item {
                    SeriesDownloadSettings(
                        viewModel = viewModel,
                        simklId = seriesItem.simklId,
                        showTitle = seriesItem.title,
                        isMovie = isMovie,
                        availableSeasons = seasons.keys.sorted()
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun SeriesHeader(seriesItem: com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
    ) {
        AsyncImage(
            model = seriesItem.poster.toPosterUrl(PosterSize.WIDE),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF1C1B1F)),
                        startY = 100f
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            val categoryColor = when (seriesItem.type) {
                MediaType.ANIME -> Color(0xFFD0BCFF)
                MediaType.MOVIE -> Color(0xFFF2B8B5)
                MediaType.TV -> Color(0xFFBAC3FF)
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = categoryColor.copy(alpha = 0.2f),
                contentColor = categoryColor
            ) {
                Text(
                    text = seriesItem.type.displayName.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = seriesItem.title,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                lineHeight = 34.sp
            )

            if (!seriesItem.titleRomaji.isNullOrBlank()) {
                Text(
                    text = seriesItem.titleRomaji,
                    fontSize = 16.sp,
                    color = Color(0xFFCAC4D0),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun SeriesSummaryStats(
    item: WatchlistTableItem?,
    viewModel: CalendarViewModel,
    simklId: Int,
    episodes: List<CalendarItemWithWatchlist>,
    isAnimeSeasonOneOnly: Boolean
) {
    if (item == null) return

    val commonStatus = remember(episodes) {
        val statuses = episodes.map { it.mediaStatus }.distinct()
        if (statuses.size == 1) statuses.first() else null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(Color(0xFF2B2930), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                StatItem("Last Ep", item.lastAiredDate?.let { DateUtil.formatDisplayDateTime(it) } ?: "-", modifier = Modifier.weight(1f))
                StatItem("Next Ep", item.nextEpisodeDate?.let { DateUtil.formatDisplayDateTime(it) } ?: "-", modifier = Modifier.weight(1f))
                
                val watchedColor = getTableItemColor(item.watchedReleasedCount, item.totalReleasedCount)
                StatItem(
                    label = "Watched", 
                    value = "${item.watchedReleasedCount}/${item.totalReleasedCount}", 
                    valueColor = watchedColor,
                    modifier = Modifier.weight(0.8f)
                )
                
                val downloadedColor = getTableItemColor(item.downloadedReleasedCount, item.totalDownloadableReleasedCount)
                StatItem(
                    label = "Downloaded", 
                    value = "${item.downloadedReleasedCount}/${item.totalDownloadableReleasedCount}", 
                    valueColor = downloadedColor,
                    modifier = Modifier.weight(0.8f)
                )
            }
        }

        if (isAnimeSeasonOneOnly) {
            VerticalDivider(modifier = Modifier.height(32.dp).padding(horizontal = 12.dp), color = Color(0xFF49454F))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MediaStatusDropdown(
                    currentStatus = commonStatus ?: MediaStatus.IGNORED,
                    onStatusChange = { viewModel.updateSeasonMediaStatus(simklId, 1, it) }
                )

                IconButton(
                    onClick = { viewModel.searchAndDownloadSeason(simklId, 1) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search All", tint = Color(0xFFD0BCFF), modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, valueColor: Color = Color.White, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 11.sp, color = Color(0xFFCAC4D0), fontWeight = FontWeight.Bold)
        Text(text = value, fontSize = 13.sp, color = valueColor, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center)
    }
}

@Composable
fun SeriesDownloadSettings(
    viewModel: CalendarViewModel,
    simklId: Int,
    showTitle: String,
    isMovie: Boolean,
    availableSeasons: List<Int>
) {
    val globalUnwatched by viewModel.autoDownloadUnwatchedDefault.collectAsState()
    val globalQuality by viewModel.autoDownloadQuality.collectAsState()
    val globalPreferHevc by viewModel.autoDownloadPreferHevc.collectAsState()
    val itemSettings by viewModel.getItemDownloadSettingsFlow(simklId).collectAsState(null)
    val isDownloaderInstalled by viewModel.isTorrentServiceInstalled.collectAsState()

    Card(
        modifier = Modifier.padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
        border = BorderStroke(1.dp, Color(0xFF49454F))
    ) {
        Column(modifier = Modifier.padding(16.dp).alpha(if (isDownloaderInstalled) 1f else 0.5f)) {
            Text("Automatic Downloads", fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5), fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))

            // Unwatched Toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Download Unwatched", color = Color(0xFFE6E1E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Auto search and add unwatched episodes.", color = Color(0xFFCAC4D0), fontSize = 11.sp)
                }
                Switch(
                    checked = itemSettings?.downloadUnwatched ?: globalUnwatched,
                    onCheckedChange = {
                        viewModel.saveItemDownloadSettings((itemSettings ?: ItemDownloadSettings(simklId)).copy(downloadUnwatched = it))
                    },
                    enabled = isDownloaderInstalled
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quality
            Text("Preferred Quality", color = Color(0xFFE6E1E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("4K", "1080p", "720p").forEach { quality ->
                    val isSelected = (itemSettings?.qualityOverride == quality) || (itemSettings?.qualityOverride == null && globalQuality == quality)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val next = if (isSelected && itemSettings?.qualityOverride != null) null else quality
                            viewModel.saveItemDownloadSettings((itemSettings ?: ItemDownloadSettings(simklId)).copy(qualityOverride = next))
                        },
                        label = { Text(quality) },
                        enabled = isDownloaderInstalled
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // HEVC
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Prefer HEVC / x265", color = Color(0xFFE6E1E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Switch(
                    checked = itemSettings?.preferHevcOverride ?: globalPreferHevc,
                    onCheckedChange = {
                        viewModel.saveItemDownloadSettings((itemSettings ?: ItemDownloadSettings(simklId)).copy(preferHevcOverride = it))
                    },
                    enabled = isDownloaderInstalled
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title Override
            var showTitleDialog by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Title Override", color = Color(0xFFE6E1E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Use custom search title for this item.", color = Color(0xFFCAC4D0), fontSize = 11.sp)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1C1B1F),
                    border = BorderStroke(1.dp, Color(0xFF49454F)),
                    modifier = Modifier.clickable(enabled = isDownloaderInstalled) { showTitleDialog = true }
                ) {
                    Text(
                        text = itemSettings?.titleOverride ?: "None",
                        color = if (itemSettings?.titleOverride != null) Color(0xFFD0BCFF) else Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            if (showTitleDialog) {
                var tempTitle by remember { mutableStateOf(itemSettings?.titleOverride ?: "") }
                AlertDialog(
                    onDismissRequest = { showTitleDialog = false },
                    title = { Text("Title Override") },
                    text = {
                        OutlinedTextField(
                            value = tempTitle,
                            onValueChange = { tempTitle = it },
                            label = { Text("Custom Title") },
                            placeholder = { Text(showTitle) },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.saveItemDownloadSettings((itemSettings ?: ItemDownloadSettings(simklId)).copy(titleOverride = tempTitle.trim().takeIf { it.isNotBlank() }))
                            showTitleDialog = false
                        }) { Text("Save") }
                    },
                    dismissButton = { TextButton(onClick = { showTitleDialog = false }) { Text("Cancel") } }
                )
            }

            // Season Overrides
            if (!isMovie) {
                Spacer(modifier = Modifier.height(16.dp))
                var showSeasonDialog by remember { mutableStateOf(false) }
                val seasonOverrides = itemSettings?.seasonOverrides ?: emptyMap()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Season Overrides", color = Color(0xFFE6E1E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Map seasons for torrent searching.", color = Color(0xFFCAC4D0), fontSize = 11.sp)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1C1B1F),
                        border = BorderStroke(1.dp, Color(0xFF49454F)),
                        modifier = Modifier.clickable(enabled = isDownloaderInstalled) { showSeasonDialog = true }
                    ) {
                        Text(
                            text = if (seasonOverrides.isEmpty()) "None" else "${seasonOverrides.size} active",
                            color = if (seasonOverrides.isNotEmpty()) Color(0xFFD0BCFF) else Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                if (showSeasonDialog) {
                    SeasonOverrideDialog(
                        simklId = simklId,
                        availableSeasons = availableSeasons,
                        existingOverrides = seasonOverrides,
                        onSave = {
                            viewModel.saveItemDownloadSettings((itemSettings ?: ItemDownloadSettings(simklId)).copy(seasonOverrides = it))
                        },
                        onDismiss = { showSeasonDialog = false }
                    )
                }
            }
        }
    }
}

@Composable
fun SeasonSectionHeader(
    season: Int,
    count: Int,
    viewModel: CalendarViewModel,
    simklId: Int,
    episodes: List<CalendarItemWithWatchlist>
) {
    val commonStatus = remember(episodes) {
        val statuses = episodes.map { it.mediaStatus }.distinct()
        if (statuses.size == 1) statuses.first() else null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1B1F).copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Season $season ($count)",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD0BCFF)
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MediaStatusDropdown(
                currentStatus = commonStatus ?: MediaStatus.IGNORED,
                onStatusChange = { viewModel.updateSeasonMediaStatus(simklId, season, it) }
            )

            IconButton(
                onClick = { viewModel.searchAndDownloadSeason(simklId, season) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search Season",
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFFD0BCFF)
                )
            }
        }
    }
}

@Composable
fun EpisodesTable(
    episodes: List<CalendarItemWithWatchlist>,
    viewModel: CalendarViewModel,
    updatingWatchKeys: Set<String>,
    torrentDownloads: Map<String, com.felixbrucker.simklcalendar.data.util.DownloadProgress>,
    onNavigateToEpisode: (String) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val minTableWidth = maxWidth
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Table(
                rows = episodes.size + 1,
                columns = 5,
                columnWeightIndex = 1,
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .widthIn(min = minTableWidth)
                    .padding(bottom = 8.dp)
            ) { row, column ->
                if (row == 0) {
                    // Header
                    val label = when (column) {
                        0 -> "Ep"
                        1 -> "Title"
                        2 -> "Air Date"
                        3 -> "Watched"
                        4 -> "Status"
                        else -> ""
                    }
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCAC4D0),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else {
                    // Row
                    val episode = episodes[row - 1]
                    val context = LocalContext.current
                    val downloadProgress = torrentDownloads[episode.downloadTaskId]

                    Box(
                        modifier = Modifier
                            .clickable { onNavigateToEpisode(episode.primaryKey) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        when (column) {
                            0 -> Text(text = episode.episodeNumber?.toString() ?: "-", fontSize = 13.sp, color = Color.White)
                            1 -> Column {
                                Text(text = episode.episodeTitle ?: "TBA", fontSize = 13.sp, color = Color.White, maxLines = 1, fontWeight = FontWeight.Medium)
                                if (episode.mediaStatus == MediaStatus.DOWNLOADING && downloadProgress != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val progress = if (downloadProgress.totalBytes > 0) downloadProgress.bytesDownloaded.toFloat() / downloadProgress.totalBytes else 0f
                                    val percentage = (progress * 100).toInt()
                                    val speedStr = android.text.format.Formatter.formatFileSize(context, downloadProgress.downloadSpeed.toLong()) + "/s"
                                    val downloaded = android.text.format.Formatter.formatFileSize(context, downloadProgress.bytesDownloaded)
                                    val total = android.text.format.Formatter.formatFileSize(context, downloadProgress.totalBytes)

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier.width(60.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                                color = Color(0xFFD0BCFF),
                                                trackColor = Color(0xFF381E72)
                                            )
                                            Text(text = "$percentage%", fontSize = 9.sp, color = Color(0xFFCAC4D0), fontWeight = FontWeight.Bold)
                                            Text(text = "$downloaded / $total", fontSize = 8.sp, color = Color(0xFF938F99))
                                        }

                                        if (downloadProgress.downloadSpeed > 0) {
                                            val remainingBytes = downloadProgress.totalBytes - downloadProgress.bytesDownloaded
                                            val remainingSeconds = (remainingBytes / downloadProgress.downloadSpeed).toLong()
                                            val eta = DateUtil.formatDuration(remainingSeconds)
                                            Text(text = "$speedStr • ETA: $eta", fontSize = 9.sp, color = Color(0xFFCAC4D0))
                                        }
                                    }
                                }
                            }
                            2 -> Text(text = DateUtil.formatDisplayDateTime(episode.date), fontSize = 12.sp, color = Color(0xFFCAC4D0), maxLines = 1)
                            3 -> WatchedStatusDropdown(
                                isWatched = episode.isWatched,
                                onStatusChange = { watched ->
                                    if (watched) {
                                        viewModel.markEpisodeWatched(episode.simklId, episode.season, episode.episodeNumber ?: 1, episode.type, episode.primaryKey, episode.title) { _, _ -> }
                                    } else {
                                        viewModel.markEpisodeUnwatched(episode.simklId, episode.season, episode.episodeNumber ?: 1, episode.type, episode.primaryKey, episode.title) { _, _ -> }
                                    }
                                },
                                isLoading = updatingWatchKeys.contains(episode.primaryKey)
                            )
                            4 -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MediaStatusDropdown(
                                    currentStatus = episode.mediaStatus,
                                    onStatusChange = { viewModel.updateMediaStatus(episode.primaryKey, it) }
                                )
                                if (episode.date.isBefore(java.time.Instant.now())) {
                                    IconButton(
                                        onClick = {
                                            viewModel.searchAndDownloadEpisode(episode) { success, message ->
                                                if (!success) {
                                                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp), tint = Color(0xFFD0BCFF))
                                    }
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
fun MovieReleasesTable(
    releases: List<CalendarItemWithWatchlist>,
    torrentDownloads: Map<String, com.felixbrucker.simklcalendar.data.util.DownloadProgress>,
    onNavigateToEpisode: (String) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val minTableWidth = maxWidth
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Table(
                rows = releases.size,
                columns = 1,
                columnWeightIndex = 0,
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .widthIn(min = minTableWidth)
                    .padding(bottom = 8.dp)
            ) { row, _ ->
                val release = releases[row]
                val downloadProgress = torrentDownloads[release.downloadTaskId]
                val context = LocalContext.current

                Box(
                    modifier = Modifier
                        .clickable { onNavigateToEpisode(release.primaryKey) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(text = release.movieReleaseType?.displayName ?: "Release", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                        Text(text = DateUtil.formatDisplayDateTime(release.date), fontSize = 12.sp, color = Color(0xFFCAC4D0), maxLines = 1)

                        if (release.mediaStatus == MediaStatus.DOWNLOADING && downloadProgress != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val progress = if (downloadProgress.totalBytes > 0) downloadProgress.bytesDownloaded.toFloat() / downloadProgress.totalBytes else 0f
                            val percentage = (progress * 100).toInt()
                            val speedStr = android.text.format.Formatter.formatFileSize(context, downloadProgress.downloadSpeed.toLong()) + "/s"
                            val downloaded = android.text.format.Formatter.formatFileSize(context, downloadProgress.bytesDownloaded)
                            val total = android.text.format.Formatter.formatFileSize(context, downloadProgress.totalBytes)

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.width(100.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = Color(0xFFD0BCFF),
                                        trackColor = Color(0xFF381E72)
                                    )
                                    Text(text = "$percentage%", fontSize = 11.sp, color = Color(0xFFCAC4D0), fontWeight = FontWeight.Bold)
                                    Text(text = "$downloaded / $total", fontSize = 10.sp, color = Color(0xFF938F99))
                                }

                                if (downloadProgress.downloadSpeed > 0) {
                                    val remainingBytes = downloadProgress.totalBytes - downloadProgress.bytesDownloaded
                                    val remainingSeconds = (remainingBytes / downloadProgress.downloadSpeed).toLong()
                                    val eta = DateUtil.formatDuration(remainingSeconds)
                                    Text(text = "$speedStr • ETA: $eta", fontSize = 11.sp, color = Color(0xFFCAC4D0))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
