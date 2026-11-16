package com.felixbrucker.simklcalendar.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.LazyPagingItems
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.WatchlistStatus
import com.felixbrucker.simklcalendar.data.network.SimklSearchItem
import com.felixbrucker.simklcalendar.data.util.PosterSize
import com.felixbrucker.simklcalendar.extensions.toPosterUrl
import com.felixbrucker.simklcalendar.ui.viewmodel.SimklSearchViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.preferences.ViewMode
import com.felixbrucker.simklcalendar.data.util.PermissionUtil
import com.felixbrucker.simklcalendar.ui.theme.ErrorPink
import com.felixbrucker.simklcalendar.ui.theme.ErrorRed
import com.felixbrucker.simklcalendar.ui.theme.YellowAccent
import java.util.Locale
import com.felixbrucker.simklcalendar.ui.theme.OutlineGrey
import com.felixbrucker.simklcalendar.ui.theme.OutlineVariant
import com.felixbrucker.simklcalendar.ui.theme.PurpleContainerLight
import com.felixbrucker.simklcalendar.ui.theme.PurpleContainerSoft
import com.felixbrucker.simklcalendar.ui.theme.PurpleLight
import com.felixbrucker.simklcalendar.ui.theme.SurfaceContainer
import com.felixbrucker.simklcalendar.ui.theme.SurfaceDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceGreyDark
import com.felixbrucker.simklcalendar.ui.theme.SurfacePrimaryVariant
import com.felixbrucker.simklcalendar.ui.theme.SurfacePurple
import com.felixbrucker.simklcalendar.ui.theme.SurfaceVariantDark
import com.felixbrucker.simklcalendar.ui.theme.TextGrey
import com.felixbrucker.simklcalendar.ui.theme.TextPrimary
import com.felixbrucker.simklcalendar.ui.theme.TextSubtle
import com.felixbrucker.simklcalendar.ui.viewmodel.CalendarViewModel
import com.felixbrucker.simklcalendar.ui.composable.MediaTypeBadge
import com.felixbrucker.simklcalendar.ui.composable.MediaTypeFilterChips
import com.felixbrucker.simklcalendar.ui.composable.overlayIcon
import com.felixbrucker.simklcalendar.ui.composable.posterAlpha
import com.felixbrucker.simklcalendar.ui.viewmodel.WatchlistTableItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private enum class SearchBarDisplayMode {
    DEFAULT,
    EXPANDED,
    DOCKED
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    viewModel: CalendarViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToTrending: () -> Unit,
    onNavigateToReleaseDetail: (CalendarItemWithWatchlist) -> Unit,
    onNavigateToWatchlistItemDetail: (WatchlistTableItem) -> Unit,
    onNavigateToDetail: (Int, MediaType) -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val context = LocalContext.current
    val totalFilteredCalendarItems by viewModel.totalFilteredCalendarItems.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val shouldShowAutoDownloadStatus by viewModel.shouldShowAutoDownloadStatus.collectAsState()
    val isSearchingWantedTorrents by viewModel.isSearchingWantedTorrents.collectAsState()
    val autoDownloadStatus by viewModel.autoDownloadStatus.collectAsState()
    val isDownloaderInstalled by viewModel.isTorrentServiceInstalled.collectAsState()
    val hasWantedCalendarItems by viewModel.hasWantedCalendarItems.collectAsState()
    val userToken by viewModel.userToken.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val isSmallScreen = with(density) { windowInfo.containerSize.width.toDp() } < 600.dp

    val uiPreferences by viewModel.uiPreferences.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()

    val calendarListState = rememberLazyListState()
    val tableListState = rememberLazyListState()
    val activeListState = if (viewMode == ViewMode.CALENDAR) calendarListState else tableListState

    var filterBarHeightPx by remember { mutableFloatStateOf(0f) }
    var filterBarOffsetPx by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (filterBarHeightPx <= 0f) return Offset.Zero

                val oldOffset = filterBarOffsetPx
                val newOffset = (oldOffset + delta).coerceIn(-filterBarHeightPx, 0f)
                val consumedY = newOffset - oldOffset
                filterBarOffsetPx = newOffset

                return Offset(0f, consumedY)
            }
        }
    }

    LaunchedEffect(activeListState) {
        snapshotFlow { activeListState.firstVisibleItemIndex to activeListState.firstVisibleItemScrollOffset }
            .collect { (currentIndex, currentOffset) ->
                if (currentIndex == 0 && currentOffset == 0) {
                    filterBarOffsetPx = 0f
                }
            }
    }

    val username = userToken?.username.takeIf { !it.isNullOrBlank() } ?: "Unknown"

    val apiSearchViewModel: SimklSearchViewModel = hiltViewModel()
    val apiSearchQuery by apiSearchViewModel.searchQuery.collectAsState()
    val apiSearchResults = apiSearchViewModel.searchResults.collectAsLazyPagingItems()
    val watchlistStatusMap by apiSearchViewModel.watchlistStatusMap.collectAsState()

    // Search UI State
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var isApiSearchActive by rememberSaveable { mutableStateOf(false) }
    var isSearchFocused by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    // Monitor IME visibility to handle keyboard dismiss
    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible && isSearchFocused) {
            focusManager.clearFocus()
            isSearchFocused = false
            if (searchQuery.isBlank()) {
                isSearchActive = false
            }
        }
    }

    // Determine current display mode
    val effectiveSearchDisplayMode = when {
        isApiSearchActive -> SearchBarDisplayMode.EXPANDED
        isSearchActive || isSearchFocused -> SearchBarDisplayMode.EXPANDED
        searchQuery.isNotBlank() -> SearchBarDisplayMode.DOCKED
        else -> SearchBarDisplayMode.DEFAULT
    }

    val activeSearchQuery = if (isApiSearchActive) apiSearchQuery else searchQuery

    // Back handler to exit search or clear query
    BackHandler(enabled = isApiSearchActive || isSearchActive || searchQuery.isNotBlank()) {
        if (isApiSearchActive) {
            if (apiSearchQuery.isNotBlank()) {
                apiSearchViewModel.clearSearchQuery()
            }
            isApiSearchActive = false
        } else {
            if (searchQuery.isNotBlank()) {
                viewModel.clearSearchQuery()
            }
            isSearchActive = false
            isSearchFocused = false
        }
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val alarmPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // Re-check permission if needed
    }

    val notificationPrefs by viewModel.notificationPreferences.collectAsState()

    LaunchedEffect(notificationPrefs.useExactAlarms) {
        val useExact = notificationPrefs.useExactAlarms

        if (useExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!PermissionUtil.hasExactAlarmPermission(context)) {
                val result = snackbarHostState.showSnackbar(
                    message = "Exact alarms are enabled but permission is missing.",
                    actionLabel = "Grant",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    alarmPermissionLauncher.launch(PermissionUtil.getExactAlarmPermissionIntent(context))
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        MainTopAppBarTitleSection(
                            searchDisplayMode = effectiveSearchDisplayMode,
                            searchQuery = activeSearchQuery,
                            username = username,
                            viewMode = viewMode,
                            isSmallScreen = isSmallScreen,
                            focusRequester = focusRequester,
                            onSearchQueryChange = {
                                if (isApiSearchActive) {
                                    apiSearchViewModel.setSearchQuery(it)
                                } else {
                                    viewModel.setSearchQuery(it)
                                }
                            },
                            onFocusChanged = { isSearchFocused = it },
                            onSearch = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                isSearchFocused = false
                            },
                            onClearSearch = {
                                if (isApiSearchActive) {
                                    if (apiSearchQuery.isNotEmpty()) {
                                        apiSearchViewModel.clearSearchQuery()
                                    } else {
                                        isApiSearchActive = false
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    }
                                } else {
                                    viewModel.clearSearchQuery()
                                    isSearchActive = false
                                    isSearchFocused = false
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }
                            },
                            onExpandSearch = {
                                isSearchActive = true
                                coroutineScope.launch {
                                    delay(50.milliseconds)
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                            },
                            onViewModeChange = { viewModel.setViewMode(it) },
                            isLoading = isApiSearchActive && apiSearchQuery.isNotBlank() && apiSearchResults.loadState.refresh is LoadState.Loading
                        )
                    },
                    actions = {
                        if (effectiveSearchDisplayMode == SearchBarDisplayMode.DEFAULT) {
                            if (isDownloaderInstalled && hasWantedCalendarItems) {
                                MainAutoDownloadActionButton(
                                    shouldShowAutoDownloadStatus = shouldShowAutoDownloadStatus,
                                    isSearchingWantedTorrents = isSearchingWantedTorrents,
                                    autoDownloadStatus = autoDownloadStatus,
                                    onRunAutoDownload = { viewModel.runAutoDownloadManual() }
                                )
                            }

                            if (isSmallScreen) {
                                MainMobileViewModeToggle(
                                    viewMode = viewMode,
                                    onViewModeToggle = {
                                        val nextMode =
                                            if (viewMode == ViewMode.CALENDAR) ViewMode.TABLE else ViewMode.CALENDAR
                                        viewModel.setViewMode(nextMode)
                                    }
                                )
                            }

                            IconButton(
                                onClick = {
                                    isSearchActive = true
                                    coroutineScope.launch {
                                        delay(100.milliseconds)
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    }
                                },
                                modifier = Modifier.testTag("search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search Calendar",
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = onNavigateToTrending,
                                modifier = Modifier.testTag("trending_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = "Trending",
                                    tint = Color.White
                                )
                            }
                            ApiSearchButton(
                                onClick = {
                                    isApiSearchActive = true
                                    coroutineScope.launch {
                                        delay(100.milliseconds)
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    }
                                }
                            )
                        }

                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SurfaceDark,
                        titleContentColor = Color.White
                    )
                )
            },
            containerColor = SurfaceDark
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                PullToRefreshBox(
                    isRefreshing = isSyncing,
                    onRefresh = { viewModel.syncLocalCalendar() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    val tvFilter = uiPreferences.filterShowTv
                    val animeFilter = uiPreferences.filterShowAnime
                    val moviesFilter = uiPreferences.filterShowMovies
                    val unwatchedFilter = uiPreferences.filterOnlyUnwatched
                    val premieresOnly = uiPreferences.filterOnlyPremieres
                    val finalesOnly = uiPreferences.filterOnlyFinales
                    val digitalDvdOnly = uiPreferences.filterOnlyDigitalDvd

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedScrollConnection)
                    ) {
                        // Toggles / Chip Filtering Bar (Shown in both views)
                        MainFilterBar(
                            viewMode = viewMode,
                            tvFilter = tvFilter,
                            animeFilter = animeFilter,
                            moviesFilter = moviesFilter,
                            unwatchedFilter = unwatchedFilter,
                            premieresOnly = premieresOnly,
                            finalesOnly = finalesOnly,
                            digitalDvdOnly = digitalDvdOnly,
                            onToggleShowTv = { viewModel.toggleShowTv() },
                            onToggleShowAnime = { viewModel.toggleShowAnime() },
                            onToggleShowMovies = { viewModel.toggleShowMovies() },
                            onToggleShowOnlyUnwatchedReleased = { viewModel.toggleShowOnlyUnwatchedReleased() },
                            onToggleOnlySeasonPremieres = { viewModel.toggleOnlySeasonPremieres() },
                            onToggleOnlySeasonFinales = { viewModel.toggleOnlySeasonFinales() },
                            onToggleOnlyDigitalDvd = { viewModel.toggleOnlyDigitalDvd() },
                            modifier = Modifier
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    val measuredHeight = placeable.height
                                    if (measuredHeight > 0 && filterBarHeightPx != measuredHeight.toFloat()) {
                                        filterBarHeightPx = measuredHeight.toFloat()
                                        filterBarOffsetPx =
                                            filterBarOffsetPx.coerceIn(-filterBarHeightPx, 0f)
                                    }

                                    val layoutHeight =
                                        (measuredHeight + filterBarOffsetPx.roundToInt()).coerceAtLeast(
                                            0
                                        )

                                    layout(placeable.width, layoutHeight) {
                                        placeable.placeRelative(0, filterBarOffsetPx.roundToInt())
                                    }
                                }
                                .clipToBounds()
                        )

                        // Search Results Status Pill (when searching)
                        if (searchQuery.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Results for \"$searchQuery\" ($totalFilteredCalendarItems)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = PurpleLight
                                )
                                Text(
                                    text = "Clear",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ErrorPink,
                                    modifier = Modifier.clickable {
                                        viewModel.clearSearchQuery()
                                        isSearchActive = false
                                        isSearchFocused = false
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Conditionally render Calendar View or Table View with slide transitions
                        AnimatedContent(
                            targetState = viewMode,
                            transitionSpec = {
                                if (targetState == ViewMode.TABLE) {
                                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                                        slideOutHorizontally { width -> -width } + fadeOut()
                                } else {
                                    slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                        slideOutHorizontally { width -> width } + fadeOut()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            label = "view_mode_content_transition"
                        ) { mode ->
                            if (mode == ViewMode.CALENDAR) {
                                CalendarView(
                                    viewModel = viewModel,
                                    onNavigateToShowDetail = onNavigateToReleaseDetail,
                                    lazyListState = calendarListState,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            } else {
                                TrackedWatchlistTableView(
                                    viewModel = viewModel,
                                    onNavigateToSeriesDetail = onNavigateToWatchlistItemDetail,
                                    lazyListState = tableListState,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isApiSearchActive,
                    enter = fadeIn(animationSpec = tween(250)),
                    exit = fadeOut(animationSpec = tween(200))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.8f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    isApiSearchActive = false
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }
                            )
                    ) {
                        ApiSearchResultsList(
                            searchResults = apiSearchResults,
                            watchlistStatusMap = watchlistStatusMap,
                            onUpdateDisplayedIds = { apiSearchViewModel.updateDisplayedSimklIds(it) },
                            onNavigateToDetail = { simklId, mediaType ->
                                onNavigateToDetail(simklId, mediaType)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTopAppBarTitleSection(
    searchDisplayMode: SearchBarDisplayMode,
    searchQuery: String,
    username: String,
    viewMode: ViewMode,
    isSmallScreen: Boolean,
    focusRequester: FocusRequester,
    onSearchQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onExpandSearch: () -> Unit,
    onViewModeChange: (ViewMode) -> Unit,
    isLoading: Boolean = false
) {
    AnimatedContent(
        targetState = searchDisplayMode,
        transitionSpec = {
            if (targetState == SearchBarDisplayMode.EXPANDED) {
                (slideInHorizontally(animationSpec = tween(280)) { width -> width } + fadeIn(animationSpec = tween(250))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(200)) { width -> -width / 4 } + fadeOut(animationSpec = tween(200)))
            } else if (initialState == SearchBarDisplayMode.EXPANDED && targetState == SearchBarDisplayMode.DEFAULT) {
                (slideInHorizontally(animationSpec = tween(200)) { width -> -width / 4 } + fadeIn(animationSpec = tween(200))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(280)) { width -> width } + fadeOut(animationSpec = tween(250)))
            } else {
                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
            }
        },
        label = "top_bar_search_transition"
    ) { mode ->
        when (mode) {
            SearchBarDisplayMode.EXPANDED -> {
                MainExpandedSearchBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    focusRequester = focusRequester,
                    onFocusChanged = onFocusChanged,
                    onSearch = onSearch,
                    onClearSearch = onClearSearch,
                    isLoading = isLoading
                )
            }

            SearchBarDisplayMode.DOCKED -> {
                MainDockedSearchBar(
                    searchQuery = searchQuery,
                    onClick = onExpandSearch,
                    onClearSearch = onClearSearch
                )
            }

            SearchBarDisplayMode.DEFAULT -> {
                MainDefaultTitleBar(
                    username = username,
                    viewMode = viewMode,
                    isSmallScreen = isSmallScreen,
                    onViewModeChange = onViewModeChange
                )
            }
        }
    }
}

@Composable
private fun MainExpandedSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    isLoading: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(SurfaceContainer)
            .border(1.dp, PurpleLight, RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = PurpleLight
            )
        } else {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search active",
                tint = PurpleLight,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        BasicTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { state -> onFocusChanged(state.isFocused) }
                .testTag("search_input_field"),
            singleLine = true,
            textStyle = TextStyle(
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
            ),
            cursorBrush = SolidColor(PurpleLight),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search show, episode, movie...",
                            color = TextGrey,
                            fontSize = 14.sp
                        )
                    }
                    innerTextField()
                }
            }
        )

        IconButton(
            onClick = onClearSearch,
            modifier = Modifier
                .size(32.dp)
                .testTag("clear_search_button")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete search query",
                tint = TextSubtle,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MainDockedSearchBar(
    searchQuery: String,
    onClick: () -> Unit,
    onClearSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceContainer)
            .border(1.dp, OutlineGrey, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = PurpleLight,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = searchQuery,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onClearSearch,
            modifier = Modifier
                .size(32.dp)
                .testTag("clear_search_button")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete search query",
                tint = TextSubtle,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MainDefaultTitleBar(
    username: String,
    viewMode: ViewMode,
    isSmallScreen: Boolean,
    onViewModeChange: (ViewMode) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Simkl Calendar",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
            Text(
                text = "Hi, $username • Tracked Schedule",
                fontSize = 12.sp,
                color = TextSubtle,
                fontWeight = FontWeight.Normal,
                lineHeight = 16.sp
            )
        }

        if (!isSmallScreen) {
            ViewModeToggle(
                viewMode = viewMode,
                onViewModeChange = onViewModeChange,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun MainAutoDownloadActionButton(
    shouldShowAutoDownloadStatus: Boolean,
    isSearchingWantedTorrents: Boolean,
    autoDownloadStatus: String,
    onRunAutoDownload: () -> Unit
) {
    AnimatedContent(
        targetState = shouldShowAutoDownloadStatus,
        transitionSpec = {
            (fadeIn(animationSpec = tween(300)) + expandHorizontally()).togetherWith(
                fadeOut(animationSpec = tween(300)) + shrinkHorizontally()
            )
        },
        label = "auto_download_action_transition"
    ) { showAutoDownloadStatus ->
        if (showAutoDownloadStatus) {
            Row(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(SurfaceContainer)
                    .border(1.dp, OutlineVariant, RoundedCornerShape(20.dp))
                    .animateContentSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(22.dp)
                ) {
                    if (isSearchingWantedTorrents) {
                        CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 2.dp,
                            color = PurpleLight
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = PurpleLight,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = autoDownloadStatus,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1
                )
            }
        } else {
            IconButton(
                onClick = onRunAutoDownload,
                modifier = Modifier.testTag("auto_download_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Search Wanted Episodes",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun MainMobileViewModeToggle(
    viewMode: ViewMode,
    onViewModeToggle: () -> Unit
) {
    IconButton(
        onClick = onViewModeToggle,
        modifier = Modifier.testTag("view_mode_toggle_mobile")
    ) {
        AnimatedContent(
            targetState = viewMode,
            transitionSpec = {
                (fadeIn(animationSpec = tween(220, delayMillis = 90)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
                    .togetherWith(fadeOut(animationSpec = tween(90)) + scaleOut(targetScale = 0.92f, animationSpec = tween(90)))
            },
            label = "view_mode_icon_transition"
        ) { currentMode ->
            val icon = if (currentMode == ViewMode.CALENDAR) Icons.Default.TableChart else Icons.Default.CalendarToday
            Icon(
                imageVector = icon,
                contentDescription = "Switch View Mode",
                tint = Color.White
            )
        }
    }
}

@Composable
fun MainFilterBar(
    viewMode: ViewMode,
    tvFilter: Boolean,
    animeFilter: Boolean,
    moviesFilter: Boolean,
    unwatchedFilter: Boolean,
    premieresOnly: Boolean,
    finalesOnly: Boolean,
    digitalDvdOnly: Boolean,
    onToggleShowTv: () -> Unit,
    onToggleShowAnime: () -> Unit,
    onToggleShowMovies: () -> Unit,
    onToggleShowOnlyUnwatchedReleased: () -> Unit,
    onToggleOnlySeasonPremieres: () -> Unit,
    onToggleOnlySeasonFinales: () -> Unit,
    onToggleOnlyDigitalDvd: () -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
            MediaTypeFilterChips(
                showTv = tvFilter,
                showAnime = animeFilter,
                showMovies = moviesFilter,
                onToggleShowTv = onToggleShowTv,
                onToggleShowAnime = onToggleShowAnime,
                onToggleShowMovies = onToggleShowMovies
            )

            // Visual Separator
            VerticalDivider(
                modifier = Modifier
                    .height(24.dp)
                    .padding(horizontal = 4.dp),
                color = OutlineVariant
            )

            // Unwatched Released Toggle (Only in Table View)
            if (viewMode == ViewMode.TABLE) {
                FilterChip(
                    selected = unwatchedFilter,
                    onClick = onToggleShowOnlyUnwatchedReleased,
                    label = { Text("Unwatched") },
                    leadingIcon = {
                        if (unwatchedFilter) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PurpleLight,
                        selectedLabelColor = SurfacePurple,
                        containerColor = SurfaceGreyDark,
                        labelColor = TextSubtle
                    )
                )
            }

            // Calendar Subtype Filters (Only in Calendar View)
            if (viewMode == ViewMode.CALENDAR) {
                FilterChip(
                    selected = premieresOnly,
                    onClick = onToggleOnlySeasonPremieres,
                    label = { Text("Season Premiere") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PurpleContainerLight,
                        selectedLabelColor = SurfaceVariantDark,
                        containerColor = SurfaceGreyDark,
                        labelColor = TextSubtle
                    )
                )

                FilterChip(
                    selected = finalesOnly,
                    onClick = onToggleOnlySeasonFinales,
                    label = { Text("Season Finale") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ErrorRed,
                        selectedLabelColor = Color.White,
                        containerColor = SurfaceGreyDark,
                        labelColor = TextSubtle
                    )
                )

                FilterChip(
                    selected = digitalDvdOnly,
                    onClick = onToggleOnlyDigitalDvd,
                    label = { Text("Digital / DVD") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SurfacePrimaryVariant,
                        selectedLabelColor = PurpleContainerSoft,
                        containerColor = SurfaceGreyDark,
                        labelColor = TextSubtle
                    )
                )
            }
    }
}

@Composable
private fun ViewModeToggle(
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val isCalendar = viewMode == ViewMode.CALENDAR
    val containerWidth = 180.dp
    val containerHeight = 40.dp
    val badgeWidth = containerWidth * 3 / 5

    Box(
        modifier = modifier
            .height(containerHeight)
            .width(containerWidth)
            .clip(RoundedCornerShape(containerHeight / 2))
            .background(SurfaceContainer)
    ) {
        // Animated Selection Badge
        val targetOffset = if (isCalendar) 0.dp else containerWidth - badgeWidth
        val animatedOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            label = "selection_badge_offset"
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToPx(), 0) }
                .fillMaxHeight()
                .width(badgeWidth)
                .clip(RoundedCornerShape((containerHeight) / 2))
                .background(PurpleLight)
        )

        // Content Row
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Calendar Side
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (isCalendar) {
                            return@clickable
                        }
                        onViewModeChange(ViewMode.CALENDAR)
                    },
                horizontalArrangement = if (isCalendar) Arrangement.End else Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = if (isCalendar) SurfacePurple else TextSubtle,
                    modifier = Modifier.size(18.dp)
                )
                AnimatedVisibility(
                    visible = isCalendar,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Text(
                        text = "Calendar",
                        color = SurfacePurple,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp),
                        maxLines = 1
                    )
                }
            }

            // Library Side
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isCalendar) {
                            return@clickable
                        }
                        onViewModeChange(ViewMode.TABLE)
                    },
                horizontalArrangement = if (!isCalendar) Arrangement.Start else Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TableChart,
                    contentDescription = null,
                    tint = if (!isCalendar) SurfacePurple else TextSubtle,
                    modifier = Modifier.size(18.dp)
                )
                AnimatedVisibility(
                    visible = !isCalendar,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Text(
                        text = "Library",
                        color = SurfacePurple,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}


@Composable
fun ApiSearchButton(
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.testTag("api_search_button")
    ) {
        Icon(
            imageVector = Icons.Default.TravelExplore,
            contentDescription = "Global Search",
            tint = Color.White
        )
    }
}

@Composable
fun SearchItemCard(
    item: SimklSearchItem,
    watchlistStatus: WatchlistStatus?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchItemPoster(item = item, watchlistStatus = watchlistStatus)
            Spacer(modifier = Modifier.width(12.dp))
            SearchItemDetails(item = item)
        }
    }
}

@Composable
fun SearchItemPoster(
    item: SimklSearchItem,
    watchlistStatus: WatchlistStatus?
) {
    val context = LocalContext.current
    val posterUrl = remember(item.poster) { item.poster.toPosterUrl(PosterSize.COMPACT) }
    val overlayIcon = watchlistStatus.overlayIcon
    val isFaded = watchlistStatus != null

    Box(
        modifier = Modifier
            .width(48.dp)
            .height(70.dp)
            .clip(RoundedCornerShape(6.dp))
            .alpha(watchlistStatus.posterAlpha),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(posterUrl).crossfade(enable = true).build(),
            contentDescription = "${item.displayTitle} Poster",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (isFaded && overlayIcon != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = overlayIcon,
                    contentDescription = watchlistStatus.displayName,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun SearchItemDetails(
    item: SimklSearchItem
) {
    val sub = item.displaySubtitle
    val mediaType = item.mediaType
    val rating = item.rating
    val hasRating = rating != null && rating > 0.0

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MediaTypeBadge(mediaType = mediaType)
            Text(
                text = item.displayTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        if (!sub.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = sub,
                fontSize = 12.sp,
                color = TextSubtle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val subInfoText = remember(item) {
            buildList {
                item.year?.let { add(it.toString()) }
                item.parsedAnimeType?.displayName?.let { add(it) }
                item.epCount?.takeIf { it > 0 }?.let { count ->
                    add(if (count == 1) "1 Episode" else "$count Episodes")
                }
                item.parsedStatus?.displayName?.let { add(it) }
            }.joinToString(" • ")
        }

        if (hasRating || subInfoText.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (rating != null && rating > 0.0) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = YellowAccent,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f", rating),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (subInfoText.isNotBlank()) {
                        Text(
                            text = "•",
                            fontSize = 11.sp,
                            color = TextGrey
                        )
                    }
                }
                if (subInfoText.isNotBlank()) {
                    Text(
                        text = subInfoText,
                        fontSize = 11.sp,
                        color = TextGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}

@Composable
fun SearchItemSkeletonCard(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonPosterBox(alpha = alpha)
            Spacer(modifier = Modifier.width(12.dp))
            SkeletonDetailsColumn(alpha = alpha)
        }
    }
}

@Composable
fun SkeletonPosterBox(alpha: Float) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(70.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(OutlineVariant.copy(alpha = alpha))
    )
}

@Composable
fun SkeletonDetailsColumn(alpha: Float) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(OutlineVariant.copy(alpha = alpha))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(OutlineVariant.copy(alpha = alpha))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.3f)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(OutlineVariant.copy(alpha = alpha))
        )
    }
}

@Composable
fun ApiSearchResultsList(
    searchResults: LazyPagingItems<SimklSearchItem>,
    watchlistStatusMap: Map<Int, WatchlistStatus>,
    onUpdateDisplayedIds: (List<Int>) -> Unit,
    onNavigateToDetail: (Int, MediaType) -> Unit
) {
    val displayedIds = remember(searchResults.itemCount) {
        (0 until searchResults.itemCount).mapNotNull { index ->
            searchResults.peek(index)?.ids?.simklId
        }
    }

    LaunchedEffect(displayedIds) {
        onUpdateDisplayedIds(displayedIds)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .testTag("api_search_results_list"),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(
            count = searchResults.itemCount,
            key = { index -> searchResults.peek(index)?.ids?.simklId ?: index }
        ) { index ->
            val item = searchResults[index]
            if (item != null) {
                val watchlistStatus = watchlistStatusMap[item.ids.simklId]
                SearchItemCard(
                    item = item,
                    watchlistStatus = watchlistStatus,
                    onClick = { onNavigateToDetail(item.ids.simklId, item.mediaType) },
                    modifier = Modifier.animateItem()
                )
            } else {
                SearchItemSkeletonCard(
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}
