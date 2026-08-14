package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
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
import com.example.data.util.DateUtil
import com.example.ui.viewmodel.CalendarViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToShowDetail: (Int) -> Unit,
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

    // Group items by date for sticky headers or grouped listing
    val groupedItems = remember(items) {
        items.groupBy { item ->
            DateUtil.formatAiringDateHeader(item.date)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                title = {
                    Column {
                        Text("Simkl Calendar", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            text = "Hi, $username • Tracked Schedule",
                            fontSize = 13.sp,
                            color = Color(0xFFCAC4D0),
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.syncLocalCalendar(force = true) },
                        modifier = Modifier.testTag("refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync database",
                            tint = Color.White
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
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = Color(0xFF1C1B1F),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF1C1B1F)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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

            // Syncing indicator
            AnimatedVisibility(
                visible = isSyncing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LinearProgressIndicator(
                    color = Color(0xFFD0BCFF),
                    trackColor = Color(0xFF49454F),
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
            }

            // Calendar Group list
            if (groupedItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
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
                    groupedItems.forEach { (dateHeader, dayItems) ->
                        // Date Header
                        stickyHeader {
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

                        // Day Release Cards
                        items(dayItems, key = { it.primaryKey }) { item ->
                            CalendarItemCard(
                                item = item,
                                onClick = { onNavigateToShowDetail(item.id) }
                            )
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
        "anime" -> Color(0xFFD0BCFF)
        "movie" -> Color(0xFFF2B8B5)
        else -> Color(0xFFBAC3FF) // TV Show
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
                        Text(item.type.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
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

                if (item.type != "movie") {
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
                } else {
                    Text(
                        text = "Theatrical release!",
                        fontSize = 13.sp,
                        color = Color(0xFFF2B8B5)
                    )
                }

                val releaseTime = DateUtil.formatLocalizedTime(item.date)
                if (releaseTime != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Air Time",
                            size = 13.dp,
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

                // Ready to Binge badge
                if (item.isLastEpisode) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(0xFF381E72), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null, size = 12.dp, tint = Color(0xFFD0BCFF))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("🍿 READY TO BINGE", fontSize = 10.sp, color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Utility icon size modifier helper
@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, size: androidx.compose.ui.unit.Dp, tint: Color) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size)
    )
}
