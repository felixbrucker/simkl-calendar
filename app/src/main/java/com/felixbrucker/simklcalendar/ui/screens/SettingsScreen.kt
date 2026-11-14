package com.felixbrucker.simklcalendar.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import android.os.Build
import com.felixbrucker.simklcalendar.data.model.AppBackupData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.felixbrucker.simklcalendar.ui.composable.CustomOutlinedTextField
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.preferences.AppSettingsPreferences
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadPreferences
import com.felixbrucker.simklcalendar.data.preferences.NotificationPreferences
import com.felixbrucker.simklcalendar.data.util.PermissionUtil
import com.felixbrucker.simklcalendar.ui.theme.ErrorPink
import com.felixbrucker.simklcalendar.ui.theme.GreenSuccess
import com.felixbrucker.simklcalendar.ui.theme.OutlineGrey
import com.felixbrucker.simklcalendar.ui.theme.OutlineVariant
import com.felixbrucker.simklcalendar.ui.theme.PurpleContainerSoft
import com.felixbrucker.simklcalendar.ui.theme.PurpleLight
import com.felixbrucker.simklcalendar.ui.theme.SurfaceContainer
import com.felixbrucker.simklcalendar.ui.theme.SurfaceDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceDarkGreen
import com.felixbrucker.simklcalendar.ui.theme.SurfaceGreen
import com.felixbrucker.simklcalendar.ui.theme.SurfacePrimaryVariant
import com.felixbrucker.simklcalendar.ui.theme.SurfacePurple
import com.felixbrucker.simklcalendar.ui.theme.SurfaceRedDeep
import com.felixbrucker.simklcalendar.ui.theme.SurfaceSettingsBlueDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceSettingsBrownDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceSettingsCardDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceSettingsDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceSettingsPurpleDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceVariantGrey
import com.felixbrucker.simklcalendar.ui.theme.TextGrey
import com.felixbrucker.simklcalendar.ui.theme.TextPrimary
import com.felixbrucker.simklcalendar.ui.theme.TextSubtle
import com.felixbrucker.simklcalendar.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SettingsCategory(
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    GENERAL("General", "Account, sync schedule, and custom search links", Icons.Default.Settings),
    NOTIFICATIONS("Notifications", "Airing and season finale alerts", Icons.Default.Notifications),
    DOWNLOADS("Downloads", "Automatic torrent search management", Icons.Default.Download),
    BACKUP("Backup", "Export and import application settings", Icons.Default.SettingsBackupRestore),
    DEBUG("Debug", "Diagnostics, Logs and Sentry crash reporting", Icons.Default.BugReport)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    initialCategory: SettingsCategory? = null,
    onNavigateBack: () -> Unit,
    onNavigateToCategory: (SettingsCategory) -> Unit = {},
    onNavigateToLogViewer: () -> Unit = {}
) {
    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current
    val isTablet = with(LocalDensity.current) { windowInfo.containerSize.width.toDp() } >= 600.dp

    val userToken by viewModel.userToken.collectAsState()
    val notificationPrefs by viewModel.notificationPreferences.collectAsState()
    val appSettings by viewModel.appSettingsPreferences.collectAsState()
    val autoDownloadPrefs by viewModel.autoDownloadPreferences.collectAsState()

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

    var hasAllFilesAccessPermission by remember {
        mutableStateOf(PermissionUtil.hasAllFilesAccessPermission())
    }

    val allFilesPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasAllFilesAccessPermission = PermissionUtil.hasAllFilesAccessPermission()
    }

    val permissionState = remember(hasAllFilesAccessPermission) {
        AllFilesPermissionState(
            hasPermission = hasAllFilesAccessPermission,
            onGrant = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    allFilesPermissionLauncher.launch(
                        PermissionUtil.getAllFilesAccessPermissionIntent(context)
                    )
                }
            }
        )
    }

    fun checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (notificationPrefs.useExactAlarms && !hasExactAlarmPermission) {
            alarmPermissionLauncher.launch(PermissionUtil.getExactAlarmPermissionIntent(context))
        }
    }

    var syncIntervalHours by remember(appSettings.syncIntervalHours) {
        mutableFloatStateOf(appSettings.syncIntervalHours.toFloat())
    }

    var searchIntervalHours by remember(autoDownloadPrefs.searchIntervalHours) {
        mutableFloatStateOf(autoDownloadPrefs.searchIntervalHours.toFloat())
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isForceSyncing by viewModel.isForceSyncing.collectAsState()
    val customSearchLinks by viewModel.customSearchLinks.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }
    var exportAppSettings by remember { mutableStateOf(true) }
    var exportNotificationSettings by remember { mutableStateOf(true) }
    var exportDownloadSettings by remember { mutableStateOf(true) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportSettings(uri, exportAppSettings, exportNotificationSettings, exportDownloadSettings) { _, message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showImportDialog by remember { mutableStateOf(false) }
    var pendingBackupData by remember { mutableStateOf<AppBackupData?>(null) }
    var importAppSettings by remember { mutableStateOf(true) }
    var importNotificationSettings by remember { mutableStateOf(true) }
    var importDownloadSettings by remember { mutableStateOf(true) }
    var hasAppSettings by remember { mutableStateOf(false) }
    var hasNotificationSettings by remember { mutableStateOf(false) }
    var hasDownloadSettings by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.readBackupMetadata(uri) { backupData ->
                if (backupData != null) {
                    pendingBackupData = backupData
                    hasAppSettings = backupData.appSettings != null || backupData.autoDownload != null || backupData.notification != null || backupData.ui != null || backupData.customSearchLinks.isNotEmpty()
                    hasNotificationSettings = backupData.notificationSettings.isNotEmpty()
                    hasDownloadSettings = backupData.itemDownloadSettings.isNotEmpty()
                    importAppSettings = hasAppSettings
                    importNotificationSettings = hasNotificationSettings
                    importDownloadSettings = hasDownloadSettings
                    showImportDialog = true
                } else {
                    Toast.makeText(context, "Failed to read backup file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var localLinks by remember(customSearchLinks) { mutableStateOf(customSearchLinks) }

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

    val isDownloaderInstalled = remember { viewModel.isTorrentServiceInstalled() }

    var tabletCategoryName by rememberSaveable { mutableStateOf(SettingsCategory.GENERAL.name) }
    val tabletSelectedCategory = remember(tabletCategoryName) {
        SettingsCategory.entries.find { it.name == tabletCategoryName } ?: SettingsCategory.GENERAL
    }

    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isTablet) "Settings" else (initialCategory?.title ?: "Settings"),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = SurfaceDark
    ) { innerPadding ->
        if (isTablet) {
            SettingsTabletLayout(
                tabletSelectedCategory = tabletSelectedCategory,
                onCategorySelected = { tabletCategoryName = it.name },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                SettingsCategoryContentView(
                    category = tabletSelectedCategory,
                    username = userToken?.username.takeIf { !it.isNullOrBlank() } ?: "Unknown",
                    syncIntervalHours = syncIntervalHours,
                    localLinks = localLinks,
                    onLogout = {
                        viewModel.logoutUser()
                        onNavigateBack()
                    },
                    onSyncIntervalChange = { syncIntervalHours = it },
                    onSyncIntervalChangeFinished = {
                        val roundedHours = syncIntervalHours.roundToInt().coerceIn(1, 24)
                        viewModel.updateSyncInterval(roundedHours)
                    },
                    onOpenAddDialog = { openAddDialog() },
                    onOpenEditDialog = { openEditDialog(it) },
                    onDeleteLink = { deleteConfirmLink = it },
                    onReorderLinks = { updated ->
                        localLinks = updated
                        viewModel.updateSearchLinksOrder(updated)
                    },
                    notificationPrefs = notificationPrefs,
                    hasExactAlarmPermission = hasExactAlarmPermission,
                    alarmPermissionLauncher = alarmPermissionLauncher,
                    context = context,
                    checkAndRequestPermission = { checkAndRequestPermission() },
                    isDownloaderInstalled = isDownloaderInstalled,
                    autoDownloadPrefs = autoDownloadPrefs,
                    permissionState = permissionState,
                    searchIntervalHours = searchIntervalHours,
                    onSearchIntervalChange = { searchIntervalHours = it },
                    viewModel = viewModel,
                    isForceSyncing = isForceSyncing,
                    appSettings = appSettings,
                    scope = scope,
                    snackbarHostState = snackbarHostState,
                    onNavigateToLogViewer = onNavigateToLogViewer,
                    onExportClick = { showExportDialog = true },
                    onImportClick = { importLauncher.launch("application/json") }
                )
            }
        } else {
            if (initialCategory == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsCategory.entries.forEach { category ->
                        MobileCategoryListItem(
                            category = category,
                            onClick = { onNavigateToCategory(category) }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsCategoryContentView(
                        category = initialCategory,
                        username = userToken?.username.takeIf { !it.isNullOrBlank() } ?: "Unknown",
                        syncIntervalHours = syncIntervalHours,
                        localLinks = localLinks,
                        onLogout = {
                            viewModel.logoutUser()
                            onNavigateBack()
                        },
                        onSyncIntervalChange = { syncIntervalHours = it },
                        onSyncIntervalChangeFinished = {
                            val roundedHours = syncIntervalHours.roundToInt().coerceIn(1, 24)
                            viewModel.updateSyncInterval(roundedHours)
                        },
                        onOpenAddDialog = { openAddDialog() },
                        onOpenEditDialog = { openEditDialog(it) },
                        onDeleteLink = { deleteConfirmLink = it },
                        onReorderLinks = { updated ->
                            localLinks = updated
                            viewModel.updateSearchLinksOrder(updated)
                        },
                        notificationPrefs = notificationPrefs,
                        hasExactAlarmPermission = hasExactAlarmPermission,
                        alarmPermissionLauncher = alarmPermissionLauncher,
                        context = context,
                        checkAndRequestPermission = { checkAndRequestPermission() },
                        isDownloaderInstalled = isDownloaderInstalled,
                        autoDownloadPrefs = autoDownloadPrefs,
                        permissionState = permissionState,
                        searchIntervalHours = searchIntervalHours,
                        onSearchIntervalChange = { searchIntervalHours = it },
                        viewModel = viewModel,
                        isForceSyncing = isForceSyncing,
                        appSettings = appSettings,
                        scope = scope,
                        snackbarHostState = snackbarHostState,
                        onNavigateToLogViewer = onNavigateToLogViewer,
                        onExportClick = { showExportDialog = true },
                        onImportClick = { importLauncher.launch("application/json") }
                    )
                }
            }
        }
    }

    if (showExportDialog) {
        SettingsExportDialog(
            exportAppSettings = exportAppSettings,
            exportNotificationSettings = exportNotificationSettings,
            exportDownloadSettings = exportDownloadSettings,
            onAppSettingsChange = { exportAppSettings = it },
            onNotificationSettingsChange = { exportNotificationSettings = it },
            onDownloadSettingsChange = { exportDownloadSettings = it },
            onDismiss = { showExportDialog = false },
            onConfirm = {
                showExportDialog = false
                exportLauncher.launch("simkl_calendar_settings_backup.json")
            }
        )
    }

    if (showImportDialog && pendingBackupData != null) {
        SettingsImportDialog(
            hasAppSettings = hasAppSettings,
            hasNotificationSettings = hasNotificationSettings,
            hasDownloadSettings = hasDownloadSettings,
            importAppSettings = importAppSettings,
            importNotificationSettings = importNotificationSettings,
            importDownloadSettings = importDownloadSettings,
            onAppSettingsChange = { importAppSettings = it },
            onNotificationSettingsChange = { importNotificationSettings = it },
            onDownloadSettingsChange = { importDownloadSettings = it },
            onDismiss = {
                showImportDialog = false
                pendingBackupData = null
            },
            onConfirm = {
                val data = pendingBackupData
                showImportDialog = false
                pendingBackupData = null
                if (data != null) {
                    viewModel.importSettings(data, importAppSettings, importNotificationSettings, importDownloadSettings) { _, message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Add / Edit Custom Search Link Dialog
    if (showAddEditDialog) {
        CustomSearchLinkAddEditDialog(
            editingLink = editingLink,
            inputName = inputName,
            onInputNameChange = { inputName = it },
            inputSubtitle = inputSubtitle,
            onInputSubtitleChange = { inputSubtitle = it },
            inputUrlTemplate = inputUrlTemplate,
            onInputUrlTemplateChange = { inputUrlTemplate = it },
            inputSelectedTypes = inputSelectedTypes,
            onInputSelectedTypesChange = { inputSelectedTypes = it },
            formError = formError,
            onSave = {
                if (inputName.isBlank()) {
                    formError = "Name cannot be blank"
                    return@CustomSearchLinkAddEditDialog
                }
                if (inputUrlTemplate.text.isBlank()) {
                    formError = "URL template cannot be blank"
                    return@CustomSearchLinkAddEditDialog
                }
                if (inputSelectedTypes.isEmpty()) {
                    formError = "Select at least one associated item type"
                    return@CustomSearchLinkAddEditDialog
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
            onDismiss = { showAddEditDialog = false }
        )
    }

    // Delete Confirmation Dialog
    deleteConfirmLink?.let { link ->
        CustomSearchLinkDeleteDialog(
            link = link,
            onConfirmDelete = {
                viewModel.deleteCustomSearchLink(link) {
                    deleteConfirmLink = null
                }
            },
            onDismiss = { deleteConfirmLink = null }
        )
    }
}

@Composable
fun SettingsTabletLayout(
    tabletSelectedCategory: SettingsCategory,
    onCategorySelected: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
                .padding(start = 16.dp, bottom = 16.dp)
        ) {
            SettingsCategorySelector(
                selectedCategory = tabletSelectedCategory,
                onCategorySelected = onCategorySelected,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SettingsCategorySelector(
    selectedCategory: SettingsCategory,
    onCategorySelected: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    var itemHeightPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val selectedIndex = SettingsCategory.entries.indexOf(selectedCategory)

    Box(modifier = modifier.width(IntrinsicSize.Max)) {
        val effectiveItemHeightDp = if (itemHeightPx > 0f) with(density) { itemHeightPx.toDp() } else 40.dp
        val spacingDp = 4.dp
        val targetOffsetDp = (effectiveItemHeightDp + spacingDp) * selectedIndex
        val animatedOffsetDp by animateDpAsState(
            targetValue = targetOffsetDp,
            animationSpec = tween(250),
            label = "category_indicator_offset"
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = SurfacePrimaryVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(effectiveItemHeightDp)
                .offset { IntOffset(0, animatedOffsetDp.roundToPx()) }
        ) {}

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.width(IntrinsicSize.Max)
        ) {
            SettingsCategory.entries.forEach { category ->
                SettingsCategoryItem(
                    category = category,
                    isSelected = category == selectedCategory,
                    onClick = { onCategorySelected(category) },
                    onMeasuredHeight = { heightPx ->
                        if (itemHeightPx == 0f && heightPx > 0f) {
                            itemHeightPx = heightPx
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsCategoryContentView(
    category: SettingsCategory,
    username: String,
    syncIntervalHours: Float,
    localLinks: List<CustomSearchLink>,
    onLogout: () -> Unit,
    onSyncIntervalChange: (Float) -> Unit,
    onSyncIntervalChangeFinished: () -> Unit,
    onOpenAddDialog: () -> Unit,
    onOpenEditDialog: (CustomSearchLink) -> Unit,
    onDeleteLink: (CustomSearchLink) -> Unit,
    onReorderLinks: (List<CustomSearchLink>) -> Unit,
    notificationPrefs: NotificationPreferences,
    hasExactAlarmPermission: Boolean,
    alarmPermissionLauncher: ActivityResultLauncher<Intent>,
    context: Context,
    checkAndRequestPermission: () -> Unit,
    isDownloaderInstalled: Boolean,
    autoDownloadPrefs: AutoDownloadPreferences,
    permissionState: AllFilesPermissionState,
    searchIntervalHours: Float,
    onSearchIntervalChange: (Float) -> Unit,
    viewModel: SettingsViewModel,
    isForceSyncing: Boolean,
    appSettings: AppSettingsPreferences,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onNavigateToLogViewer: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    when (category) {
        SettingsCategory.GENERAL -> {
            SettingsGeneralCategorySection(
                username = username,
                syncIntervalHours = syncIntervalHours,
                localLinks = localLinks,
                onLogout = onLogout,
                onSyncIntervalChange = onSyncIntervalChange,
                onSyncIntervalChangeFinished = onSyncIntervalChangeFinished,
                onOpenAddDialog = onOpenAddDialog,
                onOpenEditDialog = onOpenEditDialog,
                onDeleteLink = onDeleteLink,
                onReorderLinks = onReorderLinks
            )
        }
        SettingsCategory.NOTIFICATIONS -> {
            SettingsNotificationsCategorySection(
                notificationPrefs = notificationPrefs,
                hasExactAlarmPermission = hasExactAlarmPermission,
                alarmPermissionLauncher = alarmPermissionLauncher,
                context = context,
                checkAndRequestPermission = checkAndRequestPermission,
                viewModel = viewModel
            )
        }
        SettingsCategory.DOWNLOADS -> {
            SettingsDownloadsCategorySection(
                isDownloaderInstalled = isDownloaderInstalled,
                autoDownloadPrefs = autoDownloadPrefs,
                permissionState = permissionState,
                searchIntervalHours = searchIntervalHours,
                onSearchIntervalChange = onSearchIntervalChange,
                viewModel = viewModel
            )
        }
        SettingsCategory.BACKUP -> {
            SettingsBackupCategorySection(
                onExportClick = onExportClick,
                onImportClick = onImportClick
            )
        }
        SettingsCategory.DEBUG -> {
            SettingsDebugCategorySection(
                isForceSyncing = isForceSyncing,
                appSettings = appSettings,
                viewModel = viewModel,
                scope = scope,
                snackbarHostState = snackbarHostState,
                onNavigateToLogViewer = onNavigateToLogViewer
            )
        }
    }
}

@Composable
fun MobileCategoryListItem(
    category: SettingsCategory,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = PurpleLight,
                modifier = Modifier.size(24.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = category.title,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    fontSize = 16.sp
                )
                Text(
                    text = category.description,
                    color = TextSubtle,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun SettingsCategoryItem(
    category: SettingsCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMeasuredHeight: (Float) -> Unit = {}
) {
    val animatedContentColor by animateColorAsState(
        targetValue = if (isSelected) PurpleContainerSoft else TextSubtle,
        animationSpec = tween(200),
        label = "category_content_color"
    )
    val animatedIconColor by animateColorAsState(
        targetValue = if (isSelected) PurpleLight else TextGrey,
        animationSpec = tween(200),
        label = "category_icon_color"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        contentColor = animatedContentColor,
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { onMeasuredHeight(it.size.height.toFloat()) }
            .testTag("settings_category_${category.name.lowercase()}")
    ) {
        SettingsCategoryItemContent(category = category, isSelected = isSelected, animatedIconColor = animatedIconColor)
    }
}

@Composable
private fun SettingsCategoryItemContent(
    category: SettingsCategory,
    isSelected: Boolean,
    animatedIconColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = animatedIconColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = category.title,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

@Composable
fun SettingsGeneralCategorySection(
    username: String,
    syncIntervalHours: Float,
    localLinks: List<CustomSearchLink>,
    onLogout: () -> Unit,
    onSyncIntervalChange: (Float) -> Unit,
    onSyncIntervalChangeFinished: () -> Unit,
    onOpenAddDialog: () -> Unit,
    onOpenEditDialog: (CustomSearchLink) -> Unit,
    onDeleteLink: (CustomSearchLink) -> Unit,
    onReorderLinks: (List<CustomSearchLink>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsUserSessionCard(
            username = username,
            onLogout = onLogout
        )
        SettingsSyncIntervalCard(
            syncIntervalHours = syncIntervalHours,
            onSyncIntervalChange = onSyncIntervalChange,
            onSyncIntervalChangeFinished = onSyncIntervalChangeFinished
        )
        SettingsCustomSearchLinksCard(
            localLinks = localLinks,
            onOpenAddDialog = onOpenAddDialog,
            onOpenEditDialog = onOpenEditDialog,
            onDeleteLink = onDeleteLink,
            onReorderLinks = onReorderLinks
        )
    }
}

@Composable
fun SettingsNotificationsCategorySection(
    notificationPrefs: NotificationPreferences,
    hasExactAlarmPermission: Boolean,
    alarmPermissionLauncher: ActivityResultLauncher<Intent>,
    context: Context,
    checkAndRequestPermission: () -> Unit,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsDefaultAlertsCard(
            enableDefaultAiring = notificationPrefs.defaultNotifyAiring,
            enableDefaultSeasonFinished = notificationPrefs.defaultNotifySeasonFinished,
            enableDefaultMovieTheater = notificationPrefs.defaultNotifyMovieTheater,
            enableDefaultMovieDigital = notificationPrefs.defaultNotifyMovieDigital,
            onUpdateDefaultNotifyAiring = {
                viewModel.updateDefaultNotifyAiring(it)
                if (it) checkAndRequestPermission()
            },
            onUpdateDefaultNotifySeasonFinished = {
                viewModel.updateDefaultNotifySeasonFinished(it)
                if (it) checkAndRequestPermission()
            },
            onUpdateDefaultNotifyMovieTheater = {
                viewModel.updateDefaultNotifyMovieTheater(it)
                if (it) checkAndRequestPermission()
            },
            onUpdateDefaultNotifyMovieDigital = {
                viewModel.updateDefaultNotifyMovieDigital(it)
                if (it) checkAndRequestPermission()
            }
        )
        SettingsBatteryOptimizationCard(
            useExactAlarms = notificationPrefs.useExactAlarms,
            hasExactAlarmPermission = hasExactAlarmPermission,
            onUpdateUseExactAlarms = {
                viewModel.updateUseExactAlarms(it)
                if (it) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasExactAlarmPermission) {
                        alarmPermissionLauncher.launch(PermissionUtil.getExactAlarmPermissionIntent(context))
                    }
                }
                viewModel.scheduleAllItemsAiredAlarms()
            },
            onGrantExactAlarmPermission = {
                alarmPermissionLauncher.launch(PermissionUtil.getExactAlarmPermissionIntent(context))
            }
        )
    }
}

data class AutoDownloadActions(
    val onUpdateQuality: (String) -> Unit,
    val onUpdatePreferHevc: (Boolean) -> Unit,
    val onUpdateUnwatchedTv: (Boolean) -> Unit,
    val onUpdateUnwatchedAnime: (Boolean) -> Unit,
    val onUpdateUnwatchedMovie: (Boolean) -> Unit,
    val onUpdateSeasonUnwatchedTv: (Boolean) -> Unit,
    val onUpdateSeasonUnwatchedAnime: (Boolean) -> Unit,
    val onUpdateDeleteFileOnWatched: (Boolean) -> Unit,
    val onUpdateDeleteEmptyParentDirectory: (Boolean) -> Unit,
    val onAddPreferredKeyword: (String) -> Unit,
    val onRemovePreferredKeyword: (String) -> Unit,
    val onReorderPreferredKeywords: (List<String>) -> Unit,
    val onAddIgnoreKeyword: (String) -> Unit,
    val onRemoveIgnoreKeyword: (String) -> Unit,
    val onReorderIgnoreKeywords: (List<String>) -> Unit,
    val onUpdateSingleItemMinSize: (Double, String) -> Unit,
    val onUpdateSingleItemMaxSize: (Double, String) -> Unit,
    val onUpdateSeasonMinSize: (Double, String) -> Unit,
    val onUpdateSeasonMaxSize: (Double, String) -> Unit
)

data class AllFilesPermissionState(
    val hasPermission: Boolean,
    val onGrant: () -> Unit
)

@Composable
fun SettingsDownloadsCategorySection(
    isDownloaderInstalled: Boolean,
    autoDownloadPrefs: AutoDownloadPreferences,
    permissionState: AllFilesPermissionState,
    searchIntervalHours: Float,
    onSearchIntervalChange: (Float) -> Unit,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val actions = remember(viewModel) {
        AutoDownloadActions(
            onUpdateQuality = { viewModel.updateAutoDownloadQuality(it) },
            onUpdatePreferHevc = { viewModel.updateAutoDownloadPreferHevc(it) },
            onUpdateUnwatchedTv = { viewModel.updateAutoDownloadUnwatchedTv(it) },
            onUpdateUnwatchedAnime = { viewModel.updateAutoDownloadUnwatchedAnime(it) },
            onUpdateUnwatchedMovie = { viewModel.updateAutoDownloadUnwatchedMovie(it) },
            onUpdateSeasonUnwatchedTv = { viewModel.updateAutoDownloadSeasonUnwatchedTv(it) },
            onUpdateSeasonUnwatchedAnime = { viewModel.updateAutoDownloadSeasonUnwatchedAnime(it) },
            onUpdateDeleteFileOnWatched = { viewModel.updateDeleteFileOnWatched(it) },
            onUpdateDeleteEmptyParentDirectory = { viewModel.updateDeleteEmptyParentDirectory(it) },
            onAddPreferredKeyword = { viewModel.addPreferredKeyword(it) },
            onRemovePreferredKeyword = { viewModel.removePreferredKeyword(it) },
            onReorderPreferredKeywords = { viewModel.updatePreferredKeywordsOrder(it) },
            onAddIgnoreKeyword = { viewModel.addIgnoreKeyword(it) },
            onRemoveIgnoreKeyword = { viewModel.removeIgnoreKeyword(it) },
            onReorderIgnoreKeywords = { viewModel.updateIgnoreKeywordsOrder(it) },
            onUpdateSingleItemMinSize = { value, unit -> viewModel.updateSingleItemMinSize(value, unit) },
            onUpdateSingleItemMaxSize = { value, unit -> viewModel.updateSingleItemMaxSize(value, unit) },
            onUpdateSeasonMinSize = { value, unit -> viewModel.updateSeasonMinSize(value, unit) },
            onUpdateSeasonMaxSize = { value, unit -> viewModel.updateSeasonMaxSize(value, unit) }
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsAutomaticDownloadsCard(
            isDownloaderInstalled = isDownloaderInstalled,
            autoDownloadPrefs = autoDownloadPrefs,
            permissionState = permissionState,
            searchIntervalHours = searchIntervalHours,
            onSearchIntervalChange = onSearchIntervalChange,
            onSearchIntervalHoursChangeFinished = {
                val roundedHours = searchIntervalHours.roundToInt().coerceIn(1, 24)
                viewModel.updateSearchInterval(roundedHours)
            },
            actions = actions
        )
    }
}

@Composable
fun SettingsDebugCategorySection(
    isForceSyncing: Boolean,
    appSettings: AppSettingsPreferences,
    viewModel: SettingsViewModel,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onNavigateToLogViewer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsAppLogsDiagnosticsCard(
            sentryEnabled = appSettings.isSentryEnabled,
            isSentryConfigured = viewModel.isSentryConfigured,
            isSentryRunning = viewModel.isSentryRunning,
            showDebugActions = appSettings.showDebugActions,
            onUpdateSentryEnabled = { viewModel.updateSentryEnabled(it) },
            onUpdateShowDebugActions = { viewModel.updateShowDebugActions(it) },
            onNavigateToLogViewer = onNavigateToLogViewer,
            isForceSyncing = isForceSyncing,
            onForceSync = {
                viewModel.forceWatchlistResync { _, message ->
                    scope.launch {
                        snackbarHostState.showSnackbar(message)
                    }
                }
            }
        )
    }
}

@Composable
fun SettingsCustomSearchLinksCard(
    localLinks: List<CustomSearchLink>,
    onOpenAddDialog: () -> Unit,
    onOpenEditDialog: (CustomSearchLink) -> Unit,
    onDeleteLink: (CustomSearchLink) -> Unit,
    onReorderLinks: (List<CustomSearchLink>) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        modifier = modifier
    ) {
        SettingsCustomSearchLinksContent(
            onOpenAddDialog,
            localLinks,
            onReorderLinks,
            onOpenEditDialog,
            onDeleteLink
        )
    }
}

@Composable
private fun SettingsCustomSearchLinksContent(
    onOpenAddDialog: () -> Unit,
    localLinks: List<CustomSearchLink>,
    onReorderLinks: (List<CustomSearchLink>) -> Unit,
    onOpenEditDialog: (CustomSearchLink) -> Unit,
    onDeleteLink: (CustomSearchLink) -> Unit
) {
    val density = LocalDensity.current
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var itemSlotHeightPx by remember { mutableFloatStateOf(0f) }


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
                    tint = PurpleLight,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Custom Search Links",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 16.sp
                )
            }

            FilledTonalButton(
                onClick = onOpenAddDialog,
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
                color = SurfaceDark,
                border = BorderStroke(1.dp, SurfaceVariantGrey),
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
                        tint = OutlineGrey,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "No custom search links configured yet",
                        color = TextSubtle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Tap '+ Add Link' above to configure custom search shortcuts.",
                        color = OutlineGrey,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
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
                            tint = TextGrey,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Long press and drag anywhere on a link to reorder",
                            color = TextGrey,
                            fontSize = 11.sp
                        )
                    }
                }

                val currentLinksState by rememberUpdatedState(localLinks)
                val currentDragging = draggingIndex
                val effectiveSlotHeight = if (itemSlotHeightPx > 0f) itemSlotHeightPx else with(
                    density
                ) { 68.dp.toPx() }
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
                            color = if (isDraggingThis) SurfaceSettingsCardDark else SurfaceDark,
                            border = BorderStroke(
                                width = if (isDraggingThis) 1.5.dp else 1.dp,
                                color = if (isDraggingThis) PurpleLight else OutlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDraggingThis) 10f else 1f)
                                .onGloballyPositioned { coordinates ->
                                    if (itemSlotHeightPx == 0f && coordinates.size.height > 0) {
                                        itemSlotHeightPx = coordinates.size.height.toFloat() + with(
                                            density
                                        ) { 8.dp.toPx() }
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
                                                onReorderLinks(updated)
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
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("drag_handle_${link.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = "Reorder handle for ${link.name}",
                                        tint = if (isDraggingThis) PurpleLight else OutlineGrey,
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
                                        color = SurfaceContainer,
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
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            if (!link.subtitle.isNullOrBlank()) {
                                                Text(
                                                    text = "• ${link.subtitle}",
                                                    color = TextSubtle,
                                                    fontSize = 12.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = link.urlTemplate,
                                            color = TextGrey,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            link.associatedTypes.forEach { type ->
                                                val (label, bgCol, textCol) = when (type) {
                                                    MediaType.TV -> Triple(
                                                        "TV",
                                                        SurfacePurple,
                                                        PurpleContainerSoft
                                                    )

                                                    MediaType.ANIME -> Triple(
                                                        "Anime",
                                                        SurfaceDarkGreen,
                                                        GreenSuccess
                                                    )

                                                    MediaType.MOVIE -> Triple(
                                                        "Movie",
                                                        SurfaceRedDeep,
                                                        ErrorPink
                                                    )
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
                                        onClick = { onOpenEditDialog(link) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("edit_link_${link.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit ${link.name}",
                                            tint = PurpleLight,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteLink(link) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("delete_link_${link.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete ${link.name}",
                                            tint = ErrorPink,
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

@Composable
fun CustomSearchLinkAddEditDialog(
    editingLink: CustomSearchLink?,
    inputName: String,
    onInputNameChange: (String) -> Unit,
    inputSubtitle: String,
    onInputSubtitleChange: (String) -> Unit,
    inputUrlTemplate: TextFieldValue,
    onInputUrlTemplateChange: (TextFieldValue) -> Unit,
    inputSelectedTypes: Set<MediaType>,
    onInputSelectedTypesChange: (Set<MediaType>) -> Unit,
    formError: String?,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingLink == null) "Add Custom Search Link" else "Edit Custom Search Link",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = inputName,
                    onValueChange = onInputNameChange,
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Search Service") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_search_link_name_input")
                )

                OutlinedTextField(
                    value = inputSubtitle,
                    onValueChange = onInputSubtitleChange,
                    label = { Text("Subtitle (Optional)") },
                    placeholder = { Text("e.g. Search ratings & cast") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_search_link_subtitle_input")
                )

                OutlinedTextField(
                    value = inputUrlTemplate,
                    onValueChange = onInputUrlTemplateChange,
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
                    onInputUrlTemplateChange(
                        TextFieldValue(
                            text = newText,
                            selection = TextRange(newCursorPos)
                        )
                    )
                }

                Column {
                    Text(
                        text = "Tap placeholder to insert:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSubtle
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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
                                color = SurfaceContainer,
                                border = BorderStroke(1.dp, OutlineVariant),
                                modifier = Modifier.clickable {
                                    insertPlaceholder(placeholder)
                                }
                            ) {
                                Text(
                                    text = placeholder,
                                    color = PurpleLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = OutlineVariant, thickness = 1.dp)

                Column {
                    Text(
                        text = "Associated Item Types",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
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
                                    onInputSelectedTypesChange(
                                        if (isSelected) inputSelectedTypes - type else inputSelectedTypes + type
                                    )
                                },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                if (formError != null) {
                    Text(
                        text = formError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                modifier = Modifier.testTag("save_custom_search_link_button")
            ) {
                Text("Save")
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
fun CustomSearchLinkDeleteDialog(
    link: CustomSearchLink,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Delete Search Link", fontWeight = FontWeight.Bold)
        },
        text = {
            Text("Are you sure you want to delete '${link.name}'? This action cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
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
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SettingsUserSessionCard(
    username: String,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        modifier = modifier
    ) {
        SettingsUserSessionContent(username, onLogout)
    }
}

@Composable
private fun SettingsUserSessionContent(username: String, onLogout: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Logged in as",
                    fontSize = 13.sp,
                    color = TextSubtle
                )
                Text(
                    username,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PurpleLight
                )
            }

            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Logout", fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun SettingsSyncIntervalCard(
    syncIntervalHours: Float,
    onSyncIntervalChange: (Float) -> Unit,
    onSyncIntervalChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        modifier = modifier
    ) {
        SettingsSyncIntervalContent(syncIntervalHours, onSyncIntervalChange, onSyncIntervalChangeFinished)
    }
}

@Composable
private fun SettingsSyncIntervalContent(
    syncIntervalHours: Float,
    onSyncIntervalChange: (Float) -> Unit,
    onSyncIntervalChangeFinished: () -> Unit
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
                    tint = PurpleLight,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Background Sync Interval",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 16.sp
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceSettingsPurpleDark
            ) {
                Text(
                    text = "${syncIntervalHours.roundToInt()} hrs",
                    color = PurpleLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Sets how frequently the app runs background checks to discover new episode releases and sync your watchlist.",
            color = TextSubtle,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Slider(
            value = syncIntervalHours,
            onValueChange = onSyncIntervalChange,
            onValueChangeFinished = onSyncIntervalChangeFinished,
            valueRange = 1f..24f,
            steps = 22,
            colors = SliderDefaults.colors(
                thumbColor = PurpleLight,
                activeTrackColor = PurpleLight,
                inactiveTrackColor = OutlineVariant,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("1 hour (frequent)", color = TextGrey, fontSize = 11.sp)
            Text("12 hours (default)", color = TextGrey, fontSize = 11.sp)
            Text("24 hours (daily)", color = TextGrey, fontSize = 11.sp)
        }
    }
}

@Composable
fun SettingsDefaultAlertsCard(
    enableDefaultAiring: Boolean,
    enableDefaultSeasonFinished: Boolean,
    enableDefaultMovieTheater: Boolean,
    enableDefaultMovieDigital: Boolean,
    onUpdateDefaultNotifyAiring: (Boolean) -> Unit,
    onUpdateDefaultNotifySeasonFinished: (Boolean) -> Unit,
    onUpdateDefaultNotifyMovieTheater: (Boolean) -> Unit,
    onUpdateDefaultNotifyMovieDigital: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        modifier = modifier
    ) {
        SettingsDefaultAlertsContent(
            enableDefaultAiring,
            onUpdateDefaultNotifyAiring,
            enableDefaultSeasonFinished,
            onUpdateDefaultNotifySeasonFinished,
            enableDefaultMovieTheater,
            onUpdateDefaultNotifyMovieTheater,
            enableDefaultMovieDigital,
            onUpdateDefaultNotifyMovieDigital
        )
    }
}

@Composable
private fun SettingsDefaultAlertsContent(
    enableDefaultAiring: Boolean,
    onUpdateDefaultNotifyAiring: (Boolean) -> Unit,
    enableDefaultSeasonFinished: Boolean,
    onUpdateDefaultNotifySeasonFinished: (Boolean) -> Unit,
    enableDefaultMovieTheater: Boolean,
    onUpdateDefaultNotifyMovieTheater: (Boolean) -> Unit,
    enableDefaultMovieDigital: Boolean,
    onUpdateDefaultNotifyMovieDigital: (Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            "Default Alerts (New Items)",
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Sets default alert preferences when new shows or movies are synced. Individual settings in Release Details will always take precedence.",
            color = TextSubtle,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "TV Shows & Anime",
            fontWeight = FontWeight.Bold,
            color = PurpleLight,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Airing Notifications",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Default to alert as soon as each episode is ready to stream.",
                    color = TextSubtle,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = enableDefaultAiring,
                onCheckedChange = onUpdateDefaultNotifyAiring
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Season Finished Airing",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Default to notify when a full TV Show or Anime season has finished airing.",
                    color = TextSubtle,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = enableDefaultSeasonFinished,
                onCheckedChange = onUpdateDefaultNotifySeasonFinished
            )
        }

        HorizontalDivider(
            color = OutlineVariant,
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Text(
            text = "Movies",
            fontWeight = FontWeight.Bold,
            color = ErrorPink,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Theater Release Notifications",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Default to notify on the movie's theatrical release date.",
                    color = TextSubtle,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = enableDefaultMovieTheater,
                onCheckedChange = onUpdateDefaultNotifyMovieTheater
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Digital / DVD Release Notifications",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Default to notify when the movie releases digitally or on DVD.",
                    color = TextSubtle,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = enableDefaultMovieDigital,
                onCheckedChange = onUpdateDefaultNotifyMovieDigital
            )
        }
    }
}

@Composable
fun SettingsBatteryOptimizationCard(
    useExactAlarms: Boolean,
    hasExactAlarmPermission: Boolean,
    onUpdateUseExactAlarms: (Boolean) -> Unit,
    onGrantExactAlarmPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        SettingsBatteryOptimizationContent(
            useExactAlarms,
            onUpdateUseExactAlarms,
            hasExactAlarmPermission,
            onGrantExactAlarmPermission
        )
    }
}

@Composable
private fun SettingsBatteryOptimizationContent(
    useExactAlarms: Boolean,
    onUpdateUseExactAlarms: (Boolean) -> Unit,
    hasExactAlarmPermission: Boolean,
    onGrantExactAlarmPermission: () -> Unit
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
                    imageVector = Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    tint = PurpleLight,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Battery Optimization",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 16.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Use Exact Alarms", color = TextPrimary, fontSize = 14.sp)
                Text(
                    "Exact alarms ensure notifications arrive at the precise airing time but may increase battery consumption.",
                    color = TextSubtle,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = useExactAlarms,
                onCheckedChange = onUpdateUseExactAlarms
            )
        }

        if (useExactAlarms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasExactAlarmPermission) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSettingsBrownDark),
                border = BorderStroke(1.dp, ErrorPink)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = ErrorPink,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Exact Alarms Required",
                            fontWeight = FontWeight.Bold,
                            color = ErrorPink,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "To ensure notifications are delivered exactly when they air, the app needs permission to schedule exact alarms.",
                        color = TextSubtle,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onGrantExactAlarmPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceRedDeep,
                            contentColor = ErrorPink
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Permission", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsPermissionWarningCard(
    title: String,
    message: String,
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceSettingsBrownDark),
        border = BorderStroke(1.dp, ErrorPink),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = ErrorPink,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    color = ErrorPink,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                message,
                color = TextSubtle,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onGrantPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceRedDeep,
                    contentColor = ErrorPink
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Permission", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SettingsAutoDownloadToggles(
    autoDownloadPrefs: AutoDownloadPreferences,
    isDownloaderInstalled: Boolean,
    permissionState: AllFilesPermissionState,
    actions: AutoDownloadActions,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Download Unwatched TV Shows", color = TextPrimary, fontSize = 15.sp)
                Text("Default setting for newly tracked TV Shows.", color = TextSubtle, fontSize = 12.sp)
            }
            Switch(
                checked = autoDownloadPrefs.autoDownloadUnwatchedTv,
                onCheckedChange = actions.onUpdateUnwatchedTv,
                enabled = isDownloaderInstalled
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Download Unwatched Anime", color = TextPrimary, fontSize = 15.sp)
                Text("Default setting for newly tracked Anime.", color = TextSubtle, fontSize = 12.sp)
            }
            Switch(
                checked = autoDownloadPrefs.autoDownloadUnwatchedAnime,
                onCheckedChange = actions.onUpdateUnwatchedAnime,
                enabled = isDownloaderInstalled
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Download Unwatched Movies", color = TextPrimary, fontSize = 15.sp)
                Text("Default setting for newly tracked Movies.", color = TextSubtle, fontSize = 12.sp)
            }
            Switch(
                checked = autoDownloadPrefs.autoDownloadUnwatchedMovie,
                onCheckedChange = actions.onUpdateUnwatchedMovie,
                enabled = isDownloaderInstalled
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Download Season Unwatched TV Shows", color = TextPrimary, fontSize = 15.sp)
                Text("Default setting for newly tracked TV Shows.", color = TextSubtle, fontSize = 12.sp)
            }
            Switch(
                checked = autoDownloadPrefs.autoDownloadSeasonUnwatchedTv,
                onCheckedChange = actions.onUpdateSeasonUnwatchedTv,
                enabled = isDownloaderInstalled
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Download Season Unwatched Anime", color = TextPrimary, fontSize = 15.sp)
                Text("Default setting for newly tracked Anime.", color = TextSubtle, fontSize = 12.sp)
            }
            Switch(
                checked = autoDownloadPrefs.autoDownloadSeasonUnwatchedAnime,
                onCheckedChange = actions.onUpdateSeasonUnwatchedAnime,
                enabled = isDownloaderInstalled
            )
        }

        HorizontalDivider(color = OutlineVariant, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Delete downloaded file when watched", color = TextPrimary, fontSize = 15.sp)
                Text(
                    "Automatically delete downloaded video files when marked as watched.",
                    color = TextSubtle,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = autoDownloadPrefs.deleteFileOnWatched,
                onCheckedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !permissionState.hasPermission) {
                        permissionState.onGrant()
                    }
                    actions.onUpdateDeleteFileOnWatched(enabled)
                },
                enabled = isDownloaderInstalled
            )
        }

        if (autoDownloadPrefs.deleteFileOnWatched && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !permissionState.hasPermission) {
            Spacer(modifier = Modifier.height(12.dp))
            SettingsPermissionWarningCard(
                title = "All Files Access Required",
                message = "To delete downloaded files written by other apps, " +
                    "the application requires All Files Access permission.",
                onGrantPermission = permissionState.onGrant
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Delete empty parent directory", color = TextPrimary, fontSize = 15.sp)
                Text(
                    "Delete parent directory if empty " +
                        "(excludes top-level 'movies', 'anime', and 'series' folders).",
                    color = TextSubtle,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = autoDownloadPrefs.deleteEmptyParentDirectory,
                onCheckedChange = actions.onUpdateDeleteEmptyParentDirectory,
                enabled = isDownloaderInstalled && autoDownloadPrefs.deleteFileOnWatched
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
fun SettingsAutomaticDownloadsCard(
    isDownloaderInstalled: Boolean,
    autoDownloadPrefs: AutoDownloadPreferences,
    permissionState: AllFilesPermissionState,
    searchIntervalHours: Float,
    onSearchIntervalChange: (Float) -> Unit,
    onSearchIntervalHoursChangeFinished: () -> Unit,
    actions: AutoDownloadActions,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        SettingsAutomaticDownloadsContent(
            isDownloaderInstalled,
            autoDownloadPrefs,
            actions,
            permissionState,
            searchIntervalHours,
            onSearchIntervalChange,
            onSearchIntervalHoursChangeFinished
        )
    }
}

@Composable
private fun SettingsAutomaticDownloadsContent(
    isDownloaderInstalled: Boolean,
    autoDownloadPrefs: AutoDownloadPreferences,
    actions: AutoDownloadActions,
    permissionState: AllFilesPermissionState,
    searchIntervalHours: Float,
    onSearchIntervalChange: (Float) -> Unit,
    onSearchIntervalHoursChangeFinished: () -> Unit
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = PurpleLight,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Automatic Downloads",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 16.sp
                )
            }
            if (isDownloaderInstalled) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = SurfaceGreen,
                    contentColor = GreenSuccess
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
                    color = SurfaceVariantGrey,
                    contentColor = TextSubtle
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
            color = TextSubtle,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        if (!isDownloaderInstalled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Downloader app not found. Please install the Torrent Downloader service to enable these features.",
                color = ErrorPink,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 8.dp)) {
                Text("Preferred Quality", color = TextPrimary, fontSize = 15.sp)
                Text(
                    "Target video resolution for automatic torrent searches.",
                    color = TextSubtle,
                    fontSize = 12.sp
                )
            }
            val qualities = listOf("4K", "1080p", "720p")
            SingleChoiceSegmentedButtonRow {
                qualities.forEachIndexed { index, quality ->
                    val isSelected = autoDownloadPrefs.quality == quality
                    SegmentedButton(
                        selected = isSelected,
                        onClick = { actions.onUpdateQuality(quality) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = qualities.size
                        ),
                        enabled = isDownloaderInstalled
                    ) {
                        Text(quality)
                    }
                }
            }
        }

        PreferHevcRow(
            autoDownloadPrefs = autoDownloadPrefs,
            isDownloaderInstalled = isDownloaderInstalled,
            actions = actions
        )

        SettingsTorrentSizeSection(
            autoDownloadPrefs = autoDownloadPrefs,
            isDownloaderInstalled = isDownloaderInstalled,
            actions = actions
        )

        HorizontalDivider(
            color = OutlineVariant,
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        SettingsAutoDownloadToggles(
            autoDownloadPrefs = autoDownloadPrefs,
            isDownloaderInstalled = isDownloaderInstalled,
            permissionState = permissionState,
            actions = actions
        )

        HorizontalDivider(
            color = OutlineVariant,
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 12.dp)
        )

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
                        color = TextPrimary,
                        fontSize = 15.sp
                    )
                    Text(
                        "Sets how frequently the app searches for torrents for episodes in 'Wanted' status.",
                        color = TextSubtle,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceSettingsPurpleDark
                ) {
                    Text(
                        text = "${searchIntervalHours.roundToInt()} hrs",
                        color = PurpleLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Slider(
                value = searchIntervalHours,
                onValueChange = onSearchIntervalChange,
                onValueChangeFinished = onSearchIntervalHoursChangeFinished,
                enabled = isDownloaderInstalled,
                valueRange = 1f..24f,
                steps = 22,
                colors = SliderDefaults.colors(
                    thumbColor = PurpleLight,
                    activeTrackColor = PurpleLight,
                    inactiveTrackColor = OutlineVariant,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("1 hour", color = TextGrey, fontSize = 11.sp)
                Text("12 hours", color = TextGrey, fontSize = 11.sp)
                Text("24 hours", color = TextGrey, fontSize = 11.sp)
            }
        }

        HorizontalDivider(
            color = OutlineVariant,
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        KeywordManagerSection(
            title = "Preferred Keywords",
            subtitle = "Torrents containing these tags will be prioritized. First items take precedence.",
            keywords = autoDownloadPrefs.preferredKeywords,
            onAdd = actions.onAddPreferredKeyword,
            onRemove = actions.onRemovePreferredKeyword,
            onReorder = actions.onReorderPreferredKeywords,
            enabled = isDownloaderInstalled,
            color = PurpleLight
        )

        Spacer(modifier = Modifier.height(16.dp))

        KeywordManagerSection(
            title = "Ignore Keywords",
            subtitle = "Torrents containing these tags will be skipped.",
            keywords = autoDownloadPrefs.ignoreKeywords,
            onAdd = actions.onAddIgnoreKeyword,
            onRemove = actions.onRemoveIgnoreKeyword,
            onReorder = actions.onReorderIgnoreKeywords,
            enabled = isDownloaderInstalled,
            color = ErrorPink
        )
    }
}

@Composable
fun SettingsAppLogsDiagnosticsCard(
    sentryEnabled: Boolean,
    isSentryConfigured: Boolean,
    isSentryRunning: Boolean,
    showDebugActions: Boolean,
    onUpdateSentryEnabled: (Boolean) -> Unit,
    onUpdateShowDebugActions: (Boolean) -> Unit,
    onNavigateToLogViewer: () -> Unit,
    isForceSyncing: Boolean,
    onForceSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        SettingsAppLogsDiagnosticsContent(
            sentryEnabled,
            isSentryConfigured,
            isSentryRunning,
            onUpdateSentryEnabled,
            showDebugActions,
            onUpdateShowDebugActions,
            isForceSyncing,
            onForceSync,
            onNavigateToLogViewer
        )
    }
}

@Composable
private fun SettingsAppLogsDiagnosticsContent(
    sentryEnabled: Boolean,
    isSentryConfigured: Boolean,
    isSentryRunning: Boolean,
    onUpdateSentryEnabled: (Boolean) -> Unit,
    showDebugActions: Boolean,
    onUpdateShowDebugActions: (Boolean) -> Unit,
    isForceSyncing: Boolean,
    onForceSync: () -> Unit,
    onNavigateToLogViewer: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.BugReport,
                contentDescription = null,
                tint = PurpleLight,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Crash Reporting & Diagnostics",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        SettingsSentrySection(
            sentryEnabled = sentryEnabled,
            isSentryConfigured = isSentryConfigured,
            isSentryRunning = isSentryRunning,
            onUpdateSentryEnabled = onUpdateSentryEnabled
        )

        HorizontalDivider(
            color = OutlineVariant,
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Show Debug Actions",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    "Show debug actions like manual item deletion on release detail screen",
                    color = TextSubtle,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = showDebugActions,
                onCheckedChange = onUpdateShowDebugActions,
                modifier = Modifier.testTag("show_debug_actions_switch")
            )
        }

        HorizontalDivider(
            color = OutlineVariant,
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        SettingsWatchlistResyncSection(
            isForceSyncing = isForceSyncing,
            onForceSync = onForceSync
        )

        HorizontalDivider(
            color = OutlineVariant,
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        SettingsAppLogsSection(
            onNavigateToLogViewer = onNavigateToLogViewer
        )
    }
}

@Composable
fun SettingsWatchlistResyncSection(
    isForceSyncing: Boolean,
    onForceSync: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Watchlist Synchronization",
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "Forces a full re-synchronization of your complete SIMKL watchlist and watched history from scratch.",
            color = TextSubtle,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onForceSync,
            enabled = !isForceSyncing,
            modifier = Modifier.fillMaxWidth().height(42.dp)
        ) {
            if (isForceSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Re-syncing Watchlist...", fontSize = 13.sp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Force Watchlist Re-Sync", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SettingsSentrySection(
    sentryEnabled: Boolean,
    isSentryConfigured: Boolean,
    isSentryRunning: Boolean,
    onUpdateSentryEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Sentry Crash Reporting",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    "Automatically report application crashes",
                    color = TextSubtle,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = sentryEnabled && isSentryConfigured,
                onCheckedChange = onUpdateSentryEnabled,
                enabled = isSentryConfigured,
                modifier = Modifier.testTag("sentry_enabled_switch")
            )
        }

        if (!isSentryConfigured) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSettingsDark),
                border = BorderStroke(1.dp, SurfaceSettingsBlueDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = PurpleLight,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Sentry Not Configured",
                            fontWeight = FontWeight.Bold,
                            color = PurpleLight,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Sentry is not configured in this app build and can't be enabled.",
                        color = TextSubtle,
                        fontSize = 12.sp
                    )
                }
            }
        } else if (isSentryRunning && !sentryEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSettingsBrownDark),
                border = BorderStroke(1.dp, ErrorPink),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = ErrorPink,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Restart Required",
                            fontWeight = FontWeight.Bold,
                            color = ErrorPink,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Please restart the app for this change to take effect.",
                        color = TextSubtle,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsAppLogsSection(
    onNavigateToLogViewer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            "View diagnostic logs generated during app operation across current and earlier app runs.",
            color = TextSubtle,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onNavigateToLogViewer,
            colors = ButtonDefaults.buttonColors(
                containerColor = SurfaceSettingsPurpleDark,
                contentColor = PurpleContainerSoft
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("view_logs_button")
        ) {
            Icon(
                Icons.Default.Terminal,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("View Logs", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Suppress("CyclomaticComplexMethod", "LongParameterList", "LongMethod")
@Composable
fun KeywordManagerSection(
    title: String,
    subtitle: String,
    keywords: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    enabled: Boolean,
    color: Color,
) {
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingKeyword by remember { mutableStateOf<String?>(null) }
    var keywordInput by remember { mutableStateOf("") }

    val itemBounds = remember { mutableMapOf<String, Rect>() }
    var draggingKeyword by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var originalCenter by remember { mutableStateOf(Offset.Zero) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, color = TextSubtle, fontSize = 11.sp)
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

        if (keywords.isEmpty()) {
            Text(
                "No keywords added",
                color = OutlineGrey,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            val currentKeywordsState by rememberUpdatedState(keywords)
            Box(modifier = Modifier.fillMaxWidth()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    keywords.forEach { keyword ->
                        key(keyword) {
                            val isDragging = draggingKeyword == keyword
                            Box(
                                modifier = Modifier
                                    .onGloballyPositioned { coords ->
                                        itemBounds[keyword] = coords.boundsInParent()
                                    }
                                    .graphicsLayer {
                                        if (isDragging) {
                                            val currentBaseCenter = itemBounds[keyword]?.center ?: Offset.Zero
                                            if (currentBaseCenter != Offset.Zero && originalCenter != Offset.Zero) {
                                                translationX = (originalCenter.x + dragOffset.x) - currentBaseCenter.x
                                                translationY = (originalCenter.y + dragOffset.y) - currentBaseCenter.y
                                            } else {
                                                translationX = dragOffset.x
                                                translationY = dragOffset.y
                                            }
                                            scaleX = 1.1f
                                            scaleY = 1.1f
                                        }
                                    }
                                    .alpha(if (isDragging) 0.8f else 1f)
                                    .zIndex(if (isDragging) 10f else 1f)
                                    .pointerInput(keyword, enabled) {
                                        if (enabled) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggingKeyword = keyword
                                                    dragOffset = Offset.Zero
                                                    originalCenter = itemBounds[keyword]?.center ?: Offset.Zero
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffset += dragAmount

                                                    if (originalCenter != Offset.Zero) {
                                                        val currentCenter = originalCenter + dragOffset
                                                        val targetEntry = itemBounds.entries
                                                            .filter { it.key != keyword && currentKeywordsState.contains(it.key) }
                                                            .minByOrNull { (it.value.center - currentCenter).getDistance() }

                                                        if (targetEntry != null) {
                                                            val targetKeyword = targetEntry.key
                                                            val targetBounds = targetEntry.value
                                                            if (targetBounds.contains(currentCenter)) {
                                                                val fromIndex = currentKeywordsState.indexOf(keyword)
                                                                val toIndex = currentKeywordsState.indexOf(targetKeyword)
                                                                if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                                                                    val newList = currentKeywordsState.toMutableList().apply {
                                                                        add(toIndex, removeAt(fromIndex))
                                                                    }
                                                                    onReorder(newList)
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggingKeyword = null
                                                    dragOffset = Offset.Zero
                                                    originalCenter = Offset.Zero
                                                },
                                                onDragCancel = {
                                                    draggingKeyword = null
                                                    dragOffset = Offset.Zero
                                                    originalCenter = Offset.Zero
                                                }
                                            )
                                        }
                                    }
                            ) {
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
                                        containerColor = SurfaceDark,
                                        labelColor = TextPrimary
                                    ),
                                    enabled = enabled
                                )
                            }
                        }
                    }
                }
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

@Composable
fun PreferHevcRow(
    autoDownloadPrefs: AutoDownloadPreferences,
    isDownloaderInstalled: Boolean,
    actions: AutoDownloadActions
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Prefer HEVC / x265", color = TextPrimary, fontSize = 15.sp)
            Text("Prioritize high efficiency video coding results.", color = TextSubtle, fontSize = 12.sp)
        }
        Switch(
            checked = autoDownloadPrefs.preferHevc,
            onCheckedChange = actions.onUpdatePreferHevc,
            enabled = isDownloaderInstalled
        )
    }
}

@Composable
fun TorrentSizeTrailingUnitDropdown(
    unit: String,
    onUnitSelected: (String) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(enabled = enabled) { expanded = true }
                .padding(end = 8.dp)
        ) {
            Text(text = unit, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = TextSubtle)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("MB", fontSize = 13.sp) }, onClick = { expanded = false; onUnitSelected("MB") })
            DropdownMenuItem(text = { Text("GB", fontSize = 13.sp) }, onClick = { expanded = false; onUnitSelected("GB") })
        }
    }
}

@Composable
fun SingleTorrentSizeInput(
    textValue: String,
    unit: String,
    onTextChange: (String) -> Unit,
    onUnitSelected: (String) -> Unit,
    enabled: Boolean
) {
    val focusManager = LocalFocusManager.current
    Box(modifier = Modifier.wrapContentWidth()) {
        CustomOutlinedTextField(
            value = textValue,
            onValueChange = onTextChange,
            enabled = enabled,
            singleLine = true,
            minHeight = 16.dp,
            minWidth = 104.dp,
            contentPadding = PaddingValues(start = 14.dp, top = 0.dp, end = 14.dp, bottom = 0.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            trailingIcon = {
                TorrentSizeTrailingUnitDropdown(
                    unit = unit,
                    onUnitSelected = onUnitSelected,
                    enabled = enabled
                )
            }
        )
    }
}

@Composable
fun TorrentSizeInputRow(
    label: String,
    subtitle: String,
    value: Double,
    unit: String,
    onValueUnitChange: (Double, String) -> Unit,
    enabled: Boolean
) {
    val formatted = remember(value) { if (value % 1.0 == 0.0) value.toLong().toString() else value.toString() }
    var textState by remember(formatted) { mutableStateOf(formatted) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = TextSubtle, fontSize = 11.sp, lineHeight = 14.sp)
        }
        SingleTorrentSizeInput(
            textValue = textState,
            unit = unit,
            onTextChange = { newText ->
                textState = newText
                newText.toDoubleOrNull()?.takeIf { it >= 0 }?.let { onValueUnitChange(it, unit) }
            },
            onUnitSelected = { newUnit ->
                val curVal = textState.toDoubleOrNull() ?: value
                onValueUnitChange(curVal, newUnit)
            },
            enabled = enabled
        )
    }
}

@Composable
fun SettingsSingleItemSizeRows(
    prefs: AutoDownloadPreferences,
    enabled: Boolean,
    actions: AutoDownloadActions
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TorrentSizeInputRow(
            label = "Single Item Min Size",
            subtitle = "Minimum size for single movies/episodes.",
            value = prefs.singleItemMinSizeValue,
            unit = prefs.singleItemMinSizeUnit,
            onValueUnitChange = actions.onUpdateSingleItemMinSize,
            enabled = enabled
        )
        TorrentSizeInputRow(
            label = "Single Item Max Size",
            subtitle = "Maximum size for single movies/episodes.",
            value = prefs.singleItemMaxSizeValue,
            unit = prefs.singleItemMaxSizeUnit,
            onValueUnitChange = actions.onUpdateSingleItemMaxSize,
            enabled = enabled
        )
    }
}

@Composable
fun SettingsSeasonSizeRows(
    prefs: AutoDownloadPreferences,
    enabled: Boolean,
    actions: AutoDownloadActions
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TorrentSizeInputRow(
            label = "Season Min Size",
            subtitle = "Minimum size for complete season packs.",
            value = prefs.seasonMinSizeValue,
            unit = prefs.seasonMinSizeUnit,
            onValueUnitChange = actions.onUpdateSeasonMinSize,
            enabled = enabled
        )
        TorrentSizeInputRow(
            label = "Season Max Size",
            subtitle = "Maximum size for complete season packs.",
            value = prefs.seasonMaxSizeValue,
            unit = prefs.seasonMaxSizeUnit,
            onValueUnitChange = actions.onUpdateSeasonMaxSize,
            enabled = enabled
        )
    }
}

@Composable
fun SettingsTorrentSizeHeader(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggleExpand)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Torrent Size Limits", color = TextPrimary, fontSize = 15.sp)
            Text("Restrict search results by file size.", color = TextSubtle, fontSize = 12.sp)
        }
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = TextSubtle,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun SettingsTorrentSizeSection(
    autoDownloadPrefs: AutoDownloadPreferences,
    isDownloaderInstalled: Boolean,
    actions: AutoDownloadActions
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsTorrentSizeHeader(
            isExpanded = isExpanded,
            onToggleExpand = { isExpanded = !isExpanded },
            enabled = isDownloaderInstalled
        )
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsSingleItemSizeRows(prefs = autoDownloadPrefs, enabled = isDownloaderInstalled, actions = actions)
                SettingsSeasonSizeRows(prefs = autoDownloadPrefs, enabled = isDownloaderInstalled, actions = actions)
            }
        }
    }
}

@Composable
fun SettingsBackupCategorySection(
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsExportImportCard(onExportClick, onImportClick)
    }
}

