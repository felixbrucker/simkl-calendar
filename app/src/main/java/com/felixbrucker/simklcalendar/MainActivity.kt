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
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.felixbrucker.simklcalendar.worker.SyncCalendarWorker
import com.felixbrucker.simklcalendar.worker.AutoDownloadWorker
import com.felixbrucker.simklcalendar.ui.screens.MainScreen
import com.felixbrucker.simklcalendar.ui.screens.LoginScreen
import com.felixbrucker.simklcalendar.ui.screens.ReleaseDetailScreen
import com.felixbrucker.simklcalendar.ui.screens.WatchlistItemDetailScreen
import com.felixbrucker.simklcalendar.ui.screens.SettingsScreen
import com.felixbrucker.simklcalendar.ui.screens.LogViewerScreen
import com.felixbrucker.simklcalendar.ui.theme.MyApplicationTheme
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.felixbrucker.simklcalendar.receiver.notification.NotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.net.URLDecoder
import dagger.hilt.android.AndroidEntryPoint

import com.felixbrucker.simklcalendar.data.preferences.AppSettingsRepository
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import com.felixbrucker.simklcalendar.data.preferences.UiRepository
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var repository: SimklRepository

    @Inject
    lateinit var uiRepo: UiRepository

    @Inject
    lateinit var appSettingsRepo: AppSettingsRepository

    @Inject
    lateinit var autoDownloadRepo: AutoDownloadRepository

    @Inject
    lateinit var torrentServiceHelper: TorrentServiceHelper

    @Inject
    lateinit var notificationManager: NotificationManager

    // Modern AuthTab ActivityResultLauncher
    private val authTabLauncher = AuthTabIntent.registerActivityResultLauncher(this) { result ->
        handleAuthTabResult(result)
    }

    private fun handleAuthTabResult(result: AuthTabIntent.AuthResult) {
        val resultUri = result.resultUri
        if (result.resultCode == AuthTabIntent.RESULT_OK && resultUri != null) {
            handleOAuthUri(resultUri)
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

        // Reactively handle search interval changes
        lifecycleScope.launch {
            autoDownloadRepo.preferencesFlow
                .map { it.searchIntervalHours }
                .distinctUntilChanged()
                .collect { searchIntervalHours ->
                    if (torrentServiceHelper.isServiceInstalled()) {
                        AutoDownloadWorker.enqueuePeriodicSearch(
                            this@MainActivity,
                            searchIntervalHours.toLong()
                        )
                    }
                }
        }

        handleOAuthIntent(intent)

        lifecycleScope.launch(Dispatchers.Default) {
            handleNotificationNavigation(intent)
            notificationManager.restoreActiveNotifications()
        }

        setContent {
            MyApplicationTheme {
                SimklCalendarApp(
                    repository = repository,
                    uiRepo = uiRepo,
                    onLaunchAuthTab = { authUrl ->
                        launchAuthTab(authUrl, "simklcalendar")
                    }
                )
            }
        }
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
            handleOAuthUri(uri)
        }
    }

    private suspend fun handleNotificationNavigation(intent: Intent?) {
        if (intent == null) return
        val itemKey = intent.getStringExtra(EXTRA_ITEM_KEY)
            ?: if (intent.data?.scheme == "simklcalendar" && intent.data?.host == "detail") {
                intent.data?.lastPathSegment?.let { segment ->
                    try {
                        URLDecoder.decode(segment, "UTF-8")
                    } catch (_: Exception) {
                        segment
                    }
                }
            } else null

        if (!itemKey.isNullOrEmpty()) {
            notificationManager.removeActiveNotification(itemKey)
            uiRepo.setPendingDetailKey(itemKey)
        }
    }

    private fun handleOAuthUri(uri: Uri) {
        if (uri.scheme == "simklcalendar") {
            val iss = uri.getQueryParameter("iss")
            if (iss != null && iss != "https://simkl.com" && iss != "https://simkl.com/") {
                Toast.makeText(this, "Authorization response did not come from Simkl", Toast.LENGTH_LONG).show()
                return
            }
            val code = uri.getQueryParameter("code")
            val state = uri.getQueryParameter("state")
            if (!code.isNullOrEmpty()) {
                lifecycleScope.launch {
                    val success = repository.exchangeOAuthCode(
                        code = code,
                        state = state,
                        redirectUri = "simklcalendar://auth"
                    )
                    if (success) {
                        Toast.makeText(this@MainActivity, "Successfully authenticated with Simkl", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Authentication failed. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_ITEM_KEY = "extra_item_key"
    }
}

@Composable
fun SimklCalendarApp(
    repository: SimklRepository,
    uiRepo: UiRepository,
    onLaunchAuthTab: (url: String) -> Unit = {}
) {
    val navController = rememberNavController()
    val userToken by repository.activeUserToken.collectAsState(initial = null)
    val pendingDetailKey by uiRepo.pendingDetailKey.collectAsState()
    val context = LocalContext.current

    // Automatically navigate to detail when an item key is provided via notification or deep link
    LaunchedEffect(pendingDetailKey, userToken) {
        val targetKey = pendingDetailKey
        val token = userToken
        if (targetKey != null && token != null) {
            val encodedKey = URLEncoder.encode(targetKey, "UTF-8")
            navController.navigate("release_detail/$encodedKey") {
                launchSingleTop = true
            }
            uiRepo.clearPendingDetailKey()
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
    // We key by the 'is logged in' state to satisfy the lint while keeping the destination stable.
    val startDestination = remember(userToken == null) {
        if (userToken == null) "login" else "calendar"
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize()
    ) {
            // 1. Authentication Login (OAuth via AuthTab)
            composable("login") {
                LoginScreen(
                    onLaunchAuthTab = onLaunchAuthTab,
                    onLoginSuccess = {
                        navController.navigate("calendar") {
                            popUpTo("login") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            // 2. Calendar Schedule Dashboard
            composable("calendar") {
                MainScreen(
                    viewModel = hiltViewModel(),
                    onNavigateToSettings = {
                        navController.navigate("settings") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToReleaseDetail = { itemKey ->
                        val encodedKey = URLEncoder.encode(itemKey, "UTF-8")
                        navController.navigate("release_detail/$encodedKey") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToWatchlistItemDetail = { simklId ->
                        navController.navigate("watchlist_item_detail/$simklId") {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // 3. Settings configuration screen
            composable("settings") {
                SettingsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                ) {
                    navController.navigate("log_viewer") {
                        launchSingleTop = true
                    }
                }
            }

            // 3.1. Log Viewer screen
            composable("log_viewer") {
                LogViewerScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            // 4. Release Detail screen
            composable(
                route = "release_detail/{itemKey}",
                arguments = listOf(navArgument("itemKey") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "simklcalendar://release_detail/{itemKey}" })
            ) { backStackEntry ->
                val rawKey = backStackEntry.arguments?.getString("itemKey") ?: ""
                val itemKey = try {
                    URLDecoder.decode(rawKey, "UTF-8")
                } catch (_: Exception) {
                    rawKey
                }
                ReleaseDetailScreen(
                    viewModel = hiltViewModel(),
                    itemKey = itemKey,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToWatchlistItem = { simklId ->
                        navController.navigate("watchlist_item_detail/$simklId")
                    }
                )
            }

            // 5. Watchlist Item Details screen
            composable(
                route = "watchlist_item_detail/{simklId}",
                arguments = listOf(navArgument("simklId") { type = NavType.IntType })
            ) { backStackEntry ->
                val simklId = backStackEntry.arguments?.getInt("simklId") ?: 0
                WatchlistItemDetailScreen(
                    viewModel = hiltViewModel(),
                    simklId = simklId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEpisode = { itemKey ->
                        val encodedKey = URLEncoder.encode(itemKey, "UTF-8")
                        navController.navigate("release_detail/$encodedKey")
                    }
                )
            }
        }
    }
