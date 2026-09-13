package com.felixbrucker.simklcalendar.ui.composable

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun CustomSearchLinksCard(
    allSearchLinks: List<CustomSearchLink>,
    title: String,
    titleRomaji: String?,
    itemType: MediaType,
    season: Int?,
    episode: Int?,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    context: Context,
    modifier: Modifier = Modifier,
) {
    // Matching Custom Search Links
    val matchingSearchLinks = remember(allSearchLinks, itemType) {
        allSearchLinks.filter { it.associatedTypes.contains(itemType) }
    }

    if (matchingSearchLinks.isNotEmpty()) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
            border = BorderStroke(1.dp, Color(0xFF49454F)),
            modifier = modifier,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFFD0BCFF),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Custom Search Links",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6E1E5),
                        fontSize = 15.sp
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    matchingSearchLinks.forEach { link ->
                        val resolvedUrl = remember(link, title, titleRomaji, season, episode) {
                            link.buildUrl(
                                title = title,
                                titleRomaji = titleRomaji,
                                type = itemType,
                                season = season,
                                episode = episode,
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1C1B1F),
                            border = BorderStroke(1.dp, Color(0xFF49454F)),
                            modifier = Modifier
                                .clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW,
                                            resolvedUrl.toUri())
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Unable to open link: ${e.message}")
                                        }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF2B2930),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        AsyncImage(
                                            model = link.getFaviconUrl(),
                                            contentDescription = link.name,
                                            modifier = Modifier.size(18.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    Text(
                                        text = link.name,
                                        color = Color(0xFFE6E1E5),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        lineHeight = 15.sp,
                                        style = TextStyle(
                                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                                        )
                                    )
                                    if (!link.subtitle.isNullOrBlank()) {
                                        Text(
                                            text = link.subtitle,
                                            color = Color(0xFFCAC4D0),
                                            fontSize = 11.sp,
                                            lineHeight = 13.sp,
                                            maxLines = 1,
                                            style = TextStyle(
                                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                                            )
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open ${link.name}",
                                    tint = Color(0xFFD0BCFF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