@Composable
private fun SettingsExportImportCard(
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        SettingsExportImportContent(onExportClick, onImportClick)
    }
}

@Composable
private fun SettingsExportImportContent(onExportClick: () -> Unit, onImportClick: () -> Unit) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsBackupHeader()
        SettingsBackupButtonsRow(
            onExportClick = onExportClick,
            onImportClick = onImportClick
        )
    }
}

@Composable
fun SettingsBackupHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.SettingsBackupRestore,
            contentDescription = null,
            tint = PurpleLight,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Backup & Restore",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Export app settings and preferences to a backup file, or restore them.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSubtle
            )
        }
    }
}

@Composable
fun SettingsBackupButtonsRow(
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onExportClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Upload,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Settings")
        }

        OutlinedButton(
            onClick = onImportClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import Settings")
        }
    }
}

@Composable
fun SettingsExportDialog(
    exportAppSettings: Boolean,
    exportNotificationSettings: Boolean,
    exportDownloadSettings: Boolean,
    onAppSettingsChange: (Boolean) -> Unit,
    onNotificationSettingsChange: (Boolean) -> Unit,
    onDownloadSettingsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Settings") },
        text = {
            SettingsExportDialogBody(
                exportAppSettings = exportAppSettings,
                exportNotificationSettings = exportNotificationSettings,
                exportDownloadSettings = exportDownloadSettings,
                onAppSettingsChange = onAppSettingsChange,
                onNotificationSettingsChange = onNotificationSettingsChange,
                onDownloadSettingsChange = onDownloadSettingsChange
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Export") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SettingsExportDialogBody(
    exportAppSettings: Boolean,
    exportNotificationSettings: Boolean,
    exportDownloadSettings: Boolean,
    onAppSettingsChange: (Boolean) -> Unit,
    onNotificationSettingsChange: (Boolean) -> Unit,
    onDownloadSettingsChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Select settings to include in the backup:")
        SettingsCheckboxRow(label = "App Settings", checked = exportAppSettings, onCheckedChange = onAppSettingsChange)
        SettingsCheckboxRow(label = "Per-Item Notification Settings", checked = exportNotificationSettings, onCheckedChange = onNotificationSettingsChange)
        SettingsCheckboxRow(label = "Per-Item Download Settings", checked = exportDownloadSettings, onCheckedChange = onDownloadSettingsChange)
    }
}

@Composable
fun SettingsImportDialog(
    hasAppSettings: Boolean,
    hasNotificationSettings: Boolean,
    hasDownloadSettings: Boolean,
    importAppSettings: Boolean,
    importNotificationSettings: Boolean,
    importDownloadSettings: Boolean,
    onAppSettingsChange: (Boolean) -> Unit,
    onNotificationSettingsChange: (Boolean) -> Unit,
    onDownloadSettingsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Settings") },
        text = {
            SettingsImportDialogBody(
                hasAppSettings = hasAppSettings,
                hasNotificationSettings = hasNotificationSettings,
                hasDownloadSettings = hasDownloadSettings,
                importAppSettings = importAppSettings,
                importNotificationSettings = importNotificationSettings,
                importDownloadSettings = importDownloadSettings,
                onAppSettingsChange = onAppSettingsChange,
                onNotificationSettingsChange = onNotificationSettingsChange,
                onDownloadSettingsChange = onDownloadSettingsChange
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SettingsImportDialogBody(
    hasAppSettings: Boolean,
    hasNotificationSettings: Boolean,
    hasDownloadSettings: Boolean,
    importAppSettings: Boolean,
    importNotificationSettings: Boolean,
    importDownloadSettings: Boolean,
    onAppSettingsChange: (Boolean) -> Unit,
    onNotificationSettingsChange: (Boolean) -> Unit,
    onDownloadSettingsChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Select settings to restore from the backup:")
        SettingsCheckboxRow(label = "App Settings", checked = importAppSettings, enabled = hasAppSettings, onCheckedChange = onAppSettingsChange)
        SettingsCheckboxRow(label = "Per-Item Notification Settings", checked = importNotificationSettings, enabled = hasNotificationSettings, onCheckedChange = onNotificationSettingsChange)
        SettingsCheckboxRow(label = "Per-Item Download Settings", checked = importDownloadSettings, enabled = hasDownloadSettings, onCheckedChange = onDownloadSettingsChange)
    }
}

@Composable
fun SettingsCheckboxRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) TextPrimary else TextSubtle
        )
    }
}
