package com.felixbrucker.simklcalendar.ui.composable

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.database.TrackedWatchlistItem
import com.felixbrucker.simklcalendar.data.util.DateUtil
import com.felixbrucker.simklcalendar.data.util.DownloadProgress
import com.felixbrucker.simklcalendar.data.model.EpisodeSearchStyle
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadPreferences
import com.felixbrucker.simklcalendar.data.util.MediaFormatter
import com.felixbrucker.simklcalendar.data.util.PosterSize
import com.felixbrucker.simklcalendar.extensions.asSearchTitleWithSeason
import com.felixbrucker.simklcalendar.extensions.defaultDestinationSubdirectory
import com.felixbrucker.simklcalendar.extensions.toAnimeSeasonTokens
import com.felixbrucker.simklcalendar.extensions.toPosterUrl
import com.felixbrucker.simklcalendar.ui.theme.BlueAccent
import com.felixbrucker.simklcalendar.ui.theme.BlueLight
import com.felixbrucker.simklcalendar.ui.theme.ContainerBlue
import com.felixbrucker.simklcalendar.ui.theme.ErrorPink
import com.felixbrucker.simklcalendar.ui.theme.ErrorPinkLight
import com.felixbrucker.simklcalendar.ui.theme.GreenSuccess
import com.felixbrucker.simklcalendar.ui.theme.OutlineGrey
import com.felixbrucker.simklcalendar.ui.theme.OutlineVariant
import com.felixbrucker.simklcalendar.ui.theme.PurpleContainerLight
import com.felixbrucker.simklcalendar.ui.theme.PurpleContainerSoft
import com.felixbrucker.simklcalendar.ui.theme.PurpleLight
import com.felixbrucker.simklcalendar.ui.theme.PurplePrimaryDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceContainer
import com.felixbrucker.simklcalendar.ui.theme.SurfaceDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceDarkBlue
import com.felixbrucker.simklcalendar.ui.theme.SurfaceGreen
import com.felixbrucker.simklcalendar.ui.theme.SurfaceGreenDeep
import com.felixbrucker.simklcalendar.ui.theme.SurfacePrimaryVariant
import com.felixbrucker.simklcalendar.ui.theme.SurfacePurple
import com.felixbrucker.simklcalendar.ui.theme.SurfaceRedDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceRedDeep
import com.felixbrucker.simklcalendar.ui.theme.SurfaceVariantDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceVariantGrey
import com.felixbrucker.simklcalendar.ui.theme.TextGrey
import com.felixbrucker.simklcalendar.ui.theme.TextGreyLight
import com.felixbrucker.simklcalendar.ui.theme.TextPrimary
import com.felixbrucker.simklcalendar.ui.theme.TextSubtle
import com.felixbrucker.simklcalendar.ui.theme.YellowAccent
import com.felixbrucker.simklcalendar.ui.theme.YellowWarning
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
                Text("Remap season numbers for torrent searches.", fontSize = 12.sp, color = TextSubtle)

                if (tempOverrides.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        tempOverrides.toSortedMap().forEach { (orig, target) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Season $orig → Season $target", color = TextPrimary, fontSize = 14.sp)
                                IconButton(onClick = { tempOverrides = tempOverrides - orig }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = ErrorPink,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = OutlineVariant)
                }

                Text("Add/Update Override", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PurpleLight)

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
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = PurpleLight)
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
        MediaStatus.IGNORED -> SurfaceVariantGrey to TextGreyLight
        MediaStatus.NOT_AIRED_YET -> SurfaceVariantGrey to TextGreyLight
        MediaStatus.WANTED -> SurfaceRedDeep to ErrorPinkLight
        MediaStatus.DOWNLOADING -> ContainerBlue to BlueLight
        MediaStatus.DOWNLOADED -> SurfaceGreen to GreenSuccess
    }

    Box {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = backgroundColor,
            contentColor = textColor,
            border = BorderStroke(1.dp, textColor.copy(alpha = 0.3f)),
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
            modifier = Modifier.background(SurfaceContainer)
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
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = PurpleLight)
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
    val backgroundColor = if (isWatched) SurfaceGreen else SurfaceRedDark
    val textColor = if (isWatched) GreenSuccess else ErrorPink

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
                horizontalArrangement = Arrangement.spacedBy(2.dp)
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
            modifier = Modifier.background(SurfaceContainer)
        ) {
            DropdownMenuItem(
                text = { Text("Watched", color = Color.White, fontSize = 14.sp) },
                onClick = {
                    onStatusChange(true)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = GreenSuccess)
                }
            )
            DropdownMenuItem(
                text = { Text("Unwatched", color = Color.White, fontSize = 14.sp) },
                onClick = {
                    onStatusChange(false)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null, modifier = Modifier.size(18.dp), tint = ErrorPink)
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

@Composable
fun EpisodeDownloadProgress(
    downloadProgress: DownloadProgress,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val progress = if (downloadProgress.totalBytes > 0) {
        downloadProgress.bytesDownloaded.toFloat() / downloadProgress.totalBytes
    } else {
        0f
    }
    val percentage = (progress * 100).toInt()
    val speedStr = Formatter.formatFileSize(context, downloadProgress.downloadSpeed.toLong()) + "/s"
    val downloaded = Formatter.formatFileSize(context, downloadProgress.bytesDownloaded)
    val total = Formatter.formatFileSize(context, downloadProgress.totalBytes)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .width(60.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = PurpleLight,
                trackColor = SurfacePurple
            )
            Text(
                text = "$percentage%",
                fontSize = 9.sp,
                color = TextSubtle,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$downloaded / $total",
                fontSize = 8.sp,
                color = TextGrey
            )
        }

        if (downloadProgress.downloadSpeed > 0) {
            val remainingBytes = downloadProgress.totalBytes - downloadProgress.bytesDownloaded
            val remainingSeconds = (remainingBytes / downloadProgress.downloadSpeed).toLong()
            val eta = DateUtil.formatDuration(remainingSeconds)
            Text(
                text = "$speedStr • ETA: $eta",
                fontSize = 9.sp,
                color = TextSubtle
            )
        }
    }
}

