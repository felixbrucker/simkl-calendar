package com.felixbrucker.simklcalendar.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.ui.viewmodel.CalendarViewModel
import com.felixbrucker.simklcalendar.worker.SyncCalendarWorker
import com.felixbrucker.simklcalendar.worker.AutoDownloadWorker
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.core.content.edit
import com.felixbrucker.simklcalendar.data.util.PermissionUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: CalendarViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userToken by viewModel.userToken.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permission result */ }

    var hasExactAlarmPermission by remember {
        mutableStateOf(PermissionUtil.hasExactAlarmPermission(context))
    }

    val alarmPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasExactAlarmPermission = PermissionUtil.hasExactAlarmPermission(context)
    }

    fun checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (!hasExactAlarmPermission) {
            alarmPermissionLauncher.launch(PermissionUtil.getExactAlarmPermissionIntent(context))
        }
    }

    val prefs = remember { context.getSharedPreferences("notification_prefs", android.content.Context.MODE_PRIVATE) }
    var enableDefaultAiring by remember {
        mutableStateOf(prefs.getBoolean("default_notify_airing", false))
    }
    var enableDefaultSeasonFinished by remember {
        mutableStateOf(prefs.getBoolean("default_notify_season_finished", true))
    }
    var enableDefaultMovieTheater by remember {
        mutableStateOf(prefs.getBoolean("default_notify_movie_theater", false))
    }
    var enableDefaultMovieDigital by remember {
        mutableStateOf(prefs.getBoolean("default_notify_movie_digital", true))
    }

    val autoQuality by viewModel.autoDownloadQuality.collectAsState()
    val autoPreferHevc by viewModel.autoDownloadPreferHevc.collectAsState()
    val autoUnwatchedDefault by viewModel.autoDownloadUnwatchedDefault.collectAsState()
    val autoPreferredKeywords by viewModel.autoDownloadPreferredKeywords.collectAsState()
    val autoIgnoreKeywords by viewModel.autoDownloadIgnoreKeywords.collectAsState()

    var syncIntervalHours by remember {
        mutableFloatStateOf(prefs.getInt("sync_interval_hours", 12).toFloat())
    }

    var searchIntervalHours by remember {
        mutableFloatStateOf(prefs.getInt("search_interval_hours", 12).toFloat())
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isForceSyncing by viewModel.isForceSyncing.collectAsState()
    val customSearchLinks by viewModel.customSearchLinks.collectAsState()
    val density = LocalDensity.current

    var localLinks by remember(customSearchLinks) { mutableStateOf(customSearchLinks) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var itemSlotHeightPx by remember { mutableFloatStateOf(0f) }

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingLink by remember { mutableStateOf<CustomSearchLink?>(null) }
    var deleteConfirmLink by remember { mutableStateOf<CustomSearchLink?>(null) }

    var inputName by remember { mutableStateOf("") }
    var inputSubtitle by remember { mutableStateOf("") }
    var inputUrlTemplate by remember { mutableStateOf(TextFieldValue("")) }
    var inputSelectedTypes by remember { mutableStateOf(setOf(MediaType.TV, MediaType.ANIME, MediaType.MOVIE)) }
    var formError by remember { mutableStateOf<String?>(null) }

    fun openAddDialog() {
        editingLink = null
        inputName = ""
        inputSubtitle = ""
        inputUrlTemplate = TextFieldValue("")
        inputSelectedTypes = setOf(MediaType.TV, MediaType.ANIME, MediaType.MOVIE)
        formError = null
        showAddEditDialog = true
    }

    fun openEditDialog(link: CustomSearchLink) {
        editingLink = link
        inputName = link.name
        inputSubtitle = link.subtitle ?: ""
        inputUrlTemplate = TextFieldValue(
            text = link.urlTemplate,
            selection = TextRange(link.urlTemplate.length)
        )
        inputSelectedTypes = link.associatedTypes.toSet()
        formError = null
        showAddEditDialog = true
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Color(0xFFE6E1E5), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFE6E1E5))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1C1B1F))
            )
        },
        containerColor = Color(0xFF1C1B1F)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // System Permissions Card
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasExactAlarmPermission) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3B2D2C)),
                    border = BorderStroke(1.dp, Color(0xFFF2B8B5))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF2B8B5),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Exact Alarms Required",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF2B8B5),
                                fontSize = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "To ensure notifications for episode releases are delivered exactly when they air, the app needs permission to schedule exact alarms.",
                            color = Color(0xFFCAC4D0),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                alarmPermissionLauncher.launch(PermissionUtil.getExactAlarmPermissionIntent(context))
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF601410),
                                contentColor = Color(0xFFF2B8B5)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Permission", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // User Segment
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                border = BorderStroke(1.dp, Color(0xFF49454F))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Session Status", fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5), fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Logged in as",
                                fontSize = 13.sp,
                                color = Color(0xFFCAC4D0)
                            )
                            Text(
                                userToken?.username ?: "Not Authenticated",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFFD0BCFF)
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.logoutUser()
                                onNavigateBack()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Logout", fontSize = 14.sp)
                        }
                    }
                }
            }


            // Background Sync Interval Configuration Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                border = BorderStroke(1.dp, Color(0xFF49454F))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = null,
                                tint = Color(0xFFD0BCFF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Background Sync Interval",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE6E1E5),
                                fontSize = 16.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF4A4458)
                        ) {
                            Text(
                                text = "${syncIntervalHours.roundToInt()} hrs",
                                color = Color(0xFFD0BCFF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Sets how frequently the app runs background checks to discover new episode releases and sync your watchlist.",
                        color = Color(0xFFCAC4D0),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Slider(
                        value = syncIntervalHours,
                        onValueChange = { newValue ->
                            syncIntervalHours = newValue
                        },
                        onValueChangeFinished = {
                            val roundedHours = syncIntervalHours.roundToInt().coerceIn(1, 24)
                            prefs.edit { putInt("sync_interval_hours", roundedHours)}
                            SyncCalendarWorker.enqueuePeriodicSync(context, roundedHours.toLong())
                        },
                        valueRange = 1f..24f,
                        steps = 22, // 1 to 24 with 1-hour increments -> 22 discrete intermediate steps
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFD0BCFF),
                            activeTrackColor = Color(0xFFD0BCFF),
                            inactiveTrackColor = Color(0xFF49454F),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1 hour (frequent)", color = Color(0xFF938F99), fontSize = 11.sp)
                        Text("12 hours (default)", color = Color(0xFF938F99), fontSize = 11.sp)
                        Text("24 hours (daily)", color = Color(0xFF938F99), fontSize = 11.sp)
                    }
                }
            }

            // Notification Setup Defaults Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                border = BorderStroke(1.dp, Color(0xFF49454F))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Default Alerts (New Items)", fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5), fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Sets default alert preferences when new shows or movies are synced. Individual settings in Release Details will always take precedence.",
                        color = Color(0xFFCAC4D0),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // TV Shows & Anime Section Header
                    Text(
                        text = "TV Shows & Anime",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Show toggle 1: Airing Notifications
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Airing Notifications", color = Color(0xFFE6E1E5), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Default to alert as soon as each episode is ready to stream.", color = Color(0xFFCAC4D0), fontSize = 12.sp)
                        }
                        Switch(
                            checked = enableDefaultAiring,
                            onCheckedChange = {
                                enableDefaultAiring = it
                                prefs.edit {
                                    putBoolean("default_notify_airing", it)
                                }
                                if (it) checkAndRequestPermission()
                            }
                        )
                    }

                    // Show toggle 2: Season Finished Airing
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Season Finished Airing", color = Color(0xFFE6E1E5), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Default to notify when a full TV Show or Anime season has finished airing.", color = Color(0xFFCAC4D0), fontSize = 12.sp)
                        }
                        Switch(
                            checked = enableDefaultSeasonFinished,
                            onCheckedChange = {
                                enableDefaultSeasonFinished = it
                                prefs.edit {
                                    putBoolean("default_notify_season_finished", it)
                                }
                                if (it) checkAndRequestPermission()
                            }
                        )
                    }

                    HorizontalDivider(color = Color(0xFF49454F), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                    // Movies Section Header
                    Text(
                        text = "Movies",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF2B8B5),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Movie toggle 1: Theater Release Notifications
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Theater Release Notifications", color = Color(0xFFE6E1E5), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Default to notify on the movie's theatrical release date.", color = Color(0xFFCAC4D0), fontSize = 12.sp)
                        }
                        Switch(
                            checked = enableDefaultMovieTheater,
                            onCheckedChange = {
                                enableDefaultMovieTheater = it
                                prefs.edit {
                                    putBoolean("default_notify_movie_theater", it)
                                }
                                if (it) checkAndRequestPermission()
                            }
                        )
                    }

                    // Movie toggle 2: Digital / DVD Release Notifications
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Digital / DVD Release Notifications", color = Color(0xFFE6E1E5), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Default to notify when the movie releases digitally or on DVD.", color = Color(0xFFCAC4D0), fontSize = 12.sp)
                        }
                        Switch(
                            checked = enableDefaultMovieDigital,
                            onCheckedChange = {
                                enableDefaultMovieDigital = it
                                prefs.edit {
                                    putBoolean("default_notify_movie_digital", it)
                                }
                                if (it) checkAndRequestPermission()
                            }
                        )
                    }
                }
            }

            // Automatic Downloads Card
            val isDownloaderInstalled = remember { viewModel.isTorrentServiceInstalled() }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                border = BorderStroke(1.dp, Color(0xFF49454F)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .alpha(if (isDownloaderInstalled) 1f else 0.5f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFFD0BCFF), modifier = Modifier.size(20.dp))
                            Text("Automatic Downloads", fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5), fontSize = 16.sp)
                        }
                        if (isDownloaderInstalled) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF1E3A2B),
                                contentColor = Color(0xFF7CE49F)
                            ) {
                                Text(
                                    "AVAILABLE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF3B383E),
                                contentColor = Color(0xFFCAC4D0)
                            ) {
                                Text(
                                    "UNAVAILABLE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Configure how the app interacts with the external Torrent Downloader service.",
                        color = Color(0xFFCAC4D0),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    if (!isDownloaderInstalled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Downloader app not found. Please install the Torrent Downloader service to enable these features.",
                            color = Color(0xFFF2B8B5),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Quality Selection
                    Text("Preferred Quality", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("4K", "1080p", "720p").forEach { quality ->
                            val isSelected = autoQuality == quality
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateAutoDownloadQuality(quality) },
                                label = { Text(quality) },
                                enabled = isDownloaderInstalled
                            )
                        }
                    }

                    // HEVC Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Prefer HEVC / x265", color = Color(0xFFE6E1E5), fontSize = 15.sp)
                            Text("Prioritize high efficiency video coding results.", color = Color(0xFFCAC4D0), fontSize = 12.sp)
                        }
                        Switch(
                            checked = autoPreferHevc,
                            onCheckedChange = { viewModel.updateAutoDownloadPreferHevc(it) },
                            enabled = isDownloaderInstalled
                        )
                    }

                    // Default Download Unwatched
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Download Unwatched Episodes", color = Color(0xFFE6E1E5), fontSize = 15.sp)
                            Text("Default setting for newly tracked items.", color = Color(0xFFCAC4D0), fontSize = 12.sp)
                        }
                        Switch(
                            checked = autoUnwatchedDefault,
                            onCheckedChange = { viewModel.updateAutoDownloadUnwatchedDefault(it) },
                            enabled = isDownloaderInstalled
                        )
                    }

                    HorizontalDivider(color = Color(0xFF49454F), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                    // Periodic Torrent Search Interval Configuration
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Periodic Torrent Search",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE6E1E5),
                                    fontSize = 15.sp
                                )
                                Text(
                                    "Sets how frequently the app searches for torrents for episodes in 'Wanted' status.",
                                    color = Color(0xFFCAC4D0),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF4A4458)
                            ) {
                                Text(
                                    text = "${searchIntervalHours.roundToInt()} hrs",
                                    color = Color(0xFFD0BCFF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Slider(
                            value = searchIntervalHours,
                            onValueChange = { newValue ->
                                searchIntervalHours = newValue
                            },
                            onValueChangeFinished = {
                                val roundedHours = searchIntervalHours.roundToInt().coerceIn(1, 24)
                                prefs.edit { putInt("search_interval_hours", roundedHours)}
                                if (isDownloaderInstalled) {
                                    AutoDownloadWorker.enqueuePeriodicSearch(context, roundedHours.toLong())
                                }
                            },
                            enabled = isDownloaderInstalled,
                            valueRange = 1f..24f,
                            steps = 22,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFD0BCFF),
                                activeTrackColor = Color(0xFFD0BCFF),
                                inactiveTrackColor = Color(0xFF49454F),
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("1 hour", color = Color(0xFF938F99), fontSize = 11.sp)
                            Text("12 hours", color = Color(0xFF938F99), fontSize = 11.sp)
                            Text("24 hours", color = Color(0xFF938F99), fontSize = 11.sp)
                        }
                    }

                    HorizontalDivider(color = Color(0xFF49454F), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                    // Preferred Keywords
                    KeywordManagerSection(
                        title = "Preferred Keywords",
                        subtitle = "Torrents containing these tags will be prioritized.",
                        keywords = autoPreferredKeywords,
                        onAdd = { viewModel.addPreferredKeyword(it) },
                        onRemove = { viewModel.removePreferredKeyword(it) },
                        enabled = isDownloaderInstalled
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Ignore Keywords
                    KeywordManagerSection(
                        title = "Ignore Keywords",
                        subtitle = "Torrents containing these tags will be skipped.",
                        keywords = autoIgnoreKeywords,
                        onAdd = { viewModel.addIgnoreKeyword(it) },
                        onRemove = { viewModel.removeIgnoreKeyword(it) },
                        enabled = isDownloaderInstalled,
                        color = Color(0xFFF2B8B5)
                    )
                }
            }

            // Custom Search Links Management Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                border = BorderStroke(1.dp, Color(0xFF49454F))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFFD0BCFF),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Custom Search Links",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE6E1E5),
                                fontSize = 16.sp
                            )
                        }

                        FilledTonalButton(
                            onClick = { openAddDialog() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("add_search_link_button")
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add Link",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Link", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (localLinks.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1C1B1F),
                            border = BorderStroke(1.dp, Color(0xFF3B383E)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.LinkOff,
                                    contentDescription = null,
                                    tint = Color(0xFF79747E),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "No custom search links configured yet",
                                    color = Color(0xFFCAC4D0),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Tap '+ Add Link' above to configure custom search shortcuts.",
                                    color = Color(0xFF79747E),
                                    fontSize = 11.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (localLinks.size > 1) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(bottom = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = null,
                                        tint = Color(0xFF938F99),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Long press and drag anywhere on a link to reorder",
                                        color = Color(0xFF938F99),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            val currentLinksState by rememberUpdatedState(localLinks)
                            val currentDragging = draggingIndex
                            val effectiveSlotHeight = if (itemSlotHeightPx > 0f) itemSlotHeightPx else with(density) { 68.dp.toPx() }
                            val currentSlotHeightState by rememberUpdatedState(effectiveSlotHeight)
                            val targetIndex = if (currentDragging != null && effectiveSlotHeight > 0f) {
                                (currentDragging + (dragOffsetY / effectiveSlotHeight).roundToInt())
                                    .coerceIn(0, localLinks.size - 1)
                            } else null

                            localLinks.forEachIndexed { index, link ->
                                key(link.id) {
                                    val isDraggingThis = currentDragging == index
                                    val visualTranslationY by animateFloatAsState(
                                        targetValue = when {
                                            isDraggingThis -> dragOffsetY
                                            currentDragging != null && targetIndex != null -> {
                                                when {
                                                    currentDragging < targetIndex && index in (currentDragging + 1)..targetIndex -> -effectiveSlotHeight
                                                    currentDragging > targetIndex && index in targetIndex until currentDragging -> effectiveSlotHeight
                                                    else -> 0f
                                                }
                                            }
                                            else -> 0f
                                        },
                                        label = "reorder_trans_${link.id}"
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isDraggingThis) Color(0xFF36323D) else Color(0xFF1C1B1F),
                                        border = BorderStroke(
                                            width = if (isDraggingThis) 1.5.dp else 1.dp,
                                            color = if (isDraggingThis) Color(0xFFD0BCFF) else Color(0xFF49454F)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .zIndex(if (isDraggingThis) 10f else 1f)
                                            .onGloballyPositioned { coordinates ->
                                                if (itemSlotHeightPx == 0f && coordinates.size.height > 0) {
                                                    itemSlotHeightPx = coordinates.size.height.toFloat() + with(density) { 8.dp.toPx() }
                                                }
                                            }
                                            .graphicsLayer {
                                                translationY = visualTranslationY
                                                scaleX = if (isDraggingThis) 1.03f else 1f
                                                scaleY = if (isDraggingThis) 1.03f else 1f
                                                shadowElevation = if (isDraggingThis) with(density) { 8.dp.toPx() } else 0f
                                            }
                                            .pointerInput(link.id) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        val idx = currentLinksState.indexOfFirst { it.id == link.id }
                                                        draggingIndex = if (idx != -1) idx else index
                                                        dragOffsetY = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffsetY += dragAmount.y
                                                    },
                                                    onDragEnd = {
                                                        val from = draggingIndex
                                                        val slotH = currentSlotHeightState
                                                        val links = currentLinksState
                                                        val to = if (from != null && slotH > 0f && links.isNotEmpty()) {
                                                            (from + (dragOffsetY / slotH).roundToInt())
                                                                .coerceIn(0, links.size - 1)
                                                        } else null

                                                        if (from != null && to != null && from != to) {
                                                            val updated = links.toMutableList().apply {
                                                                add(to, removeAt(from))
                                                            }
                                                            localLinks = updated
                                                            viewModel.updateSearchLinksOrder(updated)
                                                        }
                                                        draggingIndex = null
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragCancel = {
                                                        draggingIndex = null
                                                        dragOffsetY = 0f
                                                    }
                                                )
                                            }
                                            .testTag("custom_search_link_item_${link.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Drag Handle indicator icon
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .testTag("drag_handle_${link.id}"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.DragHandle,
                                                    contentDescription = "Reorder handle for ${link.name}",
                                                    tint = if (isDraggingThis) Color(0xFFD0BCFF) else Color(0xFF79747E),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = link.name,
                                                        color = Color(0xFFE6E1E5),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                    if (!link.subtitle.isNullOrBlank()) {
                                                        Text(
                                                            text = "• ${link.subtitle}",
                                                            color = Color(0xFFCAC4D0),
                                                            fontSize = 12.sp,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = link.urlTemplate,
                                                    color = Color(0xFF938F99),
                                                    fontSize = 11.sp,
                                                    maxLines = 1
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    link.associatedTypes.forEach { type ->
                                                        val (label, bgCol, textCol) = when (type) {
                                                            MediaType.TV -> Triple("TV", Color(0xFF381E72), Color(0xFFEADDFF))
                                                            MediaType.ANIME -> Triple("Anime", Color(0xFF00382B), Color(0xFF7CE49F))
                                                            MediaType.MOVIE -> Triple("Movie", Color(0xFF601410), Color(0xFFF2B8B5))
                                                        }
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = bgCol
                                                        ) {
                                                            Text(
                                                                text = label,
                                                                color = textCol,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            IconButton(
                                                onClick = { openEditDialog(link) },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .testTag("edit_link_${link.id}")
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Edit ${link.name}",
                                                    tint = Color(0xFFD0BCFF),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { deleteConfirmLink = link },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .testTag("delete_link_${link.id}")
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete ${link.name}",
                                                    tint = Color(0xFFF2B8B5),
                                                    modifier = Modifier.size(18.dp)
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
            }

            // Force Watchlist Re-Sync Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                border = BorderStroke(1.dp, Color(0xFF49454F))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Watchlist Synchronization",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6E1E5),
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Forces a full re-synchronization of your complete SIMKL watchlist and watched history from scratch.",
                        color = Color(0xFFCAC4D0),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.forceWatchlistResync { _, message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        },
                        enabled = !isForceSyncing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4F378B),
                            contentColor = Color(0xFFEADDFF),
                            disabledContainerColor = Color(0xFF3B383E),
                            disabledContentColor = Color(0xFF79747E)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (isForceSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFEADDFF)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Re-syncing Watchlist...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Force Watchlist Re-Sync", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Custom Search Link Dialog
    if (showAddEditDialog) {
        AlertDialog(
            onDismissRequest = { showAddEditDialog = false },
            title = {
                Text(
                    text = if (editingLink == null) "Add Custom Search Link" else "Edit Custom Search Link",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6E1E5)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Name input
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Name") },
                        placeholder = { Text("e.g. Search Service") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_search_link_name_input")
                    )

                    // Subtitle input
                    OutlinedTextField(
                        value = inputSubtitle,
                        onValueChange = { inputSubtitle = it },
                        label = { Text("Subtitle (Optional)") },
                        placeholder = { Text("e.g. Search ratings & cast") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_search_link_subtitle_input")
                    )

                    // URL Template input
                    OutlinedTextField(
                        value = inputUrlTemplate,
                        onValueChange = { inputUrlTemplate = it },
                        label = { Text("URL Template") },
                        placeholder = { Text("https://example.com/search?q={TITLE}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_search_link_url_input")
                    )

                    fun insertPlaceholder(placeholder: String) {
                        val currentText = inputUrlTemplate.text
                        val selection = inputUrlTemplate.selection
                        val start = selection.min.coerceIn(0, currentText.length)
                        val end = selection.max.coerceIn(0, currentText.length)

                        val newText = currentText.substring(0, start) + placeholder + currentText.substring(end)
                        val newCursorPos = start + placeholder.length
                        inputUrlTemplate = TextFieldValue(
                            text = newText,
                            selection = TextRange(newCursorPos)
                        )
                    }

                    // Placeholder helper chips
                    Column {
                        Text(
                            text = "Tap placeholder to insert:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFCAC4D0)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "{TITLE}",
                                "{TITLE_URL_ENCODED}",
                                "{TITLE_ROMAJI}",
                                "{TITLE_ROMAJI_URL_ENCODED}",
                                "{EPISODE_SLUG}",
                                "{SEASON_SLUG}",
                                "{SEASON}",
                                "{EPISODE}"
                            ).forEach { placeholder ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF2B2930),
                                    border = BorderStroke(1.dp, Color(0xFF49454F)),
                                    modifier = Modifier.clickable {
                                        insertPlaceholder(placeholder)
                                    }
                                ) {
                                    Text(
                                        text = placeholder,
                                        color = Color(0xFFD0BCFF),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF49454F), thickness = 1.dp)

                    // Associated Item Types
                    Column {
                        Text(
                            text = "Associated Item Types",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE6E1E5)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val types = listOf(
                                MediaType.TV to "TV Show",
                                MediaType.ANIME to "Anime",
                                MediaType.MOVIE to "Movie"
                            )
                            types.forEach { (type, label) ->
                                val isSelected = inputSelectedTypes.contains(type)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        inputSelectedTypes = if (isSelected) {
                                            inputSelectedTypes - type
                                        } else {
                                            inputSelectedTypes + type
                                        }
                                    },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    if (formError != null) {
                        Text(
                            text = formError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputName.isBlank()) {
                            formError = "Name cannot be blank"
                            return@Button
                        }
                        if (inputUrlTemplate.text.isBlank()) {
                            formError = "URL template cannot be blank"
                            return@Button
                        }
                        if (inputSelectedTypes.isEmpty()) {
                            formError = "Select at least one associated item type"
                            return@Button
                        }

                        val linkToSave = CustomSearchLink(
                            id = editingLink?.id ?: 0L,
                            name = inputName.trim(),
                            subtitle = inputSubtitle.trim().takeIf { it.isNotBlank() },
                            urlTemplate = inputUrlTemplate.text.trim(),
                            associatedTypes = inputSelectedTypes.toList(),
                            position = editingLink?.position ?: 0
                        )

                        viewModel.saveCustomSearchLink(linkToSave) {
                            showAddEditDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_custom_search_link_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    deleteConfirmLink?.let { link ->
        AlertDialog(
            onDismissRequest = { deleteConfirmLink = null },
            title = {
                Text("Delete Search Link", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to delete '${link.name}'? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomSearchLink(link) {
                            deleteConfirmLink = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_search_link_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmLink = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeywordManagerSection(
    title: String,
    subtitle: String,
    keywords: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    enabled: Boolean,
    color: Color = Color(0xFFD0BCFF)
) {
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingKeyword by remember { mutableStateOf<String?>(null) }
    var keywordInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, color = Color(0xFFCAC4D0), fontSize = 11.sp)
            }
            IconButton(
                onClick = {
                    editingKeyword = null
                    keywordInput = ""
                    showAddEditDialog = true
                },
                enabled = enabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Keyword", tint = color)
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            keywords.forEach { keyword ->
                InputChip(
                    selected = false,
                    onClick = {
                        if (enabled) {
                            editingKeyword = keyword
                            keywordInput = keyword
                            showAddEditDialog = true
                        }
                    },
                    label = { Text(keyword, fontSize = 12.sp) },
                    trailingIcon = {
                        if (enabled) {
                            IconButton(
                                onClick = { onRemove(keyword) },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    },
                    colors = InputChipDefaults.inputChipColors(
                        containerColor = Color(0xFF1C1B1F),
                        labelColor = Color(0xFFE6E1E5)
                    ),
                    enabled = enabled
                )
            }
        }
    }

    if (showAddEditDialog) {
        AlertDialog(
            onDismissRequest = { showAddEditDialog = false },
            title = { Text(if (editingKeyword == null) "Add Keyword" else "Edit Keyword", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = keywordInput,
                    onValueChange = { keywordInput = it },
                    label = { Text("Keyword") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (keywordInput.isNotBlank()) {
                            if (editingKeyword != null) {
                                onRemove(editingKeyword!!)
                            }
                            onAdd(keywordInput.trim())
                            keywordInput = ""
                            showAddEditDialog = false
                        }
                    }
                ) {
                    Text(if (editingKeyword == null) "Add" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

