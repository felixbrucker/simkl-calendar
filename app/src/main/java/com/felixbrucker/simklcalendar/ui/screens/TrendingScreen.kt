package com.felixbrucker.simklcalendar.ui.screens

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felixbrucker.simklcalendar.data.database.TrendingMediaItem
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.preferences.UiPreferences
import com.felixbrucker.simklcalendar.ui.composable.MediaTypeFilterChips
import com.felixbrucker.simklcalendar.ui.composable.TrendingItemCard
import com.felixbrucker.simklcalendar.ui.theme.*
import com.felixbrucker.simklcalendar.ui.viewmodel.MediaItemHolder
import com.felixbrucker.simklcalendar.ui.viewmodel.TrendingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private enum class TrendingSearchBarDisplayMode {
    DEFAULT, DOCKED, EXPANDED
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrendingScreen(
    viewModel: TrendingViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Int, MediaType) -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val trendingItems by viewModel.trendingMediaItems.collectAsState()
    val uiPreferences by viewModel.uiPreferences.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val minYearBound by viewModel.minTrendingYear.collectAsState()
    val maxYearBound by viewModel.maxTrendingYear.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val isFilterSidebarOpen = uiPreferences.trendingFilterSidebarVisible
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible && isSearchFocused) {
            focusManager.clearFocus()
            isSearchFocused = false
            if (searchQuery.isBlank()) isSearchActive = false
        }
    }

    val searchDisplayMode = when {
        isSearchActive || isSearchFocused -> TrendingSearchBarDisplayMode.EXPANDED
        searchQuery.isNotBlank() -> TrendingSearchBarDisplayMode.DOCKED
        else -> TrendingSearchBarDisplayMode.DEFAULT
    }

    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenWidthDp = with(density) { windowInfo.containerSize.width.toDp() }
    val isTablet = screenWidthDp >= 600.dp

    BackHandler(enabled = isSearchActive || searchQuery.isNotBlank() || (isFilterSidebarOpen && !isTablet)) {
        if (isFilterSidebarOpen && !isTablet) {
            viewModel.toggleTrendingFilterSidebarVisible()
        } else if (searchQuery.isNotBlank()) {
            viewModel.clearSearchQuery()
        }
        isSearchActive = false
        isSearchFocused = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val listState = rememberLazyListState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    TrendingTopAppBarTitleSection(
                        searchDisplayMode = searchDisplayMode,
                        searchQuery = searchQuery,
                        focusRequester = focusRequester,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onFocusChanged = { isSearchFocused = it },
                        onSearch = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            isSearchFocused = false
                            if (searchQuery.isBlank()) isSearchActive = false
                        },
                        onClearSearch = {
                            viewModel.clearSearchQuery()
                            isSearchActive = false
                            isSearchFocused = false
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        },
                        onExpandSearch = {
                            isSearchActive = true
                            coroutineScope.launch {
                                delay(100.milliseconds)
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        }
                    )
                },
                navigationIcon = {
                    if (searchDisplayMode != TrendingSearchBarDisplayMode.EXPANDED) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                },
                actions = {
                    TrendingTopBarActions(
                        searchDisplayMode = searchDisplayMode,
                        isFilterOpen = isFilterSidebarOpen,
                        onOpenSearch = {
                            isSearchActive = true
                            coroutineScope.launch {
                                delay(100.milliseconds)
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        },
                        onToggleFilters = { viewModel.toggleTrendingFilterSidebarVisible() }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = SurfaceDark
    ) { innerPadding ->
        TrendingContentHost(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            trendingItems = trendingItems,
            uiPreferences = uiPreferences,
            isRefreshing = isRefreshing,
            isFilterOpen = isFilterSidebarOpen,
            listState = listState,
            minYearBound = minYearBound,
            maxYearBound = maxYearBound,
            onRefresh = { viewModel.refreshTrending() },
            onDismissFilter = { viewModel.toggleTrendingFilterSidebarVisible() },
            onItemClick = { simklId, mediaType ->
                onNavigateToDetail(simklId, mediaType)
            },
            viewModel = viewModel,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )
    }
}

@Composable
private fun TrendingContentHost(
    modifier: Modifier = Modifier,
    trendingItems: List<TrendingMediaItem>,
    uiPreferences: UiPreferences,
    isRefreshing: Boolean,
    isFilterOpen: Boolean,
    listState: LazyListState,
    minYearBound: Int,
    maxYearBound: Int,
    onRefresh: () -> Unit,
    onDismissFilter: () -> Unit,
    onItemClick: (Int, MediaType) -> Unit,
    viewModel: TrendingViewModel,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
) {
    BoxWithConstraints(modifier = modifier) {
        val isTablet = maxWidth >= 600.dp
        if (isTablet) {
            TrendingTabletLayout(
                trendingItems = trendingItems,
                uiPreferences = uiPreferences,
                isRefreshing = isRefreshing,
                isFilterOpen = isFilterOpen,
                listState = listState,
                minYearBound = minYearBound,
                maxYearBound = maxYearBound,
                onRefresh = onRefresh,
                onItemClick = onItemClick,
                viewModel = viewModel,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope
            )
        } else {
            TrendingMobileLayout(
                trendingItems = trendingItems,
                uiPreferences = uiPreferences,
                isRefreshing = isRefreshing,
                isFilterOpen = isFilterOpen,
                listState = listState,
                minYearBound = minYearBound,
                maxYearBound = maxYearBound,
                onRefresh = onRefresh,
                onDismissFilter = onDismissFilter,
                onItemClick = onItemClick,
                viewModel = viewModel,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    }
}

@Composable
private fun TrendingTabletLayout(
    trendingItems: List<TrendingMediaItem>,
    uiPreferences: UiPreferences,
    isRefreshing: Boolean,
    isFilterOpen: Boolean,
    listState: LazyListState,
    minYearBound: Int,
    maxYearBound: Int,
    onRefresh: () -> Unit,
    onItemClick: (Int, MediaType) -> Unit,
    viewModel: TrendingViewModel,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            TrendingMainContent(
                trendingItems = trendingItems,
                isRefreshing = isRefreshing,
                listState = listState,
                onRefresh = onRefresh,
                onItemClick = onItemClick,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope
            )
        }

        AnimatedVisibility(
            visible = isFilterOpen,
            enter = expandHorizontally(expandFrom = Alignment.End, animationSpec = tween(250)) + slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
            exit = shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = tween(200)) + slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200)) + fadeOut(animationSpec = tween(200))
        ) {
            Box(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .background(SurfaceDark)
                    .padding(start = 8.dp, end = 16.dp, bottom = 16.dp, top = 8.dp)
            ) {
                TrendingFilterSidebarContent(
                    uiPreferences = uiPreferences,
                    minYearBound = minYearBound,
                    maxYearBound = maxYearBound,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun TrendingMobileLayout(
    trendingItems: List<TrendingMediaItem>,
    uiPreferences: UiPreferences,
    isRefreshing: Boolean,
    isFilterOpen: Boolean,
    listState: LazyListState,
    minYearBound: Int,
    maxYearBound: Int,
    onRefresh: () -> Unit,
    onDismissFilter: () -> Unit,
    onItemClick: (Int, MediaType) -> Unit,
    viewModel: TrendingViewModel,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
) {
    Box(modifier = Modifier.fillMaxSize()) {
        TrendingMainContent(
            trendingItems = trendingItems,
            isRefreshing = isRefreshing,
            listState = listState,
            onRefresh = onRefresh,
            onItemClick = onItemClick,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )

        AnimatedVisibility(
            visible = isFilterOpen,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = onDismissFilter)
            ) {
                AnimatedVisibility(
                    visible = isFilterOpen,
                    enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(250)),
                    exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200)),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(320.dp)
                            .background(SurfaceDark)
                            .clickable(enabled = false, onClick = {})
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp)
                    ) {
                        TrendingFilterSidebarContent(
                            uiPreferences = uiPreferences,
                            minYearBound = minYearBound,
                            maxYearBound = maxYearBound,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendingMainContent(
    trendingItems: List<TrendingMediaItem>,
    isRefreshing: Boolean,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onItemClick: (Int, MediaType) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, end = 8.dp)
            ) {
                items(
                    trendingItems,
                    key = { "${it.trendingEntry.mediaType}_${it.trendingEntry.simklId}" }
                ) { item ->
                    TrendingItemCard(
                        trendingItem = item,
                        onItemClick = { simklId, mediaType ->
                            item.mediaItem?.let { MediaItemHolder.set(it) }
                            onItemClick(simklId, mediaType)
                        },
                        modifier = Modifier.animateItem(),
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            }

            VerticalScrollbar(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun TrendingFilterSidebarContent(
    uiPreferences: UiPreferences,
    minYearBound: Int,
    maxYearBound: Int,
    viewModel: TrendingViewModel
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TrendingFilterRowMediaType(
            uiPreferences = uiPreferences,
            viewModel = viewModel
        )

        TrendingFilterRowExcludeList(
            excludeOnMyLists = uiPreferences.trendingExcludeOnMyLists,
            viewModel = viewModel
        )

        TrendingFilterRowHasDvd(
            hasDvd = uiPreferences.trendingHasDvd,
            viewModel = viewModel
        )

        TrendingFilterRowMinRating(
            minRating = uiPreferences.trendingMinRating,
            viewModel = viewModel
        )

        TrendingFilterRowMinYear(
            minYear = uiPreferences.trendingMinYear,
            minYearBound = minYearBound,
            maxYearBound = maxYearBound,
            viewModel = viewModel
        )
    }
}

@Composable
private fun TrendingFilterRowMediaType(
    uiPreferences: UiPreferences,
    viewModel: TrendingViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MediaTypeFilterChips(
            showTv = uiPreferences.trendingShowTv,
            showAnime = uiPreferences.trendingShowAnime,
            showMovies = uiPreferences.trendingShowMovies,
            onToggleShowTv = { viewModel.toggleShowTv() },
            onToggleShowAnime = { viewModel.toggleShowAnime() },
            onToggleShowMovies = { viewModel.toggleShowMovies() }
        )
    }
}

@Composable
private fun TrendingFilterRowExcludeList(
    excludeOnMyLists: Boolean,
    viewModel: TrendingViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { viewModel.toggleExcludeOnMyLists() }),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Exclude items on my lists", color = TextPrimary, fontSize = 14.sp)
        Checkbox(
            checked = excludeOnMyLists,
            onCheckedChange = { viewModel.toggleExcludeOnMyLists() }
        )
    }
}

@Composable
private fun TrendingFilterRowMinRating(
    minRating: Float,
    viewModel: TrendingViewModel
) {
    var sliderValue by remember(minRating) { mutableFloatStateOf(minRating) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Minimum Rating", color = TextPrimary, fontSize = 14.sp)
            Text(
                text = if (sliderValue <= 0f) "Any" else String.format(LocalLocale.current.platformLocale, "%.1f", sliderValue),
                color = PurpleLight,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { viewModel.setTrendingMinRating(sliderValue) },
            valueRange = 0f..10f,
            steps = 19,
            colors = SliderDefaults.colors(thumbColor = PurpleLight, activeTrackColor = PurpleLight)
        )
    }
}

@Composable
private fun TrendingFilterRowMinYear(
    minYear: Int,
    minYearBound: Int,
    maxYearBound: Int,
    viewModel: TrendingViewModel
) {
    val effectiveMin = minOf(minYearBound, maxYearBound)
    val effectiveMax = maxOf(minYearBound, maxYearBound)
    val initialVal = if (minYear <= 0) effectiveMin.toFloat() else minYear.toFloat().coerceIn(effectiveMin.toFloat(), effectiveMax.toFloat())
    var sliderVal by remember(minYear, effectiveMin, effectiveMax) { mutableFloatStateOf(initialVal) }
    val steps = (effectiveMax - effectiveMin - 1).coerceAtLeast(0)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Minimum Year", color = TextPrimary, fontSize = 14.sp)
            Text(
                text = if (sliderVal <= effectiveMin.toFloat() && minYear <= 0) "Any" else sliderVal.toInt().toString(),
                color = PurpleLight,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = sliderVal,
            onValueChange = { sliderVal = it },
            onValueChangeFinished = { viewModel.setTrendingMinYear(sliderVal.toInt()) },
            valueRange = effectiveMin.toFloat()..effectiveMax.toFloat(),
            steps = if (steps > 100) 0 else steps,
            colors = SliderDefaults.colors(thumbColor = PurpleLight, activeTrackColor = PurpleLight)
        )
    }
}

