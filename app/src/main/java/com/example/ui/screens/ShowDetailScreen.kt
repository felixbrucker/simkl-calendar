package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.viewmodel.CalendarViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowDetailScreen(
    viewModel: CalendarViewModel,
    showId: Int,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items by viewModel.filteredCalendarItems.collectAsState()
    val matchingShow = remember(items, showId) {
        items.firstOrNull { it.id == showId }
    }

    val settingsList by viewModel.notificationSettings.collectAsState()
    val showSetting = remember(settingsList, showId) {
        settingsList.firstOrNull { it.showId == showId }
    }

    // Individual notification toggle flows
    var notifyEveryEpisode by remember(showSetting) {
        mutableStateOf(showSetting?.notifyEveryEpisode ?: true)
    }
    var notifyAiredLastEpisode by remember(showSetting) {
        mutableStateOf(showSetting?.notifyAiredLastEpisode ?: true)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Release Details", color = Color(0xFFE6E1E5), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_to_calendar_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFFE6E1E5))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1C1B1F))
            )
        },
        containerColor = Color(0xFF1C1B1F)
    ) { innerPadding ->
        if (matchingShow == null) {
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
                        model = matchingShow.poster,
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
                            color = when (matchingShow.type) {
                                "anime" -> Color(0xFFE8DEF8)
                                "movie" -> Color(0xFFF2B8B5)
                                else -> Color(0xFFBAC3FF)
                            },
                            contentColor = when (matchingShow.type) {
                                "anime" -> Color(0xFF1D192B)
                                "movie" -> Color(0xFF601410)
                                else -> Color(0xFF1A237E)
                            }
                        ) {
                            Text(
                                text = matchingShow.type.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = matchingShow.title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }

                // Airing details info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF49454F))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Airing Schedule", fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5), fontSize = 15.sp)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Airing Date", color = Color(0xFFCAC4D0), fontSize = 14.sp)
                                Text(matchingShow.date, color = Color(0xFFE6E1E5), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            }

                            if (matchingShow.type != "movie") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Release Focus", color = Color(0xFFCAC4D0), fontSize = 14.sp)
                                    Text("Season ${matchingShow.season} • Episode ${matchingShow.episodeNumber}", color = Color(0xFFE6E1E5), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                }

                                if (matchingShow.episodeTitle != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Episode Name", color = Color(0xFFCAC4D0), fontSize = 14.sp)
                                        Text(matchingShow.episodeTitle, color = Color(0xFFE6E1E5), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    }
                                }
                            }

                            // Prem/Finale labels
                            if (matchingShow.isSeasonPremiere || matchingShow.isSeasonFinale) {
                                Divider(color = Color(0xFF49454F), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (matchingShow.isSeasonPremiere) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFE8DEF8), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("🎉 SEASON PREMIERE", color = Color(0xFF1D192B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (matchingShow.isSeasonFinale) {
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

                    // Per-Show Notification Settings
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF49454F))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Notifications Strategy", fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5), fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(12.dp))

                            if (matchingShow.type != "movie") {
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
                                            viewModel.toggleNotification(
                                                showId = matchingShow.id,
                                                title = matchingShow.title,
                                                type = matchingShow.type,
                                                notifyEp = isChecked,
                                                notifyLast = notifyAiredLastEpisode
                                            )
                                        },
                                        modifier = Modifier.testTag("detail_notify_episode_switch")
                                    )
                                }

                                Divider(color = Color(0xFF49454F), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                                // Toggle Ready to Binge
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Binge Alert On Finale", color = Color(0xFFE6E1E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Notify me when season finale is reached, flagging series is ready to binge.", color = Color(0xFFCAC4D0), fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = notifyAiredLastEpisode,
                                        onCheckedChange = { isChecked ->
                                            notifyAiredLastEpisode = isChecked
                                            viewModel.toggleNotification(
                                                showId = matchingShow.id,
                                                title = matchingShow.title,
                                                type = matchingShow.type,
                                                notifyEp = notifyEveryEpisode,
                                                notifyLast = isChecked
                                            )
                                        },
                                        modifier = Modifier.testTag("detail_notify_binge_switch")
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
                                            viewModel.toggleNotification(
                                                showId = matchingShow.id,
                                                title = matchingShow.title,
                                                type = matchingShow.type,
                                                notifyEp = isChecked,
                                                notifyLast = notifyAiredLastEpisode
                                            )
                                        },
                                        modifier = Modifier.testTag("detail_notify_movie_switch")
                                      )
                                  }
                              }
                          }
                      }

                    // Test alerts button
                    Button(
                        onClick = { viewModel.testTriggerNotification(matchingShow) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("test_alert_trigger_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD0BCFF),
                            contentColor = Color(0xFF381E72)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, size = 18.dp, tint = Color(0xFF381E72))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulate Episode Airing Alert", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
