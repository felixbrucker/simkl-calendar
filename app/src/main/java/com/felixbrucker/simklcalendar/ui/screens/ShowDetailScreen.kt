package com.felixbrucker.simklcalendar.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.felixbrucker.simklcalendar.data.database.CalendarItem
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.util.DateUtil
import com.felixbrucker.simklcalendar.data.util.MediaFormatter
import com.felixbrucker.simklcalendar.data.util.PosterSize
import com.felixbrucker.simklcalendar.data.util.formattedEpisodeCode
import com.felixbrucker.simklcalendar.data.util.formattedEpisodeLabel
import com.felixbrucker.simklcalendar.data.util.formattedEpisodeSlugHeader
import com.felixbrucker.simklcalendar.data.util.formattedSeasonLabel
import com.felixbrucker.simklcalendar.data.util.toPosterUrl
import com.felixbrucker.simklcalendar.ui.viewmodel.CalendarViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowDetailScreen(
    viewModel: CalendarViewModel,
    itemKey: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val allItems by viewModel.allCalendarItems.collectAsState()
    val allWatchedEpisodes by viewModel.watchedEpisodes.collectAsState()
    val isMarkingWatched by viewModel.isMarkingWatched.collectAsState()
    val allSearchLinks by viewModel.customSearchLinks.collectAsState()

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

    var activeItemKey by remember(itemKey) { mutableStateOf(itemKey) }

    val activeItem = remember(allItems, activeItemKey, itemKey) {
        allItems.firstOrNull { it.primaryKey == activeItemKey }
            ?: allItems.firstOrNull { it.simklId.toString() == activeItemKey }
            ?: allItems.firstOrNull { it.primaryKey == itemKey }
            ?: allItems.firstOrNull { it.simklId.toString() == itemKey }
    }

    val showScheduleItems = remember(allItems, activeItem) {
        if (activeItem != null) {
            allItems.filter { it.simklId == activeItem.simklId }
                .sortedWith(compareBy<CalendarItem> { it.date }.thenBy { it.season }.thenBy { it.episodeNumber })
        } else {
            emptyList()
        }
    }

    val showWatched = remember(allWatchedEpisodes, activeItem) {
        if (activeItem != null) {
            allWatchedEpisodes.filter { it.simklId == activeItem.simklId }
        } else {
            emptyList()
        }
    }

    // Determine available seasons from both calendar schedule and watched episode entities
    val availableSeasons = remember(showScheduleItems, showWatched, activeItem) {
        val seasonsSet = mutableSetOf<Int>()
        showScheduleItems.forEach { item ->
            item.season?.let { if (it > 0) seasonsSet.add(it) }
        }
        showWatched.forEach { w ->
            if (w.season > 0) seasonsSet.add(w.season)
        }
        if (activeItem?.season != null && activeItem.season > 0) {
            seasonsSet.add(activeItem.season)
        }
        if (seasonsSet.isEmpty()) {
            listOf(1)
        } else {
            seasonsSet.sorted()
        }
    }

    // Helper to evaluate watch status of a season
    fun getSeasonWatchStatus(seasonNum: Int): Triple<Boolean, Boolean, String> {
        val scheduleInSeason = showScheduleItems.filter { (it.season ?: 1) == seasonNum }
        val watchedInSeason = showWatched.filter { it.season == seasonNum }

        return if (scheduleInSeason.isNotEmpty()) {
            val total = scheduleInSeason.size
            val watchedCount = scheduleInSeason.count { it.isWatched }
            val isFully = watchedCount == total
            val isPartial = watchedCount > 0 && watchedCount < total
            val statusStr = if (isFully) {
                "All $total episodes watched"
            } else if (isPartial) {
                "$watchedCount of $total episodes watched"
            } else {
                "Unwatched ($total episodes)"
            }
            Triple(isFully, isPartial, statusStr)
        } else if (watchedInSeason.isNotEmpty()) {
            Triple(true, false, "${watchedInSeason.size} episodes watched")
        } else {
            Triple(false, false, "Unwatched")
        }
    }

    val theatricalItem = remember(showScheduleItems, activeItem) {
        if (activeItem?.type == MediaType.MOVIE) {
            showScheduleItems.firstOrNull { it.movieReleaseType == MovieReleaseType.THEATER }
                ?: if (activeItem.movieReleaseType == MovieReleaseType.THEATER) activeItem else null
        } else null
    }

    val digitalItem = remember(showScheduleItems, activeItem) {
        if (activeItem?.type == MediaType.MOVIE) {
            showScheduleItems.firstOrNull { it.movieReleaseType == MovieReleaseType.DIGITAL }
                ?: if (activeItem.movieReleaseType == MovieReleaseType.DIGITAL) activeItem else null
        } else null
    }

    val settingsList by viewModel.notificationSettings.collectAsState()
    val showSetting = remember(settingsList, activeItem) {
        if (activeItem != null) settingsList.firstOrNull { it.simklId == activeItem.simklId } else null
    }

    val prefs = remember { context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE) }
    val defaultAiring = prefs.getBoolean("default_notify_airing", false)
    val defaultSeasonFinished = prefs.getBoolean("default_notify_season_finished", true)
    val defaultMovieTheater = prefs.getBoolean("default_notify_movie_theater", false)
    val defaultMovieDigital = prefs.getBoolean("default_notify_movie_digital", true)
    val isMovie = activeItem?.type == MediaType.MOVIE

    // Individual notification toggle flows
    var notifyEveryEpisode by remember(showSetting, defaultAiring, defaultMovieTheater, isMovie) {
        mutableStateOf(showSetting?.notifyEveryEpisode ?: (if (isMovie) defaultMovieTheater else defaultAiring))
    }
    var notifySeasonFinished by remember(showSetting, defaultSeasonFinished, defaultMovieDigital, isMovie) {
        mutableStateOf(showSetting?.notifyAiredLastEpisode ?: (if (isMovie) defaultMovieDigital else defaultSeasonFinished))
    }

    LaunchedEffect(showSetting) {
        if (showSetting != null) {
            notifyEveryEpisode = showSetting.notifyEveryEpisode
            notifySeasonFinished = showSetting.notifyAiredLastEpisode
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Release Details", color = Color(0xFFE6E1E5), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFE6E1E5))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1C1B1F))
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF1C1B1F)
    ) { innerPadding ->
        if (activeItem == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Show details not found.", color = Color(0xFFE6E1E5))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Banner / Poster Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    AsyncImage(
                        model = activeItem.poster.toPosterUrl(PosterSize.WIDE),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Scrim gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFF1C1B1F)
                                    ),
                                    startY = 100f
                                )
                            )
                    )

                    // Overlay Title metadata
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when (activeItem.type) {
                                MediaType.ANIME -> Color(0xFFE8DEF8)
                                MediaType.MOVIE -> Color(0xFFF2B8B5)
                                MediaType.TV -> Color(0xFFBAC3FF)
                            },
                            contentColor = when (activeItem.type) {
                                MediaType.ANIME -> Color(0xFF1D192B)
                                MediaType.MOVIE -> Color(0xFF601410)
                                MediaType.TV -> Color(0xFF1A237E)
                            }
                        ) {
                            Text(
                                text = activeItem.type.displayName.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = activeItem.title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }

                // Airing / Release details info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Watched Episode Highlight Banner
                    if (activeItem.isWatched) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1B3828),
                            border = BorderStroke(1.dp, Color(0xFF48A36E)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFF2E6543), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Watched",
                                        tint = Color(0xFF7CE49F),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (activeItem.type == MediaType.MOVIE) "Watched Movie" else "Watched Episode",
                                        color = Color(0xFF7CE49F),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    val formattedWatchedAt = activeItem.watchedAt?.let { instant ->
                                        DateUtil.formatDisplayDateTime(instant)
                                    }
                                    Text(
                                        text = if (formattedWatchedAt != null) "Watched on $formattedWatchedAt" else "Marked as watched on Simkl",
                                        color = Color(0xFFC3EED3),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                        border = BorderStroke(1.dp, Color(0xFF49454F))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (activeItem.type == MediaType.MOVIE) "Release Information" else "Selected Episode Details",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE6E1E5),
                                fontSize = 15.sp
                            )

                            if (activeItem.type == MediaType.MOVIE) {
                                if (theatricalItem != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Theatrical Release", color = Color(0xFFCAC4D0), fontSize = 14.sp)
                                        Text(
                                            text = DateUtil.formatDisplayDate(theatricalItem.date),
                                            color = Color(0xFFE6E1E5),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                if (digitalItem != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Digital / DVD Release", color = Color(0xFFCAC4D0), fontSize = 14.sp)
                                        Text(
                                            text = DateUtil.formatDisplayDate(digitalItem.date),
                                            color = Color(0xFFE6E1E5),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                if (theatricalItem == null && digitalItem == null) {
                                    val dateLabel = if (activeItem.movieReleaseType == MovieReleaseType.THEATER) "Theatrical Release" else "Digital / DVD Release"
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(dateLabel, color = Color(0xFFCAC4D0), fontSize = 14.sp)
                                        Text(
                                            text = DateUtil.formatDisplayDate(activeItem.date),
                                            color = Color(0xFFE6E1E5),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            } else {
                                val formattedDateTime = DateUtil.formatDisplayDateTime(activeItem.date)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Air Date", color = Color(0xFFCAC4D0), fontSize = 14.sp)
                                    Text(
                                        text = formattedDateTime,
                                        color = Color(0xFFE6E1E5),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            // Season and Episode Slug positioned above Episode Name
                            if (activeItem.type != MediaType.MOVIE) {
                                val slugLabel = activeItem.formattedEpisodeSlugHeader
                                val slugValue = activeItem.formattedEpisodeCode

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(slugLabel, color = Color(0xFFCAC4D0), fontSize = 14.sp)
                                    Text(
                                        text = slugValue,
                                        color = Color(0xFFE6E1E5),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }

                                val titleText = activeItem.episodeTitle?.takeIf { it.isNotBlank() } ?: "TBA"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Episode Name", color = Color(0xFFCAC4D0), fontSize = 14.sp)
                                    Text(
                                        text = titleText,
                                        color = Color(0xFFE6E1E5),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        maxLines = 2
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Watch Status", color = Color(0xFFCAC4D0), fontSize = 14.sp)
                                    if (activeItem.isWatched) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF7CE49F),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Watched",
                                                color = Color(0xFF7CE49F),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Unwatched",
                                            color = Color(0xFFCAC4D0),
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            // Prem/Finale labels
                            if (activeItem.isSeasonPremiere || activeItem.isSeasonFinale) {
                                HorizontalDivider(color = Color(0xFF49454F), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (activeItem.isSeasonPremiere) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFE8DEF8), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("🎉 SEASON PREMIERE", color = Color(0xFF1D192B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (activeItem.isSeasonFinale) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFB3261E), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("🎬 SEASON FINALE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Watch Actions Section (without surrounding Card)
                    if (activeItem.type == MediaType.MOVIE) {
                        // Mark Movie as Watched Button
                        Button(
                            onClick = {
                                viewModel.markMovieWatched(
                                    simklId = activeItem.simklId,
                                    showTitle = activeItem.title
                                ) { success, msg ->
                                    if (success) {
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = msg,
                                                actionLabel = "Revert",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.markMovieUnwatched(
                                                    simklId = activeItem.simklId,
                                                    showTitle = activeItem.title
                                                ) { _, revertMsg ->
                                                    scope.launch { snackbarHostState.showSnackbar(revertMsg) }
                                                }
                                            }
                                        }
                                    } else {
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                }
                            },
                            enabled = !isMarkingWatched && !activeItem.isWatched,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeItem.isWatched) Color(0xFF2E6543) else Color(0xFF381E72),
                                contentColor = if (activeItem.isWatched) Color(0xFF7CE49F) else Color(0xFFEADDFF),
                                disabledContainerColor = if (activeItem.isWatched) Color(0xFF1E3A2B) else Color(0xFF3B383E),
                                disabledContentColor = if (activeItem.isWatched) Color(0xFF7CE49F) else Color(0xFF79747E)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isMarkingWatched) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFEADDFF)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Updating SIMKL...", fontWeight = FontWeight.Bold)
                            } else if (activeItem.isWatched) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Movie Marked as Watched", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mark Movie as Watched", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        val sNum = activeItem.season ?: 1
                        val eNum = activeItem.episodeNumber ?: 1
                        val epLabel = activeItem.formattedEpisodeLabel
                        val seasonLabel = activeItem.formattedSeasonLabel

                        val (isSeasonFullyWatched, _, _) = getSeasonWatchStatus(sNum)

                        @Composable
                        fun EpisodeWatchSection(modifier: Modifier = Modifier) {
                            Button(
                                onClick = {
                                    viewModel.markEpisodeWatched(
                                        simklId = activeItem.simklId,
                                        season = activeItem.season,
                                        episodeNumber = eNum,
                                        mediaType = activeItem.type,
                                        showTitle = activeItem.title
                                    ) { success, msg ->
                                        if (success) {
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = msg,
                                                    actionLabel = "Revert",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    viewModel.markEpisodeUnwatched(
                                                        simklId = activeItem.simklId,
                                                        season = activeItem.season,
                                                        episodeNumber = eNum,
                                                        mediaType = activeItem.type,
                                                        showTitle = activeItem.title
                                                    ) { _, revertMsg ->
                                                        scope.launch { snackbarHostState.showSnackbar(revertMsg) }
                                                    }
                                                }
                                            }
                                        } else {
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                        }
                                    }
                                },
                                enabled = !isMarkingWatched && !activeItem.isWatched,
                                modifier = modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (activeItem.isWatched) Color(0xFF2E6543) else Color(0xFF381E72),
                                    contentColor = if (activeItem.isWatched) Color(0xFF7CE49F) else Color(0xFFEADDFF),
                                    disabledContainerColor = if (activeItem.isWatched) Color(0xFF1E3A2B) else Color(0xFF3B383E),
                                    disabledContentColor = if (activeItem.isWatched) Color(0xFF7CE49F) else Color(0xFF79747E)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isMarkingWatched) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFFEADDFF)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Updating SIMKL...", fontWeight = FontWeight.Bold)
                                } else if (activeItem.isWatched) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("$epLabel Watched", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Mark $epLabel as Watched", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        @Composable
                        fun SeasonWatchSection(modifier: Modifier = Modifier) {
                            Button(
                                onClick = {
                                    viewModel.markSeasonWatched(
                                        simklId = activeItem.simklId,
                                        season = sNum,
                                        mediaType = activeItem.type,
                                        showTitle = activeItem.title
                                    ) { success, msg ->
                                        if (success) {
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = msg,
                                                    actionLabel = "Revert",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    viewModel.markSeasonUnwatched(
                                                        simklId = activeItem.simklId,
                                                        season = sNum,
                                                        mediaType = activeItem.type,
                                                        showTitle = activeItem.title
                                                    ) { _, revertMsg ->
                                                        scope.launch { snackbarHostState.showSnackbar(revertMsg) }
                                                    }
                                                }
                                            }
                                        } else {
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                        }
                                    }
                                },
                                enabled = !isMarkingWatched && !isSeasonFullyWatched,
                                modifier = modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSeasonFullyWatched) Color(0xFF2E6543) else Color(0xFF4F378B),
                                    contentColor = if (isSeasonFullyWatched) Color(0xFF7CE49F) else Color(0xFFEADDFF),
                                    disabledContainerColor = if (isSeasonFullyWatched) Color(0xFF1E3A2B) else Color(0xFF3B383E),
                                    disabledContentColor = if (isSeasonFullyWatched) Color(0xFF7CE49F) else Color(0xFF79747E)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isMarkingWatched) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFFEADDFF)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Updating SIMKL...", fontWeight = FontWeight.Bold)
                                } else if (isSeasonFullyWatched) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$seasonLabel Watched",
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Mark $seasonLabel as Watched",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val isWide = maxWidth >= 600.dp
                            if (isWide) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    EpisodeWatchSection(modifier = Modifier.weight(1f))
                                    SeasonWatchSection(modifier = Modifier.weight(1f))
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    EpisodeWatchSection(modifier = Modifier.fillMaxWidth())
                                    SeasonWatchSection(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }

                    // Open on SIMKL Button (Filled Button)
                    Button(
                        onClick = {
                            val urlType = when (activeItem.type) {
                                MediaType.MOVIE -> "movies"
                                MediaType.ANIME -> "anime"
                                MediaType.TV -> "tv"
                            }
                            val simklUrl = "https://simkl.com/$urlType/${activeItem.simklId}"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(simklUrl))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6750A4),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open on SIMKL",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open on SIMKL",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Matching Custom Search Links
                    val matchingSearchLinks = remember(allSearchLinks, activeItem) {
                        allSearchLinks.filter { it.associatedTypes.contains(activeItem.type) }
                    }

                    if (matchingSearchLinks.isNotEmpty()) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                            border = BorderStroke(1.dp, Color(0xFF49454F))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color(0xFFD0BCFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Custom Search Links",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE6E1E5),
                                        fontSize = 15.sp
                                    )
                                }

                                matchingSearchLinks.forEach { link ->
                                    val resolvedUrl = remember(link, activeItem) {
                                        link.buildUrl(activeItem)
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1C1B1F),
                                        border = BorderStroke(1.dp, Color(0xFF49454F)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resolvedUrl))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Unable to open link: ${e.message}")
                                                    }
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF2B2930),
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.fillMaxSize()
                                                    ) {
                                                        AsyncImage(
                                                            model = link.getFaviconUrl(),
                                                            contentDescription = link.name,
                                                            modifier = Modifier.size(20.dp),
                                                            contentScale = ContentScale.Fit
                                                        )
                                                    }
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = link.name,
                                                        color = Color(0xFFE6E1E5),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                    if (!link.subtitle.isNullOrBlank()) {
                                                        Text(
                                                            text = link.subtitle,
                                                            color = Color(0xFFCAC4D0),
                                                            fontSize = 11.sp,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }

                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = "Open ${link.name}",
                                                tint = Color(0xFFD0BCFF),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // If there are multiple scheduled episodes/releases for this show/movie, display a selector / list
                    if (showScheduleItems.size > 1) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                            border = BorderStroke(1.dp, Color(0xFF49454F))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = if (activeItem.type == MediaType.MOVIE) "Scheduled Releases (${showScheduleItems.size})" else "Scheduled Episodes (${showScheduleItems.size})",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE6E1E5),
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                showScheduleItems.forEach { epItem ->
                                    val isSelected = epItem.primaryKey == activeItem.primaryKey
                                    val epTag = if (epItem.type == MediaType.MOVIE) {
                                        if (epItem.movieReleaseType == MovieReleaseType.THEATER) "THEATER" else "DIGITAL / DVD"
                                    } else {
                                        epItem.formattedEpisodeCode
                                    }
                                    val epDate = if (epItem.type == MediaType.MOVIE) DateUtil.formatDisplayDate(epItem.date) else DateUtil.formatDisplayDateTime(epItem.date)

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color(0xFF4F378B).copy(alpha = 0.4f) else Color(0xFF1C1B1F),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) Color(0xFFD0BCFF) else Color(0xFF3B383E)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable { activeItemKey = epItem.primaryKey }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (isSelected) Color(0xFFD0BCFF) else Color(0xFF381E72),
                                                    contentColor = if (isSelected) Color(0xFF381E72) else Color(0xFFEADDFF)
                                                ) {
                                                    Text(
                                                        text = epTag,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }

                                                if (epItem.isWatched) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFF1E3A2B),
                                                        contentColor = Color(0xFF7CE49F)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(10.dp),
                                                                tint = Color(0xFF7CE49F)
                                                            )
                                                            Text(
                                                                text = "WATCHED",
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }

                                                Column {
                                                    Text(
                                                        text = if (epItem.type == MediaType.MOVIE) {
                                                             epItem.movieReleaseType?.displayName ?: "Movie Release"
                                                        } else {
                                                            epItem.episodeTitle?.takeIf { it.isNotBlank() } ?: "TBA"
                                                        },
                                                        color = Color(0xFFE6E1E5),
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        text = epDate,
                                                        color = Color(0xFFA5A3B1),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }

                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Currently Selected",
                                                    tint = Color(0xFFD0BCFF),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Per-Show / Per-Movie Notification Settings
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                        border = BorderStroke(1.dp, Color(0xFF49454F))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Notifications Strategy", fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5), fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(12.dp))

                            if (activeItem.type != MediaType.MOVIE) {
                                // Toggle every episode
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Episode Alerts", color = Color(0xFFE6E1E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Notify me as soon as each episode of this series airs.", color = Color(0xFFCAC4D0), fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = notifyEveryEpisode,
                                        onCheckedChange = { isChecked ->
                                            notifyEveryEpisode = isChecked
                                            if (isChecked) checkAndRequestNotificationPermission()
                                            viewModel.toggleNotification(
                                                simklId = activeItem.simklId,
                                                notifyEpisode = isChecked,
                                                notifySeasonFinished = notifySeasonFinished
                                            )
                                        }
                                    )
                                }

                                HorizontalDivider(color = Color(0xFF49454F), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                                // Toggle Season Finished Airing
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Season Finished Airing", color = Color(0xFFE6E1E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Notify me when the season has finished airing.", color = Color(0xFFCAC4D0), fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = notifySeasonFinished,
                                        onCheckedChange = { isChecked ->
                                            notifySeasonFinished = isChecked
                                            if (isChecked) checkAndRequestNotificationPermission()
                                            viewModel.toggleNotification(
                                                simklId = activeItem.simklId,
                                                notifyEpisode = notifyEveryEpisode,
                                                notifySeasonFinished = isChecked
                                            )
                                        }
                                    )
                                }
                            } else {
                                // Movie Theater Release Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Theater Release", color = Color(0xFFE6E1E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Notify me when the movie releases in theaters.", color = Color(0xFFCAC4D0), fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = notifyEveryEpisode,
                                        onCheckedChange = { isChecked ->
                                            notifyEveryEpisode = isChecked
                                            if (isChecked) checkAndRequestNotificationPermission()
                                            viewModel.toggleNotification(
                                                simklId = activeItem.simklId,
                                                notifyEpisode = isChecked,
                                                notifySeasonFinished = notifySeasonFinished
                                            )
                                        }
                                    )
                                }

                                HorizontalDivider(color = Color(0xFF49454F), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                                // Movie Digital / DVD Release Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Digital / DVD Release", color = Color(0xFFE6E1E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Notify me when the movie is available on digital or DVD.", color = Color(0xFFCAC4D0), fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = notifySeasonFinished,
                                        onCheckedChange = { isChecked ->
                                            notifySeasonFinished = isChecked
                                            if (isChecked) checkAndRequestNotificationPermission()
                                            viewModel.toggleNotification(
                                                simklId = activeItem.simklId,
                                                notifyEpisode = notifyEveryEpisode,
                                                notifySeasonFinished = isChecked
                                            )
                                        }
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

