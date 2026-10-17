package com.felixbrucker.simklcalendar.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.util.DateUtil
import com.felixbrucker.simklcalendar.extensions.defaultDestinationSubdirectory
import com.felixbrucker.simklcalendar.ui.composable.CustomSearchLinksCard
import com.felixbrucker.simklcalendar.ui.composable.DetailHeader
import com.felixbrucker.simklcalendar.ui.composable.DetailHeaderData
import com.felixbrucker.simklcalendar.ui.composable.DownloadSettingsCard
import com.felixbrucker.simklcalendar.ui.composable.EpisodeListActions
import com.felixbrucker.simklcalendar.ui.composable.EpisodeListCard
import com.felixbrucker.simklcalendar.ui.composable.EpisodeListState
import com.felixbrucker.simklcalendar.ui.composable.ItemMediaStatusDropdown
import com.felixbrucker.simklcalendar.ui.composable.ItemWatchedStatusDropdown
import com.felixbrucker.simklcalendar.ui.composable.MediaStatusDropdown
import com.felixbrucker.simklcalendar.ui.composable.NotificationSettingsCard
import com.felixbrucker.simklcalendar.ui.composable.WatchedStatusDropdown
import com.felixbrucker.simklcalendar.ui.composable.getTableItemColor
import com.felixbrucker.simklcalendar.ui.theme.OutlineVariant
import com.felixbrucker.simklcalendar.ui.theme.SurfaceContainer
import com.felixbrucker.simklcalendar.ui.theme.SurfaceDark
import com.felixbrucker.simklcalendar.ui.theme.TextSubtle
import com.felixbrucker.simklcalendar.ui.viewmodel.CalendarViewModel
import com.felixbrucker.simklcalendar.ui.viewmodel.WatchlistTableItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistItemDetailScreen(
    viewModel: CalendarViewModel,
    simklId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToEpisode: (String) -> Unit
) {
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

    val setting by viewModel.getSettingForSimklIdFlow(simklId).collectAsState(null)
    val notificationSetting = setting // Required for smart casting

    val watchlistItemDelegate by viewModel.getWatchlistItemFlow(simklId).collectAsState(null)
    val watchlistItem = watchlistItemDelegate // Required for smart casting

    val tableItem = remember(tableItems, simklId) {
        tableItems.find { it.watchlistItem.simklId == simklId }
    }

    val episodes by viewModel
        .getSortedCalendarItemsForSimklIdFlow(simklId)
        .collectAsState(emptyList())

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

    var notifyEveryEpisode by remember(notificationSetting, defaultAiring, defaultMovieTheater, isMovie) {
        mutableStateOf(notificationSetting?.notifyEveryEpisode
            ?: (if (isMovie) defaultMovieTheater else defaultAiring))
    }
    var notifySeasonFinished by remember(notificationSetting, defaultSeasonFinished, defaultMovieDigital, isMovie) {
        mutableStateOf(notificationSetting?.notifyAiredLastEpisode
            ?: (if (isMovie) defaultMovieDigital else defaultSeasonFinished))
    }

    LaunchedEffect(notificationSetting) {
        if (notificationSetting != null) {
            notifyEveryEpisode = notificationSetting.notifyEveryEpisode
            notifySeasonFinished = notificationSetting.notifyAiredLastEpisode
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = SurfaceDark,
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
                        data = DetailHeaderData(
                            simklId = watchlistItem.simklId,
                            type = watchlistItem.type,
                            title = watchlistItem.title,
                            poster = watchlistItem.poster,
                            rating = watchlistItem.rating,
                            titleRomaji = watchlistItem.titleRomaji,
                            hasUniqueTitleRomaji = watchlistItem.hasUniqueTitleRomaji,
                            animeSeason = watchlistItem.animeSeason
                        )
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
                    val density = LocalDensity.current
                    val windowInfo = LocalWindowInfo.current
                    val isSmallScreen = with(density) { windowInfo.containerSize.width.toDp() } < 600.dp

                    EpisodeListCard(
                        state = EpisodeListState(
                            items = episodes,
                            mediaType = watchlistItem.type,
                            isSmallScreen = isSmallScreen,
                            updatingWatchKeys = updatingWatchKeys,
                            torrentDownloads = torrentDownloads,
                            showSeasonHeaders = !isAnimeSeasonOneOnly,
                            cardTitle = if (isMovie) "Release Information" else null
                        ),
                        actions = EpisodeListActions(
                            onItemClick = { ep -> onNavigateToEpisode(ep.primaryKey) },
                            onWatchedStatusChange = { ep, watched ->
                                if (ep.type == MediaType.MOVIE) {
                                    if (watched) {
                                        viewModel.markMovieWatched(ep.simklId, ep.primaryKey, ep.title) { _, _ -> }
                                    } else {
                                        viewModel.markMovieUnwatched(ep.simklId, ep.primaryKey, ep.title) { _, _ -> }
                                    }
                                } else {
                                    if (watched) {
                                        viewModel.markEpisodeWatched(ep.simklId, ep.season, ep.episodeNumber ?: 1, ep.type, ep.primaryKey, ep.title) { _, _ -> }
                                    } else {
                                        viewModel.markEpisodeUnwatched(ep.simklId, ep.season, ep.episodeNumber ?: 1, ep.type, ep.primaryKey, ep.title) { _, _ -> }
                                    }
                                }
                            },
                            onMediaStatusChange = { itemKey, status ->
                                viewModel.updateMediaStatus(itemKey, status)
                            },
                            onMarkSeasonWatched = { sNum -> viewModel.markSeasonWatched(simklId, sNum, watchlistItem.type) },
                            onMarkSeasonUnwatched = { sNum -> viewModel.markSeasonUnwatched(simklId, sNum, watchlistItem.type) },
                            onUpdateSeasonMediaStatus = { sNum, status -> viewModel.updateSeasonMediaStatus(simklId, sNum, status) },
                        ),
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // 4. Notification Settings
                item {
                    NotificationSettingsCard(
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
                        itemTitleRomaji = watchlistItem.titleRomaji,
                        animeSeason = watchlistItem.animeSeason,
                        mediaType = watchlistItem.type,
                        defaultSubdirectory = watchlistItem.defaultDestinationSubdirectory(),
                        autoDownloadPrefs = autoDownloadPrefs,
                        itemSettings = itemSettings,
                        isDownloaderInstalled = isDownloaderInstalled,
                        availableSubdirectories = availableSubdirectories,
                        onSaveItemDownloadSettings = { viewModel.saveItemDownloadSettings(it) },
                        onSearchTorrents = { term -> viewModel.detectTorrents(simklId = watchlistItem.simklId, customTitle = term) },
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
            .background(SurfaceContainer, RoundedCornerShape(12.dp))
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
    onMarkSeasonWatched: (season: Int) -> Unit,
    onMarkSeasonUnwatched: (season: Int) -> Unit,
    onUpdateSeasonMediaStatus: (season: Int, status: MediaStatus) -> Unit
) {
    if (item == null) return

    val commonStatus = remember(episodes) {
        val first = episodes.firstOrNull()?.mediaStatus
        if (first != null && episodes.all { it.mediaStatus == first }) first else null
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(SurfaceContainer, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        val isNarrow = maxWidth < 450.dp

        if (isNarrow && isAnimeSeasonOneOnly) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SummaryStatsContent(item, isNarrow = true)

                HorizontalDivider(color = OutlineVariant, thickness = 1.dp)

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
                        color = OutlineVariant
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
        Text(text = label, fontSize = 11.sp, color = TextSubtle, fontWeight = FontWeight.Bold)
        Text(text = value, fontSize = 13.sp, color = valueColor, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center)
    }
}