@Composable
private fun TrendingFilterRowHasDvd(
    hasDvd: Boolean,
    viewModel: TrendingViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { viewModel.toggleTrendingHasDvd() }),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Has DVD / Digital Release", color = TextPrimary, fontSize = 14.sp)
        Checkbox(
            checked = hasDvd,
            onCheckedChange = { viewModel.toggleTrendingHasDvd() }
        )
    }
}

@SuppressLint("FrequentlyChangingValue")
@Composable
private fun VerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val totalItems = listState.layoutInfo.totalItemsCount
    if (totalItems <= 1) return

    val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull()
    val itemSize = visibleItem?.size?.toFloat() ?: 100f
    val firstIndex = listState.firstVisibleItemIndex.toFloat()
    val scrollOffset = listState.firstVisibleItemScrollOffset.toFloat()
    val rawPos = firstIndex + (scrollOffset / itemSize)
    val progress = (rawPos / (totalItems - 1).toFloat()).coerceIn(0f, 1f)

    val isScrolling = listState.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if (isScrolling) 1f else 0f,
        animationSpec = tween(durationMillis = if (isScrolling) 100 else 400),
        label = "scrollbar_fade"
    )

    if (alpha > 0f) {
        ScrollbarContainer(progress = progress, alpha = alpha, modifier = modifier)
    }
}

