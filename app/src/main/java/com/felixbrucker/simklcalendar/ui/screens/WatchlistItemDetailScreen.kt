package com.felixbrucker.simklcalendar.ui.screens

import android.Manifest
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
import com.felixbrucker.simklcalendar.extensions.defaultDestinationSubdirectory
import com.felixbrucker.simklcalendar.ui.composable.CustomSearchLinksCard
import com.felixbrucker.simklcalendar.ui.composable.DetailHeader
import com.felixbrucker.simklcalendar.ui.composable.DownloadSettingsCard
import com.felixbrucker.simklcalendar.ui.composable.ItemMediaStatusDropdown
import com.felixbrucker.simklcalendar.ui.composable.ItemWatchedStatusDropdown
import com.felixbrucker.simklcalendar.ui.composable.MediaStatusDropdown
import com.felixbrucker.simklcalendar.ui.composable.NotificationSettingsCard
import com.felixbrucker.simklcalendar.ui.viewmodel.CalendarViewModel
import com.felixbrucker.simklcalendar.ui.viewmodel.WatchlistTableItem
import com.felixbrucker.simklcalendar.ui.composable.Table
import com.felixbrucker.simklcalendar.ui.composable.WatchedStatusDropdown
import com.felixbrucker.simklcalendar.ui.composable.getTableItemColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistItemDetailScreen(
    viewModel: CalendarViewModel,
    simklId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToEpisode: (String) -> Unit
) {
    val allCalendarItems by viewModel.allCalendarItems.collectAsState()
    val watchlistItems by viewModel.watchlistItems.collectAsState(initial = emptyList())
    val tableItems by viewModel.watchlistTableItems.collectAsState()
    val updatingWatchKeys by viewModel.updatingWatchStatusKeys.collectAsState()
    val torrentDownloads by viewModel.torrentDownloads.collectAsState()
    val customSearchLinks by viewModel.customSearchLinks.collectAsState()
    val autoDownloadPrefs by viewModel.autoDownloadPreferences.collectAsState()
    val isDownloaderInstalled by viewModel.isTorrentServiceInstalled.collectAsState()
    val availableSubdirectories by viewModel.downloadSubdirectories.collectAsState()
    val itemSettings by viewModel.getItemDownloadSettingsFlow(simklId).collectAsState(null)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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

    val notificationPrefs by viewModel.notificationPreferences.collectAsState()
    val defaultAiring = notificationPrefs.defaultNotifyAiring
    val defaultSeasonFinished = notificationPrefs.defaultNotifySeasonFinished
    val defaultMovieTheater = notificationPrefs.defaultNotifyMovieTheater
    val defaultMovieDigital = notificationPrefs.defaultNotifyMovieDigital

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
        containerColor = Color(0xFF1C1B1F),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            episodes = episodes,
                            isAnimeSeasonOneOnly = isAnimeSeasonOneOnly,
                            updatingWatchKeys = updatingWatchKeys,
                            mediaType = watchlistItem.type,
                            onMarkSeasonWatched = { season -> viewModel.markSeasonWatched(simklId, season, watchlistItem.type) },
                            onMarkSeasonUnwatched = { season -> viewModel.markSeasonUnwatched(simklId, season, watchlistItem.type) },
                            onUpdateSeasonMediaStatus = { season, status -> viewModel.updateSeasonMediaStatus(simklId, season, status) }
                        )
                    }
                } else {
                    // Movie shared actions
                    item {
                        val digitalRelease = episodes.find { it.movieReleaseType == MovieReleaseType.DIGITAL }
                        val digitalOrTheaterRelease = digitalRelease ?: episodes.firstOrNull()
                        if (digitalOrTheaterRelease != null) {
                            MovieSharedActionsCard(
                                digitalOrTheaterRelease = digitalOrTheaterRelease,
                                digitalRelease = digitalRelease,
                                updatingWatchKeys = updatingWatchKeys,
                                onWatchedStatusChange = { rel, watched ->
                                    if (watched) {
                                        viewModel.markMovieWatched(rel.simklId, rel.primaryKey, rel.title) { _, _ -> }
                                    } else {
                                        viewModel.markMovieUnwatched(rel.simklId, rel.primaryKey, rel.title) { _, _ -> }
                                    }
                                },
                                onMediaStatusChange = { itemKeyToUpdate, newStatus ->
                                    viewModel.updateMediaStatus(itemKeyToUpdate, newStatus)
                                }
                            )
                        }
                    }
                }

                item {
                    CustomSearchLinksCard(
                        searchLinks = customSearchLinks,
                        title = watchlistItem.title,
                        titleRomaji = watchlistItem.titleRomaji,
                        itemType = watchlistItem.type,
                        season = null,
                        episode = null,
                        snackbarHostState = snackbarHostState,
                        scope = scope,
                        context = context,
                        modifier = Modifier.padding(16.dp),
                    )
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
                                            episodes = seasonEpisodes,
                                            updatingWatchKeys = updatingWatchKeys,
                                            onMarkSeasonWatched = { sNum -> viewModel.markSeasonWatched(simklId, sNum, watchlistItem.type) },
                                            onMarkSeasonUnwatched = { sNum -> viewModel.markSeasonUnwatched(simklId, sNum, watchlistItem.type) },
                                            onUpdateSeasonMediaStatus = { sNum, status -> viewModel.updateSeasonMediaStatus(simklId, sNum, status) }
                                        )
                                    }

                                    EpisodesTable(
                                        episodes = seasonEpisodes,
                                        updatingWatchKeys = updatingWatchKeys,
                                        torrentDownloads = torrentDownloads,
                                        onItemWatchedStatusChange = { ep, watched ->
                                            if (watched) {
                                                viewModel.markEpisodeWatched(ep.simklId, ep.season, ep.episodeNumber ?: 1, ep.type, ep.primaryKey, ep.title) { _, _ -> }
                                            } else {
                                                viewModel.markEpisodeUnwatched(ep.simklId, ep.season, ep.episodeNumber ?: 1, ep.type, ep.primaryKey, ep.title) { _, _ -> }
                                            }
                                        },
                                        onItemMediaStatusChange = { itemKey, status ->
                                            viewModel.updateMediaStatus(itemKey, status)
                                        },
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
                        onNotifyEveryEpisodeChange = { isChecked ->
                            notifyEveryEpisode = isChecked
                            viewModel.toggleNotification(
                                simklId = simklId,
                                notifyEpisode = isChecked,
                                notifySeasonFinished = notifySeasonFinished
                            )
                        },
                        onNotifySeasonFinishedChange = { isChecked ->
                            notifySeasonFinished = isChecked
                            viewModel.toggleNotification(
                                simklId = simklId,
                                notifyEpisode = notifyEveryEpisode,
                                notifySeasonFinished = isChecked
                            )
                        },
                        checkPermission = { checkAndRequestNotificationPermission() },
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // 5. Download Settings (Moved to bottom)
                item {
                    DownloadSettingsCard(
                        simklId = watchlistItem.simklId,
                        itemTitle = watchlistItem.title,
                        mediaType = watchlistItem.type,
                        defaultSubdirectory = watchlistItem.defaultDestinationSubdirectory(),
                        autoDownloadPrefs = autoDownloadPrefs,
                        itemSettings = itemSettings,
                        isDownloaderInstalled = isDownloaderInstalled,
                        availableSubdirectories = availableSubdirectories,
                        onSaveItemDownloadSettings = { viewModel.saveItemDownloadSettings(it) },
                        modifier = Modifier.padding(16.dp),
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
fun MovieSharedActionsCard(
    digitalOrTheaterRelease: CalendarItemWithWatchlist,
    digitalRelease: CalendarItemWithWatchlist?,
    updatingWatchKeys: Set<String>,
    onWatchedStatusChange: (CalendarItemWithWatchlist, Boolean) -> Unit,
    onMediaStatusChange: (String, MediaStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(Color(0xFF2B2930), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ItemWatchedStatusDropdown(
            item = digitalOrTheaterRelease,
            onWatchedStatusChange = { watched -> onWatchedStatusChange(digitalOrTheaterRelease, watched) },
            updatingWatchKeys = updatingWatchKeys
        )

        if (digitalRelease != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ItemMediaStatusDropdown(
                    item = digitalRelease,
                    onStatusChange = { newStatus -> onMediaStatusChange(digitalRelease.primaryKey, newStatus) }
                )
            }
        }
    }
}

@Composable
fun WatchlistItemSummaryStats(
    item: WatchlistTableItem?,
    episodes: List<CalendarItemWithWatchlist>,
    isAnimeSeasonOneOnly: Boolean,
    updatingWatchKeys: Set<String>,
    mediaType: MediaType,
    onMarkSeasonWatched: (season: Int) -> Unit,
    onMarkSeasonUnwatched: (season: Int) -> Unit,
    onUpdateSeasonMediaStatus: (season: Int, status: MediaStatus) -> Unit
) {
    if (item == null) return

    val commonStatus = remember(episodes) {
        val statuses = episodes.map { it.mediaStatus }.distinct()
        if (statuses.size == 1) statuses.first() else null
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(Color(0xFF2B2930), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        val isNarrow = maxWidth < 450.dp

        if (isNarrow && isAnimeSeasonOneOnly) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SummaryStatsContent(item, isNarrow = true)

                HorizontalDivider(color = Color(0xFF49454F), thickness = 1.dp)

                SummaryDropdowns(
                    episodes = episodes,
                    updatingWatchKeys = updatingWatchKeys,
                    commonStatus = commonStatus,
                    onMarkSeasonWatched = onMarkSeasonWatched,
                    onMarkSeasonUnwatched = onMarkSeasonUnwatched,
                    onUpdateSeasonMediaStatus = onUpdateSeasonMediaStatus
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SummaryStatsContent(item, isNarrow = isNarrow)
                }

                if (isAnimeSeasonOneOnly) {
                    VerticalDivider(
                        modifier = Modifier
                            .height(32.dp)
                            .padding(horizontal = 12.dp),
                        color = Color(0xFF49454F)
                    )
                    SummaryDropdowns(
                        episodes = episodes,
                        updatingWatchKeys = updatingWatchKeys,
                        commonStatus = commonStatus,
                        onMarkSeasonWatched = onMarkSeasonWatched,
                        onMarkSeasonUnwatched = onMarkSeasonUnwatched,
                        onUpdateSeasonMediaStatus = onUpdateSeasonMediaStatus
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStatsContent(item: WatchlistTableItem, isNarrow: Boolean) {
    if (isNarrow) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StatItem(
                    "Last Ep",
                    item.lastAiredDate?.let { DateUtil.formatDisplayDateTime(it) } ?: "-"
                )
                Spacer(modifier = Modifier.height(8.dp))
                StatItem(
                    "Next Ep",
                    item.nextEpisodeDate?.let { DateUtil.formatDisplayDateTime(it) } ?: "-"
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val watchedColor = getTableItemColor(item.watchedReleasedCount, item.totalReleasedCount)
                StatItem(
                    label = "Watched",
                    value = "${item.watchedReleasedCount}/${item.totalReleasedCount}",
                    valueColor = watchedColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                val downloadedColor = getTableItemColor(
                    item.downloadedReleasedCount,
                    item.totalDownloadableReleasedCount
                )
                StatItem(
                    label = "Downloaded",
                    value = "${item.downloadedReleasedCount}/${item.totalDownloadableReleasedCount}",
                    valueColor = downloadedColor
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(
                "Last Ep",
                item.lastAiredDate?.let { DateUtil.formatDisplayDateTime(it) } ?: "-",
                modifier = Modifier.weight(1f)
            )
            StatItem(
                "Next Ep",
                item.nextEpisodeDate?.let { DateUtil.formatDisplayDateTime(it) } ?: "-",
                modifier = Modifier.weight(1f)
            )

            val watchedColor = getTableItemColor(item.watchedReleasedCount, item.totalReleasedCount)
            StatItem(
                label = "Watched",
                value = "${item.watchedReleasedCount}/${item.totalReleasedCount}",
                valueColor = watchedColor,
                modifier = Modifier.weight(1f)
            )

            val downloadedColor = getTableItemColor(
                item.downloadedReleasedCount,
                item.totalDownloadableReleasedCount
            )
            StatItem(
                label = "Downloaded",
                value = "${item.downloadedReleasedCount}/${item.totalDownloadableReleasedCount}",
                valueColor = downloadedColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryDropdowns(
    episodes: List<CalendarItemWithWatchlist>,
    updatingWatchKeys: Set<String>,
    commonStatus: MediaStatus?,
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
                    onMarkSeasonWatched(1)
                } else {
                    onMarkSeasonUnwatched(1)
                }
            },
            isLoading = isLoading
        )

        MediaStatusDropdown(
            currentStatus = commonStatus ?: MediaStatus.IGNORED,
            onStatusChange = { onUpdateSeasonMediaStatus(1, it) }
        )
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
    episodes: List<CalendarItemWithWatchlist>,
    updatingWatchKeys: Set<String>,
    onMarkSeasonWatched: (season: Int) -> Unit,
    onMarkSeasonUnwatched: (season: Int) -> Unit,
    onUpdateSeasonMediaStatus: (season: Int, status: MediaStatus) -> Unit
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
}

@Composable
fun EpisodesTable(
    episodes: List<CalendarItemWithWatchlist>,
    updatingWatchKeys: Set<String>,
    torrentDownloads: Map<String, DownloadProgress>,
    onItemWatchedStatusChange: (item: CalendarItemWithWatchlist, isWatched: Boolean) -> Unit,
    onItemMediaStatusChange: (itemKey: String, newStatus: MediaStatus) -> Unit,
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
                                onWatchedStatusChange = { isWatched -> onItemWatchedStatusChange(episode, isWatched) },
                                updatingWatchKeys = updatingWatchKeys
                            )
                            4 -> Row(verticalAlignment = Alignment.CenterVertically) {
                                ItemMediaStatusDropdown(
                                    item = episode,
                                    onStatusChange = { newStatus -> onItemMediaStatusChange(episode.primaryKey, newStatus) }
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
