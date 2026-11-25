package com.felixbrucker.simklcalendar.ui.screens

import android.Manifest
import android.R.drawable.ic_menu_gallery
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import com.felixbrucker.simklcalendar.ui.composable.ExpressiveSwitch
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.felixbrucker.simklcalendar.data.database.AnimeType
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.MediaItem
import com.felixbrucker.simklcalendar.data.database.MediaItemWithDetails
import com.felixbrucker.simklcalendar.data.database.RecommendedMediaItem
import com.felixbrucker.simklcalendar.data.database.RelatedMediaItem
import com.felixbrucker.simklcalendar.data.database.SimilarMediaItem
import com.felixbrucker.simklcalendar.data.database.WatchlistItem
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.WatchlistStatus
import com.felixbrucker.simklcalendar.data.util.DateUtil
import com.felixbrucker.simklcalendar.data.util.MediaFormatter
import com.felixbrucker.simklcalendar.data.util.DownloadProgress
import com.felixbrucker.simklcalendar.data.util.PosterSize
import com.felixbrucker.simklcalendar.data.util.displayTitle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.felixbrucker.simklcalendar.extensions.toPosterUrl
import com.felixbrucker.simklcalendar.ui.composable.EpisodeListSliceType
import com.felixbrucker.simklcalendar.ui.composable.episodeListSliceBorder
import com.felixbrucker.simklcalendar.ui.composable.CustomSearchLinksCard
import com.felixbrucker.simklcalendar.ui.composable.DetailHeaderOpenSimklButton
import com.felixbrucker.simklcalendar.ui.composable.DownloadSettingsCard
import com.felixbrucker.simklcalendar.ui.composable.EpisodeDownloadProgress
import com.felixbrucker.simklcalendar.ui.composable.ItemMediaStatusDropdown
import com.felixbrucker.simklcalendar.ui.composable.ManualSearchDialog
import com.felixbrucker.simklcalendar.ui.composable.MediaStatusDropdown
import com.felixbrucker.simklcalendar.ui.composable.NotificationSettingsCard
import com.felixbrucker.simklcalendar.ui.composable.MediaWatchButton
import com.felixbrucker.simklcalendar.ui.composable.SeasonWatchButton
import com.felixbrucker.simklcalendar.ui.composable.WatchedStatusDropdown
import com.felixbrucker.simklcalendar.ui.composable.sharedBoundsItem
import com.felixbrucker.simklcalendar.ui.composable.sharedElementItem
import com.felixbrucker.simklcalendar.ui.composable.badgeTextColor
import com.felixbrucker.simklcalendar.ui.composable.overlayIcon
import com.felixbrucker.simklcalendar.ui.composable.WatchlistOverlayPosterWithBadge
import com.felixbrucker.simklcalendar.ui.theme.*
import com.felixbrucker.simklcalendar.ui.viewmodel.EpisodeListItemUiModel
import com.felixbrucker.simklcalendar.ui.viewmodel.EpisodeListUiState
import com.felixbrucker.simklcalendar.ui.viewmodel.MediaItemDetailViewModel
import com.felixbrucker.simklcalendar.ui.viewmodel.WatchProgressUiState
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaItemDetailScreen(
    simklId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Int, MediaType) -> Unit,
    onNavigateToReleaseDetail: (CalendarItemWithWatchlist) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MediaItemDetailViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val isSmallScreen = with(density) { windowInfo.containerSize.width.toDp() } < 600.dp
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val mediaItemWithDetails by viewModel.mediaItem.collectAsState()
    val watchlistItem by viewModel.watchlistItem.collectAsState()
    val targetMovieCalendarItemDelegate by viewModel.targetMovieCalendarItem.collectAsState()
    val trackedWatchlistItem by viewModel.getTrackedWatchlistItemFlow(simklId).collectAsState(null)
    val customSearchLinks by viewModel.customSearchLinks.collectAsState()
    val isUpdatingStatus by viewModel.isUpdatingStatus.collectAsState()
    val watchlistStatusMap by viewModel.watchlistStatusMap.collectAsState()
    val uiPreferences by viewModel.uiPreferences.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val episodeListUiState by viewModel.episodeListUiState.collectAsState()
    val watchProgressState by viewModel.watchProgressState.collectAsState()
    var isEpisodesExpanded by rememberSaveable { mutableStateOf(false) }

    val notificationSetting by viewModel.getSettingForSimklIdFlow(simklId).collectAsState(null)
    val itemSettings by viewModel.getItemDownloadSettingsFlow(simklId).collectAsState(null)
    val autoDownloadPrefs by viewModel.autoDownloadPreferences.collectAsState()
    val isDownloaderInstalled by viewModel.isTorrentServiceInstalled.collectAsState()
    val availableSubdirectories by viewModel.downloadSubdirectories.collectAsState()

    val isMovie by viewModel.isMovie.collectAsState()
    val manualSearchSeasonNum by viewModel.manualSearchSeasonNum.collectAsState()
    val isMovieManualSearchOpen by viewModel.isMovieManualSearchOpen.collectAsState()
    val manualSearchCalendarItem by viewModel.manualSearchCalendarItem.collectAsState()

    val targetSeason by viewModel.targetSeason.collectAsState()
    val isTargetSeasonFullyWatched by viewModel.isTargetSeasonFullyWatched.collectAsState()
    val isTargetSeasonOrMovieSearchable by viewModel.isTargetSeasonOrMovieSearchable.collectAsState()
    val isTargetSeasonUpdatingWatchStatus by viewModel.isTargetSeasonUpdatingWatchStatus.collectAsState()

    val notificationPrefs by viewModel.notificationPreferences.collectAsState()
    val notifyEveryEpisode = notificationSetting?.notifyEveryEpisode ?: (if (isMovie) notificationPrefs.defaultNotifyMovieTheater else notificationPrefs.defaultNotifyAiring)
    val notifySeasonFinished = notificationSetting?.notifyAiredLastEpisode ?: (if (isMovie) notificationPrefs.defaultNotifyMovieDigital else notificationPrefs.defaultNotifySeasonFinished)

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

    val item = mediaItemWithDetails
    val categoryColor = when (item?.mediaType ?: MediaType.TV) {
        MediaType.ANIME -> PurpleLight
        MediaType.MOVIE -> ErrorPink
        MediaType.TV -> BlueAccent
    }

    Scaffold(
        modifier = modifier
            .sharedBoundsItem(
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                key = "container_$simklId"
            ),
        topBar = {
            TopAppBar(
                title = { Text(item?.title ?: "Details", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = SurfaceDark,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                viewModel.refresh { _, msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (item == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        MediaDetailHeaderContent(
                            simklId = simklId,
                            item = item,
                            categoryColor = categoryColor,
                            watchlistItem = watchlistItem,
                            isUpdatingStatus = isUpdatingStatus,
                            onUpdateStatus = { status ->
                                viewModel.updateWatchlistStatus(simklId, item.mediaType, status) { _, msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            },
                            targetSeason = targetSeason,
                            isTargetSeasonFullyWatched = isTargetSeasonFullyWatched,
                            isTargetSeasonOrMovieSearchable = isTargetSeasonOrMovieSearchable,
                            isTargetSeasonUpdatingWatchStatus = isTargetSeasonUpdatingWatchStatus,
                            watchProgressState = watchProgressState,
                            onMarkSeasonWatched = { sNum ->
                                viewModel.markSeasonWatched(simklId, sNum, item.mediaType) { _, msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            },
                            onManualSearch = { sNum -> viewModel.setManualSearchSeason(sNum) },
                            onMovieManualSearch = { viewModel.setMovieManualSearchOpen(true) },
                            onMovieWatchedChange = { watched ->
                                if (watched) {
                                    viewModel.markMovieWatched(simklId, showTitle = item.title) { _, msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                } else {
                                    viewModel.markMovieUnwatched(simklId, showTitle = item.title) { _, msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                }
                            },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
                        )
                    }

                    item {
                        CustomSearchLinksCard(
                            searchLinks = customSearchLinks,
                            title = item.title,
                            titleRomaji = item.titleRomaji,
                            itemType = item.mediaType,
                            season = null,
                            episode = null,
                            snackbarHostState = snackbarHostState,
                            scope = scope,
                            context = context,
                            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp)
                        )
                    }

                    val isAnimeMovie = item.mediaType == MediaType.ANIME && item.animeMetadata?.animeType == AnimeType.MOVIE
                    val shouldShowEpisodes = item.mediaType != MediaType.MOVIE && !isAnimeMovie
                    if (shouldShowEpisodes) {
                        val headerTitle = if (item.mediaType == MediaType.MOVIE) {
                            "Releases (${episodeListUiState.totalCount})"
                        } else {
                            "Episodes (${episodeListUiState.totalCount})"
                        }
                        mediaEpisodeCardSection(
                            uiState = episodeListUiState,
                            downloadsFlow = viewModel.downloads,
                            isExpanded = isEpisodesExpanded,
                            headerTitle = headerTitle,
                            isSmallScreen = isSmallScreen,
                            onToggleExpand = { isEpisodesExpanded = !isEpisodesExpanded },
                            onMarkEpisodeWatched = { epRow, watched ->
                                if (watched) {
                                    viewModel.markEpisodeWatched(simklId, epRow.season, epRow.episodeNumber, item.mediaType, showTitle = epRow.title) { _, msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                } else {
                                    viewModel.markEpisodeUnwatched(simklId, epRow.season, epRow.episodeNumber, item.mediaType, showTitle = epRow.title) { _, msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                }
                            },
                            onUpdateMediaStatus = { epRow, status ->
                                epRow.calendarItem?.primaryKey?.let { key ->
                                    viewModel.updateMediaStatus(key, status)
                                }
                            },
                            onMarkSeasonWatched = { season, watched ->
                                if (watched) {
                                    viewModel.markSeasonWatched(simklId, season, item.mediaType) { _, msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                } else {
                                    viewModel.markSeasonUnwatched(simklId, season, item.mediaType) { _, msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                }
                            },
                            onUpdateSeasonMediaStatus = { season, status ->
                                viewModel.updateSeasonMediaStatus(simklId, season, status)
                            },
                            onManualSearchSeason = { season -> viewModel.setManualSearchSeason(season) },
                            onNavigateToReleaseDetail = onNavigateToReleaseDetail,
                            onManualSearchEpisode = { calItem -> viewModel.setManualSearchCalendarItem(calItem) }
                        )
                    }

                    if (item.relations.isNotEmpty()) {
                        item {
                            RelatedMediaCarousel(
                                relations = item.relations,
                                watchlistStatusMap = watchlistStatusMap,
                                onNavigateToDetail = onNavigateToDetail,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    if (item.similarItems.isNotEmpty()) {
                        item {
                            SimilarMediaCarousel(
                                similarItems = item.similarItems,
                                watchlistStatusMap = watchlistStatusMap,
                                hideWatchlist = uiPreferences.hideWatchlistItemsInSimilar,
                                onToggleHide = { viewModel.toggleHideWatchlistItemsInSimilar(it) },
                                onNavigateToDetail = onNavigateToDetail,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    if (item.recommendedItems.isNotEmpty()) {
                        item {
                            RecommendedMediaCarousel(
                                recommendedItems = item.recommendedItems,
                                watchlistStatusMap = watchlistStatusMap,
                                hideWatchlist = uiPreferences.hideWatchlistItemsInRecommended,
                                onToggleHide = { viewModel.toggleHideWatchlistItemsInRecommended(it) },
                                onNavigateToDetail = onNavigateToDetail,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    if (trackedWatchlistItem != null) {
                        item {
                            NotificationSettingsCard(
                                isMovie = isMovie,
                                notifyEveryEpisode = notifyEveryEpisode,
                                notifySeasonFinished = notifySeasonFinished,
                                onNotifyEveryEpisodeChange = { isChecked ->
                                    viewModel.toggleNotification(
                                        simklId = simklId,
                                        notifyEpisode = isChecked,
                                        notifySeasonFinished = notifySeasonFinished
                                    )
                                },
                                onNotifySeasonFinishedChange = { isChecked ->
                                    viewModel.toggleNotification(
                                        simklId = simklId,
                                        notifyEpisode = notifyEveryEpisode,
                                        notifySeasonFinished = isChecked
                                    )
                                },
                                checkPermission = { checkAndRequestNotificationPermission() },
                                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp)
                            )
                        }

                        item {
                            DownloadSettingsCard(
                                watchlistItem = trackedWatchlistItem!!,
                                autoDownloadPrefs = autoDownloadPrefs,
                                itemSettings = itemSettings,
                                isDownloaderInstalled = isDownloaderInstalled,
                                availableSubdirectories = availableSubdirectories,
                                onSaveItemDownloadSettings = { viewModel.saveItemDownloadSettings(it) },
                                onSearchTorrents = { term -> viewModel.detectTorrents(simklId = simklId, customTitle = term) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            if (manualSearchSeasonNum != null && item != null) {
                val seasonOfItem = manualSearchSeasonNum!!
                val season = item.animeMetadata?.season ?: seasonOfItem
                val initialSearchTitle = remember(item, itemSettings) {
                    item.getSearchTitle(itemSettings)
                }
                ManualSearchDialog(
                    initialTitle = initialSearchTitle,
                    mediaType = item.mediaType,
                    animeSeasonOrSeason = season,
                    itemSettings = itemSettings,
                    initialIncludeSeason = true,
                    dialogTitle = "Search Season $season Manually",
                    onPerformSearch = { customTitle ->
                        viewModel.searchSeasonTorrents(
                            simklId = simklId,
                            season = seasonOfItem,
                            customTitle = customTitle,
                        )
                    },
                    onSelectResult = { selectedResult ->
                        viewModel.downloadSeasonResult(
                            simklId = simklId,
                            season = seasonOfItem,
                            resultItem = selectedResult,
                            onResult = { _, msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    },
                    onDismiss = { viewModel.setManualSearchSeason(null) }
                )
            }

            val targetMovieCalendarItem = targetMovieCalendarItemDelegate // required for smart casting
            if (isMovieManualSearchOpen && targetMovieCalendarItem != null) {
                val initialSearchTitle = remember(targetMovieCalendarItem.watchlistItem) {
                    targetMovieCalendarItem.getSearchTitle()
                }
                ManualSearchDialog(
                    initialTitle = initialSearchTitle,
                    mediaType = MediaType.MOVIE,
                    animeSeasonOrSeason = null,
                    itemSettings = itemSettings,
                    initialIncludeSeason = false,
                    dialogTitle = "Search Movie Manually",
                    onPerformSearch = { customTitle ->
                        viewModel.searchTorrents(
                            item = targetMovieCalendarItem,
                            customTitle = customTitle,
                        )
                    },
                    onSelectResult = { selectedResult ->
                        viewModel.downloadItemResult(
                            item = targetMovieCalendarItem,
                            resultItem = selectedResult,
                            onResult = { _, msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    },
                    onDismiss = { viewModel.setMovieManualSearchOpen(false) }
                )
            }

            if (manualSearchCalendarItem != null) {
                val activeItem = manualSearchCalendarItem!!
                val initialSearchTitle = remember(activeItem.watchlistItem) {
                    activeItem.getSearchTitle()
                }
                ManualSearchDialog(
                    initialTitle = initialSearchTitle,
                    mediaType = activeItem.type,
                    animeSeasonOrSeason = activeItem.animeSeason ?: activeItem.season,
                    itemSettings = itemSettings,
                    initialIncludeSeason = false,
                    dialogTitle = "Search Episode Manually",
                    onPerformSearch = { customTitle ->
                        viewModel.searchTorrents(
                            item = activeItem,
                            customTitle = customTitle,
                        )
                    },
                    onSelectResult = { selectedResult ->
                        viewModel.downloadItemResult(
                            item = activeItem,
                            resultItem = selectedResult,
                            onResult = { _, msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    },
                    onDismiss = { viewModel.setManualSearchCalendarItem(null) }
                )
            }
        }
    }
}

@Composable
fun ManualSearchActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SurfacePrimaryVariant,
            contentColor = PurpleContainerSoft
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Search manually", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun CarouselHeaderWithToggle(
    title: String,
    hideWatchlist: Boolean,
    onToggleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 15.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Hide watchlist",
                fontSize = 11.sp,
                color = TextSubtle
            )
            ExpressiveSwitch(
                checked = hideWatchlist,
                onCheckedChange = onToggleChange,
                modifier = Modifier.scale(0.7f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SurfaceDark,
                    checkedTrackColor = PurpleLight,
                    uncheckedThumbColor = TextSubtle,
                    uncheckedTrackColor = SurfaceContainer
                )
            )
        }
    }
}

@Composable
private fun MediaSeasonHeaderActions(
    hasSearchableEpisode: Boolean,
    isFullyWatched: Boolean,
    isUpdatingWatchStatus: Boolean,
    hasCalendarItems: Boolean,
    commonMediaStatus: MediaStatus?,
    onManualSearchSeason: () -> Unit,
    onMarkSeasonWatched: (Boolean) -> Unit,
    onUpdateSeasonMediaStatus: (MediaStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        if (hasSearchableEpisode) {
            ManualSearchButton(onManualSearchSeason)
        }

        WatchedStatusDropdown(
            isWatched = isFullyWatched,
            onStatusChange = onMarkSeasonWatched,
            isLoading = isUpdatingWatchStatus
        )

        if (hasCalendarItems) {
            MediaStatusDropdown(
                currentStatus = commonMediaStatus ?: MediaStatus.IGNORED,
                onStatusChange = onUpdateSeasonMediaStatus
            )
        }
    }
}

@Composable
private fun ManualSearchButton(onManualSearch: () -> Unit) {
    OutlinedButton(
        onClick = onManualSearch,
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        border = BorderStroke(1.dp, PurpleLight.copy(alpha = 0.5f)),
        modifier = Modifier.height(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = PurpleLight,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Search", fontSize = 11.sp, color = PurpleLight, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EpisodeDownloadProgressCell(
    downloadTaskId: String?,
    downloadsFlow: StateFlow<Map<String, DownloadProgress>>,
    modifier: Modifier = Modifier
) {
    if (downloadTaskId == null) return
    val progressFlow = remember(downloadTaskId) {
        downloadsFlow
            .map { it[downloadTaskId] }
            .distinctUntilChanged()
    }
    val progress by progressFlow.collectAsState(initial = null)
    progress?.let { prog ->
        EpisodeDownloadProgress(downloadProgress = prog, modifier = modifier)
    }
}

@Composable
private fun EpisodeRowActionsContent(
    epRow: EpisodeListItemUiModel.EpisodeRow,
    onMarkEpisodeWatched: (EpisodeListItemUiModel.EpisodeRow, Boolean) -> Unit,
    onUpdateMediaStatus: (EpisodeListItemUiModel.EpisodeRow, MediaStatus) -> Unit,
    onManualSearchEpisode: (CalendarItemWithWatchlist) -> Unit
) {
    if (epRow.calendarItem != null && epRow.calendarItem.mediaStatus.isSearchable) {
        ManualSearchButton(onManualSearch = { onManualSearchEpisode(epRow.calendarItem) })
    }

    WatchedStatusDropdown(
        isWatched = epRow.isWatched,
        onStatusChange = { watched -> onMarkEpisodeWatched(epRow, watched) },
        isLoading = epRow.isUpdatingWatchStatus
    )

    if (epRow.calendarItem != null) {
        ItemMediaStatusDropdown(
            item = epRow.calendarItem,
            onStatusChange = { status -> onUpdateMediaStatus(epRow, status) }
        )
    }
}

@Composable
private fun EpisodeRowActions(
    epRow: EpisodeListItemUiModel.EpisodeRow,
    onMarkEpisodeWatched: (EpisodeListItemUiModel.EpisodeRow, Boolean) -> Unit,
    onUpdateMediaStatus: (EpisodeListItemUiModel.EpisodeRow, MediaStatus) -> Unit,
    onManualSearchEpisode: (CalendarItemWithWatchlist) -> Unit,
    isSmallScreen: Boolean,
    modifier: Modifier = Modifier
) {
    if (isSmallScreen) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.End,
            modifier = modifier
        ) {
            EpisodeRowActionsContent(
                epRow = epRow,
                onMarkEpisodeWatched = onMarkEpisodeWatched,
                onUpdateMediaStatus = onUpdateMediaStatus,
                onManualSearchEpisode = onManualSearchEpisode
            )
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
        ) {
            EpisodeRowActionsContent(
                epRow = epRow,
                onMarkEpisodeWatched = onMarkEpisodeWatched,
                onUpdateMediaStatus = onUpdateMediaStatus,
                onManualSearchEpisode = onManualSearchEpisode
            )
        }
    }
}

@Composable
fun MediaEpisodeCardHeader(
    title: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    singleSeasonHeader: EpisodeListItemUiModel.SeasonHeader?,
    onMarkSeasonWatched: (Int, Boolean) -> Unit,
    onUpdateSeasonMediaStatus: (Int, MediaStatus) -> Unit,
    onManualSearchSeason: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = if (isExpanded) RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp) else RoundedCornerShape(12.dp)
    val sliceModifier = if (isExpanded) {
        modifier
            .fillMaxWidth()
            .episodeListSliceBorder(EpisodeListSliceType.HEADER)
    } else {
        modifier
            .fillMaxWidth()
            .border(1.dp, OutlineVariant, shape)
    }

    Surface(
        color = SurfaceContainer,
        shape = shape,
        modifier = sliceModifier.clickable { onToggleExpand() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (singleSeasonHeader != null) {
                    MediaSeasonHeaderActions(
                        hasSearchableEpisode = singleSeasonHeader.hasSearchableEpisode,
                        isFullyWatched = singleSeasonHeader.isFullyWatched,
                        isUpdatingWatchStatus = singleSeasonHeader.isSeasonUpdatingWatchStatus,
                        hasCalendarItems = singleSeasonHeader.hasCalendarItems,
                        commonMediaStatus = singleSeasonHeader.commonMediaStatus,
                        onManualSearchSeason = { onManualSearchSeason(singleSeasonHeader.season) },
                        onMarkSeasonWatched = { watched -> onMarkSeasonWatched(singleSeasonHeader.season, watched) },
                        onUpdateSeasonMediaStatus = { status -> onUpdateSeasonMediaStatus(singleSeasonHeader.season, status) }
                    )

                    Spacer(modifier = Modifier.width(4.dp))
                }

                val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "arrowRotation")
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color.White,
                    modifier = Modifier.rotate(rotation)
                )
            }
        }
    }
}

@Composable
fun MediaSeasonHeader(
    seasonHeader: EpisodeListItemUiModel.SeasonHeader,
    onMarkSeasonWatched: (Int, Boolean) -> Unit,
    onUpdateSeasonMediaStatus: (Int, MediaStatus) -> Unit,
    onManualSearchSeason: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (seasonHeader.showTitleText) Arrangement.SpaceBetween else Arrangement.End
    ) {
        if (seasonHeader.showTitleText) {
            Text(
                text = "Season ${seasonHeader.season} (${seasonHeader.totalEpisodes})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PurpleLight
            )
        }

        MediaSeasonHeaderActions(
            hasSearchableEpisode = seasonHeader.hasSearchableEpisode,
            isFullyWatched = seasonHeader.isFullyWatched,
            isUpdatingWatchStatus = seasonHeader.isSeasonUpdatingWatchStatus,
            hasCalendarItems = seasonHeader.hasCalendarItems,
            commonMediaStatus = seasonHeader.commonMediaStatus,
            onManualSearchSeason = { onManualSearchSeason(seasonHeader.season) },
            onMarkSeasonWatched = { watched -> onMarkSeasonWatched(seasonHeader.season, watched) },
            onUpdateSeasonMediaStatus = { status -> onUpdateSeasonMediaStatus(seasonHeader.season, status) }
        )
    }
}

@Composable
private fun EpisodeCodeBadge(
    episodeCode: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = SurfacePurple,
        contentColor = PurpleContainerSoft,
        modifier = modifier
    ) {
        Text(
            text = episodeCode,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EpisodeRowTitleAndDate(
    title: String,
    date: Instant?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (date != null) {
            Text(
                text = DateUtil.formatDisplayDate(date),
                color = TextSubtle,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun EpisodeRowInfo(
    epRow: EpisodeListItemUiModel.EpisodeRow,
    downloadsFlow: StateFlow<Map<String, DownloadProgress>>,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        EpisodeCodeBadge(episodeCode = epRow.episodeCode)

        FlowRow(
            itemVerticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EpisodeRowTitleAndDate(title = epRow.title, date = epRow.date)
            EpisodeDownloadProgressCell(
                downloadTaskId = epRow.calendarItem?.downloadTaskId,
                downloadsFlow = downloadsFlow
            )
        }
    }
}

@Composable
fun EpisodeRowContent(
    epRow: EpisodeListItemUiModel.EpisodeRow,
    downloadsFlow: StateFlow<Map<String, DownloadProgress>>,
    onMarkEpisodeWatched: (EpisodeListItemUiModel.EpisodeRow, Boolean) -> Unit,
    onUpdateMediaStatus: (EpisodeListItemUiModel.EpisodeRow, MediaStatus) -> Unit,
    onNavigateToReleaseDetail: (CalendarItemWithWatchlist) -> Unit,
    onManualSearchEpisode: (CalendarItemWithWatchlist) -> Unit,
    isSmallScreen: Boolean,
    modifier: Modifier = Modifier
) {
    val calItem = epRow.calendarItem
    val clickableModifier = if (calItem != null) {
        Modifier.clickable { onNavigateToReleaseDetail(calItem) }
    } else {
        Modifier
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, OutlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(clickableModifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EpisodeRowInfo(
                epRow = epRow,
                downloadsFlow = downloadsFlow,
                modifier = Modifier.weight(1f)
            )

            EpisodeRowActions(
                epRow = epRow,
                onMarkEpisodeWatched = onMarkEpisodeWatched,
                onUpdateMediaStatus = onUpdateMediaStatus,
                onManualSearchEpisode = onManualSearchEpisode,
                isSmallScreen = isSmallScreen
            )
        }
    }
}

@Composable
fun MediaEpisodeCardItemRow(
    item: EpisodeListItemUiModel,
    downloadsFlow: StateFlow<Map<String, DownloadProgress>>,
    onMarkEpisodeWatched: (EpisodeListItemUiModel.EpisodeRow, Boolean) -> Unit,
    onUpdateMediaStatus: (EpisodeListItemUiModel.EpisodeRow, MediaStatus) -> Unit,
    onMarkSeasonWatched: (Int, Boolean) -> Unit,
    onUpdateSeasonMediaStatus: (Int, MediaStatus) -> Unit,
    onManualSearchSeason: (Int) -> Unit,
    onNavigateToReleaseDetail: (CalendarItemWithWatchlist) -> Unit,
    onManualSearchEpisode: (CalendarItemWithWatchlist) -> Unit,
    isSmallScreen: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SurfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .episodeListSliceBorder(EpisodeListSliceType.MIDDLE)
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            when (item) {
                is EpisodeListItemUiModel.SeasonHeader -> MediaSeasonHeader(
                    seasonHeader = item,
                    onMarkSeasonWatched = onMarkSeasonWatched,
                    onUpdateSeasonMediaStatus = onUpdateSeasonMediaStatus,
                    onManualSearchSeason = onManualSearchSeason
                )
                is EpisodeListItemUiModel.EpisodeRow -> EpisodeRowContent(
                    epRow = item,
                    downloadsFlow = downloadsFlow,
                    onMarkEpisodeWatched = onMarkEpisodeWatched,
                    onUpdateMediaStatus = onUpdateMediaStatus,
                    onNavigateToReleaseDetail = onNavigateToReleaseDetail,
                    onManualSearchEpisode = onManualSearchEpisode,
                    isSmallScreen = isSmallScreen
                )
            }
        }
    }
}

@Composable
fun MediaEpisodeCardEmptyFooter(
    modifier: Modifier = Modifier
) {
    Surface(
        color = SurfaceContainer,
        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
        modifier = modifier
            .fillMaxWidth()
            .episodeListSliceBorder(EpisodeListSliceType.FOOTER)
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text = "No episodes available", fontSize = 13.sp, color = TextSubtle)
        }
    }
}

@Composable
fun MediaEpisodeCardBottomSpacer(
    modifier: Modifier = Modifier
) {
    Surface(
        color = SurfaceContainer,
        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
        modifier = modifier
            .fillMaxWidth()
            .episodeListSliceBorder(EpisodeListSliceType.FOOTER)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
    }
}

fun LazyListScope.mediaEpisodeCardSection(
    uiState: EpisodeListUiState,
    downloadsFlow: StateFlow<Map<String, DownloadProgress>>,
    isExpanded: Boolean,
    headerTitle: String,
    isSmallScreen: Boolean,
    onToggleExpand: () -> Unit,
    onMarkEpisodeWatched: (EpisodeListItemUiModel.EpisodeRow, Boolean) -> Unit,
    onUpdateMediaStatus: (EpisodeListItemUiModel.EpisodeRow, MediaStatus) -> Unit,
    onMarkSeasonWatched: (Int, Boolean) -> Unit,
    onUpdateSeasonMediaStatus: (Int, MediaStatus) -> Unit,
    onManualSearchSeason: (Int) -> Unit,
    onNavigateToReleaseDetail: (CalendarItemWithWatchlist) -> Unit,
    onManualSearchEpisode: (CalendarItemWithWatchlist) -> Unit
) {
    item(key = "episode_header") {
        MediaEpisodeCardHeader(
            title = headerTitle,
            isExpanded = isExpanded,
            onToggleExpand = onToggleExpand,
            singleSeasonHeader = uiState.singleSeasonHeader,
            onMarkSeasonWatched = onMarkSeasonWatched,
            onUpdateSeasonMediaStatus = onUpdateSeasonMediaStatus,
            onManualSearchSeason = onManualSearchSeason,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = if (isExpanded) 0.dp else 8.dp)
        )
    }
    if (isExpanded) {
        if (uiState.items.isEmpty()) {
            item(key = "episode_card_empty") {
                MediaEpisodeCardEmptyFooter(
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = 16.dp)
                )
            }
        } else {
            items(
                items = uiState.items,
                key = { it.key }
            ) { episodeItem ->
                MediaEpisodeCardItemRow(
                    item = episodeItem,
                    downloadsFlow = downloadsFlow,
                    onMarkEpisodeWatched = onMarkEpisodeWatched,
                    onUpdateMediaStatus = onUpdateMediaStatus,
                    onMarkSeasonWatched = onMarkSeasonWatched,
                    onUpdateSeasonMediaStatus = onUpdateSeasonMediaStatus,
                    onManualSearchSeason = onManualSearchSeason,
                    onNavigateToReleaseDetail = onNavigateToReleaseDetail,
                    onManualSearchEpisode = onManualSearchEpisode,
                    isSmallScreen = isSmallScreen,
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = 16.dp)
                )
            }
            item(key = "episode_card_bottom") {
                MediaEpisodeCardBottomSpacer(
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
fun RelatedMediaCarousel(
    relations: List<RelatedMediaItem>,
    watchlistStatusMap: Map<Int, WatchlistStatus>,
    onNavigateToDetail: (Int, MediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Related",
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(relations, key = { "${it.relatedSimklId}_${it.mediaType}" }) { rel ->
                val watchlistStatus = watchlistStatusMap[rel.relatedSimklId]
                MediaItemCard(
                    simklId = rel.relatedSimklId,
                    title = rel.title,
                    poster = rel.poster,
                    year = rel.year,
                    mediaType = rel.mediaType,
                    topStartBadge = rel.relationType.displayName,
                    topEndBadge = if (rel.isDirect == false) "Spin-off" else null,
                    subtitleBadge = null,
                    watchlistStatus = watchlistStatus,
                    onNavigateToDetail = onNavigateToDetail,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
fun SimilarMediaCarousel(
    similarItems: List<SimilarMediaItem>,
    watchlistStatusMap: Map<Int, WatchlistStatus>,
    hideWatchlist: Boolean,
    onToggleHide: (Boolean) -> Unit,
    onNavigateToDetail: (Int, MediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayItems = remember(similarItems, watchlistStatusMap, hideWatchlist) {
        if (hideWatchlist) similarItems.filter { watchlistStatusMap[it.similarSimklId] == null } else similarItems
    }

    if (similarItems.isNotEmpty()) {
        Column(modifier = modifier.fillMaxWidth()) {
            CarouselHeaderWithToggle(
                title = "Similar",
                hideWatchlist = hideWatchlist,
                onToggleChange = onToggleHide
            )
            if (displayItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "No items available",
                        fontSize = 12.sp,
                        color = TextSubtle
                    )
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayItems, key = { "${it.similarSimklId}_${it.mediaType}" }) { sim ->
                        val watchlistStatus = watchlistStatusMap[sim.similarSimklId]
                        MediaItemCard(
                            simklId = sim.similarSimklId,
                            title = sim.title,
                            poster = sim.poster,
                            year = sim.year,
                            mediaType = sim.mediaType,
                            topStartBadge = null,
                            topEndBadge = null,
                            subtitleBadge = null,
                            watchlistStatus = watchlistStatus,
                            onNavigateToDetail = onNavigateToDetail,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecommendedMediaCarousel(
    recommendedItems: List<RecommendedMediaItem>,
    watchlistStatusMap: Map<Int, WatchlistStatus>,
    hideWatchlist: Boolean,
    onToggleHide: (Boolean) -> Unit,
    onNavigateToDetail: (Int, MediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayItems = remember(recommendedItems, watchlistStatusMap, hideWatchlist) {
        if (hideWatchlist) recommendedItems.filter { watchlistStatusMap[it.recommendedSimklId] == null } else recommendedItems
    }

    if (recommendedItems.isNotEmpty()) {
        Column(modifier = modifier.fillMaxWidth()) {
            CarouselHeaderWithToggle(
                title = "Recommended",
                hideWatchlist = hideWatchlist,
                onToggleChange = onToggleHide
            )
            if (displayItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "No items available",
                        fontSize = 12.sp,
                        color = TextSubtle
                    )
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayItems, key = { "${it.recommendedSimklId}_${it.mediaType}" }) { rec ->
                        val watchlistStatus = watchlistStatusMap[rec.recommendedSimklId]
                        MediaItemCard(
                            simklId = rec.recommendedSimklId,
                            title = rec.title,
                            poster = rec.poster,
                            year = rec.year,
                            mediaType = rec.mediaType,
                            topStartBadge = null,
                            topEndBadge = null,
                            subtitleBadge = rec.usersPercent,
                            watchlistStatus = watchlistStatus,
                            onNavigateToDetail = onNavigateToDetail,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaItemCard(
    simklId: Int,
    title: String,
    poster: String?,
    year: Int?,
    mediaType: MediaType,
    topStartBadge: String?,
    topEndBadge: String?,
    subtitleBadge: String?,
    watchlistStatus: WatchlistStatus?,
    onNavigateToDetail: (Int, MediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(110.dp)
            .clickable { onNavigateToDetail(simklId, mediaType) },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(110.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            WatchlistOverlayPosterWithBadge(
                posterFragment = poster,
                contentDescription = null,
                mediaType = mediaType,
                watchlistStatus = watchlistStatus,
                modifier = Modifier.width(110.dp),
                posterHeight = 150.dp,
                iconSize = 32.dp
            )

            if (!topStartBadge.isNullOrBlank() || !topEndBadge.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (!topStartBadge.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = topStartBadge,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    if (!topEndBadge.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = topEndBadge,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        val yearStr = year?.toString() ?: ""
        val infoText = buildString {
            if (yearStr.isNotBlank()) append(yearStr)
            if (!subtitleBadge.isNullOrBlank()) {
                if (isNotEmpty()) append(" • ")
                append(subtitleBadge)
            }
        }
        if (infoText.isNotBlank()) {
            Text(
                text = infoText,
                fontSize = 10.sp,
                color = TextSubtle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MediaDetailHeaderContent(
    simklId: Int,
    item: MediaItemWithDetails,
    categoryColor: Color,
    watchlistItem: WatchlistItem?,
    isUpdatingStatus: Boolean,
    onUpdateStatus: (WatchlistStatus?) -> Unit,
    targetSeason: Int,
    isTargetSeasonFullyWatched: Boolean,
    isTargetSeasonOrMovieSearchable: Boolean,
    isTargetSeasonUpdatingWatchStatus: Boolean,
    watchProgressState: WatchProgressUiState?,
    onMarkSeasonWatched: (Int) -> Unit,
    onManualSearch: (Int) -> Unit,
    onMovieManualSearch: () -> Unit,
    onMovieWatchedChange: (Boolean) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isNarrow = maxWidth < 600.dp

        if (isNarrow) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MediaDetailPosterColumn(
                    simklId = simklId,
                    item = item.mediaItem,
                    categoryColor = categoryColor,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    modifier = Modifier.fillMaxWidth()
                )
                MediaDetailInfoColumn(
                    simklId = simklId,
                    item = item.mediaItem,
                    watchProgressState = watchProgressState,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    modifier = Modifier.fillMaxWidth()
                )
                MediaDetailActionSection(
                    simklId = simklId,
                    mediaType = item.mediaType,
                    watchlistItem = watchlistItem,
                    isUpdatingStatus = isUpdatingStatus,
                    onUpdateStatus = onUpdateStatus,
                    targetSeason = targetSeason,
                    isTargetSeasonFullyWatched = isTargetSeasonFullyWatched,
                    isTargetSeasonOrMovieSearchable = isTargetSeasonOrMovieSearchable,
                    isTargetSeasonUpdatingWatchStatus = isTargetSeasonUpdatingWatchStatus,
                    watchProgressState = watchProgressState,
                    onMarkSeasonWatched = onMarkSeasonWatched,
                    onManualSearch = onManualSearch,
                    onMovieManualSearch = onMovieManualSearch,
                    onMovieWatchedChange = onMovieWatchedChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                MediaDetailPosterColumn(
                    simklId = simklId,
                    item = item.mediaItem,
                    categoryColor = categoryColor,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    modifier = Modifier.width(220.dp)
                )
                MediaDetailInfoColumn(
                    simklId = simklId,
                    item = item.mediaItem,
                    watchProgressState = watchProgressState,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    modifier = Modifier.weight(1f)
                )
                MediaDetailActionSection(
                    simklId = simklId,
                    mediaType = item.mediaType,
                    watchlistItem = watchlistItem,
                    isUpdatingStatus = isUpdatingStatus,
                    onUpdateStatus = onUpdateStatus,
                    targetSeason = targetSeason,
                    isTargetSeasonFullyWatched = isTargetSeasonFullyWatched,
                    isTargetSeasonOrMovieSearchable = isTargetSeasonOrMovieSearchable,
                    isTargetSeasonUpdatingWatchStatus = isTargetSeasonUpdatingWatchStatus,
                    watchProgressState = watchProgressState,
                    onMarkSeasonWatched = onMarkSeasonWatched,
                    onManualSearch = onManualSearch,
                    onMovieManualSearch = onMovieManualSearch,
                    onMovieWatchedChange = onMovieWatchedChange,
                    modifier = Modifier.width(200.dp)
                )
            }
        }
    }
}

@Composable
fun MediaDetailPosterColumn(
    simklId: Int,
    item: MediaItem,
    categoryColor: Color,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .sharedBoundsItem(
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                key = "poster_$simklId"
            )
            .clip(RoundedCornerShape(12.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val compactPosterUrl = remember(item.poster) { item.poster.toPosterUrl(PosterSize.COMPACT) }
        val mediumPosterUrl = remember(item.poster) { item.poster.toPosterUrl(PosterSize.MEDIUM) }
        val posterRequest = remember(mediumPosterUrl, compactPosterUrl, context) {
            ImageRequest.Builder(context)
                .data(mediumPosterUrl)
                .placeholderMemoryCacheKey(compactPosterUrl)
                .placeholder(ic_menu_gallery)
                .error(ic_menu_gallery)
                .crossfade(enable = true)
                .build()
        }

        AsyncImage(
            model = posterRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceGreyDark),
            error = painterResource(id = ic_menu_gallery)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(categoryColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.mediaType.displayName.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = item.mediaType.badgeTextColor,
            )
        }
    }
}

@Composable
fun MediaDetailInfoColumn(
    simklId: Int,
    item: MediaItem,
    watchProgressState: WatchProgressUiState?,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val displayTitle = remember(item) { item.displayTitle }

        Text(
            text = displayTitle,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.sharedElementItem(
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                key = "title_$simklId"
            )
        )

        if (!item.titleRomaji.isNullOrBlank()) {
            Text(
                text = item.titleRomaji,
                fontSize = 13.sp,
                color = TextSubtle
            )
        }

        val subtitle = remember(item, watchProgressState) {
            MediaFormatter.formatMediaSubtitle(
                item = item,
                unwatchedCount = watchProgressState?.unwatchedCount,
                notAiredCount = watchProgressState?.notAiredCount
            )
        }
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextSubtle
            )
        }

        if (item.genres.isNotEmpty()) {
            Text(
                text = item.genres.joinToString(" • "),
                fontSize = 12.sp,
                color = PurpleLight,
            )
        }

        if (item.rating != null && item.rating > 0f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = YellowAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = String.format(Locale.US, "%.1f", item.rating),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        MediaMovieReleaseDates(
            releaseDate = item.movieMetadata?.releaseDate,
            dvdDate = item.movieMetadata?.dvdDate
        )

        if (!item.description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.description,
                fontSize = 12.sp,
                color = TextGrey,
            )
        }
    }
}

@Composable
fun MediaReleaseDateRow(
    label: String,
    date: LocalDate,
    modifier: Modifier = Modifier
) {
    val formattedDate = DateUtil.formatDisplayDate(date)
    val relativeTime = DateUtil.formatRelativeTime(date)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSubtle
        )
        Text(
            text = "$formattedDate ($relativeTime)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

@Composable
fun MediaMovieReleaseDates(
    releaseDate: LocalDate?,
    dvdDate: LocalDate?,
    modifier: Modifier = Modifier
) {
    if (releaseDate == null && dvdDate == null) return

    Column(
        modifier = modifier.padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        releaseDate?.let {
            MediaReleaseDateRow(label = "Release Date:", date = it)
        }
        dvdDate?.let {
            MediaReleaseDateRow(label = "DVD / Digital Release:", date = it)
        }
    }
}

@Composable
fun MediaDetailActionSection(
    simklId: Int,
    mediaType: MediaType,
    watchlistItem: WatchlistItem?,
    isUpdatingStatus: Boolean,
    onUpdateStatus: (WatchlistStatus?) -> Unit,
    targetSeason: Int,
    isTargetSeasonFullyWatched: Boolean,
    isTargetSeasonOrMovieSearchable: Boolean,
    isTargetSeasonUpdatingWatchStatus: Boolean,
    watchProgressState: WatchProgressUiState?,
    onMarkSeasonWatched: (Int) -> Unit,
    onManualSearch: (Int) -> Unit,
    onMovieManualSearch: () -> Unit,
    onMovieWatchedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var watchlistStatusDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DetailHeaderOpenSimklButton(
            simklId = simklId,
            type = mediaType,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            iconSize = 18.dp,
            textSize = 12.sp
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            val isInWatchlist = watchlistItem != null
            val currentStatus = watchlistItem?.status
            val isWatching = currentStatus == WatchlistStatus.WATCHING
            val buttonText = if (isUpdatingStatus) "Updating..." else (currentStatus?.displayName ?: "Add to list")
            val currentIcon = if (isInWatchlist) currentStatus.overlayIcon ?: Icons.Default.Bookmark else Icons.Default.Add

            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isInWatchlist) SurfacePrimaryVariant else PurpleLight
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { watchlistStatusDropdownExpanded = true },
                        enabled = !isUpdatingStatus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = if (isWatching && watchProgressState != null && watchProgressState.totalAiredCount > 0) {
                            RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
                        } else {
                            RoundedCornerShape(10.dp)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isInWatchlist) SurfacePrimaryVariant else PurpleLight,
                            contentColor = if (isInWatchlist) PurpleContainerSoft else SurfaceDark
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        if (isUpdatingStatus) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = if (isInWatchlist) PurpleContainerSoft else SurfaceDark
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = currentIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = buttonText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (isWatching && watchProgressState != null && watchProgressState.totalAiredCount > 0) {
                        LinearProgressIndicator(
                            progress = { watchProgressState.percentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = PurpleLight,
                            trackColor = SurfacePrimaryVariant,
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = watchlistStatusDropdownExpanded,
                onDismissRequest = { watchlistStatusDropdownExpanded = false },
                modifier = Modifier.background(SurfaceContainer)
            ) {
                val allowedStatuses = if (mediaType == MediaType.MOVIE) {
                    listOf(WatchlistStatus.PLAN_TO_WATCH, WatchlistStatus.COMPLETED, WatchlistStatus.DROPPED)
                } else {
                    WatchlistStatus.entries.toList()
                }

                allowedStatuses.forEach { status ->
                    val icon = status.overlayIcon
                    DropdownMenuItem(
                        text = { Text(status.displayName, color = Color.White, fontSize = 14.sp) },
                        leadingIcon = if (icon != null) {
                            {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null,
                        onClick = {
                            watchlistStatusDropdownExpanded = false
                            onUpdateStatus(status)
                        }
                    )
                }

                if (isInWatchlist) {
                    HorizontalDivider(color = OutlineVariant)
                    DropdownMenuItem(
                        text = { Text("Remove from list", color = ErrorPink, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = ErrorPink,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            watchlistStatusDropdownExpanded = false
                            onUpdateStatus(null)
                        }
                    )
                }
            }
        }

        if (mediaType == MediaType.MOVIE) {
            val isCompleted = watchlistItem?.status == WatchlistStatus.COMPLETED
            if (!isCompleted) {
                MediaWatchButton(
                    isMarkingWatched = isTargetSeasonUpdatingWatchStatus,
                    isWatched = false,
                    unwatchedLabel = "Mark as Watched",
                    watchedLabel = "Watched",
                    onToggleWatched = {
                        onMovieWatchedChange(true)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textSize = 12.sp
                )
            }

            if (isTargetSeasonOrMovieSearchable) {
                ManualSearchActionButton(onClick = onMovieManualSearch)
            }
        } else {
            if (!isTargetSeasonFullyWatched) {
                val seasonLabel = if (mediaType == MediaType.ANIME && targetSeason == 1) "Season" else "Season $targetSeason"

                SeasonWatchButton(
                    isMarkingWatched = isTargetSeasonUpdatingWatchStatus,
                    isSeasonFullyWatched = false,
                    seasonLabel = seasonLabel,
                    onMarkSeasonWatched = { onMarkSeasonWatched(targetSeason) },
                    modifier = Modifier.fillMaxWidth(),
                    textSize = 12.sp
                )
            }

            if (isTargetSeasonOrMovieSearchable) {
                ManualSearchActionButton(onClick = { onManualSearch(targetSeason) })
            }
        }
    }
}
