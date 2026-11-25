package com.felixbrucker.simklcalendar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.felixbrucker.simklcalendar.worker.SyncCalendarWorker
import com.felixbrucker.simklcalendar.worker.AutoDownloadWorker
import com.felixbrucker.simklcalendar.ui.screens.MainScreen
import com.felixbrucker.simklcalendar.ui.screens.LoginScreen
import com.felixbrucker.simklcalendar.ui.screens.ReleaseDetailScreen
import com.felixbrucker.simklcalendar.ui.screens.TrendingScreen
import com.felixbrucker.simklcalendar.ui.screens.MediaItemDetailScreen
import com.felixbrucker.simklcalendar.ui.screens.SettingsScreen
import com.felixbrucker.simklcalendar.ui.screens.SettingsCategory
import com.felixbrucker.simklcalendar.ui.screens.LogViewerScreen
import com.felixbrucker.simklcalendar.ui.theme.MyApplicationTheme
import com.felixbrucker.simklcalendar.ui.theme.PurpleLight
import com.felixbrucker.simklcalendar.ui.viewmodel.CalendarViewModel
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.felixbrucker.simklcalendar.receiver.notification.NotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.net.URLDecoder
import dagger.hilt.android.AndroidEntryPoint
import com.felixbrucker.simklcalendar.data.preferences.AppSettingsRepository
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import com.felixbrucker.simklcalendar.data.repository.DownloadRepository
import com.felixbrucker.simklcalendar.data.util.OAuthEventHub
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import com.felixbrucker.simklcalendar.ui.viewmodel.TrendingViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: CalendarViewModel by viewModels()

    @Inject
    lateinit var oAuthEventHub: OAuthEventHub

    @Inject
    lateinit var appSettingsRepo: AppSettingsRepository

    @Inject
    lateinit var autoDownloadRepo: AutoDownloadRepository

    @Inject
    lateinit var notificationManager: NotificationManager

    @Inject
    lateinit var torrentServiceHelper: TorrentServiceHelper

    @Inject
    lateinit var downloadRepository: DownloadRepository

    private val _pendingReleaseDetailKey = MutableStateFlow<String?>(null)
    val pendingReleaseDetailKey: StateFlow<String?> = _pendingReleaseDetailKey.asStateFlow()

    fun setPendingReleaseDetailKey(key: String) {
        _pendingReleaseDetailKey.value = key
    }

    fun clearPendingReleaseDetailKey() {
        _pendingReleaseDetailKey.value = null
    }

    private val _pendingMediaItemRoute = MutableStateFlow<String?>(null)
    val pendingMediaItemRoute: StateFlow<String?> = _pendingMediaItemRoute.asStateFlow()

    fun setPendingMediaItemRoute(route: String) {
        _pendingMediaItemRoute.value = route
    }

    fun clearPendingMediaItemRoute() {
        _pendingMediaItemRoute.value = null
    }

    // Modern AuthTab ActivityResultLauncher
    private val authTabLauncher = AuthTabIntent.registerActivityResultLauncher(this) { result ->
        handleAuthTabResult(result)
    }

    private fun handleAuthTabResult(result: AuthTabIntent.AuthResult) {
        val resultUri = result.resultUri
        if (result.resultCode == AuthTabIntent.RESULT_OK && resultUri != null) {
            handleOAuthCallback(resultUri)
        }
    }

    fun launchAuthTab(url: String, redirectScheme: String = "simklcalendar") {
        val uri = url.toUri()
        val packageName = CustomTabsClient.getPackageName(this, null)

        // Open the Authorization URI in an Auth Tab if supported by the default browser.
        if (packageName != null && CustomTabsClient.isAuthTabSupported(this, packageName)) {
            val authTabIntent = AuthTabIntent.Builder().build()
            authTabIntent.launch(authTabLauncher, uri, redirectScheme)
        } else {
            // Fall back to a Custom Tab.
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            customTabsIntent.launchUrl(this, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationManager.createNotificationChannel()

        // Reactively handle sync interval changes
        lifecycleScope.launch {
            appSettingsRepo.preferencesFlow
                .map { it.syncIntervalHours }
                .distinctUntilChanged()
                .collect { syncIntervalHours ->
                    SyncCalendarWorker.enqueuePeriodicSync(
                        this@MainActivity,
                        syncIntervalHours.toLong()
                    )
                }
        }

        // Reactively handle search interval and service status changes
        lifecycleScope.launch {
            combine(
                autoDownloadRepo.preferencesFlow.map { it.searchIntervalHours },
                torrentServiceHelper.isInstalled
            ) { searchIntervalHours, isInstalled ->
                Pair(searchIntervalHours, isInstalled)
            }
                .distinctUntilChanged()
                .collect { (searchIntervalHours, isInstalled) ->
                    if (isInstalled) {
                        AutoDownloadWorker.enqueuePeriodicSearch(
                            this@MainActivity,
                            searchIntervalHours.toLong()
                        )
                    } else {
                        AutoDownloadWorker.cancelPeriodicSearch(this@MainActivity)
                    }
                }
        }

        downloadRepository.refreshDownloadSubdirectories()
        handleOAuthIntent(intent)

        lifecycleScope.launch(Dispatchers.Default) {
            handleNotificationNavigation(intent)
            notificationManager.restoreActiveNotifications()
        }

        setContent {
            MyApplicationTheme {
                SimklCalendarApp(
                    viewModel = viewModel,
                    pendingReleaseDetailKey = pendingReleaseDetailKey,
                    pendingMediaItemRoute = pendingMediaItemRoute,
                    onClearPendingReleaseDetailKey = ::clearPendingReleaseDetailKey,
                    onClearPendingMediaItemRoute = ::clearPendingMediaItemRoute,
                    onLaunchAuthTab = { authUrl ->
                        launchAuthTab(authUrl, "simklcalendar")
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        torrentServiceHelper.refreshServiceStatus()
        downloadRepository.refreshDownloadSubdirectories()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
        lifecycleScope.launch(Dispatchers.Default) {
            handleNotificationNavigation(intent)
        }
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val uri: Uri? = intent?.data
        if (uri != null) {
            handleOAuthCallback(uri)
        }
    }

    internal suspend fun handleNotificationNavigation(intent: Intent?) {
        val uri = intent?.data
        if (uri == null || uri.scheme != "simklcalendar") return

        if (uri.host == "release_detail") {
            val itemKey = intent.getStringExtra(EXTRA_ITEM_KEY)
            if (!itemKey.isNullOrEmpty()) {
                setPendingReleaseDetailKey(itemKey)
                notificationManager.dismissNotification(itemKey)
            }
        } else if (uri.host == "media_item") {
            val simklId = uri.pathSegments.getOrNull(0)
            val mediaType = uri.pathSegments.getOrNull(1)
            if (!simklId.isNullOrEmpty() && !mediaType.isNullOrEmpty()) {
                val route = "media_item/$simklId/$mediaType"
                setPendingMediaItemRoute(route)
                simklId.toIntOrNull()?.let { id ->
                    notificationManager.dismissTrendingNotification(id)
                }
            }
        }
    }

    internal fun handleOAuthCallback(uri: Uri) {
        if (uri.scheme != "simklcalendar" || uri.host != "auth") return

        val iss = uri.getQueryParameter("iss")
        if (iss != null && iss != "https://simkl.com" && iss != "https://simkl.com/") {
            Toast.makeText(this, "Authorization response did not come from Simkl", Toast.LENGTH_LONG).show()
            return
        }
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        if (!code.isNullOrEmpty() && !state.isNullOrEmpty()) {
            oAuthEventHub.onOAuthCallbackReceived(
                code = code,
                state = state,
                redirectUri = "simklcalendar://auth"
            )
        }
    }

    companion object {
        const val EXTRA_ITEM_KEY = "extra_item_key"
    }
}

internal fun NavController.popBackStackSafely(): Boolean {
    val currentEntry = currentBackStackEntry ?: return false
    if (currentEntry.lifecycle.currentState != Lifecycle.State.RESUMED) {
        return false
    }
    if (previousBackStackEntry == null) {
        return false
    }
    return popBackStack()
}

@Composable
fun SimklCalendarApp(
    viewModel: CalendarViewModel,
    pendingReleaseDetailKey: StateFlow<String?>,
    pendingMediaItemRoute: StateFlow<String?>,
    onClearPendingReleaseDetailKey: () -> Unit,
    onClearPendingMediaItemRoute: () -> Unit,
    onLaunchAuthTab: (url: String) -> Unit = {}
) {
    val navController = rememberNavController()
    val authState by viewModel.authState.collectAsState()
    val pendingReleaseDetailKey by pendingReleaseDetailKey.collectAsState()
    val pendingMediaItemRoute by pendingMediaItemRoute.collectAsState()
    val context = LocalContext.current

    // Don't render navigation until we know if the user is logged in or not
    if (!authState.isReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PurpleLight)
        }
        return
    }

    // Automatically navigate to detail when an item key is provided via notification
    LaunchedEffect(pendingReleaseDetailKey, authState.token) {
        val targetKey = pendingReleaseDetailKey
        val token = authState.token
        if (targetKey != null && token != null) {
            val encodedKey = URLEncoder.encode(targetKey, "UTF-8")
            navController.navigate("release_detail/$encodedKey") {
                launchSingleTop = true
            }
            onClearPendingReleaseDetailKey()
        }
    }

    // Automatically navigate to media item detail when a media item route is provided via notification
    LaunchedEffect(pendingMediaItemRoute, authState.token) {
        val targetRoute = pendingMediaItemRoute
        val token = authState.token
        if (targetRoute != null && token != null) {
            navController.navigate(targetRoute)
            onClearPendingMediaItemRoute()
        }
    }

    // Request notification permission on Android 13+ (API 33+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { /* Granted or dismissed */ }

        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Determine initial active route exactly once when auth state is ready to avoid graph resets.
    val startDestination = remember {
        if (authState.token == null) "login" else "calendar"
    }

    SharedTransitionLayout {
        val slideInFromRight = slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth / 6 },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing))

        val slideOutToLeft = slideOutHorizontally(
            targetOffsetX = { fullWidth -> -fullWidth / 6 },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing))

        val slideInFromLeft = slideInHorizontally(
            initialOffsetX = { fullWidth -> -fullWidth / 6 },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing))

        val slideOutToRight = slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth / 6 },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing))

        NavHost(
            navController = navController,
            startDestination = startDestination,
            predictivePopEnterTransition = {
                val isSlideScreen =
                    initialState.destination.route?.startsWith("settings") == true ||
                    initialState.destination.route?.startsWith("log_viewer") == true ||
                    (initialState.destination.route?.startsWith("trending") == true && targetState.destination.route?.startsWith("media_item") != true) ||
                    (targetState.destination.route?.startsWith("trending") == true && initialState.destination.route?.startsWith("media_item") != true)
                val isDetailToDetail =
                    (initialState.destination.route?.startsWith("release_detail") == true &&
                     targetState.destination.route?.startsWith("media_item") == true) ||
                    (initialState.destination.route?.startsWith("media_item") == true &&
                     targetState.destination.route?.startsWith("release_detail") == true) ||
                    (initialState.destination.route?.startsWith("media_item") == true &&
                     targetState.destination.route?.startsWith("media_item") == true)
                val isMediaItemToSearch =
                    (initialState.destination.route?.startsWith("media_item") == true &&
                     initialState.arguments?.getBoolean("usingSharedTransition") == false &&
                     targetState.destination.route?.startsWith("calendar") == true)

                if (isSlideScreen || isDetailToDetail || isMediaItemToSearch) slideInFromLeft else EnterTransition.None
            },
            predictivePopExitTransition = {
                val isSlideScreen =
                    initialState.destination.route?.startsWith("settings") == true ||
                    initialState.destination.route?.startsWith("log_viewer") == true ||
                    (initialState.destination.route?.startsWith("trending") == true && targetState.destination.route?.startsWith("media_item") != true) ||
                    (targetState.destination.route?.startsWith("trending") == true && initialState.destination.route?.startsWith("media_item") != true)
                val isDetailToDetail =
                    (initialState.destination.route?.startsWith("release_detail") == true &&
                     targetState.destination.route?.startsWith("media_item") == true) ||
                    (initialState.destination.route?.startsWith("media_item") == true &&
                     targetState.destination.route?.startsWith("release_detail") == true) ||
                    (initialState.destination.route?.startsWith("media_item") == true &&
                     targetState.destination.route?.startsWith("media_item") == true)
                val isMediaItemToSearch =
                    (initialState.destination.route?.startsWith("media_item") == true &&
                     initialState.arguments?.getBoolean("usingSharedTransition") == false &&
                     targetState.destination.route?.startsWith("calendar") == true)

                if (isSlideScreen || isDetailToDetail || isMediaItemToSearch) slideOutToRight else ExitTransition.None
            },
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Authentication Login (OAuth via AuthTab)
            composable(
                route = "login",
                exitTransition = { slideOutToLeft },
            ) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate("calendar") {
                            popUpTo("login") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onLaunchAuthTab = onLaunchAuthTab
                )
            }

            // 2. Calendar Schedule Dashboard
            composable(
                route = "calendar",
                enterTransition = { slideInFromRight },
                exitTransition = {
                    val targetRoute = targetState.destination.route ?: ""
                    val arguments = targetState.arguments
                    if (targetRoute.startsWith("release_detail") || arguments?.getBoolean("usingSharedTransition") == true) {
                        ExitTransition.None
                    } else {
                        slideOutToLeft
                    }
                },
                popEnterTransition = {
                    val initialRoute = initialState.destination.route ?: ""
                    val arguments = initialState.arguments
                    if (initialRoute.startsWith("release_detail") || arguments?.getBoolean("usingSharedTransition") == true) {
                        EnterTransition.None
                    } else {
                        slideInFromLeft
                    }
                },
            ) {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {
                        navController.navigate("settings") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToTrending = {
                        navController.navigate("trending") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToReleaseDetail = { item ->
                        viewModel.setSelectedCalendarItem(item)
                        val encodedKey = URLEncoder.encode(item.primaryKey, "UTF-8")
                        navController.navigate("release_detail/$encodedKey") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToWatchlistItemDetail = { tableItem ->
                        navController.navigate("media_item/${tableItem.watchlistItem.simklId}/${tableItem.watchlistItem.type.key}?usingSharedTransition=true")
                    },
                    onNavigateToDetail = { simklId, mediaType ->
                        navController.navigate("media_item/$simklId/${mediaType.key}")
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable
                )
            }

            // 6. Trending Screen
            composable(
                route = "trending",
                enterTransition = { slideInFromRight },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { slideOutToRight },
            ) {
                val trendingViewModel: TrendingViewModel = hiltViewModel()
                TrendingScreen(
                    viewModel = trendingViewModel,
                    onNavigateBack = { navController.popBackStackSafely() },
                    onNavigateToDetail = { simklId, mediaType ->
                        navController.navigate("media_item/$simklId/${mediaType.key}")
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable
                )
            }

            // 7. Media Item Detail Screen
            composable(
                route = "media_item/{simklId}/{mediaType}?usingSharedTransition={usingSharedTransition}",
                arguments = listOf(
                    navArgument("simklId") { type = NavType.IntType },
                    navArgument("mediaType") { type = NavType.StringType },
                    navArgument("usingSharedTransition") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "simklcalendar://media_item/{simklId}/{mediaType}" }
                ),
                enterTransition = { slideInFromRight },
                exitTransition = { slideOutToLeft },
                popEnterTransition = { slideInFromLeft },
                popExitTransition = { slideOutToRight },
            ) { backStackEntry ->
                val simklId = backStackEntry.arguments?.getInt("simklId") ?: 0
                MediaItemDetailScreen(
                    simklId = simklId,
                    onNavigateBack = { navController.popBackStackSafely() },
                    onNavigateToDetail = { id, mType ->
                        navController.navigate("media_item/$id/${mType.key}")
                    },
                    onNavigateToReleaseDetail = { item ->
                        viewModel.setSelectedCalendarItem(item)
                        val encodedKey = URLEncoder.encode(item.primaryKey, "UTF-8")
                        navController.navigate("release_detail/$encodedKey") {
                            launchSingleTop = true
                        }
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable
                )
            }

            // 3. Settings configuration screen
            composable(
                route = "settings",
                enterTransition = { slideInFromRight },
                exitTransition = { slideOutToLeft },
                popEnterTransition = { slideInFromLeft },
                popExitTransition = { slideOutToRight },
            ) {
                SettingsScreen(
                    initialCategory = null,
                    onNavigateBack = {
                        navController.popBackStackSafely()
                    },
                    onNavigateToCategory = { category ->
                        navController.navigate("settings/${category.name}") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToLogViewer = {
                        navController.navigate("log_viewer") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToLogin = {
                        navController.navigate("login") {
                            popUpTo("calendar") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            // 3.1. Sub-settings category screen
            composable(
                route = "settings/{categoryName}",
                arguments = listOf(navArgument("categoryName") { type = NavType.StringType }),
                enterTransition = { slideInFromRight },
                exitTransition = { slideOutToLeft },
                popEnterTransition = { slideInFromLeft },
                popExitTransition = { slideOutToRight },
            ) { backStackEntry ->
                val categoryName = backStackEntry.arguments?.getString("categoryName")
                val category = try {
                    categoryName?.let { SettingsCategory.valueOf(it) }
                } catch (_: Exception) {
                    null
                }
                SettingsScreen(
                    initialCategory = category,
                    onNavigateBack = {
                        navController.popBackStackSafely()
                    },
                    onNavigateToCategory = {},
                    onNavigateToLogViewer = {
                        navController.navigate("log_viewer") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToLogin = {
                        navController.navigate("login") {
                            popUpTo("calendar") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            // 3.1. Log Viewer screen
            composable(
                route = "log_viewer",
                enterTransition = { slideInFromRight },
                exitTransition = { slideOutToLeft },
                popEnterTransition = { slideInFromLeft },
                popExitTransition = { slideOutToRight },
            ) {
                LogViewerScreen {
                    navController.popBackStackSafely()
                }
            }

            // 4. Release Detail screen
            composable(
                route = "release_detail/{itemKey}",
                arguments = listOf(navArgument("itemKey") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "simklcalendar://release_detail/{itemKey}" }),
                enterTransition = { slideInFromRight },
                exitTransition = { slideOutToLeft },
                popEnterTransition = { slideInFromLeft },
                popExitTransition = { slideOutToRight },
            ) { backStackEntry ->
                val rawKey = backStackEntry.arguments?.getString("itemKey") ?: ""
                val itemKey = try {
                    URLDecoder.decode(rawKey, "UTF-8")
                } catch (_: Exception) {
                    rawKey
                }
                ReleaseDetailScreen(
                    viewModel = viewModel,
                    itemKey = itemKey,
                    onNavigateBack = {
                        navController.popBackStackSafely()
                    },
                    onNavigateToWatchlistItem = { simklId, mediaType ->
                        navController.navigate("media_item/$simklId/${mediaType.key}")
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable
                )
            }
        }
    }
}
