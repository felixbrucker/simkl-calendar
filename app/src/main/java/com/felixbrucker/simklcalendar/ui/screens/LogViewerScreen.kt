package com.felixbrucker.simklcalendar.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.felixbrucker.simklcalendar.data.logging.LogEntry
import com.felixbrucker.simklcalendar.ui.composable.AlignedPopover
import com.felixbrucker.simklcalendar.ui.composable.SearchBarDisplayMode
import com.felixbrucker.simklcalendar.ui.composable.TopAppBarTitleSectionWithExpandableSearch
import com.felixbrucker.simklcalendar.ui.theme.ErrorPink
import com.felixbrucker.simklcalendar.ui.theme.GreenSuccess
import com.felixbrucker.simklcalendar.ui.theme.OutlineGrey
import com.felixbrucker.simklcalendar.ui.theme.PurpleLight
import com.felixbrucker.simklcalendar.ui.theme.SurfaceContainer
import com.felixbrucker.simklcalendar.ui.theme.SurfaceDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceDarkGreen
import com.felixbrucker.simklcalendar.ui.theme.SurfaceGreyDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceRedDeep
import com.felixbrucker.simklcalendar.ui.theme.SurfaceVariantDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceVariantGrey
import com.felixbrucker.simklcalendar.ui.theme.SurfaceYellowDark
import com.felixbrucker.simklcalendar.ui.theme.TextGrey
import com.felixbrucker.simklcalendar.ui.theme.TextPrimary
import com.felixbrucker.simklcalendar.ui.theme.TextSubtle
import com.felixbrucker.simklcalendar.ui.theme.YellowWarningLight
import com.felixbrucker.simklcalendar.ui.viewmodel.LogViewerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private val logTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    .withZone(ZoneId.systemDefault())

private fun logPriorityColors(priority: Int): Pair<Color, Color> {
    return when (priority) {
        Log.ERROR -> SurfaceRedDeep to ErrorPink
        Log.WARN -> SurfaceYellowDark to YellowWarningLight
        Log.INFO -> SurfaceDarkGreen to GreenSuccess
        Log.DEBUG -> SurfaceVariantDark to PurpleLight
        else -> SurfaceGreyDark to TextSubtle
    }
}

