package com.felixbrucker.simklcalendar.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felixbrucker.simklcalendar.R
import com.felixbrucker.simklcalendar.ui.viewmodel.CalendarViewModel
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: CalendarViewModel,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    onLaunchAuthTab: ((url: String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val token by viewModel.userToken.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val showAuthV2UpgradeHint by viewModel.showAuthV2UpgradeHint.collectAsState()

    var oauthError by remember { mutableStateOf<String?>(null) }

    val isConfigured = viewModel.repository.isRealApiConfigured()

    // Redirect to calendar dashboard if already logged in
    LaunchedEffect(token) {
        if (token != null) {
            onLoginSuccess()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF2B2930),
                            Color(0xFF1C1B1F)
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // App Logo
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF313033))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "Simkl Calendar Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "SIMKL CALENDAR",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFE6E1E5),
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Track your favorite TV Shows, Anime & Movies",
                    fontSize = 15.sp,
                    color = Color(0xFFCAC4D0),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )

                if (showAuthV2UpgradeHint) {
                    AuthV2UpgradeCard()
                }

                // OAuth Options Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                    border = BorderStroke(1.dp, Color(0xFF49454F)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Authenticate with Simkl",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (isConfigured) {
                            Button(
                                onClick = {
                                    oauthError = null
                                    val authUrl = viewModel.createAuthorizationUrl("simklcalendar://auth")
                                    if (authUrl != null) {
                                        if (onLaunchAuthTab != null) {
                                            onLaunchAuthTab(authUrl)
                                        } else {
                                            val intent = Intent(Intent.ACTION_VIEW, authUrl.toUri())
                                            context.startActivity(intent)
                                        }
                                    } else {
                                        oauthError = "Could not initialize PKCE OAuth flow"
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD0BCFF),
                                    contentColor = Color(0xFF381E72)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isSyncing
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text("Log in with Simkl Account", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            }
                        } else {
                            // Info alert when client credentials are placeholder
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF313033), RoundedCornerShape(8.dp))
                                    .border(BorderStroke(1.dp, Color(0xFF49454F)), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFF2B8B5))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Secrets Not Configured",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFFF2B8B5)
                                    )
                                    Text(
                                        text = "SIMKL_CLIENT_ID is missing from secrets panel.",
                                        fontSize = 12.sp,
                                        color = Color(0xFFCAC4D0)
                                    )
                                }
                            }
                        }

                        oauthError?.let { err ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Authentication Error: $err",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (isSyncing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFD0BCFF))
                }
            }
        }
    }
}

@Composable
private fun AuthV2UpgradeCard() {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF381E72)),
        border = BorderStroke(1.dp, Color(0xFFD0BCFF)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = Color(0xFFD0BCFF),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Auth V2 Upgrade Required",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Simkl has upgraded to Auth V2 authentication. Please log in again to reconnect your account. All your saved watchlist items, custom settings, and watched history have been preserved.",
                    fontSize = 13.sp,
                    color = Color(0xFFE6E1E5)
                )
            }
        }
    }
}
