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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.felixbrucker.simklcalendar.receiver.NotificationReceiver
import com.felixbrucker.simklcalendar.worker.SyncCalendarWorker
import com.felixbrucker.simklcalendar.worker.AutoDownloadWorker
import com.felixbrucker.simklcalendar.ui.screens.CalendarScreen
import com.felixbrucker.simklcalendar.ui.screens.LoginScreen
import com.felixbrucker.simklcalendar.ui.screens.ReleaseDetailScreen
import com.felixbrucker.simklcalendar.ui.screens.WatchlistItemDetailScreen
import com.felixbrucker.simklcalendar.ui.screens.SettingsScreen
import com.felixbrucker.simklcalendar.ui.theme.MyApplicationTheme
import com.felixbrucker.simklcalendar.ui.viewmodel.CalendarViewModel
import androidx.core.net.toUri
import java.net.URLEncoder
import java.net.URLDecoder

class MainActivity : ComponentActivity() {
    private val viewModel: CalendarViewModel by viewModels()

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
        NotificationReceiver.createNotificationChannel(this)
        val syncPrefs = getSharedPreferences("notification_prefs", MODE_PRIVATE)
        val syncIntervalHours = syncPrefs.getInt("sync_interval_hours", 12).toLong()
        SyncCalendarWorker.enqueuePeriodicSync(this, syncIntervalHours)

        val searchIntervalHours = syncPrefs.getInt("search_interval_hours", 12).toLong()
        if (viewModel.isTorrentServiceInstalled()) {
            AutoDownloadWorker.enqueuePeriodicSearch(this, searchIntervalHours)
        }

        handleOAuthIntent(intent)
        handleNotificationNavigation(intent)

        setContent {
            MyApplicationTheme {
                SimklCalendarApp(
                    viewModel = viewModel,
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
        handleNotificationNavigation(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val uri: Uri? = intent?.data
        if (uri != null) {
            handleOAuthUri(uri)
        }
    }

    private fun handleNotificationNavigation(intent: Intent?) {
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
            viewModel.setPendingDetailKey(itemKey)
        }
    }

    private fun handleOAuthUri(uri: Uri) {
        if (uri.scheme == "simklcalendar") {
            val code = uri.getQueryParameter("code")
            val state = uri.getQueryParameter("state")
            if (!code.isNullOrEmpty()) {
                viewModel.exchangeOAuthCode(
                    code = code,
                    state = state,
                    redirectUri = "simklcalendar://auth",
                    onSuccess = {
                        Toast.makeText(this, "Successfully authenticated with Simkl", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = {
                        Toast.makeText(this, "Authentication failed. Please try again.", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_ITEM_KEY = "extra_item_key"
    }
}

@Composable
fun SimklCalendarApp(
    viewModel: CalendarViewModel,
    onLaunchAuthTab: (url: String) -> Unit = {}
) {
    val navController = rememberNavController()
    val authState by viewModel.authState.collectAsState()
    val pendingDetailKey by viewModel.pendingDetailKey.collectAsState()
    val context = LocalContext.current

    // Don't render navigation until we know if the user is logged in or not
    if (!authState.isReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFD0BCFF))
        }
        return
    }

    // Automatically navigate to detail when an item key is provided via notification or deep link
    LaunchedEffect(pendingDetailKey, authState.token) {
        val targetKey = pendingDetailKey
        val token = authState.token
        if (targetKey != null && token != null) {
            val encodedKey = URLEncoder.encode(targetKey, "UTF-8")
            navController.navigate("release_detail/$encodedKey") {
                launchSingleTop = true
            }
            viewModel.clearPendingDetailKey()
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
    val startDestination = remember(authState.token == null) {
        if (authState.token == null) "login" else "calendar"
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize()
    ) {
            // 1. Authentication Login (OAuth via AuthTab)
            composable("login") {
                LoginScreen(
                    viewModel = viewModel,
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
                CalendarScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {
                        navController.navigate("settings") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToShowDetail = { itemKey ->
                        val encodedKey = URLEncoder.encode(itemKey, "UTF-8")
                        navController.navigate("release_detail/$encodedKey") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSeriesDetail = { simklId ->
                        navController.navigate("watchlist_item_detail/$simklId") {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // 3. Settings configuration screen
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
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
                    viewModel = viewModel,
                    itemKey = itemKey,
                    onNavigateBack = {
                        navController.popBackStack()
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
                    viewModel = viewModel,
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
