package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.receiver.NotificationReceiver
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ShowDetailScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CalendarViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: CalendarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationReceiver.createNotificationChannel(this)

        handleOAuthIntent(intent)

        setContent {
            MyApplicationTheme {
                SimklCalendarApp(viewModel = viewModel)
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
        if (uri != null && uri.scheme == "simklcalendar") {
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
fun SimklCalendarApp(viewModel: CalendarViewModel) {
    val navController = rememberNavController()
    val userToken by viewModel.userToken.collectAsState()

    // Determine initial active route depending on user authentication status
    val startDestination = if (userToken == null) "login" else "calendar"

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            // 1. Authentication Login (OAuth + Demo Fallbacks)
            composable("login") {
                LoginScreen(
                    viewModel = viewModel,
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
                    onNavigateToShowDetail = { showId ->
                        navController.navigate("detail/$showId")
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
                route = "detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { backStackEntry ->
                val showId = backStackEntry.arguments?.getInt("id") ?: 0
                ShowDetailScreen(
                    viewModel = viewModel,
                    showId = showId,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

