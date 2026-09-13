package com.felixbrucker.simklcalendar.ui.screens

import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.felixbrucker.simklcalendar.data.util.PermissionUtil
import com.felixbrucker.simklcalendar.data.util.DateUtil
import com.felixbrucker.simklcalendar.ui.viewmodel.CalendarViewModel
import com.felixbrucker.simklcalendar.ui.viewmodel.MainViewMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private enum class SearchBarDisplayMode {
    DEFAULT,
    EXPANDED,
    DOCKED
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    viewModel: CalendarViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToReleaseDetail: (String) -> Unit,
    onNavigateToWatchlistItemDetail: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val items by viewModel.filteredCalendarItems.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val shouldShowAutoDownloadStatus by viewModel.shouldShowAutoDownloadStatus.collectAsState()
    val isSearchingWantedTorrents by viewModel.isSearchingWantedTorrents.collectAsState()
    val autoDownloadStatus by viewModel.autoDownloadStatus.collectAsState()
    val isDownloaderInstalled by viewModel.isTorrentServiceInstalled.collectAsState()
    val hasWantedCalendarItems by viewModel.hasWantedCalendarItems.collectAsState()
    val userToken by viewModel.userToken.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val torrentDownloads by viewModel.torrentDownloads.collectAsState()

    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val isSmallScreen = with(density) { windowInfo.containerSize.width.toDp() } < 600.dp

    // Filters states are now handled inside CalendarView
    val showEarlierReleases by viewModel.showEarlierReleases.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()

    val username = userToken?.username ?: "Guest"

    // Search UI State
    var isSearchActive by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
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
    val searchDisplayMode = when {
        isSearchActive || isSearchFocused -> SearchBarDisplayMode.EXPANDED
        searchQuery.isNotBlank() -> SearchBarDisplayMode.DOCKED
        else -> SearchBarDisplayMode.DEFAULT
    }

    // Back handler to exit search or clear query
    BackHandler(enabled = isSearchActive || searchQuery.isNotBlank()) {
        if (searchQuery.isNotBlank()) {
            viewModel.clearSearchQuery()
        }
        isSearchActive = false
        isSearchFocused = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    // Separate earlier releases from today/upcoming releases
    val (earlierItems, upcomingItems) = remember(items) {
        items.partition { DateUtil.isEarlierThanToday(it.date) }
    }

    val earlierGrouped = remember(earlierItems) {
        earlierItems.groupBy { item ->
            DateUtil.formatAiringDateHeader(item.date)
        }
    }

    val upcomingGrouped = remember(upcomingItems) {
        upcomingItems.groupBy { item ->
            DateUtil.formatAiringDateHeader(item.date)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val alarmPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // Re-check permission if needed, but the snackbar is a one-time thing here
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        val useExact = prefs.getBoolean("use_exact_alarms", false)

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
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
                                // Full slide-out focused search input field
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(Color(0xFF2B2930))
                                        .border(1.dp, Color(0xFFD0BCFF), RoundedCornerShape(22.dp))
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search active",
                                        tint = Color(0xFFD0BCFF),
                                        modifier = Modifier.size(20.dp)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { viewModel.setSearchQuery(it) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(focusRequester)
                                            .onFocusChanged { state ->
                                                isSearchFocused = state.isFocused
                                            }
                                            .testTag("search_input_field"),
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = Color(0xFFE6E1E5),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Normal
                                        ),
                                        cursorBrush = SolidColor(Color(0xFFD0BCFF)),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(
                                            onSearch = {
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                isSearchFocused = false
                                                if (searchQuery.isBlank()) {
                                                    isSearchActive = false
                                                }
                                            }
                                        ),
                                        decorationBox = { innerTextField ->
                                            Box(contentAlignment = Alignment.CenterStart) {
                                                if (searchQuery.isEmpty()) {
                                                    Text(
                                                        text = "Search show, episode, movie...",
                                                        color = Color(0xFF938F99),
                                                        fontSize = 14.sp
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    )

                                    IconButton(
                                        onClick = {
                                            viewModel.clearSearchQuery()
                                            isSearchActive = false
                                            isSearchFocused = false
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("clear_search_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete search query",
                                            tint = Color(0xFFCAC4D0),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            SearchBarDisplayMode.DOCKED -> {
                                // Slid-back docked search bar visible with query, tap to edit, X to clear
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color(0xFF2B2930))
                                        .border(1.dp, Color(0xFF79747E), RoundedCornerShape(20.dp))
                                        .clickable {
                                            isSearchActive = true
                                            coroutineScope.launch {
                                                delay(50.milliseconds)
                                                focusRequester.requestFocus()
                                                keyboardController?.show()
                                            }
                                        }
                                        .padding(start = 12.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = Color(0xFFD0BCFF),
                                        modifier = Modifier.size(18.dp)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = searchQuery,
                                        color = Color(0xFFE6E1E5),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = {
                                            viewModel.clearSearchQuery()
                                            isSearchActive = false
                                            isSearchFocused = false
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("clear_search_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete search query",
                                            tint = Color(0xFFCAC4D0),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            SearchBarDisplayMode.DEFAULT -> {
                                // Standard Calendar Title & Subtitle + View Mode Toggle
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
                                            color = Color(0xFFCAC4D0),
                                            fontWeight = FontWeight.Normal,
                                            lineHeight = 16.sp
                                        )
                                    }

                                    if (!isSmallScreen) {
                                        ViewModeToggle(
                                            viewMode = viewMode,
                                            onViewModeChange = { viewModel.setViewMode(it) },
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (searchDisplayMode == SearchBarDisplayMode.DEFAULT) {
                        if (isDownloaderInstalled && hasWantedCalendarItems) {
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
                                            .background(Color(0xFF2B2930))
                                            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(20.dp))
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
                                                    color = Color(0xFFD0BCFF)
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = null,
                                                tint = Color(0xFFD0BCFF),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = autoDownloadStatus,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFE6E1E5),
                                            maxLines = 1
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = { viewModel.runAutoDownloadManual() },
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

                        if (isSmallScreen) {
                            IconButton(
                                onClick = {
                                    val nextMode = if (viewMode == MainViewMode.CALENDAR) MainViewMode.TABLE else MainViewMode.CALENDAR
                                    viewModel.setViewMode(nextMode)
                                },
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
                                    val icon = if (currentMode == MainViewMode.CALENDAR) Icons.Default.TableChart else Icons.Default.CalendarToday
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = "Switch View Mode",
                                        tint = Color.White
                                    )
                                }
                            }
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
                    containerColor = Color(0xFF1C1B1F),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF1C1B1F)
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = { viewModel.syncLocalCalendar() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val tvFilter by viewModel.showTv.collectAsState()
            val animeFilter by viewModel.showAnime.collectAsState()
            val moviesFilter by viewModel.showMovies.collectAsState()
            val unwatchedFilter by viewModel.showOnlyUnwatchedReleased.collectAsState()
            val premieresOnly by viewModel.onlySeasonPremieres.collectAsState()
            val finalesOnly by viewModel.onlySeasonFinales.collectAsState()
            val digitalDvdOnly by viewModel.onlyDigitalDvd.collectAsState()

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Toggles / Chip Filtering Bar (Shown in both views)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    // TV Toggle
                    FilterChip(
                        selected = tvFilter,
                        onClick = { viewModel.toggleShowTv() },
                        label = { Text("TV") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFBAC3FF),
                            selectedLabelColor = Color(0xFF1A237E),
                            containerColor = Color(0xFF313033),
                            labelColor = Color(0xFFCAC4D0)
                        )
                    )

                    // Anime Toggle
                    FilterChip(
                        selected = animeFilter,
                        onClick = { viewModel.toggleShowAnime() },
                        label = { Text("Anime") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE8DEF8),
                            selectedLabelColor = Color(0xFF1D192B),
                            containerColor = Color(0xFF313033),
                            labelColor = Color(0xFFCAC4D0)
                        )
                    )

                    // Movies Toggle
                    FilterChip(
                        selected = moviesFilter,
                        onClick = { viewModel.toggleShowMovies() },
                        label = { Text("Movies") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFF2B8B5),
                            selectedLabelColor = Color(0xFF601410),
                            containerColor = Color(0xFF313033),
                            labelColor = Color(0xFFCAC4D0)
                        )
                    )

                    // Visual Separator
                    VerticalDivider(
                        modifier = Modifier
                            .height(24.dp)
                            .padding(horizontal = 4.dp),
                        color = Color(0xFF49454F)
                    )

                    // Unwatched Released Toggle (Only in Table View)
                    if (viewMode == MainViewMode.TABLE) {
                        FilterChip(
                            selected = unwatchedFilter,
                            onClick = { viewModel.toggleShowOnlyUnwatchedReleased() },
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
                                selectedContainerColor = Color(0xFFD0BCFF),
                                selectedLabelColor = Color(0xFF381E72),
                                containerColor = Color(0xFF313033),
                                labelColor = Color(0xFFCAC4D0)
                            )
                        )
                    }

                    // Calendar Subtype Filters (Only in Calendar View)
                    if (viewMode == MainViewMode.CALENDAR) {
                        FilterChip(
                            selected = premieresOnly,
                            onClick = { viewModel.toggleOnlySeasonPremieres() },
                            label = { Text("Season Premiere") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFE8DEF8),
                                selectedLabelColor = Color(0xFF1D192B),
                                containerColor = Color(0xFF313033),
                                labelColor = Color(0xFFCAC4D0)
                            )
                        )

                        FilterChip(
                            selected = finalesOnly,
                            onClick = { viewModel.toggleOnlySeasonFinales() },
                            label = { Text("Season Finale") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFB3261E),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF313033),
                                labelColor = Color(0xFFCAC4D0)
                            )
                        )

                        FilterChip(
                            selected = digitalDvdOnly,
                            onClick = { viewModel.toggleOnlyDigitalDvd() },
                            label = { Text("Digital / DVD") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF4F378B),
                                selectedLabelColor = Color(0xFFEADDFF),
                                containerColor = Color(0xFF313033),
                                labelColor = Color(0xFFCAC4D0)
                            )
                        )
                    }
                }

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
                            text = "Results for \"$searchQuery\" (${items.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFD0BCFF)
                        )
                        Text(
                            text = "Clear",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF2B8B5),
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
                        if (targetState == MainViewMode.TABLE) {
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
                    if (mode == MainViewMode.CALENDAR) {
                        CalendarView(
                            items = items,
                            earlierItems = earlierItems,
                            upcomingGrouped = upcomingGrouped,
                            earlierGrouped = earlierGrouped,
                            showEarlierReleases = showEarlierReleases,
                            searchQuery = searchQuery,
                            torrentDownloads = torrentDownloads,
                            viewModel = viewModel,
                            onNavigateToShowDetail = onNavigateToReleaseDetail,
                        )
                    } else {
                        TrackedWatchlistTableView(
                            viewModel = viewModel,
                            onNavigateToSeriesDetail = onNavigateToWatchlistItemDetail
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewModeToggle(
    viewMode: MainViewMode,
    onViewModeChange: (MainViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val isCalendar = viewMode == MainViewMode.CALENDAR
    val containerWidth = 180.dp
    val containerHeight = 40.dp
    val badgeWidth = containerWidth * 3 / 5

    Box(
        modifier = modifier
            .height(containerHeight)
            .width(containerWidth)
            .clip(RoundedCornerShape(containerHeight / 2))
            .background(Color(0xFF2B2930))
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
                .background(Color(0xFFD0BCFF))
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
                        onViewModeChange(MainViewMode.CALENDAR)
                    },
                horizontalArrangement = if (isCalendar) Arrangement.End else Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = if (isCalendar) Color(0xFF381E72) else Color(0xFFCAC4D0),
                    modifier = Modifier.size(18.dp)
                )
                AnimatedVisibility(
                    visible = isCalendar,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Text(
                        text = "Calendar",
                        color = Color(0xFF381E72),
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
                        onViewModeChange(MainViewMode.TABLE)
                    },
                horizontalArrangement = if (!isCalendar) Arrangement.Start else Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TableChart,
                    contentDescription = null,
                    tint = if (!isCalendar) Color(0xFF381E72) else Color(0xFFCAC4D0),
                    modifier = Modifier.size(18.dp)
                )
                AnimatedVisibility(
                    visible = !isCalendar,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Text(
                        text = "Library",
                        color = Color(0xFF381E72),
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
