package com.felixbrucker.simklcalendar.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felixbrucker.simklcalendar.data.logging.LogEntry
import com.felixbrucker.simklcalendar.data.logging.LogRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val logTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogViewerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val allLogs by LogRepository.logsFlow.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableIntStateOf(-1) } // -1 means All
    var showClearDialog by remember { mutableStateOf(false) }

    var isTopSectionVisible by remember { mutableStateOf(true) }
    var previousIndex by remember { mutableIntStateOf(0) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (currentIndex, currentOffset) ->
                if (currentIndex == 0 && currentOffset == 0) {
                    isTopSectionVisible = true
                } else if (currentIndex > previousIndex || (currentIndex == previousIndex && currentOffset > previousScrollOffset + 15)) {
                    isTopSectionVisible = false
                } else if (currentIndex < previousIndex || (currentIndex == previousIndex && currentOffset < previousScrollOffset - 15)) {
                    isTopSectionVisible = true
                }
                previousIndex = currentIndex
                previousScrollOffset = currentOffset
            }
    }

    val filteredLogs = remember(allLogs, searchQuery, selectedPriority) {
        allLogs.filter { entry ->
            val matchesPriority = selectedPriority == -1 || entry.priority == selectedPriority
            val matchesSearch = searchQuery.isBlank() ||
                    (entry.tag?.contains(searchQuery, ignoreCase = true) == true) ||
                    entry.message.contains(searchQuery, ignoreCase = true) ||
                    (entry.throwableStackTrace?.contains(searchQuery, ignoreCase = true) == true)
            matchesPriority && matchesSearch
        }
    }

    fun copyLogsToClipboard() {
        if (filteredLogs.isEmpty()) {
            Toast.makeText(context, "No logs to copy", Toast.LENGTH_SHORT).show()
            return
        }
        val text = filteredLogs.joinToString("\n") { entry ->
            val timeStr = try {
                logTimeFormatter.format(Instant.ofEpochMilli(entry.timestamp))
            } catch (_: Exception) {
                entry.timestamp.toString()
            }
            val tagStr = entry.tag ?: "SimklCalendar"
            val levelStr = entry.priorityLabel()
            val base = "[$timeStr] [$levelStr] [$tagStr]: ${entry.message}"
            if (!entry.throwableStackTrace.isNullOrBlank()) {
                "$base\n${entry.throwableStackTrace}"
            } else {
                base
            }
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SimklCalendar Logs", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied ${filteredLogs.size} log entries to clipboard", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("App Log Viewer", color = Color(0xFFE6E1E5), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFE6E1E5))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { copyLogsToClipboard() },
                        modifier = Modifier.testTag("copy_logs_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Logs", tint = Color(0xFFD0BCFF))
                    }
                    IconButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.testTag("clear_logs_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Logs", tint = Color(0xFFF2B8B5))
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
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Collapsible Top Controls Section (Search, Filter Chips, Count Header)
            AnimatedVisibility(
                visible = isTopSectionVisible,
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
                exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(220))
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    // Search Input Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter logs by tag or text...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFCAC4D0)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color(0xFFCAC4D0))
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("log_search_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFD0BCFF),
                            unfocusedBorderColor = Color(0xFF49454F),
                            focusedContainerColor = Color(0xFF2B2930),
                            unfocusedContainerColor = Color(0xFF2B2930),
                            focusedTextColor = Color(0xFFE6E1E5),
                            unfocusedTextColor = Color(0xFFE6E1E5)
                        )
                    )

                    // Priority Filter Chips
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val priorities = listOf(
                            -1 to "All",
                            Log.VERBOSE to "Verbose",
                            Log.DEBUG to "Debug",
                            Log.INFO to "Info",
                            Log.WARN to "Warn",
                            Log.ERROR to "Error"
                        )

                        priorities.forEach { (priorityVal, label) ->
                            val isSelected = selectedPriority == priorityVal
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedPriority = priorityVal },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFD0BCFF),
                                    selectedLabelColor = Color(0xFF381E72),
                                    containerColor = Color(0xFF2B2930),
                                    labelColor = Color(0xFFCAC4D0)
                                )
                            )
                        }
                    }

                    // Log Count Status Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Showing ${filteredLogs.size} of ${allLogs.size} log entries",
                            color = Color(0xFFCAC4D0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        if (filteredLogs.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        listState.animateScrollToItem(filteredLogs.size - 1)
                                    }
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Jump to Bottom", fontSize = 11.sp, color = Color(0xFFD0BCFF))
                            }
                        }
                    }
                }
            }

            // Log Entries List
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = null,
                            tint = Color(0xFF79747E),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (allLogs.isEmpty()) "No log entries recorded yet" else "No matching log entries found",
                            color = Color(0xFFCAC4D0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("log_entries_list")
                ) {
                    items(filteredLogs) { entry ->
                        LogEntryCard(entry = entry)
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Logs", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete all stored diagnostic logs? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        LogRepository.clearLogs()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LogEntryCard(entry: LogEntry) {
    val (badgeBg, badgeFg) = when (entry.priority) {
        Log.ERROR -> Color(0xFF601410) to Color(0xFFF2B8B5)
        Log.WARN -> Color(0xFF4A3800) to Color(0xFFFFDDB3)
        Log.INFO -> Color(0xFF00382B) to Color(0xFF7CE49F)
        Log.DEBUG -> Color(0xFF1D192B) to Color(0xFFD0BCFF)
        else -> Color(0xFF313033) to Color(0xFFCAC4D0)
    }

    val formattedTime = remember(entry.timestamp) {
        try {
            logTimeFormatter.format(Instant.ofEpochMilli(entry.timestamp))
        } catch (_: Exception) {
            entry.timestamp.toString()
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF2B2930),
        border = BorderStroke(1.dp, Color(0xFF3B383E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = entry.priorityLabel(),
                            color = badgeFg,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = entry.tag ?: "SimklCalendar",
                        color = Color(0xFFE6E1E5),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = formattedTime,
                    color = Color(0xFF938F99),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = entry.message,
                color = Color(0xFFE6E1E5),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp
            )

            if (!entry.throwableStackTrace.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1C1B1F),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                ) {
                    Text(
                        text = entry.throwableStackTrace,
                        color = Color(0xFFF2B8B5),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}
