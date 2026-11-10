package com.felixbrucker.simklcalendar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felixbrucker.simklcalendar.data.database.CachedMediaItem
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.repository.CalendarRepository
import com.felixbrucker.simklcalendar.data.repository.MediaItemRepository
import com.felixbrucker.simklcalendar.ui.composable.MediaItemCard
import com.felixbrucker.simklcalendar.ui.theme.OutlineVariant
import com.felixbrucker.simklcalendar.ui.theme.PurpleLight
import com.felixbrucker.simklcalendar.ui.theme.PurplePrimaryDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceDark
import com.felixbrucker.simklcalendar.ui.theme.TextPrimary
import com.felixbrucker.simklcalendar.ui.theme.TextSubtle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendingScreen(
    mediaItemRepository: MediaItemRepository,
    calendarRepository: CalendarRepository,
    onNavigateBack: () -> Unit,
    onNavigateToItemDetail: (Int, MediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val mediaTypes = listOf(MediaType.MOVIE, MediaType.TV, MediaType.ANIME)
    val currentType = mediaTypes[selectedTabIndex]

    var excludeOnMyList by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    val trendingItems by mediaItemRepository.getTrendingItemsFlow(currentType).collectAsState(initial = emptyList())
    val myWatchlist by calendarRepository.watchlistItems.collectAsState(initial = emptyList())
    val myWatchlistMap = remember(myWatchlist) { myWatchlist.associateBy { it.simklId } }

    val displayedItems = remember(trendingItems, excludeOnMyList, myWatchlistMap) {
        if (!excludeOnMyList) {
            trendingItems
        } else {
            trendingItems.filter { it.simklId !in myWatchlistMap }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Trending", color = Color.White) },
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
        ) {
            // Segmented/Tabs for Movies, TV Shows, Anime
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = SurfaceDark,
                contentColor = PurpleLight
            ) {
                mediaTypes.forEachIndexed { index, type ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = type.displayName,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIndex == index) PurpleLight else TextSubtle
                            )
                        }
                    )
                }
            }

            // Filter Bar with Chip/Badge to exclude items on my lists
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = excludeOnMyList,
                    onClick = { excludeOnMyList = !excludeOnMyList },
                    label = { Text("Exclude on my lists", fontSize = 12.sp) },
                    leadingIcon = if (excludeOnMyList) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = PurpleLight
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PurplePrimaryDark,
                        selectedLabelColor = Color.White,
                        containerColor = SurfaceDark,
                        labelColor = TextSubtle
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = excludeOnMyList,
                        borderColor = OutlineVariant,
                        selectedBorderColor = PurpleLight
                    )
                )
            }

            // Trending Items List with Pull to Refresh
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    coroutineScope.launch {
                        try {
                            mediaItemRepository.syncTrending(force = true)
                        } finally {
                            isRefreshing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                if (displayedItems.isEmpty() && !isRefreshing) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (excludeOnMyList) "No new trending items found (all on your lists)" else "No trending items available",
                            color = TextSubtle,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = displayedItems,
                            key = { it.simklId }
                        ) { item ->
                            val myItem = myWatchlistMap[item.simklId]
                            MediaItemCard(
                                item = item,
                                listStatus = myItem?.status,
                                onClick = { onNavigateToItemDetail(item.simklId, item.mediaType) }
                            )
                        }
                    }
                }
            }
        }
    }
}
