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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.felixbrucker.simklcalendar.extensions.defaultDestinationSubdirectory
import com.felixbrucker.simklcalendar.data.util.formattedEpisodeCode
import com.felixbrucker.simklcalendar.data.util.formattedEpisodeSlugHeader
import com.felixbrucker.simklcalendar.data.util.formattedSeasonLabel
import com.felixbrucker.simklcalendar.ui.viewmodel.CalendarViewModel
import kotlinx.coroutines.launch
import com.felixbrucker.simklcalendar.ui.composable.CustomSearchLinksCard
import com.felixbrucker.simklcalendar.ui.composable.DetailHeader
import com.felixbrucker.simklcalendar.ui.composable.DownloadSettingsCard
import com.felixbrucker.simklcalendar.ui.composable.ItemMediaStatusDropdown
import com.felixbrucker.simklcalendar.ui.composable.ItemWatchedStatusDropdown
import com.felixbrucker.simklcalendar.ui.composable.NotificationSettingsCard

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

    val allItems by viewModel.allCalendarItems.collectAsState()
    val allWatchedEpisodes by viewModel.watchedEpisodes.collectAsState()
    val isMarkingWatched by viewModel.isMarkingWatched.collectAsState()
    val updatingWatchKeys by viewModel.updatingWatchStatusKeys.collectAsState()
    val customSearchLinks by viewModel.customSearchLinks.collectAsState()
    val autoDownloadPrefs by viewModel.autoDownloadPreferences.collectAsState()
    val isDownloaderInstalled by viewModel.isTorrentServiceInstalled.collectAsState()
    val availableSubdirectories by viewModel.downloadSubdirectories.collectAsState()

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

    val activeItem = remember(allItems, activeItemKey, itemKey) {
        allItems.firstOrNull { it.primaryKey == activeItemKey }
            ?: allItems.firstOrNull { it.simklId.toString() == activeItemKey }
            ?: allItems.firstOrNull { it.primaryKey == itemKey }
            ?: allItems.firstOrNull { it.simklId.toString() == itemKey }
    }

    val itemSettings by viewModel.getItemDownloadSettingsFlow(activeItem?.simklId ?: 0).collectAsState(null)

    val showScheduleItems = remember(allItems, activeItem) {
        if (activeItem != null) {
            allItems.filter { it.simklId == activeItem.simklId }
                .sortedWith(compareBy<CalendarItemWithWatchlist> { it.date }.thenBy { it.season }.thenBy { it.episodeNumber })
        } else {
            emptyList()
        }
    }

    val showWatched = remember(allWatchedEpisodes, activeItem) {
        if (activeItem != null) {
            allWatchedEpisodes.filter { it.simklId == activeItem.simklId }
        } else {
            emptyList()
        }
    }

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

    val settingsList by viewModel.notificationSettings.collectAsState()
    val showSetting = remember(settingsList, activeItem) {
        if (activeItem != null) settingsList.firstOrNull { it.simklId == activeItem.simklId } else null
    }

    val notificationPrefs by viewModel.notificationPreferences.collectAsState()
    val defaultAiring = notificationPrefs.defaultNotifyAiring
    val defaultSeasonFinished = notificationPrefs.defaultNotifySeasonFinished
    val defaultMovieTheater = notificationPrefs.defaultNotifyMovieTheater
    val defaultMovieDigital = notificationPrefs.defaultNotifyMovieDigital
    val isMovie = activeItem?.type == MediaType.MOVIE

    // Individual notification toggle flows
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
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Release Details", color = Color(0xFFE6E1E5), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFE6E1E5))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1C1B1F))
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF1C1B1F)
    ) { innerPadding ->
        if (activeItem == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Show details not found.", color = Color(0xFFE6E1E5))
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
                    simklId = activeItem.simklId,
                    type = activeItem.type,
                    title = activeItem.title,
                    poster = activeItem.poster,
                    titleRomaji = activeItem.titleRomaji,
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

                    // Watch Actions Section
                    if (activeItem.type != MediaType.MOVIE) {
                        val sNum = activeItem.season ?: 1
                        val seasonLabel = activeItem.formattedSeasonLabel
                        val (isSeasonFullyWatched, _, _) = getSeasonWatchStatus(sNum)

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
                        ScheduledReleasesCard(
                            activeItem = activeItem,
                            showScheduleItems = showScheduleItems,
                            isSmallScreen = isSmallScreen,
                            updatingWatchKeys = updatingWatchKeys,
                            onSelectActiveItemKey = { activeItemKey = it },
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
                            }
                        )
                    }

                    // Notification Settings
                    NotificationSettingsCard(
                        simklId = activeItem.simklId,
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
                        mediaType = activeItem.type,
                        defaultSubdirectory = activeItem.defaultDestinationSubdirectory(),
                        autoDownloadPrefs = autoDownloadPrefs,
                        itemSettings = itemSettings,
                        isDownloaderInstalled = isDownloaderInstalled,
                        availableSubdirectories = availableSubdirectories,
                        onSaveItemDownloadSettings = { viewModel.saveItemDownloadSettings(it) }
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
        color = Color(0xFF1B3828),
        border = BorderStroke(1.dp, Color(0xFF48A36E)),
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
                    .background(Color(0xFF2E6543), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Watched",
                    tint = Color(0xFF7CE49F),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (activeItem.type == MediaType.MOVIE) "Watched Movie" else "Watched Episode",
                    color = Color(0xFF7CE49F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                val formattedWatchedAt = activeItem.watchedAt?.let { instant ->
                    DateUtil.formatDisplayDateTime(instant)
                }
                Text(
                    text = if (formattedWatchedAt != null) "Watched on $formattedWatchedAt" else "Marked as watched on Simkl",
                    color = Color(0xFFC3EED3),
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
        border = BorderStroke(1.dp, Color(0xFF49454F)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (activeItem.type == MediaType.MOVIE) "Release Information" else "Selected Episode Details",
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE6E1E5),
                fontSize = 15.sp
            )

            if (activeItem.type == MediaType.MOVIE) {
                if (theatricalItem != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Theatrical Release", color = Color(0xFFCAC4D0), fontSize = 14.sp)
                        Text(
                            text = DateUtil.formatDisplayDate(theatricalItem.date),
                            color = Color(0xFFE6E1E5),
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
                        Text("Digital / DVD Release", color = Color(0xFFCAC4D0), fontSize = 14.sp)
                        Text(
                            text = DateUtil.formatDisplayDate(digitalItem.date),
                            color = Color(0xFFE6E1E5),
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
                        Text(dateLabel, color = Color(0xFFCAC4D0), fontSize = 14.sp)
                        Text(
                            text = DateUtil.formatDisplayDate(activeItem.date),
                            color = Color(0xFFE6E1E5),
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
                    Text("Air Date", color = Color(0xFFCAC4D0), fontSize = 14.sp)
                    Text(
                        text = formattedDateTime,
                        color = Color(0xFFE6E1E5),
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
                    Text(slugLabel, color = Color(0xFFCAC4D0), fontSize = 14.sp)
                    Text(
                        text = slugValue,
                        color = Color(0xFFE6E1E5),
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
                    Text("Episode Name", color = Color(0xFFCAC4D0), fontSize = 14.sp)
                    Text(
                        text = titleText,
                        color = Color(0xFFE6E1E5),
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
                Text("Watch Status", color = Color(0xFFCAC4D0), fontSize = 14.sp)
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
                    Text("Status", color = Color(0xFFCAC4D0), fontSize = 14.sp)
                    ItemMediaStatusDropdown(
                        item = mediaStatusItem,
                        onStatusChange = { newStatus -> onMediaStatusChange(mediaStatusItem.primaryKey, newStatus) }
                    )
                }
            }

            // Prem/Finale labels
            if (activeItem.isSeasonPremiere || activeItem.isSeasonFinale) {
                HorizontalDivider(color = Color(0xFF49454F), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (activeItem.isSeasonPremiere) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE8DEF8), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("🎉 SEASON PREMIERE", color = Color(0xFF1D192B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (activeItem.isSeasonFinale) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFB3261E), RoundedCornerShape(4.dp))
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
fun ScheduledReleasesCard(
    activeItem: CalendarItemWithWatchlist,
    showScheduleItems: List<CalendarItemWithWatchlist>,
    isSmallScreen: Boolean,
    updatingWatchKeys: Set<String>,
    onSelectActiveItemKey: (String) -> Unit,
    onWatchedStatusChange: (CalendarItemWithWatchlist, Boolean) -> Unit,
    onMediaStatusChange: (String, MediaStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
        border = BorderStroke(1.dp, Color(0xFF49454F)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (activeItem.type == MediaType.MOVIE) "Scheduled Releases (${showScheduleItems.size})" else "Scheduled Episodes (${showScheduleItems.size})",
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE6E1E5),
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            showScheduleItems.forEach { epItem ->
                val isSelected = epItem.primaryKey == activeItem.primaryKey
                val epTag = if (epItem.type == MediaType.MOVIE) {
                    if (epItem.movieReleaseType == MovieReleaseType.THEATER) "THEATER" else "DIGITAL / DVD"
                } else {
                    epItem.formattedEpisodeCode
                }
                val epDate = if (epItem.type == MediaType.MOVIE) DateUtil.formatDisplayDate(epItem.date) else DateUtil.formatDisplayDateTime(epItem.date)

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Color(0xFF4F378B).copy(alpha = 0.4f) else Color(0xFF1C1B1F),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFFD0BCFF) else Color(0xFF3B383E)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onSelectActiveItemKey(epItem.primaryKey) }
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
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSelected) Color(0xFFD0BCFF) else Color(0xFF381E72),
                                contentColor = if (isSelected) Color(0xFF381E72) else Color(0xFFEADDFF)
                            ) {
                                Text(
                                    text = epTag,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = if (epItem.type == MediaType.MOVIE) {
                                         epItem.movieReleaseType?.displayName ?: "Movie Release"
                                    } else {
                                        epItem.episodeTitle?.takeIf { it.isNotBlank() } ?: "TBA"
                                    },
                                    color = Color(0xFFE6E1E5),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                                Text(
                                    text = epDate,
                                    color = Color(0xFFA5A3B1),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (activeItem.type != MediaType.MOVIE) {
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

                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Currently Selected",
                                tint = Color(0xFFD0BCFF),
                                modifier = Modifier.size(18.dp)
                            )
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
        modifier = modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSeasonFullyWatched) Color(0xFF2E6543) else Color(0xFF4F378B),
            contentColor = if (isSeasonFullyWatched) Color(0xFF7CE49F) else Color(0xFFEADDFF),
            disabledContainerColor = if (isSeasonFullyWatched) Color(0xFF1E3A2B) else Color(0xFF3B383E),
            disabledContentColor = if (isSeasonFullyWatched) Color(0xFF7CE49F) else Color(0xFF79747E)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        if (isMarkingWatched) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color(0xFFEADDFF)
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