@Composable
fun LogViewerScreen(
    modifier: Modifier = Modifier,
    viewModel: LogViewerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val allLogs by viewModel.allLogs.collectAsState()
    val filteredLogs by viewModel.filteredLogs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedPriorities by viewModel.selectedPriorities.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }
    var showFilterPopover by remember { mutableStateOf(false) }
    var filterButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var topControlsHeightPx by remember { mutableFloatStateOf(0f) }
    var topControlsOffsetPx by remember { mutableFloatStateOf(0f) }
    var shouldAutoScroll by remember { mutableStateOf(true) }

    val effectiveSearchDisplayMode = if (isSearchActive || isSearchFocused || searchQuery.isNotBlank()) {
        SearchBarDisplayMode.EXPANDED
    } else {
        SearchBarDisplayMode.HIDDEN
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (topControlsHeightPx <= 0f) return Offset.Zero

                val oldOffset = topControlsOffsetPx
                val newOffset = (oldOffset + delta).coerceIn(-topControlsHeightPx, 0f)
                val consumedY = newOffset - oldOffset
                topControlsOffsetPx = newOffset

                return Offset(0f, consumedY)
            }
        }
    }

    BackHandler(enabled = isSearchActive || searchQuery.isNotBlank() || showFilterPopover) {
        if (showFilterPopover) {
            showFilterPopover = false
        } else {
            if (searchQuery.isNotBlank()) {
                viewModel.setSearchQuery("")
            }
            isSearchActive = false
            isSearchFocused = false
        }
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    LogViewerEffects(
        listState = listState,
        filteredLogs = filteredLogs,
        shouldAutoScroll = shouldAutoScroll,
        onUpdateOffset = { topControlsOffsetPx = it },
        onUpdateAutoScroll = { shouldAutoScroll = it }
    )

    LogViewerScaffold(
        modifier = modifier,
        allLogs = allLogs,
        filteredLogs = filteredLogs,
        searchQuery = searchQuery,
        listState = listState,
        effectiveSearchDisplayMode = effectiveSearchDisplayMode,
        focusRequester = focusRequester,
        topControlsHeightPx = topControlsHeightPx,
        topControlsOffsetPx = topControlsOffsetPx,
        nestedScrollConnection = nestedScrollConnection,
        onNavigateBack = onNavigateBack,
        onCopyLogs = { copyLogsToClipboard(context = context, filteredLogs = filteredLogs) },
        onClearLogs = { showClearDialog = true },
        onOpenFilter = { showFilterPopover = true },
        onButtonBoundsChanged = { filterButtonBounds = it },
        onOpenSearch = {
            isSearchActive = true
            scope.launch {
                delay(100.milliseconds)
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        },
        onSearchQueryChange = { viewModel.setSearchQuery(it) },
        onFocusChanged = { isSearchFocused = it },
        onSearch = {
            focusManager.clearFocus()
            keyboardController?.hide()
            isSearchFocused = false
        },
        onClearSearch = {
            if (searchQuery.isNotEmpty()) {
                viewModel.setSearchQuery("")
            } else {
                isSearchActive = false
                isSearchFocused = false
                focusManager.clearFocus()
                keyboardController?.hide()
            }
        },
        onJumpToBottom = {
            if (filteredLogs.isNotEmpty()) {
                shouldAutoScroll = true
                scope.launch { listState.animateScrollToItem(filteredLogs.size - 1) }
            }
        },
        onUpdateHeight = { topControlsHeightPx = it },
        onUpdateOffset = { topControlsOffsetPx = it }
    )

    if (showClearDialog) {
        ClearLogsDialog(
            onConfirm = { viewModel.clearLogs(); showClearDialog = false },
            onDismiss = { showClearDialog = false }
        )
    }

    AlignedPopover(
        isVisible = showFilterPopover,
        anchorBounds = filterButtonBounds,
        onDismissRequest = { showFilterPopover = false }
    ) {
        LogViewerPriorityChips(
            selectedPriorities = selectedPriorities,
            onTogglePriority = { viewModel.togglePriority(it) }
        )
    }
}

@Composable
fun LogViewerEffects(
    listState: LazyListState,
    filteredLogs: List<LogEntry>,
    shouldAutoScroll: Boolean,
    onUpdateOffset: (Float) -> Unit,
    onUpdateAutoScroll: (Boolean) -> Unit
) {
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (currentIndex, currentOffset) ->
                if (currentIndex == 0 && currentOffset == 0) {
                    onUpdateOffset(0f)
                }
            }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (isScrolling, canScrollForward) ->
                if (isScrolling) {
                    onUpdateAutoScroll(!canScrollForward)
                }
            }
    }
    LaunchedEffect(filteredLogs) {
        if (shouldAutoScroll && filteredLogs.isNotEmpty()) {
            listState.scrollToItem(filteredLogs.size - 1)
        }
    }
}

