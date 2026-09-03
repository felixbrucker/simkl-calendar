package com.felixbrucker.simklcalendar.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.util.DateUtil
import com.felixbrucker.simklcalendar.data.util.PosterSize
import com.felixbrucker.simklcalendar.data.util.formattedEpisodeCardBadge
import com.felixbrucker.simklcalendar.data.util.toPosterUrl
import com.felixbrucker.simklcalendar.R
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.util.DownloadProgress
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
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToShowDetail: (String) -> Unit,
    onNavigateToSeriesDetail: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items by viewModel.filteredCalendarItems.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val shouldShowAutoDownloadStatus by viewModel.shouldShowAutoDownloadStatus.collectAsState()
    val isSearchingWantedTorrents by viewModel.isSearchingWantedTorrents.collectAsState()
    val autoDownloadStatus by viewModel.autoDownloadStatus.collectAsState()
    val isDownloaderInstalled by viewModel.isTorrentServiceInstalled.collectAsState()
    val userToken by viewModel.userToken.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val torrentDownloads by viewModel.torrentDownloads.collectAsState()

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
                                // Standard Calendar Title & Subtitle
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
                            }
                        }
                    }
                },
                actions = {
                    if (searchDisplayMode == SearchBarDisplayMode.DEFAULT) {
                        IconButton(
                            onClick = {
                                val nextMode = if (viewMode == MainViewMode.CALENDAR) MainViewMode.TABLE else MainViewMode.CALENDAR
                                viewModel.setViewMode(nextMode)
                            },
                            modifier = Modifier.testTag("view_mode_toggle_button")
                        ) {
                            AnimatedContent(
                                targetState = viewMode,
                                transitionSpec = {
                                    (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                                },
                                label = "view_mode_icon_transition"
                            ) { mode ->
                                Icon(
                                    imageVector = if (mode == MainViewMode.CALENDAR) Icons.Default.TableChart else Icons.Default.CalendarToday,
                                    contentDescription = if (mode == MainViewMode.CALENDAR) "Switch to Table View" else "Switch to Calendar View",
                                    tint = Color.White
                                )
                            }
                        }

                        if (isDownloaderInstalled) {
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // TV Toggle
                    FilterChip(
                        selected = tvFilter,
                        onClick = { viewModel.showTv.value = !tvFilter },
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
                        onClick = { viewModel.showAnime.value = !animeFilter },
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
                        onClick = { viewModel.showMovies.value = !moviesFilter },
                        label = { Text("Movies") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFF2B8B5),
                            selectedLabelColor = Color(0xFF601410),
                            containerColor = Color(0xFF313033),
                            labelColor = Color(0xFFCAC4D0)
                        )
                    )

                    // Visual Separator
                    val showAdditional = (viewMode == MainViewMode.TABLE) || (viewMode == MainViewMode.CALENDAR)
                    if (showAdditional) {
                        VerticalDivider(
                            modifier = Modifier
                                .height(24.dp)
                                .padding(horizontal = 4.dp),
                            color = Color(0xFF49454F)
                        )
                    }

                    // Unwatched Released Toggle (Only in Table View)
                    if (viewMode == MainViewMode.TABLE) {
                        FilterChip(
                            selected = unwatchedFilter,
                            onClick = { viewModel.showOnlyUnwatchedReleased.value = !unwatchedFilter },
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
                            onClick = { viewModel.onlySeasonPremieres.value = !premieresOnly },
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
                            onClick = { viewModel.onlySeasonFinales.value = !finalesOnly },
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
                            onClick = { viewModel.onlyDigitalDvd.value = !digitalDvdOnly },
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

                // Conditionally render Calendar View or Table View
                if (viewMode == MainViewMode.CALENDAR) {
                        CalendarView(
                            items = items,
                            earlierItems = earlierItems,
                            upcomingGrouped = upcomingGrouped,
                            earlierGrouped = earlierGrouped,
                            showEarlierReleases = showEarlierReleases,
                            searchQuery = searchQuery,
                            torrentDownloads = torrentDownloads,
                            viewModel = viewModel,
                            onNavigateToShowDetail = onNavigateToShowDetail,
                            snackbarHostState = snackbarHostState
                        )
                } else {
                    TrackedWatchlistTableView(
                        viewModel = viewModel,
                        onNavigateToSeriesDetail = onNavigateToSeriesDetail
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalendarView(
    items: List<CalendarItemWithWatchlist>,
    earlierItems: List<CalendarItemWithWatchlist>,
    upcomingGrouped: Map<String, List<CalendarItemWithWatchlist>>,
    earlierGrouped: Map<String, List<CalendarItemWithWatchlist>>,
    showEarlierReleases: Boolean,
    searchQuery: String,
    torrentDownloads: Map<String, DownloadProgress>,
    viewModel: CalendarViewModel,
    onNavigateToShowDetail: (String) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(8.dp))

        // Calendar Group list
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (searchQuery.isNotBlank()) Icons.Default.SearchOff else Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = Color(0xFF3E3D4F),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (searchQuery.isNotBlank()) {
                            "No releases found matching \"$searchQuery\""
                        } else {
                            "No releases found matching filters"
                        },
                        color = Color(0xFFA5A3B1),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (searchQuery.isNotBlank()) {
                        TextButton(
                            onClick = {
                                viewModel.clearSearchQuery()
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        ) {
                            Text("Clear Search Query", color = Color(0xFFD0BCFF))
                        }
                    } else {
                        TextButton(
                            onClick = {
                                viewModel.showTv.value = true
                                viewModel.showAnime.value = true
                                viewModel.showMovies.value = true
                                viewModel.onlySeasonPremieres.value = false
                                viewModel.onlySeasonFinales.value = false
                                viewModel.onlyDigitalDvd.value = false
                            },
                        ) {
                            Text("Reset Active Filters", color = Color(0xFFD0BCFF))
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Earlier Releases Expandable Header Card
                if (earlierItems.isNotEmpty()) {
                    item(key = "earlier_releases_toggle_card") {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (showEarlierReleases) Color(0xFF381E72).copy(alpha = 0.5f) else Color(0xFF2B2930)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (showEarlierReleases) Color(0xFFD0BCFF) else Color(0xFF49454F)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { viewModel.showEarlierReleases.value = !showEarlierReleases }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = Color(0xFFD0BCFF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = if (showEarlierReleases) "Hide Earlier Releases" else "Show Earlier Releases",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = Color(0xFFE6E1E5)
                                        )
                                        Text(
                                            text = if (searchQuery.isNotBlank()) {
                                                "${earlierItems.size} matching past ${if (earlierItems.size == 1) "release" else "releases"}"
                                            } else {
                                                "${earlierItems.size} past ${if (earlierItems.size == 1) "release" else "releases"} hidden by default"
                                            },
                                            fontSize = 12.sp,
                                            color = Color(0xFFCAC4D0)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (showEarlierReleases) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (showEarlierReleases) "Collapse earlier releases" else "Expand earlier releases",
                                    tint = Color(0xFFD0BCFF),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // When expanded, render earlier day groups
                    if (showEarlierReleases) {
                        earlierGrouped.forEach { (dateHeader, dayItems) ->
                            stickyHeader(key = "earlier_header_$dateHeader") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1C1B1F))
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = dateHeader,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF9E9AA3),
                                        letterSpacing = 1.sp
                                    )
                                }
                            }

                            items(dayItems, key = { "earlier_${it.primaryKey}" }) { item ->
                                SwipeableCalendarItemCard(
                                    modifier = Modifier.animateItem(),
                                    item = item,
                                    downloadProgress = torrentDownloads[item.downloadTaskId],
                                    onClick = { onNavigateToShowDetail(item.primaryKey) },
                                    onMarkEpisodeWatched = {
                                        if (item.type == MediaType.MOVIE) {
                                            viewModel.markMovieWatched(
                                                simklId = item.simklId,
                                                showTitle = item.title
                                            ) { success, msg ->
                                                if (success) {
                                                    coroutineScope.launch {
                                                        val result = snackbarHostState.showSnackbar(
                                                            message = msg,
                                                            actionLabel = "Revert",
                                                            duration = SnackbarDuration.Short
                                                        )
                                                        if (result == SnackbarResult.ActionPerformed) {
                                                            viewModel.markMovieUnwatched(
                                                                simklId = item.simklId,
                                                                showTitle = item.title
                                                            ) { _, revertMsg ->
                                                                coroutineScope.launch { snackbarHostState.showSnackbar(revertMsg) }
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                                }
                                            }
                                        } else {
                                            viewModel.markEpisodeWatched(
                                                simklId = item.simklId,
                                                season = item.season,
                                                episodeNumber = item.episodeNumber ?: 1,
                                                mediaType = item.type,
                                                showTitle = item.title
                                            ) { success, msg ->
                                                if (success) {
                                                    coroutineScope.launch {
                                                        val result = snackbarHostState.showSnackbar(
                                                            message = msg,
                                                            actionLabel = "Revert",
                                                            duration = SnackbarDuration.Short
                                                        )
                                                        if (result == SnackbarResult.ActionPerformed) {
                                                            viewModel.markEpisodeUnwatched(
                                                                simklId = item.simklId,
                                                                season = item.season,
                                                                episodeNumber = item.episodeNumber ?: 1,
                                                                mediaType = item.type,
                                                                showTitle = item.title
                                                            ) { _, revertMsg ->
                                                                coroutineScope.launch { snackbarHostState.showSnackbar(revertMsg) }
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                                }
                                            }
                                        }
                                    },
                                    onMarkSeasonWatched = {
                                        if (item.type != MediaType.MOVIE) {
                                            viewModel.markSeasonWatched(
                                                simklId = item.simklId,
                                                season = item.season ?: 1,
                                                mediaType = item.type,
                                                showTitle = item.title
                                            ) { success, msg ->
                                                if (success) {
                                                    coroutineScope.launch {
                                                        val result = snackbarHostState.showSnackbar(
                                                            message = msg,
                                                            actionLabel = "Revert",
                                                            duration = SnackbarDuration.Short
                                                        )
                                                        if (result == SnackbarResult.ActionPerformed) {
                                                            viewModel.markSeasonUnwatched(
                                                                simklId = item.simklId,
                                                                season = item.season ?: 1,
                                                                mediaType = item.type,
                                                                showTitle = item.title
                                                            ) { _, revertMsg ->
                                                                coroutineScope.launch { snackbarHostState.showSnackbar(revertMsg) }
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Upcoming releases (today and future dates)
                if (upcomingGrouped.isNotEmpty()) {
                    upcomingGrouped.forEach { (dateHeader, dayItems) ->
                        stickyHeader(key = "upcoming_header_$dateHeader") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1C1B1F))
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = dateHeader,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCAC4D0),
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        items(dayItems, key = { it.primaryKey }) { item ->
                            SwipeableCalendarItemCard(
                                modifier = Modifier.animateItem(),
                                item = item,
                                downloadProgress = torrentDownloads[item.downloadTaskId],
                                onClick = { onNavigateToShowDetail(item.primaryKey) },
                                onMarkEpisodeWatched = {
                                    if (item.type == MediaType.MOVIE) {
                                        viewModel.markMovieWatched(
                                            simklId = item.simklId,
                                            showTitle = item.title
                                        ) { success, msg ->
                                            if (success) {
                                                coroutineScope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = msg,
                                                        actionLabel = "Revert",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        viewModel.markMovieUnwatched(
                                                            simklId = item.simklId,
                                                            showTitle = item.title
                                                        ) { _, revertMsg ->
                                                            coroutineScope.launch { snackbarHostState.showSnackbar(revertMsg) }
                                                        }
                                                    }
                                                }
                                            } else {
                                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                            }
                                        }
                                    } else {
                                        viewModel.markEpisodeWatched(
                                            simklId = item.simklId,
                                            season = item.season,
                                            episodeNumber = item.episodeNumber ?: 1,
                                            mediaType = item.type,
                                            showTitle = item.title
                                        ) { success, msg ->
                                            if (success) {
                                                coroutineScope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = msg,
                                                        actionLabel = "Revert",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        viewModel.markEpisodeUnwatched(
                                                            simklId = item.simklId,
                                                            season = item.season,
                                                            episodeNumber = item.episodeNumber ?: 1,
                                                            mediaType = item.type,
                                                            showTitle = item.title
                                                        ) { _, revertMsg ->
                                                            coroutineScope.launch { snackbarHostState.showSnackbar(revertMsg) }
                                                        }
                                                    }
                                                }
                                            } else {
                                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                            }
                                        }
                                    }
                                },
                                onMarkSeasonWatched = {
                                    if (item.type != MediaType.MOVIE) {
                                        viewModel.markSeasonWatched(
                                            simklId = item.simklId,
                                            season = item.season ?: 1,
                                            mediaType = item.type,
                                            showTitle = item.title
                                        ) { success, msg ->
                                            if (success) {
                                                coroutineScope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = msg,
                                                        actionLabel = "Revert",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        viewModel.markSeasonUnwatched(
                                                            simklId = item.simklId,
                                                            season = item.season ?: 1,
                                                            mediaType = item.type,
                                                            showTitle = item.title
                                                        ) { _, revertMsg ->
                                                            coroutineScope.launch { snackbarHostState.showSnackbar(revertMsg) }
                                                        }
                                                    }
                                                }
                                            } else {
                                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                } else if (earlierItems.isNotEmpty() && !showEarlierReleases) {
                    // Notice when upcoming is empty but earlier items exist
                    item(key = "no_upcoming_prompt") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.EventAvailable,
                                    contentDescription = null,
                                    tint = Color(0xFF3E3D4F),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    if (searchQuery.isNotBlank()) {
                                        "No upcoming releases matching \"$searchQuery\""
                                    } else {
                                        "No upcoming releases for active filters"
                                    },
                                    color = Color(0xFFA5A3B1),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                  )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { viewModel.showEarlierReleases.value = true }) {
                                    Text(
                                        if (searchQuery.isNotBlank()) {
                                            "View ${earlierItems.size} Matching Earlier Releases"
                                        } else {
                                            "View ${earlierItems.size} Earlier Releases"
                                        },
                                        color = Color(0xFFD0BCFF)
                                    )
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
fun CalendarItemCard(
    item: CalendarItemWithWatchlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    downloadProgress: DownloadProgress? = null
) {
    val categoryColor = when (item.type) {
        MediaType.ANIME -> Color(0xFFD0BCFF)
        MediaType.MOVIE -> Color(0xFFF2B8B5)
        MediaType.TV -> Color(0xFFBAC3FF)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2B2930)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.mediaStatus == MediaStatus.DOWNLOADING) Color(0xFF004A77) else Color(0xFF49454F)
        ),
        modifier = modifier
            .clickable(onClick = onClick)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Coil Async Image loading cropped poster
                Box(
                    modifier = Modifier
                        .size(width = 60.dp, height = 90.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF313033))
                ) {
                    AsyncImage(
                        model = item.poster.toPosterUrl(PosterSize.COMPACT),
                        contentDescription = "${item.title} Poster",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = painterResource(id = android.R.drawable.ic_menu_gallery)
                    )

                    // Slim type overlay bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(categoryColor)
                            .align(Alignment.BottomStart)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Details Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Type Badge
                        Badge(
                            containerColor = categoryColor.copy(alpha = 0.2f),
                            contentColor = categoryColor
                        ) {
                            Text(item.type.displayName.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                        }

                        // Downloading badge
                        if (item.mediaStatus == MediaStatus.DOWNLOADING) {
                            Badge(
                                containerColor = Color(0xFF004A77),
                                contentColor = Color(0xFFC2E8FF)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Downloading",
                                        modifier = Modifier.size(10.dp),
                                        tint = Color(0xFFC2E8FF)
                                    )
                                    Text(
                                        text = "DOWNLOADING",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFC2E8FF)
                                    )
                                }
                            }
                        }

                        // Downloaded badge
                        if (item.mediaStatus == MediaStatus.DOWNLOADED) {
                            Badge(
                                containerColor = Color(0xFF1E3A2B),
                                contentColor = Color(0xFF7CE49F)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Downloaded",
                                        modifier = Modifier.size(10.dp),
                                        tint = Color(0xFF7CE49F)
                                    )
                                    Text(
                                        text = "DOWNLOADED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF7CE49F)
                                    )
                                }
                            }
                        }

                        // Premiere badge
                        if (item.isSeasonPremiere) {
                            Badge(containerColor = Color(0xFFE8DEF8), contentColor = Color(0xFF1D192B)) {
                                Text("SEASON PREMIERE", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                            }
                        }

                        // Finale badge
                        if (item.isSeasonFinale) {
                            Badge(containerColor = Color(0xFFB3261E), contentColor = Color.White) {
                                Text("SEASON FINALE", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color(0xFFE6E1E5),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val romaji = item.titleRomaji
                    if (item.type == MediaType.ANIME && !romaji.isNullOrBlank()) {
                        Text(
                            text = romaji,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFFCAC4D0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    when (item.type) {
                        MediaType.ANIME, MediaType.TV -> {
                            val epLabel = item.formattedEpisodeCardBadge
                            val epTitle = item.episodeTitle?.takeIf { it.isNotBlank() } ?: "TBA"
                            Text(
                                text = "$epLabel: $epTitle",
                                fontSize = 13.sp,
                                color = Color(0xFFCAC4D0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        MediaType.MOVIE -> {
                            Text(
                                text = item.movieReleaseType?.displayName ?: "Movie Release",
                                fontSize = 13.sp,
                                color = Color(0xFFF2B8B5)
                            )
                        }
                    }

                    if (item.type != MediaType.MOVIE) {
                        val releaseTime = DateUtil.formatLocalizedTime(item.date)
                        if (releaseTime != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Air Time",
                                    modifier = Modifier.size(13.dp),
                                    tint = Color(0xFFD0BCFF)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = releaseTime,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFD0BCFF)
                                )
                            }
                        }
                    }
                }
            }

            // Progress bar and stats for downloading items
            if (item.mediaStatus == MediaStatus.DOWNLOADING && downloadProgress != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF004A77).copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    val progress = if (downloadProgress.totalBytes > 0) downloadProgress.bytesDownloaded.toFloat() / downloadProgress.totalBytes else 0f
                    val percentage = (progress * 100).toInt()
                    val downloaded = android.text.format.Formatter.formatFileSize(LocalContext.current, downloadProgress.bytesDownloaded)
                    val total = android.text.format.Formatter.formatFileSize(LocalContext.current, downloadProgress.totalBytes)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .width(100.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFFC2E8FF),
                            trackColor = Color(0xFF004A77).copy(alpha = 0.3f)
                        )
                        Text(
                            text = "${percentage}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC2E8FF)
                        )
                        Text(
                            text = "$downloaded / $total",
                            fontSize = 10.sp,
                            color = Color(0xFFC2E8FF).copy(alpha = 0.7f)
                        )
                    }

                    if (downloadProgress.downloadSpeed > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val speedStr = android.text.format.Formatter.formatFileSize(LocalContext.current, downloadProgress.downloadSpeed.toLong()) + "/s"
                        val remainingBytes = downloadProgress.totalBytes - downloadProgress.bytesDownloaded
                        val remainingSeconds = (remainingBytes / downloadProgress.downloadSpeed).toLong()
                        val eta = DateUtil.formatDuration(remainingSeconds)

                        Text(
                            text = "$speedStr • ETA: $eta",
                            fontSize = 11.sp,
                            color = Color(0xFFC2E8FF)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableCalendarItemCard(
    item: CalendarItemWithWatchlist,
    onClick: () -> Unit,
    onMarkEpisodeWatched: () -> Unit,
    onMarkSeasonWatched: () -> Unit,
    modifier: Modifier = Modifier,
    downloadProgress: DownloadProgress? = null
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Swiped Right -> Mark this episode as watched
                    onMarkEpisodeWatched()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Swiped Left -> Mark the season as watched (only available on anime/shows)
                    if (item.type != MediaType.MOVIE) {
                        onMarkSeasonWatched()
                    }
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.35f }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = item.type != MediaType.MOVIE,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isStartToEnd = direction == SwipeToDismissBoxValue.StartToEnd
            val isEndToStart = direction == SwipeToDismissBoxValue.EndToStart

            val backgroundColor = when {
                isStartToEnd -> Color(0xFF1B4D3E) // Episode watched: deep green
                isEndToStart && item.type != MediaType.MOVIE -> Color(0xFF004D40) // Season watched: deep teal
                else -> Color.Transparent
            }

            val icon = when {
                isStartToEnd -> painterResource(id = R.drawable.ic_check)
                isEndToStart && item.type != MediaType.MOVIE -> painterResource(id = R.drawable.ic_done_all)
                else -> null
            }

            val label = when {
                isStartToEnd -> "Mark as watched"
                isEndToStart && item.type != MediaType.MOVIE -> "Mark season as watched"
                else -> ""
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(backgroundColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (isStartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (icon != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isStartToEnd) {
                            Icon(
                                painter = icon,
                                contentDescription = label,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = label,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        } else {
                            Text(
                                text = label,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Icon(
                                painter = icon,
                                contentDescription = label,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    ) {
        CalendarItemCard(
            item = item,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            downloadProgress = downloadProgress
        )
    }
}
