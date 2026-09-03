package com.felixbrucker.simklcalendar.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.util.DateUtil
import com.felixbrucker.simklcalendar.data.util.DownloadProgress
import com.felixbrucker.simklcalendar.ui.composable.DetailHeader
import com.felixbrucker.simklcalendar.ui.composable.DownloadSettingsCard
import com.felixbrucker.simklcalendar.ui.composable.ItemMediaStatusDropdown
import com.felixbrucker.simklcalendar.ui.composable.ItemWatchedStatusDropdown
import com.felixbrucker.simklcalendar.ui.composable.MediaStatusDropdown
import com.felixbrucker.simklcalendar.ui.composable.NotificationSettingsCard
import com.felixbrucker.simklcalendar.ui.viewmodel.CalendarViewModel
import com.felixbrucker.simklcalendar.ui.viewmodel.WatchlistTableItem
import com.felixbrucker.simklcalendar.ui.composable.Table
import com.felixbrucker.simklcalendar.ui.composable.getTableItemColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WatchlistItemDetailScreen(
    viewModel: CalendarViewModel,
    simklId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToEpisode: (String) -> Unit
) {
    val allCalendarItems by viewModel.allCalendarItems.collectAsState()
    val watchlistItems by viewModel.repository.watchlistItems.collectAsState(initial = emptyList())
    val tableItems by viewModel.watchlistTableItems.collectAsState()
    val updatingWatchKeys by viewModel.updatingWatchStatusKeys.collectAsState()
    val torrentDownloads by viewModel.torrentDownloads.collectAsState()

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permission callback */ }

    fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val settingsList by viewModel.notificationSettings.collectAsState()
    val showSetting = remember(settingsList, simklId) {
        settingsList.find { it.simklId == simklId }
    }

    val watchlistItem = remember(watchlistItems, simklId) {
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

    val isMovie = watchlistItem?.type == MediaType.MOVIE
    val isAnimeSeasonOneOnly = watchlistItem?.type == MediaType.ANIME && seasons.size == 1 && seasons.containsKey(1)

    val prefs = remember { context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE) }
    val defaultAiring = prefs.getBoolean("default_notify_airing", false)
    val defaultSeasonFinished = prefs.getBoolean("default_notify_season_finished", true)
    val defaultMovieTheater = prefs.getBoolean("default_notify_movie_theater", false)
    val defaultMovieDigital = prefs.getBoolean("default_notify_movie_digital", true)

    var notifyEveryEpisode by remember(showSetting, defaultAiring, defaultMovieTheater, isMovie) {
        mutableStateOf(showSetting?.notifyEveryEpisode ?: (if (isMovie) defaultMovieTheater else defaultAiring))
    }
    var notifySeasonFinished by remember(showSetting, defaultSeasonFinished, defaultMovieDigital, isMovie) {
        mutableStateOf(showSetting?.notifyAiredLastEpisode ?: (if (isMovie) defaultMovieDigital else defaultSeasonFinished))
    }

    LaunchedEffect(showSetting) {
        if (showSetting != null) {
            notifyEveryEpisode = showSetting.notifyEveryEpisode
            notifySeasonFinished = showSetting.notifyAiredLastEpisode
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(watchlistItem?.title ?: "Details", color = Color.White) },
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
        if (watchlistItem == null) {
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
                    DetailHeader(
                        simklId = watchlistItem.simklId,
                        type = watchlistItem.type,
                        title = watchlistItem.title,
                        poster = watchlistItem.poster,
                        titleRomaji = watchlistItem.titleRomaji
                    )
                }

                // 2. Summary Stats Bar (Hidden for movies)
                if (!isMovie) {
                    item {
                        WatchlistItemSummaryStats(
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
                        val digitalRelease = episodes.find { it.movieReleaseType == MovieReleaseType.DIGITAL }
                        val digitalOrTheaterRelease = digitalRelease ?: episodes.firstOrNull()
                        if (digitalOrTheaterRelease != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .background(Color(0xFF2B2930), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ItemWatchedStatusDropdown(
                                    item = digitalOrTheaterRelease,
                                    viewModel = viewModel,
                                    updatingWatchKeys = updatingWatchKeys
                                )

                                if (digitalRelease != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        ItemMediaStatusDropdown(
                                            item = digitalRelease,
                                            viewModel = viewModel
                                        )
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

                // 4. Notification Settings
                item {
                    NotificationSettingsCard(
                        simklId = simklId,
                        isMovie = isMovie,
                        notifyEveryEpisode = notifyEveryEpisode,
                        notifySeasonFinished = notifySeasonFinished,
                        onNotifyEveryEpisodeChange = { notifyEveryEpisode = it },
                        onNotifySeasonFinishedChange = { notifySeasonFinished = it },
                        checkPermission = { checkAndRequestNotificationPermission() },
                        viewModel = viewModel,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // 5. Download Settings (Moved to bottom)
                item {
                    DownloadSettingsCard(
                        viewModel = viewModel,
                        simklId = watchlistItem.simklId,
                        itemTitle = watchlistItem.title,
                        isMovie = isMovie
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
fun WatchlistItemSummaryStats(
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

                val watchedColor =
                    getTableItemColor(item.watchedReleasedCount, item.totalReleasedCount)
                StatItem(
                    label = "Watched",
                    value = "${item.watchedReleasedCount}/${item.totalReleasedCount}",
                    valueColor = watchedColor,
                    modifier = Modifier.weight(0.8f)
                )

                val downloadedColor = getTableItemColor(
                    item.downloadedReleasedCount,
                    item.totalDownloadableReleasedCount
                )
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                MediaStatusDropdown(
                    currentStatus = commonStatus ?: MediaStatus.IGNORED,
                    onStatusChange = { viewModel.updateSeasonMediaStatus(simklId, 1, it) }
                )
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.White) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 11.sp, color = Color(0xFFCAC4D0), fontWeight = FontWeight.Bold)
        Text(text = value, fontSize = 13.sp, color = valueColor, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center)
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            MediaStatusDropdown(
                currentStatus = commonStatus ?: MediaStatus.IGNORED,
                onStatusChange = { viewModel.updateSeasonMediaStatus(simklId, season, it) }
            )
        }
    }
}

@Composable
fun EpisodesTable(
    episodes: List<CalendarItemWithWatchlist>,
    viewModel: CalendarViewModel,
    updatingWatchKeys: Set<String>,
    torrentDownloads: Map<String, DownloadProgress>,
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
                            3 -> ItemWatchedStatusDropdown(
                                item = episode,
                                viewModel = viewModel,
                                updatingWatchKeys = updatingWatchKeys
                            )
                            4 -> Row(verticalAlignment = Alignment.CenterVertically) {
                                ItemMediaStatusDropdown(
                                    item = episode,
                                    viewModel = viewModel
                                )
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
    torrentDownloads: Map<String, DownloadProgress>,
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
                val context = LocalContext.current
                val downloadProgress = torrentDownloads[release.downloadTaskId]

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
