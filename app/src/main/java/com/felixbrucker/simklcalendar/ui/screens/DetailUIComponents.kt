package com.felixbrucker.simklcalendar.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.ui.viewmodel.CalendarViewModel

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
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )

                    Text("→", color = Color.White)

                    OutlinedTextField(
                        value = targetSeason,
                        onValueChange = { if (it.all { char -> char.isDigit() }) targetSeason = it },
                        label = { Text("Target") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
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
    viewModel: CalendarViewModel,
    updatingWatchKeys: Set<String>
) {
    WatchedStatusDropdown(
        isWatched = item.isWatched,
        onStatusChange = { watched ->
            if (item.type == MediaType.MOVIE) {
                if (watched) {
                    viewModel.markMovieWatched(item.simklId, item.primaryKey, item.title) { _, _ -> }
                } else {
                    viewModel.markMovieUnwatched(item.simklId, item.primaryKey, item.title) { _, _ -> }
                }
            } else {
                if (watched) {
                    viewModel.markEpisodeWatched(item.simklId, item.season, item.episodeNumber ?: 1, item.type, item.primaryKey, item.title) { _, _ -> }
                } else {
                    viewModel.markEpisodeUnwatched(item.simklId, item.season, item.episodeNumber ?: 1, item.type, item.primaryKey, item.title) { _, _ -> }
                }
            }
        },
        isLoading = updatingWatchKeys.contains(item.primaryKey)
    )
}

@Composable
fun ItemMediaStatusDropdown(
    item: CalendarItemWithWatchlist,
    viewModel: CalendarViewModel
) {
    MediaStatusDropdown(
        currentStatus = item.mediaStatus,
        onStatusChange = { viewModel.updateMediaStatus(item.primaryKey, it) }
    )
}

fun getTableItemColor(x: Int, y: Int): Color {
    if (y == 0 || x == y) return Color(0xFF7CE49F) // Green
    val ratio = x.toDouble() / y
    return if (ratio > 0.4) Color(0xFFE2E262) else Color(0xFFF2B8B5) // Yellow, Red
}
