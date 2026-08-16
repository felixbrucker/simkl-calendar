package com.example.ui.screens

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.database.CalendarItem
import com.example.data.util.DateUtil
import com.example.ui.viewmodel.CalendarViewModel
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
    val allItems by viewModel.allCalendarItems.collectAsState()

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
            ?: allItems.firstOrNull { it.id.toString() == activeItemKey }
            ?: allItems.firstOrNull { it.primaryKey == itemKey }
            ?: allItems.firstOrNull { it.id.toString() == itemKey }
    }

    val showScheduleItems = remember(allItems, activeItem) {
        if (activeItem != null) {
            allItems.filter { it.id == activeItem.id }
                .sortedWith(compareBy<CalendarItem> { it.date }.thenBy { it.season }.thenBy { it.episodeNumber })
        } else {
            emptyList()
        }
    }

    val settingsList by viewModel.notificationSettings.collectAsState()
    val showSetting = remember(settingsList, activeItem) {
        if (activeItem != null) settingsList.firstOrNull { it.showId == activeItem.id } else null
    }

    val prefs = remember { context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE) }
    val defaultAiring = prefs.getBoolean("default_notify_airing", false)
    val defaultSeasonFinished = prefs.getBoolean("default_notify_season_finished", true)
    val isMovie = activeItem?.type == "movie"
    val defaultMovieNotify = defaultAiring || defaultSeasonFinished

    // Individual notification toggle flows
    var notifyEveryEpisode by remember(showSetting, defaultAiring, defaultMovieNotify, isMovie) {
        mutableStateOf(showSetting?.notifyEveryEpisode ?: (if (isMovie) defaultMovieNotify else defaultAiring))
    }
    var notifySeasonFinished by remember(showSetting, defaultSeasonFinished, defaultMovieNotify, isMovie) {
        mutableStateOf(showSetting?.notifyAiredLastEpisode ?: (if (isMovie) defaultMovieNotify else defaultSeasonFinished))
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
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_to_calendar_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFE6E1E5))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1C1B1F))
            )
        },
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
                        model = activeItem.poster,
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
                                "anime" -> Color(0xFFE8DEF8)
                                "movie" -> Color(0xFFF2B8B5)
                                else -> Color(0xFFBAC3FF)
                            },
                            contentColor = when (activeItem.type) {
                                "anime" -> Color(0xFF1D192B)
                                "movie" -> Color(0xFF601410)
                                else -> Color(0xFF1A237E)
                            }
                        ) {
                            Text(
                                text = activeItem.type.uppercase(),
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
                                text = if (activeItem.type == "movie") "Release Information" else "Selected Episode Details",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE6E1E5),
                                fontSize = 15.sp
                            )

                            val dateLabel = if (activeItem.type == "movie") "Digital / DVD Release" else "Air Date"
                            val formattedDateTime = if (activeItem.type == "movie") {
                                DateUtil.formatDisplayDate(activeItem.date)
                            } else {
                                DateUtil.formatDisplayDateTime(activeItem.date)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(dateLabel, color = Color(0xFFCAC4D0), fontSize = 14.sp)
                                Text(
                                    text = formattedDateTime,
                                    color = Color(0xFFE6E1E5),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }

                            // Season and Episode Slug positioned above Episode Name
                            if (activeItem.type != "movie") {
                                val sNum = activeItem.season ?: 1
                                val eNum = activeItem.episodeNumber ?: 1
                                val (slugLabel, slugValue) = if (activeItem.type == "anime") {
                                    "Episode" to "Episode $eNum"
                                } else {
                                    "Season & Episode" to String.format(Locale.US, "S%02dE%02d", sNum, eNum)
                                }

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

                                if (!activeItem.episodeTitle.isNullOrBlank()) {
                                    val titleText = activeItem.episodeTitle
                                    val isGenericAnimeTitle = activeItem.type == "anime" && (
                                        titleText.equals("Episode $eNum", ignoreCase = true) ||
                                        titleText.equals("Ep $eNum", ignoreCase = true) ||
                                        titleText.equals("Ep. $eNum", ignoreCase = true)
                                    )

                                    if (!isGenericAnimeTitle) {
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

                    // If there are multiple scheduled episodes for this show, display an episode selector / list
                    if (showScheduleItems.size > 1) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                            border = BorderStroke(1.dp, Color(0xFF49454F))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Scheduled Episodes (${showScheduleItems.size})",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE6E1E5),
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                showScheduleItems.forEach { epItem ->
                                    val isSelected = epItem.primaryKey == activeItem.primaryKey
                                    val s = epItem.season ?: 1
                                    val e = epItem.episodeNumber ?: 1
                                    val epTag = if (epItem.type == "anime") "Ep $e" else String.format(Locale.US, "S%02dE%02d", s, e)
                                    val epDate = DateUtil.formatDisplayDateTime(epItem.date)

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
                                            .testTag("schedule_episode_row_${epItem.primaryKey}")
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

                                                Column {
                                                    Text(
                                                        text = epItem.episodeTitle?.takeIf { it.isNotBlank() } ?: "Episode $e",
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

                    // Open on SIMKL Button
                    Button(
                        onClick = {
                            val urlType = when (activeItem.type) {
                                "movie", "movies" -> "movies"
                                "anime" -> "anime"
                                else -> "tv"
                            }
                            val simklUrl = "https://simkl.com/$urlType/${activeItem.id}"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(simklUrl))
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_on_simkl_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4F378B),
                            contentColor = Color(0xFFEADDFF)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open on SIMKL",
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFFEADDFF)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open on SIMKL",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Per-Show Notification Settings
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                        border = BorderStroke(1.dp, Color(0xFF49454F))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Notifications Strategy", fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5), fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(12.dp))

                            if (activeItem.type != "movie") {
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
                                                showId = activeItem.id,
                                                title = activeItem.title,
                                                type = activeItem.type,
                                                notifyEpisode = isChecked,
                                                notifySeasonFinished = notifySeasonFinished
                                            )
                                        },
                                        modifier = Modifier.testTag("detail_notify_episode_switch")
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
                                                showId = activeItem.id,
                                                title = activeItem.title,
                                                type = activeItem.type,
                                                notifyEpisode = notifyEveryEpisode,
                                                notifySeasonFinished = isChecked
                                            )
                                        },
                                        modifier = Modifier.testTag("detail_notify_season_finished_switch")
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Release Notification", color = Color(0xFFE6E1E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Send reminder notification on movie release day.", color = Color(0xFFCAC4D0), fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = notifyEveryEpisode,
                                        onCheckedChange = { isChecked ->
                                            notifyEveryEpisode = isChecked
                                            notifySeasonFinished = isChecked
                                            if (isChecked) checkAndRequestNotificationPermission()
                                            viewModel.toggleNotification(
                                                showId = activeItem.id,
                                                title = activeItem.title,
                                                type = activeItem.type,
                                                notifyEpisode = isChecked,
                                                notifySeasonFinished = isChecked
                                            )
                                        },
                                        modifier = Modifier.testTag("detail_notify_movie_switch")
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