fun getTableItemColor(x: Int, y: Int): Color {
    if (y == 0 || x == y) return GreenSuccess // Green
    val ratio = x.toDouble() / y
    return if (ratio > 0.4) YellowWarning else ErrorPink // Yellow, Red
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
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Notifications Strategy", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(12.dp))

            if (!isMovie) {
                // Toggle every episode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Episode Alerts", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Notify me as soon as each episode of this series airs.", color = TextSubtle, fontSize = 11.sp)
                    }
                    Switch(
                        checked = notifyEveryEpisode,
                        onCheckedChange = { isChecked ->
                            onNotifyEveryEpisodeChange(isChecked)
                            if (isChecked) checkPermission()
                        }
                    )
                }

                HorizontalDivider(color = OutlineVariant, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                // Toggle Season Finished Airing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Season Finished Airing", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Notify me when the season has finished airing.", color = TextSubtle, fontSize = 11.sp)
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
                        Text("Theater Release", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Notify me when the movie releases in theaters.", color = TextSubtle, fontSize = 11.sp)
                    }
                    Switch(
                        checked = notifyEveryEpisode,
                        onCheckedChange = { isChecked ->
                            onNotifyEveryEpisodeChange(isChecked)
                            if (isChecked) checkPermission()
                        }
                    )
                }

                HorizontalDivider(color = OutlineVariant, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                // Movie Digital / DVD Release Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Digital / DVD Release", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Notify me when the movie is available on digital or DVD.", color = TextSubtle, fontSize = 11.sp)
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

@Immutable
data class DetailHeaderData(
    val simklId: Int,
    val type: MediaType,
    val title: String,
    val poster: String?,
    val rating: Double? = null,
    val titleRomaji: String? = null,
    val hasUniqueTitleRomaji: Boolean = !titleRomaji.isNullOrBlank() && !titleRomaji.equals(title, ignoreCase = true),
    val animeSeason: Int? = null,
)

@Composable
fun DetailHeader(
    data: DetailHeaderData,
    modifier: Modifier = Modifier,
    onTitleClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
    ) {
        DetailHeaderPosterScrim(poster = data.poster)
        if (data.rating != null) {
            DetailHeaderRatingBadge(
                rating = data.rating,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
        DetailHeaderTitleOverlay(
            data = data,
            onTitleClick = onTitleClick,
            modifier = Modifier.align(Alignment.BottomStart)
        )
        DetailHeaderOpenSimklButton(
            simklId = data.simklId,
            type = data.type,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun DetailHeaderPosterScrim(poster: String?) {
    AsyncImage(
        model = poster.toPosterUrl(PosterSize.WIDE),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, SurfaceDark),
                    startY = 100f
                )
            )
    )
}

@Composable
private fun DetailHeaderRatingBadge(
    rating: Double,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.75f),
        modifier = modifier.padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Rating",
                tint = YellowAccent,
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

@Composable
private fun DetailHeaderTitleOverlay(
    data: DetailHeaderData,
    onTitleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .padding(end = 100.dp)
    ) {
        DetailMediaTypeBadge(type = data.type)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = data.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier.clickable(onClick = onTitleClick)
        )
        DetailSubtitles(data = data)
    }
}

