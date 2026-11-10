package com.felixbrucker.simklcalendar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.network.SearchItemDto
import com.felixbrucker.simklcalendar.data.repository.CalendarRepository
import com.felixbrucker.simklcalendar.data.repository.MediaItemRepository
import com.felixbrucker.simklcalendar.ui.composable.SearchMediaItemCard
import com.felixbrucker.simklcalendar.ui.theme.OutlineVariant
import com.felixbrucker.simklcalendar.ui.theme.PurpleLight
import com.felixbrucker.simklcalendar.ui.theme.PurplePrimaryDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceContainer
import com.felixbrucker.simklcalendar.ui.theme.SurfaceDark
import com.felixbrucker.simklcalendar.ui.theme.TextPrimary
import com.felixbrucker.simklcalendar.ui.theme.TextSubtle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchCatalogScreen(
    mediaItemRepository: MediaItemRepository,
    calendarRepository: CalendarRepository,
    onNavigateBack: () -> Unit,
    onNavigateToItemDetail: (Int, MediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedMediaType by remember { mutableStateOf<MediaType?>(null) } // null = All
    var currentPage by remember { mutableIntStateOf(1) }
    var isLoading by remember { mutableStateOf(false) }
    var canLoadMore by remember { mutableStateOf(true) }

    val searchResults = remember { mutableStateListOf<SearchItemDto>() }
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val myWatchlist by calendarRepository.watchlistItems.collectAsState(initial = emptyList())
    val myWatchlistMap = remember(myWatchlist) { myWatchlist.associateBy { it.simklId } }

    fun executeSearch(reset: Boolean) {
        val query = searchQuery.trim()
        if (query.isBlank()) return

        if (reset) {
            currentPage = 1
            searchResults.clear()
            canLoadMore = true
        }

        if (!canLoadMore && !reset) return

        isLoading = true
        val pageToFetch = if (reset) 1 else currentPage

        coroutineScope.launch {
            try {
                val results = mediaItemRepository.search(
                    query = query,
                    mediaType = selectedMediaType,
                    page = pageToFetch,
                    limit = 20
                )
                if (reset) {
                    searchResults.clear()
                }
                searchResults.addAll(results)
                if (results.size < 20) {
                    canLoadMore = false
                } else {
                    currentPage = pageToFetch + 1
                }
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedMediaType) {
        if (searchQuery.isNotBlank()) {
            executeSearch(reset = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Simkl", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = SurfaceDark,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text("Search movies, TV shows, anime...", color = TextSubtle) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextSubtle
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            searchResults.clear()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = TextSubtle
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        executeSearch(reset = true)
                    }
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceContainer,
                    unfocusedContainerColor = SurfaceContainer,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = PurpleLight,
                    unfocusedBorderColor = OutlineVariant
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Type filter chips (All, Movies, TV Shows, Anime)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chipTypes = listOf<Pair<String, MediaType?>>(
                    "All" to null,
                    "Movies" to MediaType.MOVIE,
                    "TV Shows" to MediaType.TV,
                    "Anime" to MediaType.ANIME
                )

                chipTypes.forEach { (label, type) ->
                    val isSelected = selectedMediaType == type
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedMediaType = type
                        },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PurplePrimaryDark,
                            selectedLabelColor = Color.White,
                            containerColor = SurfaceDark,
                            labelColor = TextSubtle
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = OutlineVariant,
                            selectedBorderColor = PurpleLight
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search results list with infinite scroll pagination
            val listState = rememberLazyListState()
            val shouldLoadMore = remember {
                derivedStateOf {
                    val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                        ?: return@derivedStateOf false
                    lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 3
                }
            }

            LaunchedEffect(shouldLoadMore.value) {
                if (shouldLoadMore.value && !isLoading && canLoadMore && searchResults.isNotEmpty()) {
                    executeSearch(reset = false)
                }
            }

            if (isLoading && searchResults.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PurpleLight)
                }
            } else if (searchResults.isEmpty() && searchQuery.isNotBlank() && !isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No results found for '$searchQuery'",
                        color = TextSubtle,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        items = searchResults,
                        key = { "${it.endpointType}_${it.ids.simkl}" }
                    ) { item ->
                        val resolvedType = when (item.endpointType?.lowercase()) {
                            "movies", "movie" -> MediaType.MOVIE
                            "tv", "shows", "show" -> MediaType.TV
                            "anime" -> MediaType.ANIME
                            else -> MediaType.TV
                        }
                        val myItem = myWatchlistMap[item.ids.simkl]
                        SearchMediaItemCard(
                            item = item,
                            listStatus = myItem?.status,
                            onClick = {
                                onNavigateToItemDetail(item.ids.simkl, resolvedType)
                            }
                        )
                    }

                    if (isLoading && searchResults.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = PurpleLight, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
