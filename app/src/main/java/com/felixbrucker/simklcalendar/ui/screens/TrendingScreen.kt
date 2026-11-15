package com.felixbrucker.simklcalendar.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.ui.composable.MediaTypeFilterChips
import com.felixbrucker.simklcalendar.ui.composable.TrendingItemCard
import com.felixbrucker.simklcalendar.ui.theme.*
import com.felixbrucker.simklcalendar.ui.viewmodel.MediaItemHolder
import com.felixbrucker.simklcalendar.ui.viewmodel.TrendingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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

    var isSearchActive by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

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

    val searchDisplayMode = when {
        isSearchActive || isSearchFocused -> TrendingSearchBarDisplayMode.EXPANDED
        searchQuery.isNotBlank() -> TrendingSearchBarDisplayMode.DOCKED
        else -> TrendingSearchBarDisplayMode.DEFAULT
    }

    BackHandler(enabled = isSearchActive || searchQuery.isNotBlank()) {
        if (searchQuery.isNotBlank()) {
            viewModel.clearSearchQuery()
        }
        isSearchActive = false
        isSearchFocused = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val listState = rememberLazyListState()

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

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (currentIndex, currentOffset) ->
                if (currentIndex == 0 && currentOffset == 0) {
                    filterBarOffsetPx = 0f
                }
            }
    }

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
                            if (searchQuery.isBlank()) {
                                isSearchActive = false
                            }
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
                    if (searchDisplayMode == TrendingSearchBarDisplayMode.DEFAULT) {
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
                                contentDescription = "Search Trending",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = SurfaceDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(nestedScrollConnection)
        ) {
            // Auto-hiding Filter Bar
            Surface(
                color = SurfaceDark.copy(alpha = 0.95f),
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val measuredHeight = placeable.height
                        if (measuredHeight > 0 && filterBarHeightPx != measuredHeight.toFloat()) {
                            filterBarHeightPx = measuredHeight.toFloat()
                            filterBarOffsetPx = filterBarOffsetPx.coerceIn(-filterBarHeightPx, 0f)
                        }

                        val layoutHeight = (measuredHeight + filterBarOffsetPx.roundToInt()).coerceAtLeast(0)

                        layout(placeable.width, layoutHeight) {
                            placeable.placeRelative(0, filterBarOffsetPx.roundToInt())
                        }
                    }
                    .clipToBounds()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) {
                        MediaTypeFilterChips(
                            showTv = uiPreferences.trendingShowTv,
                            showAnime = uiPreferences.trendingShowAnime,
                            showMovies = uiPreferences.trendingShowMovies,
                            onToggleShowTv = { viewModel.toggleShowTv() },
                            onToggleShowAnime = { viewModel.toggleShowAnime() },
                            onToggleShowMovies = { viewModel.toggleShowMovies() }
                        )

                        VerticalDivider(
                            modifier = Modifier
                                .height(24.dp)
                                .padding(horizontal = 4.dp),
                            color = OutlineVariant
                        )

                        FilterChip(
                            selected = uiPreferences.trendingExcludeOnMyLists,
                            onClick = { viewModel.toggleExcludeOnMyLists() },
                            label = { Text("Exclude on my lists") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PurplePrimaryDark,
                                selectedLabelColor = Color.White,
                                containerColor = SurfaceGreyDark,
                                labelColor = TextSubtle
                            )
                        )
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshTrending() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
                ) {
                    items(
                        trendingItems,
                        key = { "${it.trendingEntry.mediaType}_${it.trendingEntry.simklId}" }
                    ) { item ->
                        TrendingItemCard(
                            trendingItem = item,
                            onItemClick = { simklId, mediaType ->
                                item.mediaItem?.let { MediaItemHolder.set(it) }
                                onNavigateToDetail(simklId, mediaType)
                            },
                            modifier = Modifier.animateItem(),
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }
            }
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
