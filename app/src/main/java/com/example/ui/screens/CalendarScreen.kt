package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.example.data.database.CalendarItem
import com.example.data.model.MediaType
import com.example.data.model.MovieReleaseType
import com.example.data.util.DateUtil
import com.example.ui.viewmodel.CalendarViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToShowDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items by viewModel.filteredCalendarItems.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val userToken by viewModel.userToken.collectAsState()

    // Filters states
    val tvFilter by viewModel.showTv.collectAsState()
    val animeFilter by viewModel.showAnime.collectAsState()
    val moviesFilter by viewModel.showMovies.collectAsState()
    val premieresOnly by viewModel.onlySeasonPremieres.collectAsState()
    val finalesOnly by viewModel.onlySeasonFinales.collectAsState()

    val username = userToken?.username ?: "Guest"

    var showEarlierReleases by remember { mutableStateOf(false) }

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
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
                },
                actions = {
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
            onRefresh = { viewModel.syncLocalCalendar(force = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("pull_to_refresh_box")
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Toggles / Chip Filtering Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                        ),
                        modifier = Modifier.testTag("filter_tv_toggle")
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
                        ),
                        modifier = Modifier.testTag("filter_anime_toggle")
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
                        ),
                        modifier = Modifier.testTag("filter_movies_toggle")
                    )
                }

                // Subtype Row filters (Season Premiere / Season Finale highlights)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = premieresOnly,
                        onClick = { viewModel.onlySeasonPremieres.value = !premieresOnly },
                        label = { Text("Season Premiere 🎉") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE8DEF8),
                            selectedLabelColor = Color(0xFF1D192B),
                            containerColor = Color(0xFF313033),
                            labelColor = Color(0xFFCAC4D0)
                        ),
                        modifier = Modifier.testTag("filter_premieres_toggle")
                    )

                    FilterChip(
                        selected = finalesOnly,
                        onClick = { viewModel.onlySeasonFinales.value = !finalesOnly },
                        label = { Text("Season Finale 🍿") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFB3261E),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF313033),
                            labelColor = Color(0xFFCAC4D0)
                        ),
                        modifier = Modifier.testTag("filter_finales_toggle")
                    )
                }

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
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = Color(0xFF3E3D4F),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No releases found matching filters",
                            color = Color(0xFFA5A3B1),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                viewModel.showTv.value = true
                                viewModel.showAnime.value = true
                                viewModel.showMovies.value = true
                                viewModel.onlySeasonPremieres.value = false
                                viewModel.onlySeasonFinales.value = false
                            },
                        ) {
                            Text("Reset Active Filters", color = Color(0xFFD0BCFF))
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
                                    .clickable { showEarlierReleases = !showEarlierReleases }
                                    .testTag("toggle_earlier_releases_button")
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
                                                text = "${earlierItems.size} past ${if (earlierItems.size == 1) "release" else "releases"} hidden by default",
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
                                    CalendarItemCard(
                                        item = item,
                                        onClick = { onNavigateToShowDetail(item.primaryKey) }
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
                                CalendarItemCard(
                                    item = item,
                                    onClick = { onNavigateToShowDetail(item.primaryKey) }
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
                                        "No upcoming releases for active filters",
                                        color = Color(0xFFA5A3B1),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(onClick = { showEarlierReleases = true }) {
                                        Text("View ${earlierItems.size} Earlier Releases", color = Color(0xFFD0BCFF))
                                    }
                                }
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
    item: CalendarItem,
    onClick: () -> Unit
) {
    val categoryColor = when (item.type) {
        MediaType.ANIME -> Color(0xFFD0BCFF)
        MediaType.MOVIE -> Color(0xFFF2B8B5)
        MediaType.TV -> Color(0xFFBAC3FF)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF49454F)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
            .testTag("calendar_item_card_${item.id}")
    ) {
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
                    model = item.poster,
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

                Spacer(modifier = Modifier.height(2.dp))

                when (item.type) {
                    MediaType.ANIME -> {
                        val epNum = item.episodeNumber ?: 1
                        Text(
                            text = "Episode $epNum",
                            fontSize = 13.sp,
                            color = Color(0xFFCAC4D0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    MediaType.TV -> {
                        val epSeason = item.season ?: 1
                        val epNum = item.episodeNumber ?: 1
                        val epLabel = String.format(Locale.US, "S%02d • E%02d", epSeason, epNum)
                        Text(
                            text = "$epLabel : ${item.episodeTitle ?: "TBD"}",
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
    }
}
