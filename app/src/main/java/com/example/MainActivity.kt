package com.example

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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
import com.example.receiver.NotificationReceiver
import com.example.worker.SyncCalendarWorker
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ShowDetailScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CalendarViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: CalendarViewModel by viewModels()

    // Modern AuthTab ActivityResultLauncher
    private val authTabLauncher = AuthTabIntent.registerActivityResultLauncher(this) { result ->
        handleAuthTabResult(result)
    }

    private fun handleAuthTabResult(result: AuthTabIntent.AuthResult) {
        val resultUri = result.resultUri
        when (result.resultCode) {
            AuthTabIntent.RESULT_OK -> {
                if (resultUri != null) {
                    handleOAuthUri(resultUri)
                } else {
                    Toast.makeText(this, "Authentication succeeded but no redirect data received.", Toast.LENGTH_LONG).show()
                }
            }
            AuthTabIntent.RESULT_CANCELED -> {
                Toast.makeText(this, "Authentication cancelled", Toast.LENGTH_SHORT).show()
            }
            AuthTabIntent.RESULT_VERIFICATION_FAILED -> {
                Toast.makeText(this, "Authentication verification failed.", Toast.LENGTH_LONG).show()
            }
            else -> {
                if (resultUri != null) {
                    handleOAuthUri(resultUri)
                }
            }
        }
    }

    fun launchAuthTab(url: String, redirectScheme: String = "simklcalendar") {
        try {
            val authTabIntent = AuthTabIntent.Builder().build()
            authTabIntent.launch(authTabLauncher, Uri.parse(url), redirectScheme)
        } catch (e: Exception) {
            // Graceful fallback to standard intent if AuthTab launch encounters unexpected environment issues
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(fallbackIntent)
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
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val uri: Uri? = intent?.data
        if (uri != null) {
            handleOAuthUri(uri)
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
                        Toast.makeText(this, "Successfully authenticated with Simkl!", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = {
                        Toast.makeText(this, "Authentication failed. Please try again.", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
}

@Composable
fun SimklCalendarApp(
    viewModel: CalendarViewModel,
    onLaunchAuthTab: (url: String) -> Unit = {}
) {
    val navController = rememberNavController()
    val userToken by viewModel.userToken.collectAsState()
    val context = LocalContext.current

    // Request notification permission on Android 13+ (API 33+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { /* Granted or dismissed */ }

        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Determine initial active route depending on user authentication status
    val startDestination = if (userToken == null) "login" else "calendar"

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
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
                arguments = listOf(navArgument("itemKey") { type = NavType.StringType })
            ) { backStackEntry ->
                val rawKey = backStackEntry.arguments?.getString("itemKey") ?: ""
                val itemKey = try {
                    java.net.URLDecoder.decode(rawKey, "UTF-8")
                } catch (_: Exception) {
                    rawKey
                }
                ShowDetailScreen(
                    viewModel = viewModel,
                    itemKey = itemKey,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