@Composable
private fun ScrollbarContainer(
    progress: Float,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .width(6.dp)
            .fillMaxHeight()
            .alpha(alpha)
            .padding(vertical = 4.dp)
    ) {
        val density = LocalDensity.current
        val maxHeightPx = constraints.maxHeight.toFloat()
        val thumbHeightPx = 40f
        val availablePx = (maxHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val yOffsetDp = with(density) { (availablePx * progress).toDp() }
        val thumbHeightDp = with(density) { thumbHeightPx.toDp() }

        Box(
            modifier = Modifier
                .width(4.dp)
                .height(thumbHeightDp)
                .offset(y = yOffsetDp)
                .background(PurpleLight, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun TrendingTopBarActions(
    searchDisplayMode: TrendingSearchBarDisplayMode,
    isFilterOpen: Boolean,
    onOpenSearch: () -> Unit,
    onToggleFilters: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (searchDisplayMode == TrendingSearchBarDisplayMode.DEFAULT) {
            IconButton(
                onClick = onOpenSearch,
                modifier = Modifier.testTag("search_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Trending",
                    tint = Color.White
                )
            }
        }
        IconButton(
            onClick = onToggleFilters,
            modifier = Modifier.testTag("filter_button")
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Filters",
                tint = if (isFilterOpen) PurpleLight else Color.White
            )
        }
    }
}

@Composable
private fun TrendingTopAppBarTitleSection(
    searchDisplayMode: TrendingSearchBarDisplayMode,
    searchQuery: String,
    focusRequester: FocusRequester,
    onSearchQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onExpandSearch: () -> Unit
) {
    AnimatedContent(
        targetState = searchDisplayMode,
        transitionSpec = {
            if (targetState == TrendingSearchBarDisplayMode.EXPANDED) {
                (slideInHorizontally(animationSpec = tween(280)) { width -> width } + fadeIn(animationSpec = tween(250))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(200)) { width -> -width / 4 } + fadeOut(animationSpec = tween(200)))
            } else if (initialState == TrendingSearchBarDisplayMode.EXPANDED && targetState == TrendingSearchBarDisplayMode.DEFAULT) {
                (slideInHorizontally(animationSpec = tween(200)) { width -> -width / 4 } + fadeIn(animationSpec = tween(200))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(280)) { width -> width } + fadeOut(animationSpec = tween(250)))
            } else {
                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
            }
        },
        label = "trending_top_bar_search_transition"
    ) { mode ->
        when (mode) {
            TrendingSearchBarDisplayMode.EXPANDED -> {
                TrendingExpandedSearchBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    focusRequester = focusRequester,
                    onFocusChanged = onFocusChanged,
                    onSearch = onSearch,
                    onClearSearch = onClearSearch
                )
            }

            TrendingSearchBarDisplayMode.DOCKED -> {
                TrendingDockedSearchBar(
                    searchQuery = searchQuery,
                    onClick = onExpandSearch,
                    onClearSearch = onClearSearch
                )
            }

            TrendingSearchBarDisplayMode.DEFAULT -> {
                Text("Trending", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TrendingExpandedSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit
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
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search active",
            tint = PurpleLight,
            modifier = Modifier.size(20.dp)
        )

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
                            text = "Search trending items...",
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
private fun TrendingDockedSearchBar(
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
