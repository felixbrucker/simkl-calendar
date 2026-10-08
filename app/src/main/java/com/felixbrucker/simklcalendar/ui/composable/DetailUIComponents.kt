package com.felixbrucker.simklcalendar.ui.composable

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.model.EpisodeSearchStyle
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadPreferences
import com.felixbrucker.simklcalendar.data.util.MediaFormatter
import com.felixbrucker.simklcalendar.data.util.PosterSize
import com.felixbrucker.simklcalendar.extensions.formatAnimeSeasonTokens
import com.felixbrucker.simklcalendar.extensions.toNormalizedAnimeTitle
import com.felixbrucker.simklcalendar.extensions.toPosterUrl
import com.felixbrucker.torrent_search_api.SearchResultItem
import kotlinx.coroutines.launch

@Composable
fun SeasonOverrideDialog(
    existingOverrides: Map<Int, Int>,
    onSave: (Map<Int, Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var tempOverrides by remember { mutableStateOf(existingOverrides) }
    var origSeason by remember { mutableStateOf("") }
    var targetSeason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Season Overrides") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Remap season numbers for torrent searches.", fontSize = 12.sp, color = Color(0xFFCAC4D0))

                if (tempOverrides.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        tempOverrides.toSortedMap().forEach { (orig, target) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Season $orig → Season $target", color = Color(0xFFE6E1E5), fontSize = 14.sp)
                                IconButton(onClick = { tempOverrides = tempOverrides - orig }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFF2B8B5), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFF49454F))
                }

                Text("Add/Update Override", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFD0BCFF))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = origSeason,
                        onValueChange = { if (it.all { char -> char.isDigit() }) origSeason = it },
                        label = { Text("Original") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Text("→", color = Color.White)

                    OutlinedTextField(
                        value = targetSeason,
                        onValueChange = { if (it.all { char -> char.isDigit() }) targetSeason = it },
                        label = { Text("Target") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    IconButton(
                        onClick = {
                            val s = origSeason.toIntOrNull()
                            val v = targetSeason.toIntOrNull()
                            if (s != null && v != null) {
                                tempOverrides = tempOverrides + (s to v)
                                origSeason = ""
                                targetSeason = ""
                            }
                        },
                        enabled = origSeason.isNotBlank() && targetSeason.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color(0xFFD0BCFF))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(tempOverrides)
                onDismiss()
            }) {
                Text("Save All")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MediaStatusDropdown(
    currentStatus: MediaStatus,
    onStatusChange: (MediaStatus) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val (backgroundColor, textColor) = when (currentStatus) {
        MediaStatus.IGNORED -> Color(0xFF3B383E) to Color(0xFFA5A3B1)
        MediaStatus.NOT_AIRED_YET -> Color(0xFF3B383E) to Color(0xFFA5A3B1)
        MediaStatus.WANTED -> Color(0xFF601410) to Color(0xFFF9DEDC)
        MediaStatus.DOWNLOADING -> Color(0xFF004A77) to Color(0xFFC2E8FF)
        MediaStatus.DOWNLOADED -> Color(0xFF1E3A2B) to Color(0xFF7CE49F)
    }

    Box {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = backgroundColor,
            contentColor = textColor,
            border = BorderStroke(1.dp, textColor.copy(alpha = 0.3f)),
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = currentStatus.displayName.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF2B2930))
        ) {
            MediaStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text(status.displayName, color = Color.White, fontSize = 14.sp) },
                    onClick = {
                        onStatusChange(status)
                        expanded = false
                    },
                    leadingIcon = {
                        val icon = when (status) {
                            MediaStatus.IGNORED -> Icons.Default.Block
                            MediaStatus.NOT_AIRED_YET -> Icons.Default.Schedule
                            MediaStatus.WANTED -> Icons.Default.Favorite
                            MediaStatus.DOWNLOADING -> Icons.Default.Download
                            MediaStatus.DOWNLOADED -> Icons.Default.CheckCircle
                        }
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFD0BCFF))
                    }
                )
            }
        }
    }
}

