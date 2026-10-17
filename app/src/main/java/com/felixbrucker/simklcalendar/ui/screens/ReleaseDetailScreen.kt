package com.felixbrucker.simklcalendar.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.util.DateUtil
import com.felixbrucker.simklcalendar.data.util.formattedEpisodeCode
import com.felixbrucker.simklcalendar.data.util.formattedEpisodeSlugHeader
import com.felixbrucker.simklcalendar.data.util.formattedItemLabel
import com.felixbrucker.simklcalendar.data.util.formattedSeasonLabel
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
import com.felixbrucker.simklcalendar.ui.composable.NotificationSettingsCard
import com.felixbrucker.simklcalendar.ui.theme.ErrorPink
import com.felixbrucker.simklcalendar.ui.theme.ErrorRed
import com.felixbrucker.simklcalendar.ui.theme.GreenSuccess
import com.felixbrucker.simklcalendar.ui.theme.OutlineGrey
import com.felixbrucker.simklcalendar.ui.theme.OutlineVariant
import com.felixbrucker.simklcalendar.ui.theme.PurpleContainerLight
import com.felixbrucker.simklcalendar.ui.theme.PurpleContainerSoft
import com.felixbrucker.simklcalendar.ui.theme.SuccessLight
import com.felixbrucker.simklcalendar.ui.theme.SurfaceContainer
import com.felixbrucker.simklcalendar.ui.theme.SurfaceDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceDarkGreenVariant
import com.felixbrucker.simklcalendar.ui.theme.SurfaceGreen
import com.felixbrucker.simklcalendar.ui.theme.SurfaceGreenBright
import com.felixbrucker.simklcalendar.ui.theme.SurfaceGreenDeep
import com.felixbrucker.simklcalendar.ui.theme.SurfacePrimaryVariant
import com.felixbrucker.simklcalendar.ui.theme.SurfaceRedDeep
import com.felixbrucker.simklcalendar.ui.theme.SurfaceRedWine
import com.felixbrucker.simklcalendar.ui.theme.SurfaceVariantDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceVariantGrey
import com.felixbrucker.simklcalendar.ui.theme.TextPrimary
import com.felixbrucker.simklcalendar.ui.theme.TextSubtle
import com.felixbrucker.simklcalendar.ui.viewmodel.CalendarViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseDetailScreen(
    viewModel: CalendarViewModel,
    itemKey: String,
    onNavigateBack: () -> Unit,
    onNavigateToWatchlistItem: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val isSmallScreen = with(density) { windowInfo.containerSize.width.toDp() } < 600.dp
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val isMarkingWatched by viewModel.isMarkingWatched.collectAsState()
    val updatingWatchKeys by viewModel.updatingWatchStatusKeys.collectAsState()
    val torrentDownloads by viewModel.torrentDownloads.collectAsState()
    val customSearchLinks by viewModel.customSearchLinks.collectAsState()
    val autoDownloadPrefs by viewModel.autoDownloadPreferences.collectAsState()
    val isDownloaderInstalled by viewModel.isTorrentServiceInstalled.collectAsState()
    val availableSubdirectories by viewModel.downloadSubdirectories.collectAsState()
    val appSettingsPrefs by viewModel.appSettingsPreferences.collectAsState()

    var showDeleteConfirmation by remember { mutableStateOf(false) }

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

    var activeItemKey by remember(itemKey) { mutableStateOf(itemKey) }
    val item by viewModel.getCalendarItemFlow(activeItemKey).collectAsState(null)
    val activeItem = item // Required for smart casting
    val itemSettings by viewModel.getItemDownloadSettingsFlow(activeItem?.simklId ?: 0).collectAsState(null)

    val showScheduleItems by viewModel
        .getSortedCalendarItemsForSimklIdFlow(activeItem?.simklId ?: 0)
        .collectAsState(emptyList())

    val showWatched by viewModel
        .getWatchedEpisodesForSimklIdFlow(activeItem?.simklId ?: 0)
        .collectAsState(emptyList())

    // Helper to evaluate watch status of a season
    fun getSeasonWatchStatus(seasonNum: Int): Triple<Boolean, Boolean, String> {
        val scheduleInSeason = showScheduleItems.filter { (it.season ?: 1) == seasonNum }
        val watchedInSeason = showWatched.filter { it.season == seasonNum }

        return if (scheduleInSeason.isNotEmpty()) {
            val total = scheduleInSeason.size
            val watchedCount = scheduleInSeason.count { it.isWatched }
            val isFully = watchedCount == total
            val isPartial = watchedCount in 1..<total
            val statusStr = if (isFully) {
                "All $total episodes watched"
            } else if (isPartial) {
                "$watchedCount of $total episodes watched"
            } else {
                "Unwatched ($total episodes)"
            }
            Triple(isFully, isPartial, statusStr)
        } else if (watchedInSeason.isNotEmpty()) {
            Triple(true, false, "${watchedInSeason.size} episodes watched")
        } else {
            Triple(false, false, "Unwatched")
        }
    }

    val theatricalItem = remember(showScheduleItems, activeItem) {
        if (activeItem?.type == MediaType.MOVIE) {
            showScheduleItems.firstOrNull { it.movieReleaseType == MovieReleaseType.THEATER }
                ?: if (activeItem.movieReleaseType == MovieReleaseType.THEATER) activeItem else null
        } else null
    }

    val digitalItem = remember(showScheduleItems, activeItem) {
        if (activeItem?.type == MediaType.MOVIE) {
            showScheduleItems.firstOrNull { it.movieReleaseType == MovieReleaseType.DIGITAL }
                ?: if (activeItem.movieReleaseType == MovieReleaseType.DIGITAL) activeItem else null
        } else null
    }

    val setting by viewModel.getSettingForSimklIdFlow(activeItem?.simklId ?: 0).collectAsState(null)
    val notificationSetting = setting // Required for smart casting

    val notificationPrefs by viewModel.notificationPreferences.collectAsState()
    val defaultAiring = notificationPrefs.defaultNotifyAiring
    val defaultSeasonFinished = notificationPrefs.defaultNotifySeasonFinished
    val defaultMovieTheater = notificationPrefs.defaultNotifyMovieTheater
    val defaultMovieDigital = notificationPrefs.defaultNotifyMovieDigital
    val isMovie = activeItem?.type == MediaType.MOVIE

    // Individual notification toggle flows
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
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Release Details", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SurfaceDark
    ) { innerPadding ->
        if (activeItem == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Show details not found.", color = TextPrimary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Banner / Poster Header
                DetailHeader(
                    data = DetailHeaderData(
                        simklId = activeItem.simklId,
                        type = activeItem.type,
                        title = activeItem.title,
                        poster = activeItem.poster,
                        rating = activeItem.rating,
                        titleRomaji = activeItem.titleRomaji,
                        hasUniqueTitleRomaji = activeItem.hasUniqueTitleRomaji,
                        animeSeason = activeItem.animeSeason,
                    ),
                    onTitleClick = { onNavigateToWatchlistItem(activeItem.simklId) }
                )

                // Airing / Release details info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Watched Episode Highlight Banner
                    if (activeItem.isWatched) {
                        WatchedEpisodeHighlightBanner(activeItem = activeItem)
                    }

                    SelectedEpisodeDetailsCard(
                        activeItem = activeItem,
                        theatricalItem = theatricalItem,
                        digitalItem = digitalItem,
                        updatingWatchKeys = updatingWatchKeys,
                        onWatchedStatusChange = { watched ->
                            if (activeItem.type == MediaType.MOVIE) {
                                if (watched) {
                                    viewModel.markMovieWatched(activeItem.simklId, activeItem.primaryKey, activeItem.title) { _, _ -> }
                                } else {
                                    viewModel.markMovieUnwatched(activeItem.simklId, activeItem.primaryKey, activeItem.title) { _, _ -> }
                                }
                            } else {
                                if (watched) {
                                    viewModel.markEpisodeWatched(activeItem.simklId, activeItem.season, activeItem.episodeNumber ?: 1, activeItem.type, activeItem.primaryKey, activeItem.title) { _, _ -> }
                                } else {
                                    viewModel.markEpisodeUnwatched(activeItem.simklId, activeItem.season, activeItem.episodeNumber ?: 1, activeItem.type, activeItem.primaryKey, activeItem.title) { _, _ -> }
                                }
                            }
                        },
                        onMediaStatusChange = { itemKeyToUpdate, newStatus ->
                            viewModel.updateMediaStatus(itemKeyToUpdate, newStatus)
                        }
                    )

                    // Dynamic Action Buttons Section (Max 2 per row)
                    val actions = remember(activeItem, showWatched, isMarkingWatched, appSettingsPrefs.showDebugActions) {
                        val list = mutableListOf<@Composable (Modifier) -> Unit>()

                        if (activeItem.type != MediaType.MOVIE) {
                            val sNum = activeItem.season ?: 1
                            val seasonLabel = activeItem.formattedSeasonLabel
                            val (isSeasonFullyWatched, _, _) = getSeasonWatchStatus(sNum)

                            list.add { mod ->
                                SeasonWatchButton(
                                    isMarkingWatched = isMarkingWatched,
                                    isSeasonFullyWatched = isSeasonFullyWatched,
                                    seasonLabel = seasonLabel,
                                    onMarkSeasonWatched = {
                                        viewModel.markSeasonWatched(
                                            simklId = activeItem.simklId,
                                            season = sNum,
                                            mediaType = activeItem.type,
                                            showTitle = activeItem.title
                                        ) { success, msg ->
                                            if (success) {
                                                scope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = msg,
                                                        actionLabel = "Revert",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        viewModel.markSeasonUnwatched(
                                                            simklId = activeItem.simklId,
                                                            season = sNum,
                                                            mediaType = activeItem.type,
                                                            showTitle = activeItem.title
                                                        ) { _, revertMsg ->
                                                            scope.launch { snackbarHostState.showSnackbar(revertMsg) }
                                                        }
                                                    }
                                                }
                                            } else {
                                                scope.launch { snackbarHostState.showSnackbar(msg) }
                                            }
                                        }
                                    },
                                    modifier = mod
                                )
                            }
                        }

                        if (appSettingsPrefs.showDebugActions) {
                            list.add { mod ->
                                Button(
                                    onClick = { showDeleteConfirmation = true },
                                    modifier = mod.height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SurfaceRedDeep,
                                        contentColor = ErrorPink
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Delete", fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }

                        list
                    }

                    if (actions.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            actions.chunked(if (isSmallScreen) 1 else 2).forEach { rowActions ->
                                if (rowActions.size == 2) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowActions.forEach { action ->
                                            action(Modifier.weight(1f))
                                        }
                                    }
                                } else {
                                    rowActions.first()(Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }

                    if (showDeleteConfirmation) {
                        val itemLabel = activeItem.formattedItemLabel
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirmation = false },
                            title = { Text("Delete $itemLabel", fontWeight = FontWeight.Bold, color = TextPrimary) },
                            text = { Text("Are you sure you want to delete this item? This action cannot be undone.", color = TextSubtle) },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showDeleteConfirmation = false
                                        viewModel.deleteCalendarItem(activeItem.primaryKey) {
                                            onNavigateBack()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SurfaceRedWine,
                                        contentColor = ErrorPink
                                    )
                                ) {
                                    Text("Delete")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirmation = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    CustomSearchLinksCard(
                        searchLinks = customSearchLinks,
                        title = activeItem.title,
                        titleRomaji = activeItem.titleRomaji,
                        itemType = activeItem.type,
                        season = activeItem.season,
                        episode = activeItem.episodeNumber,
                        snackbarHostState = snackbarHostState,
                        scope = scope,
                        context = context,
                    )

                    // Scheduled Episodes / Releases selector card
                    if (showScheduleItems.size > 1) {
                        EpisodeListCard(
                            state = EpisodeListState(
                                items = showScheduleItems,
                                mediaType = activeItem.type,
                                isSmallScreen = isSmallScreen,
                                activeItemKey = activeItem.primaryKey,
                                updatingWatchKeys = updatingWatchKeys,
                                torrentDownloads = torrentDownloads,
                                showSeasonHeaders = showScheduleItems.groupBy { it.season ?: 1 }.size > 1
                            ),
                            actions = EpisodeListActions(
                                onItemClick = { epItem -> activeItemKey = epItem.primaryKey },
                                onWatchedStatusChange = { epItem, watched ->
                                    if (epItem.type == MediaType.MOVIE) {
                                        if (watched) {
                                            viewModel.markMovieWatched(epItem.simklId, epItem.primaryKey, epItem.title) { _, _ -> }
                                        } else {
                                            viewModel.markMovieUnwatched(epItem.simklId, epItem.primaryKey, epItem.title) { _, _ -> }
                                        }
                                    } else {
                                        if (watched) {
                                            viewModel.markEpisodeWatched(epItem.simklId, epItem.season, epItem.episodeNumber ?: 1, epItem.type, epItem.primaryKey, epItem.title) { _, _ -> }
                                        } else {
                                            viewModel.markEpisodeUnwatched(epItem.simklId, epItem.season, epItem.episodeNumber ?: 1, epItem.type, epItem.primaryKey, epItem.title) { _, _ -> }
                                        }
                                    }
                                },
                                onMediaStatusChange = { epKey, newStatus ->
                                    viewModel.updateMediaStatus(epKey, newStatus)
                                },
                                onMarkSeasonWatched = { season ->
                                    viewModel.markSeasonWatched(
                                        simklId = activeItem.simklId,
                                        season = season,
                                        mediaType = activeItem.type,
                                        showTitle = activeItem.title
                                    ) { _, _ -> }
                                },
                                onMarkSeasonUnwatched = { season ->
                                    viewModel.markSeasonUnwatched(
                                        simklId = activeItem.simklId,
                                        season = season,
                                        mediaType = activeItem.type,
                                        showTitle = activeItem.title
                                    ) { _, _ -> }
                                },
                                onUpdateSeasonMediaStatus = { season, status ->
                                    viewModel.updateSeasonMediaStatus(activeItem.simklId, season, status)
                                }
                            )
                        )
                    }

                    // Notification Settings
                    NotificationSettingsCard(
                        isMovie = isMovie,
                        notifyEveryEpisode = notifyEveryEpisode,
                        notifySeasonFinished = notifySeasonFinished,
                        onNotifyEveryEpisodeChange = { isChecked ->
                            notifyEveryEpisode = isChecked
                            viewModel.toggleNotification(
                                simklId = activeItem.simklId,
                                notifyEpisode = isChecked,
                                notifySeasonFinished = notifySeasonFinished
                            )
                        },
                        onNotifySeasonFinishedChange = { isChecked ->
                            notifySeasonFinished = isChecked
                            viewModel.toggleNotification(
                                simklId = activeItem.simklId,
                                notifyEpisode = notifyEveryEpisode,
                                notifySeasonFinished = isChecked
                            )
                        },
                        checkPermission = { checkAndRequestNotificationPermission() }
                    )

                    DownloadSettingsCard(
                        simklId = activeItem.simklId,
                        itemTitle = activeItem.title,
                        itemTitleRomaji = activeItem.titleRomaji,
                        animeSeason = activeItem.watchlistItem?.animeSeason,
                        mediaType = activeItem.type,
                        defaultSubdirectory = activeItem.defaultDestinationSubdirectory(),
                        autoDownloadPrefs = autoDownloadPrefs,
                        itemSettings = itemSettings,
                        isDownloaderInstalled = isDownloaderInstalled,
                        availableSubdirectories = availableSubdirectories,
                        onSaveItemDownloadSettings = { viewModel.saveItemDownloadSettings(it) },
                        onSearchTorrents = { term -> viewModel.detectTorrents(simklId = activeItem.simklId, customTitle = term) }
                    )
                }
            }
        }
    }
}