@Composable
private fun DetailSubtitles(data: DetailHeaderData) {
    if (data.hasUniqueTitleRomaji && data.titleRomaji != null) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = data.titleRomaji,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = TextSubtle
        )
    }
    val animeSeasonLabel = MediaFormatter.formatAnimeSeasonLabel(data.type, data.animeSeason)
    if (animeSeasonLabel != null) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = animeSeasonLabel,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = TextSubtle
        )
    }
}

@Composable
private fun DetailMediaTypeBadge(
    type: MediaType,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = when (type) {
            MediaType.ANIME -> PurpleContainerLight
            MediaType.MOVIE -> ErrorPink
            MediaType.TV -> BlueAccent
        },
        contentColor = when (type) {
            MediaType.ANIME -> SurfaceVariantDark
            MediaType.MOVIE -> SurfaceRedDeep
            MediaType.TV -> SurfaceDarkBlue
        }
    ) {
        Text(
            text = type.displayName.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DetailHeaderOpenSimklButton(
    simklId: Int,
    type: MediaType,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Button(
        onClick = {
            val intent = Intent(
                Intent.ACTION_VIEW,
                MediaFormatter.formatSimklUrl(simklId, type).toUri(),
            )
            context.startActivity(intent)
        },
        modifier = modifier.padding(16.dp).height(32.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PurplePrimaryDark.copy(alpha = 0.8f),
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
        Text(text = "Open on SIMKL", fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable
fun DownloadSettingsCard(
    watchlistItem: TrackedWatchlistItem,
    autoDownloadPrefs: AutoDownloadPreferences,
    itemSettings: ItemDownloadSettings?,
    isDownloaderInstalled: Boolean,
    availableSubdirectories: List<String>,
    onSaveItemDownloadSettings: (ItemDownloadSettings) -> Unit,
    onSearchTorrents: (suspend (String) -> List<SearchResultItem>),
    modifier: Modifier = Modifier,
) {
    val mediaType = watchlistItem.type
    val simklId = watchlistItem.simklId
    val searchTitle = watchlistItem.getSearchTitle(itemSettings)
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
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp).alpha(if (isDownloaderInstalled) 1f else 0.5f)) {
            Text("Automatic Downloads", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))

            // Unwatched Toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Download Unwatched", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Auto search and add unwatched episodes.", color = TextSubtle, fontSize = 11.sp)
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
                        Text("Download Season Unwatched", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Auto download all episodes when a season finishes airing.", color = TextSubtle, fontSize = 11.sp)
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
            Text("Preferred Quality", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                    Text("Prefer HEVC / x265", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
            DownloadSubdirectorySection(
                simklId = simklId,
                defaultSubdirectory = watchlistItem.defaultDestinationSubdirectory(),
                itemSettings = itemSettings,
                isDownloaderInstalled = isDownloaderInstalled,
                availableSubdirectories = availableSubdirectories,
                onSaveItemDownloadSettings = onSaveItemDownloadSettings
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Title Override
            var showTitleDialog by remember { mutableStateOf(false) }
            var showDetectDialog by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Title Override", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Use custom search title for this item.", color = TextSubtle, fontSize = 11.sp)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (itemSettings?.titleOverride.isNullOrBlank()) {
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
                        color = SurfaceDark,
                        border = BorderStroke(1.dp, OutlineVariant),
                        modifier = Modifier.clickable(enabled = isDownloaderInstalled) { showTitleDialog = true }
                    ) {
                        Text(
                            text = itemSettings?.titleOverride ?: "None",
                            color = if (itemSettings?.titleOverride != null) PurpleLight else Color.White,
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
                            placeholder = { Text(searchTitle) },
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
                    initialTitle = searchTitle,
                    mediaType = mediaType,
                    animeSeason = watchlistItem.animeSeason,
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
                        Text("Season Overrides", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Map seasons for torrent searching.", color = TextSubtle, fontSize = 11.sp)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceDark,
                        border = BorderStroke(1.dp, OutlineVariant),
                        modifier = Modifier.clickable(enabled = isDownloaderInstalled) { showSeasonDialog = true }
                    ) {
                        Text(
                            text = if (seasonOverrides.isEmpty()) "None" else "${seasonOverrides.size} active",
                            color = if (seasonOverrides.isNotEmpty()) PurpleLight else Color.White,
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
                        Text("Episode Search Style", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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

@Composable
private fun DownloadSubdirectorySection(
    simklId: Int,
    defaultSubdirectory: String,
    itemSettings: ItemDownloadSettings?,
    isDownloaderInstalled: Boolean,
    availableSubdirectories: List<String>,
    onSaveItemDownloadSettings: (ItemDownloadSettings) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val selectedSubdirectory = itemSettings?.downloadSubdirectoryOverride ?: defaultSubdirectory

    Column(modifier = Modifier.fillMaxWidth()) {
        DownloadSubdirectoryHeader(
            selectedSubdirectory = selectedSubdirectory,
            hasOverride = itemSettings?.downloadSubdirectoryOverride != null,
            isExpanded = isExpanded,
            isDownloaderInstalled = isDownloaderInstalled,
            onToggleExpand = { isExpanded = !isExpanded }
        )

        if (isExpanded) {
            val subdirs = (listOf(defaultSubdirectory) + availableSubdirectories).distinct().sorted()
            DownloadSubdirectoryChipList(
                defaultSubdirectory = defaultSubdirectory,
                selectedSubdirectory = selectedSubdirectory,
                subdirs = subdirs,
                isDownloaderInstalled = isDownloaderInstalled,
                onSelectSubdirectory = { next ->
                    val updated = (itemSettings ?: ItemDownloadSettings(simklId)).copy(
                        downloadSubdirectoryOverride = next
                    )
                    onSaveItemDownloadSettings(updated)
                    isExpanded = false
                }
            )
        }
    }
}

@Composable
private fun DownloadSubdirectoryHeader(
    selectedSubdirectory: String,
    hasOverride: Boolean,
    isExpanded: Boolean,
    isDownloaderInstalled: Boolean,
    onToggleExpand: () -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isDownloaderInstalled, onClick = onToggleExpand),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Download Subdirectory",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, OutlineVariant),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = selectedSubdirectory,
                    color = if (hasOverride) PurpleLight else Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextSubtle,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DownloadSubdirectoryChipList(
    defaultSubdirectory: String,
    selectedSubdirectory: String,
    subdirs: List<String>,
    isDownloaderInstalled: Boolean,
    onSelectSubdirectory: (String?) -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        subdirs.forEach { subdir ->
            val isSelected = subdir == selectedSubdirectory
            FilterChip(
                selected = isSelected,
                onClick = {
                    val next = if (subdir == defaultSubdirectory) null else subdir
                    onSelectSubdirectory(next)
                },
                label = { Text(subdir, fontSize = 11.sp) },
                enabled = isDownloaderInstalled
            )
        }
    }
}

@Composable
fun SearchTermInputField(
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
fun SeasonCheckbox(
    includeSeason: Boolean,
    season: Int?,
    onIncludeSeasonChange: (Boolean) -> Unit
) {
    val isEnabled = season != null && season > 0
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
            color = if (isEnabled) TextPrimary else OutlineGrey
        )
    }
}

@Composable
fun TorrentSearchResultsTable(
    results: List<SearchResultItem>,
    isLoading: Boolean,
    tableHeaderTitle: String = "Search Results",
    onSelectResult: ((SearchResultItem) -> Unit)? = null
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
    ) {
        Text(
            text = tableHeaderTitle,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = TextSubtle,
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
                Text("No torrents found", fontSize = 12.sp, color = TextSubtle)
            }
        } else {
            TorrentResultsTableHeader()
            HorizontalDivider(color = OutlineVariant, thickness = 0.5.dp)
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(results) { item ->
                    TorrentSearchResultRow(
                        item = item,
                        context = context,
                        onClick = onSelectResult?.let { callback -> { callback(item) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun TorrentResultsTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Title", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextSubtle, modifier = Modifier.weight(1f))
        Text("Size", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextSubtle, modifier = Modifier.width(65.dp))
        Text("Seeders", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextSubtle, modifier = Modifier.width(45.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun TorrentSearchResultRow(
    item: SearchResultItem,
    context: Context,
    onClick: (() -> Unit)? = null
) {
    val formattedSize = remember(item.sizeInBytes) {
        Formatter.formatFileSize(context, item.sizeInBytes)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
            color = TextSubtle,
            modifier = Modifier.width(65.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${item.seeder}",
            fontSize = 11.sp,
            color = PurpleLight,
            modifier = Modifier.width(45.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun DetectTitleDialog(
    initialTitle: String,
    mediaType: MediaType,
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

    val seasonTokenStr = if (mediaType == MediaType.ANIME && includeSeason && animeSeason != null && animeSeason > 0) {
        animeSeason.toAnimeSeasonTokens()
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

    LaunchedEffect(includeSeason) {
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
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
            border = BorderStroke(1.dp, OutlineVariant)
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

                SearchTermInputField(
                    titleInput = titleInput,
                    onTitleChange = { titleInput = it },
                    onSearch = { performSearch() },
                    isLoading = isLoading,
                )

                if (mediaType == MediaType.ANIME) {
                    SeasonCheckbox(
                        includeSeason = includeSeason,
                        season = animeSeason,
                        onIncludeSeasonChange = { includeSeason = it }
                    )
                }

                TorrentSearchResultsTable(
                    results = results,
                    isLoading = isLoading,
                    tableHeaderTitle = "First Episode Search Results"
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
fun SeasonWatchButton(
    isMarkingWatched: Boolean,
    isSeasonFullyWatched: Boolean,
    seasonLabel: String,
    onMarkSeasonWatched: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onMarkSeasonWatched,
        enabled = !isMarkingWatched && !isSeasonFullyWatched,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSeasonFullyWatched) SurfaceGreenDeep else SurfacePrimaryVariant,
            contentColor = if (isSeasonFullyWatched) GreenSuccess else PurpleContainerSoft,
            disabledContainerColor = if (isSeasonFullyWatched) SurfaceGreen else SurfaceVariantGrey,
            disabledContentColor = if (isSeasonFullyWatched) GreenSuccess else OutlineGrey
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        if (isMarkingWatched) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = PurpleContainerSoft
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

@Composable
fun ManualSearchDialog(
    initialTitle: String,
    mediaType: MediaType,
    animeSeasonOrSeason: Int?,
    initialIncludeSeason: Boolean,
    itemSettings: ItemDownloadSettings?,
    dialogTitle: String = "Search Torrents Manually",
    onPerformSearch: (suspend (title: String) -> List<SearchResultItem>),
    onSelectResult: (SearchResultItem) -> Unit,
    onDismiss: () -> Unit
) {
    var titleInput by remember { mutableStateOf(initialTitle) }
    var includeSeason by remember { mutableStateOf(initialIncludeSeason) }
    var results by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun performSearch() {
        if (titleInput.isNotBlank()) {
            val searchTitle = if (includeSeason && animeSeasonOrSeason != null && animeSeasonOrSeason > 0) {
                titleInput.trim().asSearchTitleWithSeason(
                    season = animeSeasonOrSeason,
                    type = mediaType,
                    settings = itemSettings
                )
            } else {
                titleInput.trim()
            }
            coroutineScope.launch {
                isLoading = true
                results = onPerformSearch(searchTitle)
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        performSearch()
    }

    LaunchedEffect(includeSeason) {
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
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
            border = BorderStroke(1.dp, OutlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = dialogTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                SearchTermInputField(
                    titleInput = titleInput,
                    onTitleChange = { titleInput = it },
                    onSearch = { performSearch() },
                    isLoading = isLoading,
                )

                if (mediaType != MediaType.MOVIE) {
                    SeasonCheckbox(
                        includeSeason = includeSeason,
                        season = animeSeasonOrSeason,
                        onIncludeSeasonChange = { includeSeason = it }
                    )
                }

                TorrentSearchResultsTable(
                    results = results,
                    isLoading = isLoading,
                    tableHeaderTitle = "Select a result to download:",
                    onSelectResult = { selectedItem ->
                        onSelectResult(selectedItem)
                        onDismiss()
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