@Composable
fun WatchedStatusDropdown(
    isWatched: Boolean,
    onStatusChange: (Boolean) -> Unit,
    isLoading: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val backgroundColor = if (isWatched) Color(0xFF1E3A2B) else Color(0xFF3C1F1E)
    val textColor = if (isWatched) Color(0xFF7CE49F) else Color(0xFFF2B8B5)

    Box {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = backgroundColor,
            contentColor = textColor,
            border = BorderStroke(1.dp, textColor.copy(alpha = 0.3f)),
            modifier = Modifier.clickable(enabled = !isLoading) { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = textColor
                    )
                }
                Text(
                    text = if (isWatched) "WATCHED" else "UNWATCHED",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF2B2930))
        ) {
            DropdownMenuItem(
                text = { Text("Watched", color = Color.White, fontSize = 14.sp) },
                onClick = {
                    onStatusChange(true)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF7CE49F))
                }
            )
            DropdownMenuItem(
                text = { Text("Unwatched", color = Color.White, fontSize = 14.sp) },
                onClick = {
                    onStatusChange(false)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFF2B8B5))
                }
            )
        }
    }
}

@Composable
fun ItemWatchedStatusDropdown(
    item: CalendarItemWithWatchlist,
    onWatchedStatusChange: (isWatched: Boolean) -> Unit,
    updatingWatchKeys: Set<String>
) {
    WatchedStatusDropdown(
        isWatched = item.isWatched,
        onStatusChange = onWatchedStatusChange,
        isLoading = updatingWatchKeys.contains(item.primaryKey)
    )
}

@Composable
fun ItemMediaStatusDropdown(
    item: CalendarItemWithWatchlist,
    onStatusChange: (MediaStatus) -> Unit
) {
    MediaStatusDropdown(
        currentStatus = item.mediaStatus,
        onStatusChange = onStatusChange
    )
}

fun getTableItemColor(x: Int, y: Int): Color {
    if (y == 0 || x == y) return Color(0xFF7CE49F) // Green
    val ratio = x.toDouble() / y
    return if (ratio > 0.4) Color(0xFFE2E262) else Color(0xFFF2B8B5) // Yellow, Red
}