@Composable
fun WatchedEpisodeHighlightBanner(
    activeItem: CalendarItemWithWatchlist,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceDarkGreenVariant,
        border = BorderStroke(1.dp, SurfaceGreenBright),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(SurfaceGreenDeep, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Watched",
                    tint = GreenSuccess,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (activeItem.type == MediaType.MOVIE) "Watched Movie" else "Watched Episode",
                    color = GreenSuccess,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                val formattedWatchedAt = activeItem.watchedAt?.let { instant ->
                    DateUtil.formatDisplayDateTime(instant)
                }
                Text(
                    text = if (formattedWatchedAt != null) "Watched on $formattedWatchedAt" else "Marked as watched on Simkl",
                    color = SuccessLight,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun SelectedEpisodeDetailsCard(
    activeItem: CalendarItemWithWatchlist,
    theatricalItem: CalendarItemWithWatchlist?,
    digitalItem: CalendarItemWithWatchlist?,
    updatingWatchKeys: Set<String>,
    onWatchedStatusChange: (Boolean) -> Unit,
    onMediaStatusChange: (String, MediaStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (activeItem.type == MediaType.MOVIE) "Release Information" else "Selected Episode Details",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 15.sp
            )

            if (activeItem.type == MediaType.MOVIE) {
                if (theatricalItem != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Theatrical Release", color = TextSubtle, fontSize = 14.sp)
                        Text(
                            text = DateUtil.formatDisplayDate(theatricalItem.date),
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }

                if (digitalItem != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Digital / DVD Release", color = TextSubtle, fontSize = 14.sp)
                        Text(
                            text = DateUtil.formatDisplayDate(digitalItem.date),
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }

                if (theatricalItem == null && digitalItem == null) {
                    val dateLabel = if (activeItem.movieReleaseType == MovieReleaseType.THEATER) "Theatrical Release" else "Digital / DVD Release"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(dateLabel, color = TextSubtle, fontSize = 14.sp)
                        Text(
                            text = DateUtil.formatDisplayDate(activeItem.date),
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                val formattedDateTime = DateUtil.formatDisplayDateTime(activeItem.date)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Air Date", color = TextSubtle, fontSize = 14.sp)
                    Text(
                        text = formattedDateTime,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            // Season and Episode Slug
            if (activeItem.type != MediaType.MOVIE) {
                val slugLabel = activeItem.formattedEpisodeSlugHeader
                val slugValue = activeItem.formattedEpisodeCode

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(slugLabel, color = TextSubtle, fontSize = 14.sp)
                    Text(
                        text = slugValue,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                val titleText = activeItem.episodeTitle?.takeIf { it.isNotBlank() } ?: "TBA"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Episode Name", color = TextSubtle, fontSize = 14.sp)
                    Text(
                        text = titleText,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Watch Status", color = TextSubtle, fontSize = 14.sp)
                ItemWatchedStatusDropdown(
                    item = activeItem,
                    onWatchedStatusChange = onWatchedStatusChange,
                    updatingWatchKeys = updatingWatchKeys
                )
            }

            val mediaStatusItem = if (activeItem.type == MediaType.MOVIE) digitalItem else activeItem
            if (mediaStatusItem != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Status", color = TextSubtle, fontSize = 14.sp)
                    ItemMediaStatusDropdown(
                        item = mediaStatusItem,
                        onStatusChange = { newStatus -> onMediaStatusChange(mediaStatusItem.primaryKey, newStatus) }
                    )
                }
            }

            // Prem/Finale labels
            if (activeItem.isSeasonPremiere || activeItem.isSeasonFinale) {
                HorizontalDivider(
                    color = OutlineVariant,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (activeItem.isSeasonPremiere) {
                        Box(
                            modifier = Modifier
                                .background(PurpleContainerLight, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "🎉 SEASON PREMIERE",
                                color = SurfaceVariantDark,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (activeItem.isSeasonFinale) {
                        Box(
                            modifier = Modifier
                                .background(ErrorRed, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("🎬 SEASON FINALE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeasonWatchButton(
    isMarkingWatched: Boolean,
    isSeasonFullyWatched: Boolean,
    seasonLabel: String,
    onMarkSeasonWatched: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onMarkSeasonWatched,
        enabled = !isMarkingWatched && !isSeasonFullyWatched,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSeasonFullyWatched) SurfaceGreenDeep else SurfacePrimaryVariant,
            contentColor = if (isSeasonFullyWatched) GreenSuccess else PurpleContainerSoft,
            disabledContainerColor = if (isSeasonFullyWatched) SurfaceGreen else SurfaceVariantGrey,
            disabledContentColor = if (isSeasonFullyWatched) GreenSuccess else OutlineGrey
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        if (isMarkingWatched) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = PurpleContainerSoft
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Updating SIMKL...", fontWeight = FontWeight.Bold)
        } else if (isSeasonFullyWatched) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$seasonLabel Watched",
                fontWeight = FontWeight.Bold
            )
        } else {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Mark $seasonLabel as Watched",
                fontWeight = FontWeight.Bold
            )
        }
    }
}
