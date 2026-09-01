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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.felixbrucker.simklcalendar.ui.screens.CalendarScreen
import com.felixbrucker.simklcalendar.ui.screens.LoginScreen
import com.felixbrucker.simklcalendar.ui.screens.ReleaseDetailScreen
import com.felixbrucker.simklcalendar.ui.screens.SeriesDetailScreen
import com.felixbrucker.simklcalendar.ui.screens.SettingsScreen
import com.felixbrucker.simklcalendar.ui.theme.MyApplicationTheme
import com.felixbrucker.simklcalendar.ui.viewmodel.CalendarViewModel

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
        val uri = Uri.parse(url)
        val packageName = CustomTabsClient.getPackageName(this, null)

        // Open the Authorization URI in an Auth Tab if supported by the default browser.
        if (packageName != null && CustomTabsClient.isAuthTabSupported(this, packageName)) {
            val authTabIntent = AuthTabIntent.Builder().build()
            authTabIntent.launch(authTabLauncher, uri, redirectScheme)
        } else {
            // Fall back to a Custom Tab.
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(this, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationReceiver.createNotificationChannel(this)
        val syncPrefs = getSharedPreferences("notification_prefs", android.content.Context.MODE_PRIVATE)
        val syncIntervalHours = syncPrefs.getInt("sync_interval_hours", 12).toLong()
        SyncCalendarWorker.enqueuePeriodicSync(this, syncIntervalHours)

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
                        java.net.URLDecoder.decode(segment, "UTF-8")
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
    val userToken by viewModel.userToken.collectAsState()
    val pendingDetailKey by viewModel.pendingDetailKey.collectAsState()
    val context = LocalContext.current

    // Automatically navigate to detail when an item key is provided via notification or deep link
    LaunchedEffect(pendingDetailKey, userToken) {
        val targetKey = pendingDetailKey
        if (targetKey != null && userToken != null) {
            val encodedKey = java.net.URLEncoder.encode(targetKey, "UTF-8")
            navController.navigate("detail/$encodedKey") {
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

    // Determine initial active route depending on user authentication status
    val startDestination = if (userToken == null) "login" else "calendar"

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
                        }
                    }
                )
            }

            // 2. Calendar Schedule Dashboard
            composable("calendar") {
                CalendarScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    },
                    onNavigateToShowDetail = { itemKey ->
                        val encodedKey = java.net.URLEncoder.encode(itemKey, "UTF-8")
                        navController.navigate("detail/$encodedKey")
                    },
                    onNavigateToSeriesDetail = { simklId ->
                        navController.navigate("series_detail/$simklId")
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

            // 4. Show Details screen
            composable(
                route = "detail/{itemKey}",
                arguments = listOf(navArgument("itemKey") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "simklcalendar://detail/{itemKey}" })
            ) { backStackEntry ->
                val rawKey = backStackEntry.arguments?.getString("itemKey") ?: ""
                val itemKey = try {
                    java.net.URLDecoder.decode(rawKey, "UTF-8")
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

            // 5. Watchlist Series Details screen
            composable(
                route = "series_detail/{simklId}",
                arguments = listOf(navArgument("simklId") { type = NavType.IntType })
            ) { backStackEntry ->
                val simklId = backStackEntry.arguments?.getInt("simklId") ?: 0
                SeriesDetailScreen(
                    viewModel = viewModel,
                    simklId = simklId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEpisode = { itemKey ->
                        val encodedKey = java.net.URLEncoder.encode(itemKey, "UTF-8")
                        navController.navigate("detail/$encodedKey")
                    }
                )
            }
        }
    }