@Composable
fun LogViewerScaffold(
    modifier: Modifier,
    allLogs: List<LogEntry>,
    filteredLogs: List<LogEntry>,
    searchQuery: String,
    listState: LazyListState,
    effectiveSearchDisplayMode: SearchBarDisplayMode,
    focusRequester: FocusRequester,
    topControlsHeightPx: Float,
    topControlsOffsetPx: Float,
    nestedScrollConnection: NestedScrollConnection,
    onNavigateBack: () -> Unit,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onOpenFilter: () -> Unit,
    onButtonBoundsChanged: (Rect) -> Unit,
    onOpenSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onJumpToBottom: () -> Unit,
    onUpdateHeight: (Float) -> Unit,
    onUpdateOffset: (Float) -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            LogViewerTopBar(
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                effectiveSearchDisplayMode = effectiveSearchDisplayMode,
                focusRequester = focusRequester,
                onFocusChanged = onFocusChanged,
                onSearch = onSearch,
                onClearSearch = onClearSearch,
                onOpenSearch = onOpenSearch,
                onCopyLogs = onCopyLogs,
                onClearLogs = onClearLogs,
                onOpenFilter = onOpenFilter,
                onButtonBoundsChanged = onButtonBoundsChanged,
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = SurfaceDark
    ) { innerPadding ->
        LogViewerContentContainer(
            innerPadding = innerPadding,
            allLogs = allLogs,
            filteredLogs = filteredLogs,
            listState = listState,
            nestedScrollConnection = nestedScrollConnection,
            filteredLogsCount = filteredLogs.size,
            totalLogsCount = allLogs.size,
            topControlsHeightPx = topControlsHeightPx,
            topControlsOffsetPx = topControlsOffsetPx,
            onJumpToBottom = onJumpToBottom,
            onUpdateHeight = onUpdateHeight,
            onUpdateOffset = onUpdateOffset
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    effectiveSearchDisplayMode: SearchBarDisplayMode,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onOpenSearch: () -> Unit,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onOpenFilter: () -> Unit,
    onButtonBoundsChanged: (Rect) -> Unit,
    onNavigateBack: () -> Unit
) {
    TopAppBar(
        title = {
            LogViewerSearchTitle(
                effectiveSearchDisplayMode = effectiveSearchDisplayMode,
                searchQuery = searchQuery,
                focusRequester = focusRequester,
                onSearchQueryChange = onSearchQueryChange,
                onFocusChanged = onFocusChanged,
                onSearch = onSearch,
                onClearSearch = onClearSearch
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
        },
        actions = {
            LogViewerTopBarActions(
                effectiveSearchDisplayMode = effectiveSearchDisplayMode,
                onOpenSearch = onOpenSearch,
                onOpenFilter = onOpenFilter,
                onButtonBoundsChanged = onButtonBoundsChanged,
                onCopyLogs = onCopyLogs,
                onClearLogs = onClearLogs
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
    )
}

@Composable
fun LogViewerSearchTitle(
    effectiveSearchDisplayMode: SearchBarDisplayMode,
    searchQuery: String,
    focusRequester: FocusRequester,
    onSearchQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit
) {
    TopAppBarTitleSectionWithExpandableSearch(
        searchDisplayMode = effectiveSearchDisplayMode,
        searchQuery = searchQuery,
        searchQueryPlaceholder = "Filter logs by tag or text...",
        focusRequester = focusRequester,
        onSearchQueryChange = onSearchQueryChange,
        onFocusChanged = onFocusChanged,
        onSearch = onSearch,
        onClearSearch = onClearSearch,
        regularTopAppBarTitleSection = { LogViewerTitleText() }
    )
}

@Composable
fun LogViewerTitleText() {
    Text(
        text = "App Log Viewer",
        color = TextPrimary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun LogViewerTopBarActions(
    effectiveSearchDisplayMode: SearchBarDisplayMode,
    onOpenSearch: () -> Unit,
    onOpenFilter: () -> Unit,
    onButtonBoundsChanged: (Rect) -> Unit,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (effectiveSearchDisplayMode == SearchBarDisplayMode.HIDDEN) {
            LogViewerSearchIconButton(onOpenSearch = onOpenSearch)
        }
        LogViewerFilterIconButton(
            onOpenFilter = onOpenFilter,
            onButtonBoundsChanged = onButtonBoundsChanged
        )
        LogViewerCopyIconButton(onCopyLogs = onCopyLogs)
        if (effectiveSearchDisplayMode == SearchBarDisplayMode.HIDDEN) {
            LogViewerClearIconButton(onClearLogs = onClearLogs)
        }
    }
}

@Composable
fun LogViewerSearchIconButton(onOpenSearch: () -> Unit) {
    IconButton(onClick = onOpenSearch, modifier = Modifier.testTag("open_search_button")) {
        Icon(Icons.Default.Search, contentDescription = "Open Search")
    }
}

@Composable
fun LogViewerFilterIconButton(
    onOpenFilter: () -> Unit,
    onButtonBoundsChanged: (Rect) -> Unit
) {
    IconButton(
        onClick = onOpenFilter,
        modifier = Modifier
            .testTag("open_filter_button")
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                if (!bounds.isEmpty && bounds.width > 0f) {
                    onButtonBoundsChanged(bounds)
                }
            }
    ) {
        Icon(Icons.Default.Tune, contentDescription = "Filter Logs")
    }
}

@Composable
fun LogViewerCopyIconButton(onCopyLogs: () -> Unit) {
    IconButton(onClick = onCopyLogs, modifier = Modifier.testTag("copy_logs_button")) {
        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Logs", tint = PurpleLight)
    }
}

@Composable
fun LogViewerClearIconButton(onClearLogs: () -> Unit) {
    IconButton(onClick = onClearLogs, modifier = Modifier.testTag("clear_logs_button")) {
        Icon(Icons.Default.Delete, contentDescription = "Clear Logs", tint = ErrorPink)
    }
}

@Composable
fun LogViewerPriorityChips(
    selectedPriorities: Set<Int>,
    onTogglePriority: (Int) -> Unit
) {
    val priorities = remember {
        listOf(
            Log.VERBOSE to "Verbose",
            Log.DEBUG to "Debug",
            Log.INFO to "Info",
            Log.WARN to "Warn",
            Log.ERROR to "Error"
        )
    }
    FlowRow(
        modifier = Modifier.wrapContentWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        priorities.forEach { (priorityVal, label) ->
            LogViewerPriorityFilterChip(
                priorityVal = priorityVal,
                label = label,
                isSelected = priorityVal in selectedPriorities,
                onTogglePriority = onTogglePriority
            )
        }
    }
}

@Composable
fun LogViewerPriorityFilterChip(
    priorityVal: Int,
    label: String,
    isSelected: Boolean,
    onTogglePriority: (Int) -> Unit
) {
    val (chipBg, chipFg) = remember(priorityVal) { logPriorityColors(priorityVal) }

    FilterChip(
        selected = isSelected,
        onClick = { onTogglePriority(priorityVal) },
        label = { Text(label, fontSize = 13.sp) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = chipBg,
            labelColor = chipFg,
            selectedContainerColor = chipFg,
            selectedLabelColor = SurfaceDark
        )
    )
}

@Composable
fun LogViewerContentContainer(
    innerPadding: PaddingValues,
    allLogs: List<LogEntry>,
    filteredLogs: List<LogEntry>,
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    filteredLogsCount: Int,
    totalLogsCount: Int,
    topControlsHeightPx: Float,
    topControlsOffsetPx: Float,
    onJumpToBottom: () -> Unit,
    onUpdateHeight: (Float) -> Unit,
    onUpdateOffset: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .nestedScroll(nestedScrollConnection)
    ) {
        LogViewerCountHeaderContainer(
            filteredLogsCount = filteredLogsCount,
            totalLogsCount = totalLogsCount,
            onJumpToBottom = onJumpToBottom,
            topControlsHeightPx = topControlsHeightPx,
            topControlsOffsetPx = topControlsOffsetPx,
            onUpdateHeight = onUpdateHeight,
            onUpdateOffset = onUpdateOffset
        )

        if (filteredLogs.isEmpty()) {
            LogEmptyView(isLogsEmpty = allLogs.isEmpty())
        } else {
            LogViewerList(
                filteredLogs = filteredLogs,
                listState = listState,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun LogViewerCountHeaderContainer(
    filteredLogsCount: Int,
    totalLogsCount: Int,
    onJumpToBottom: () -> Unit,
    topControlsHeightPx: Float,
    topControlsOffsetPx: Float,
    onUpdateHeight: (Float) -> Unit,
    onUpdateOffset: (Float) -> Unit
) {
    LogViewerCountHeader(
        filteredLogsCount = filteredLogsCount,
        totalLogsCount = totalLogsCount,
        onJumpToBottom = onJumpToBottom,
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val measuredHeight = placeable.height
                if (measuredHeight > 0 && topControlsHeightPx != measuredHeight.toFloat()) {
                    onUpdateHeight(measuredHeight.toFloat())
                    onUpdateOffset(topControlsOffsetPx.coerceIn(-measuredHeight.toFloat(), 0f))
                }
                val layoutHeight = (measuredHeight + topControlsOffsetPx.roundToInt()).coerceAtLeast(0)
                layout(placeable.width, layoutHeight) {
                    placeable.placeRelative(0, topControlsOffsetPx.roundToInt())
                }
            }
            .clipToBounds()
            .fillMaxWidth()
    )
}

@Composable
fun LogViewerCountHeader(
    filteredLogsCount: Int,
    totalLogsCount: Int,
    onJumpToBottom: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Showing $filteredLogsCount of $totalLogsCount log entries",
            color = TextSubtle,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        if (filteredLogsCount > 0) {
            TextButton(
                onClick = onJumpToBottom,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Jump to Bottom", fontSize = 11.sp, color = PurpleLight)
            }
        }
    }
}

@Composable
fun LogEmptyView(isLogsEmpty: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Terminal, contentDescription = null, tint = OutlineGrey, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isLogsEmpty) "No log entries recorded yet" else "No matching log entries found",
                color = TextSubtle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun LogViewerList(
    filteredLogs: List<LogEntry>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.testTag("log_entries_list")
    ) {
        items(
            items = filteredLogs,
            key = { entry -> entry.id }
        ) { entry ->
            LogEntryCard(entry = entry)
        }
    }
}

@Composable
fun LogBadge(
    priorityLabel: String,
    badgeBg: Color,
    badgeFg: Color
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = badgeBg
    ) {
        Text(
            text = priorityLabel,
            color = badgeFg,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun LogEntryHeader(
    entry: LogEntry,
    formattedTime: String,
    badgeBg: Color,
    badgeFg: Color,
    isExpandable: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LogBadge(priorityLabel = entry.priorityLabel(), badgeBg = badgeBg, badgeFg = badgeFg)
            Text(
                text = entry.tag ?: "SimklCalendar",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formattedTime,
                color = TextGrey,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            if (isExpandable) {
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(24.dp).padding(start = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = TextSubtle
                    )
                }
            }
        }
    }
}

@Composable
fun LogEntryMessage(
    message: String,
    isLong: Boolean,
    isExpanded: Boolean,
    modifier: Modifier = Modifier
) {
    val displayText = remember(message, isExpanded, isLong) {
        if (!isExpanded && isLong) message.take(300) + "…" else message
    }

    SelectionContainer(modifier = modifier) {
        Text(
            text = displayText,
            color = TextPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp
        )
    }
}

@Composable
fun LogEntryStackTrace(
    stackTrace: String,
    isLong: Boolean,
    isExpanded: Boolean,
    modifier: Modifier = Modifier
) {
    val displayText = remember(stackTrace, isExpanded, isLong) {
        if (!isExpanded && isLong) stackTrace.take(300) + "…" else stackTrace
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = SurfaceDark,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
    ) {
        SelectionContainer(modifier = Modifier.padding(8.dp)) {
            Text(
                text = displayText,
                color = ErrorPink,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun LogEntryCard(entry: LogEntry) {
    var isExpanded by remember { mutableStateOf(false) }

    val isMessageLong = remember(entry.message) {
        entry.message.length > 300 || entry.message.count { it == '\n' } > 5
    }

    val isStackTraceLong = remember(entry.throwableStackTrace) {
        entry.throwableStackTrace?.run { length > 300 || count { it == '\n' } > 5 } == true
    }

    val isExpandable = isMessageLong || isStackTraceLong

    val (badgeBg, badgeFg) = remember(entry.priority) { logPriorityColors(entry.priority) }

    val formattedTime = remember(entry.timestamp) {
        try {
            logTimeFormatter.format(Instant.ofEpochMilli(entry.timestamp))
        } catch (_: Exception) {
            entry.timestamp.toString()
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SurfaceContainer,
        border = BorderStroke(1.dp, SurfaceVariantGrey),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isExpandable) {
                    Modifier.clickable { isExpanded = !isExpanded }
                } else {
                    Modifier
                }
            )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            LogEntryHeader(
                entry = entry,
                formattedTime = formattedTime,
                badgeBg = badgeBg,
                badgeFg = badgeFg,
                isExpandable = isExpandable,
                isExpanded = isExpanded,
                onToggleExpand = { isExpanded = !isExpanded }
            )
            Spacer(modifier = Modifier.height(6.dp))
            LogEntryMessage(
                message = entry.message,
                isLong = isMessageLong,
                isExpanded = isExpanded
            )
            if (!entry.throwableStackTrace.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                LogEntryStackTrace(
                    stackTrace = entry.throwableStackTrace,
                    isLong = isMessageLong,
                    isExpanded = isExpanded
                )
            }
        }
    }
}

@Composable
fun ClearLogsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear All Logs", fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to delete all stored diagnostic logs? This action cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Clear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun copyLogsToClipboard(context: Context, filteredLogs: List<LogEntry>) {
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
    @Suppress("UsePropertyAccessSyntax")
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied ${filteredLogs.size} log entries to clipboard", Toast.LENGTH_SHORT).show()
}