@Composable
fun NotificationSettingsCard(
    isMovie: Boolean,
    notifyEveryEpisode: Boolean,
    notifySeasonFinished: Boolean,
    onNotifyEveryEpisodeChange: (Boolean) -> Unit,
    onNotifySeasonFinishedChange: (Boolean) -> Unit,
    checkPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
        border = BorderStroke(1.dp, Color(0xFF49454F))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Notifications Strategy", fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5), fontSize = 15.sp)
            Spacer(modifier = Modifier.height(12.dp))

            if (!isMovie) {
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
                            onNotifyEveryEpisodeChange(isChecked)
                            if (isChecked) checkPermission()
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
                            onNotifySeasonFinishedChange(isChecked)
                            if (isChecked) checkPermission()
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
                            onNotifyEveryEpisodeChange(isChecked)
                            if (isChecked) checkPermission()
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
                            onNotifySeasonFinishedChange(isChecked)
                            if (isChecked) checkPermission()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DetailHeader(
    simklId: Int,
    type: MediaType,
    title: String,
    poster: String?,
    modifier: Modifier = Modifier,
    rating: Double? = null,
    titleRomaji: String? = null,
    hasUniqueTitleRomaji: Boolean = !titleRomaji.isNullOrBlank() && !titleRomaji.equals(title, ignoreCase = true),
    onTitleClick: () -> Unit = {},
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
    ) {
        AsyncImage(
            model = poster.toPosterUrl(PosterSize.WIDE),
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

        // Rating Badge on top right of poster
        if (rating != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Rating",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", rating),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Overlay Title metadata
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .padding(end = 100.dp) // Avoid overlapping with the button
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = when (type) {
                    MediaType.ANIME -> Color(0xFFE8DEF8)
                    MediaType.MOVIE -> Color(0xFFF2B8B5)
                    MediaType.TV -> Color(0xFFBAC3FF)
                },
                contentColor = when (type) {
                    MediaType.ANIME -> Color(0xFF1D192B)
                    MediaType.MOVIE -> Color(0xFF601410)
                    MediaType.TV -> Color(0xFF1A237E)
                }
            ) {
                Text(
                    text = type.displayName.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.clickable(onClick = onTitleClick)
            )

            if (hasUniqueTitleRomaji) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = titleRomaji!!,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFFCAC4D0)
                )
            }
        }

        // Open on SIMKL Button in Bottom Right
        Button(
            onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    MediaFormatter.formatSimklUrl(simklId, type).toUri(),
                )
                context.startActivity(intent)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .height(32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6750A4).copy(alpha = 0.8f),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Open on SIMKL",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsCard(
    simklId: Int,
    itemTitle: String,
    itemTitleRomaji: String?,
    animeSeason: Int?,
    mediaType: MediaType,
    defaultSubdirectory: String,
    autoDownloadPrefs: AutoDownloadPreferences,
    itemSettings: ItemDownloadSettings?,
    isDownloaderInstalled: Boolean,
    availableSubdirectories: List<String>,
    onSaveItemDownloadSettings: (ItemDownloadSettings) -> Unit,
    onSearchTorrents: (suspend (String) -> List<SearchResultItem>),
    modifier: Modifier = Modifier,
) {
    val globalUnwatched = when (mediaType) {
        MediaType.TV -> autoDownloadPrefs.autoDownloadUnwatchedTv
        MediaType.ANIME -> autoDownloadPrefs.autoDownloadUnwatchedAnime
        MediaType.MOVIE -> autoDownloadPrefs.autoDownloadUnwatchedMovie
    }
    val globalSeasonUnwatched = when (mediaType) {
        MediaType.TV -> autoDownloadPrefs.autoDownloadSeasonUnwatchedTv
        MediaType.ANIME -> autoDownloadPrefs.autoDownloadSeasonUnwatchedAnime
        MediaType.MOVIE -> false
    }
    val globalQuality = autoDownloadPrefs.quality
    val globalPreferHevc = autoDownloadPrefs.preferHevc

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
        border = BorderStroke(1.dp, Color(0xFF49454F))
    ) {
        Column(modifier = Modifier.padding(16.dp).alpha(if (isDownloaderInstalled) 1f else 0.5f)) {
            Text("Automatic Downloads", fontWeight = FontWeight.Bold, color = Color(0xFFE6E1E5), fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))

            // Unwatched Toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Download Unwatched", color = Color(0xFFE6E1E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Auto search and add unwatched episodes.", color = Color(0xFFCAC4D0), fontSize = 11.sp)
                }
                Switch(
                    checked = itemSettings?.downloadUnwatched ?: globalUnwatched,
                    onCheckedChange = {
                        onSaveItemDownloadSettings((itemSettings ?: ItemDownloadSettings(simklId)).copy(downloadUnwatched = it))
                    },
                    enabled = isDownloaderInstalled
                )
            }

            if (mediaType != MediaType.MOVIE) {
                Spacer(modifier = Modifier.height(16.dp))

                // Season Unwatched Toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Download Season Unwatched", color = Color(0xFFE6E1E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Auto download all episodes when a season finishes airing.", color = Color(0xFFCAC4D0), fontSize = 11.sp)
                    }
                    Switch(
                        checked = itemSettings?.downloadSeasonUnwatched ?: globalSeasonUnwatched,
                        onCheckedChange = {
                            onSaveItemDownloadSettings((itemSettings ?: ItemDownloadSettings(simklId)).copy(downloadSeasonUnwatched = it))
                        },
                        enabled = isDownloaderInstalled
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quality
            Text("Preferred Quality", color = Color(0xFFE6E1E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("4K", "1080p", "720p").forEach { quality ->
                    val isSelected = (itemSettings?.qualityOverride == quality) || (itemSettings?.qualityOverride == null && globalQuality == quality)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val next = if (isSelected && itemSettings?.qualityOverride != null) null else quality
                            onSaveItemDownloadSettings((itemSettings ?: ItemDownloadSettings(simklId)).copy(qualityOverride = next))
                        },
                        label = { Text(quality) },
                        enabled = isDownloaderInstalled
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // HEVC
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Prefer HEVC / x265", color = Color(0xFFE6E1E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Switch(
                    checked = itemSettings?.preferHevcOverride ?: globalPreferHevc,
                    onCheckedChange = {
                        onSaveItemDownloadSettings((itemSettings ?: ItemDownloadSettings(simklId)).copy(preferHevcOverride = it))
                    },
                    enabled = isDownloaderInstalled
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Download Subdirectory Override
            Text("Download Subdirectory", color = Color(0xFFE6E1E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            val subdirs = (listOf(defaultSubdirectory) + availableSubdirectories).distinct().sorted()
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                subdirs.forEach { subdir ->
                    val isSelected = (itemSettings?.downloadSubdirectoryOverride == subdir) || (itemSettings?.downloadSubdirectoryOverride == null && subdir == defaultSubdirectory)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val next = if (subdir == defaultSubdirectory) null else subdir
                            onSaveItemDownloadSettings((itemSettings ?: ItemDownloadSettings(simklId)).copy(downloadSubdirectoryOverride = next))
                        },
                        label = { Text(subdir, fontSize = 11.sp) },
                        enabled = isDownloaderInstalled
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title Override
            var showTitleDialog by remember { mutableStateOf(false) }
            var showDetectDialog by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Title Override", color = Color(0xFFE6E1E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Use custom search title for this item.", color = Color(0xFFCAC4D0), fontSize = 11.sp)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (mediaType == MediaType.ANIME && itemSettings?.titleOverride.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { showDetectDialog = true },
                            enabled = isDownloaderInstalled,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Detect", fontSize = 12.sp)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1C1B1F),
                        border = BorderStroke(1.dp, Color(0xFF49454F)),
                        modifier = Modifier.clickable(enabled = isDownloaderInstalled) { showTitleDialog = true }
                    ) {
                        Text(
                            text = itemSettings?.titleOverride ?: "None",
                            color = if (itemSettings?.titleOverride != null) Color(0xFFD0BCFF) else Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            if (showTitleDialog) {
                var tempTitle by remember { mutableStateOf(itemSettings?.titleOverride ?: "") }
                AlertDialog(
                    onDismissRequest = { showTitleDialog = false },
                    title = { Text("Title Override") },
                    text = {
                        OutlinedTextField(
                            value = tempTitle,
                            onValueChange = { tempTitle = it },
                            label = { Text("Custom Title") },
                            placeholder = { Text(itemTitle) },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            onSaveItemDownloadSettings((itemSettings ?: ItemDownloadSettings(simklId)).copy(titleOverride = tempTitle.trim().takeIf { it.isNotBlank() }))
                            showTitleDialog = false
                        }) { Text("Save") }
                    },
                    dismissButton = { TextButton(onClick = { showTitleDialog = false }) { Text("Cancel") } }
                )
            }
            if (showDetectDialog) {
                DetectTitleDialog(
                    initialTitle = (itemTitleRomaji?.takeIf { it.isNotBlank() } ?: itemTitle).toNormalizedAnimeTitle(),
                    animeSeason = animeSeason,
                    onSearchTorrents = onSearchTorrents,
                    onSave = { detectedTitle ->
                        onSaveItemDownloadSettings((itemSettings ?: ItemDownloadSettings(simklId)).copy(titleOverride = detectedTitle.trim().takeIf { it.isNotBlank() }))
                        showDetectDialog = false
                    },
                    onDismiss = { showDetectDialog = false }
                )
            }

            // Season Overrides
            if (mediaType != MediaType.MOVIE) {
                Spacer(modifier = Modifier.height(16.dp))
                var showSeasonDialog by remember { mutableStateOf(false) }
                val seasonOverrides = itemSettings?.seasonOverrides ?: emptyMap()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Season Overrides", color = Color(0xFFE6E1E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Map seasons for torrent searching.", color = Color(0xFFCAC4D0), fontSize = 11.sp)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1C1B1F),
                        border = BorderStroke(1.dp, Color(0xFF49454F)),
                        modifier = Modifier.clickable(enabled = isDownloaderInstalled) { showSeasonDialog = true }
                    ) {
                        Text(
                            text = if (seasonOverrides.isEmpty()) "None" else "${seasonOverrides.size} active",
                            color = if (seasonOverrides.isNotEmpty()) Color(0xFFD0BCFF) else Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                if (showSeasonDialog) {
                    SeasonOverrideDialog(
                        existingOverrides = seasonOverrides,
                        onSave = {
                            onSaveItemDownloadSettings((itemSettings ?: ItemDownloadSettings(simklId)).copy(seasonOverrides = it))
                        },
                        onDismiss = { showSeasonDialog = false }
                    )
                }

                // Episode Search Style
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Episode Search Style", color = Color(0xFFE6E1E5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    val currentSearchStyle = itemSettings?.episodeSearchStyle ?: mediaType.defaultEpisodeSearchStyle
                    SingleChoiceSegmentedButtonRow {
                        EpisodeSearchStyle.entries.forEachIndexed { index, style ->
                            SegmentedButton(
                                selected = currentSearchStyle == style,
                                onClick = {
                                    onSaveItemDownloadSettings(
                                        (itemSettings ?: ItemDownloadSettings(simklId)).copy(episodeSearchStyle = style)
                                    )
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = EpisodeSearchStyle.entries.size),
                                enabled = isDownloaderInstalled
                            ) {
                                Text(
                                    when (style) {
                                        EpisodeSearchStyle.SeasonAndEpisode -> "S01E01"
                                        EpisodeSearchStyle.Episode -> "01"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectTitleDialog(
    initialTitle: String,
    animeSeason: Int?,
    onSearchTorrents: (suspend (String) -> List<SearchResultItem>),
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var titleInput by remember { mutableStateOf(initialTitle) }
    var includeSeason by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val seasonTokenStr = if (includeSeason && animeSeason != null && animeSeason > 0) {
        formatAnimeSeasonTokens(animeSeason)
    } else {
        ""
    }

    val effectiveTitle = remember(titleInput, seasonTokenStr) {
        val trimmed = titleInput.trim()
        if (seasonTokenStr.isNotEmpty()) "$trimmed $seasonTokenStr" else trimmed
    }

    fun performSearch() {
        if (effectiveTitle.isNotBlank()) {
            coroutineScope.launch {
                isLoading = true
                results = onSearchTorrents(effectiveTitle)
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        performSearch()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 900.dp)
                .fillMaxWidth(0.96f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
            border = BorderStroke(1.dp, Color(0xFF49454F))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Detect Title Override",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                DetectTitleSearchField(
                    titleInput = titleInput,
                    onTitleChange = { titleInput = it },
                    onSearch = { performSearch() },
                    isLoading = isLoading,
                )

                DetectSeasonCheckbox(
                    includeSeason = includeSeason,
                    animeSeason = animeSeason,
                    onIncludeSeasonChange = { includeSeason = it }
                )

                DetectSearchResultsTable(
                    results = results,
                    isLoading = isLoading
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(effectiveTitle) },
                        enabled = effectiveTitle.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectTitleSearchField(
    titleInput: String,
    onTitleChange: (String) -> Unit,
    onSearch: () -> Unit,
    isLoading: Boolean,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val handleSearch = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onSearch()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = titleInput,
            onValueChange = onTitleChange,
            label = { Text("Title Search Term") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { handleSearch() }),
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(
            onClick = handleSearch,
            modifier = Modifier.padding(top = 6.dp),
            enabled = !isLoading,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun DetectSeasonCheckbox(
    includeSeason: Boolean,
    animeSeason: Int?,
    onIncludeSeasonChange: (Boolean) -> Unit
) {
    val isEnabled = animeSeason != null && animeSeason > 0
    val labelText = "Include season"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isEnabled) { onIncludeSeasonChange(!includeSeason) }
    ) {
        Checkbox(
            checked = includeSeason,
            onCheckedChange = onIncludeSeasonChange,
            enabled = isEnabled
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = labelText,
            fontSize = 13.sp,
            color = if (isEnabled) Color(0xFFE6E1E5) else Color(0xFF79747E)
        )
    }
}

@Composable
private fun DetectSearchResultsTable(
    results: List<SearchResultItem>,
    isLoading: Boolean
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
    ) {
        Text(
            text = "First Episode Search Results",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Color(0xFFCAC4D0),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No torrents found", fontSize = 12.sp, color = Color(0xFFCAC4D0))
            }
        } else {
            DetectResultsTableHeader()
            HorizontalDivider(color = Color(0xFF49454F), thickness = 0.5.dp)
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(results) { item ->
                    DetectSearchResultRow(item = item, context = context)
                }
            }
        }
    }
}

@Composable
private fun DetectResultsTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Title", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFCAC4D0), modifier = Modifier.weight(1f))
        Text("Size", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFCAC4D0), modifier = Modifier.width(65.dp))
        Text("Seeders", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFCAC4D0), modifier = Modifier.width(45.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun DetectSearchResultRow(
    item: SearchResultItem,
    context: Context
) {
    val formattedSize = remember(item.sizeInBytes) {
        Formatter.formatFileSize(context, item.sizeInBytes)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.name,
            fontSize = 11.sp,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = formattedSize,
            fontSize = 11.sp,
            color = Color(0xFFCAC4D0),
            modifier = Modifier.width(65.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${item.seeder}",
            fontSize = 11.sp,
            color = Color(0xFFD0BCFF),
            modifier = Modifier.width(45.dp),
            textAlign = TextAlign.End
        )
    }
}

