package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.MediaType
import com.example.data.model.MovieReleaseType
import com.example.ui.viewmodel.CalendarViewModel
import com.example.receiver.NotificationReceiver
import com.example.worker.SyncCalendarWorker
import kotlin.math.roundToInt

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

    fun checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
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

    var syncIntervalHours by remember {
        mutableStateOf(prefs.getInt("sync_interval_hours", 12).toFloat())
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings & Notifications", color = Color(0xFFE6E1E5), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
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
            // User Segment
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF49454F))
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("settings_logout_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
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
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF49454F))
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
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp).testTag("sync_interval_value_badge")
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
                            prefs.edit().putInt("sync_interval_hours", roundedHours).apply()
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
                        modifier = Modifier.fillMaxWidth().testTag("sync_interval_slider")
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
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF49454F))
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
                                prefs.edit()
                                    .putBoolean("default_notify_airing", it)
                                    .apply()
                                if (it) checkAndRequestPermission()
                            },
                            modifier = Modifier.testTag("air_notification_switch")
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
                                prefs.edit()
                                    .putBoolean("default_notify_season_finished", it)
                                    .apply()
                                if (it) checkAndRequestPermission()
                            },
                            modifier = Modifier.testTag("season_finished_notification_switch")
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
                                prefs.edit()
                                    .putBoolean("default_notify_movie_theater", it)
                                    .apply()
                                if (it) checkAndRequestPermission()
                            },
                            modifier = Modifier.testTag("movie_theater_notification_switch")
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
                                prefs.edit()
                                    .putBoolean("default_notify_movie_digital", it)
                                    .apply()
                                if (it) checkAndRequestPermission()
                            },
                            modifier = Modifier.testTag("movie_digital_notification_switch")
                        )
                    }
                }
            }

            // Manual Debug / Trigger Testing segment
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF49454F))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Test Notification Channel Drivers", fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5), fontSize = 16.sp)
                    Text("Directly fire alerts to verify delivery on your active Android device or emulator drawer instantly.", color = Color(0xFFCAC4D0), fontSize = 12.sp)

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            checkAndRequestPermission()
                            NotificationReceiver.triggerEpisodeNotification(
                                context,
                                showTitle = "Demon Slayer: Kimetsu no Yaiba",
                                episodeName = "The Hashira Training",
                                season = 4,
                                episodeNumber = 1,
                                isLastEpisode = false,
                                type = MediaType.ANIME
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("simulate_episode_alert_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF49454F),
                            contentColor = Color(0xFFE6E1E5)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFE6E1E5))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulate Episode Alert", color = Color(0xFFE6E1E5))
                    }

                    Button(
                        onClick = {
                            checkAndRequestPermission()
                            NotificationReceiver.triggerEpisodeNotification(
                                context,
                                showTitle = "Succession",
                                episodeName = "With Open Eyes",
                                season = 4,
                                episodeNumber = 10,
                                isLastEpisode = true,
                                type = MediaType.TV,
                                totalEpisodes = 10
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("simulate_season_finished_alert_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF381E72),
                            contentColor = Color(0xFFD0BCFF)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.MovieFilter, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFD0BCFF))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulate Season Finished Alert", color = Color(0xFFD0BCFF))
                    }

                    Button(
                        onClick = {
                            checkAndRequestPermission()
                            NotificationReceiver.triggerEpisodeNotification(
                                context,
                                showTitle = "Dune: Part Two",
                                episodeName = null,
                                season = null,
                                episodeNumber = null,
                                isLastEpisode = false,
                                type = MediaType.MOVIE,
                                movieReleaseType = MovieReleaseType.THEATER
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("simulate_movie_theater_alert_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4A2525),
                            contentColor = Color(0xFFF2B8B5)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.LocalMovies, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFF2B8B5))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulate Movie Theater Alert", color = Color(0xFFF2B8B5))
                    }

                    Button(
                        onClick = {
                            checkAndRequestPermission()
                            NotificationReceiver.triggerEpisodeNotification(
                                context,
                                showTitle = "Dune: Part Two",
                                episodeName = null,
                                season = null,
                                episodeNumber = null,
                                isLastEpisode = false,
                                type = MediaType.MOVIE,
                                movieReleaseType = MovieReleaseType.DIGITAL
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("simulate_movie_digital_alert_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1B3B2B),
                            contentColor = Color(0xFFA8DAB5)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFA8DAB5))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulate Movie Digital / DVD Alert", color = Color(0xFFA8DAB5))
                    }
                }
            }
        }
    }
}
