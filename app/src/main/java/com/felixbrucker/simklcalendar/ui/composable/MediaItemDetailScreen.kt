package com.felixbrucker.simklcalendar.ui.composable

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.felixbrucker.simklcalendar.data.database.CachedMediaItem
import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.database.CachedSimilarMediaItem
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.WatchlistStatus
import com.felixbrucker.simklcalendar.data.network.SimklEpisodeResponse
import com.felixbrucker.simklcalendar.data.util.PosterSize
import com.felixbrucker.simklcalendar.extensions.toPosterUrl
import com.felixbrucker.simklcalendar.ui.theme.ContainerBlue
import com.felixbrucker.simklcalendar.ui.theme.ErrorPink
import com.felixbrucker.simklcalendar.ui.theme.GreenSuccess
import com.felixbrucker.simklcalendar.ui.theme.OutlineVariant
import com.felixbrucker.simklcalendar.ui.theme.PurpleLight
import com.felixbrucker.simklcalendar.ui.theme.PurplePrimaryDark
import com.felixbrucker.simklcalendar.ui.theme.SurfaceContainer
import com.felixbrucker.simklcalendar.ui.theme.SurfaceDark
import com.felixbrucker.simklcalendar.ui.theme.TextGrey
import com.felixbrucker.simklcalendar.ui.theme.TextPrimary
import com.felixbrucker.simklcalendar.ui.theme.TextSubtle
import com.felixbrucker.simklcalendar.ui.theme.YellowAccent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaItemDetailScreen(
    item: CachedMediaItem,
    listStatus: WatchlistStatus?,
    episodes: List<SimklEpisodeResponse> = emptyList(),
    customSearchLinks: List<CustomSearchLink> = emptyList(),
    onUpdateWatchlistStatus: (WatchlistStatus) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onNavigateToSimilar: (Int, MediaType) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isStatusMenuExpanded by remember { mutableStateOf(false) }

    // Season collapse state: collapsed by default as requested
    val seasonExpandedState = remember { mutableStateMapOf<Int, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = item.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = SurfaceDark,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Section: Poster on Left, Details on Right
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Poster left
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.poster.toPosterUrl(PosterSize.WIDE))
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(115.dp)
                            .height(170.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainer)
                    )

                    // Details section right
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Media type badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PurplePrimaryDark,
                        ) {
                            Text(
                                text = item.mediaType.displayName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Title
                        Text(
                            text = item.title,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )



                        // Release dates / start-end year
                        val relDates = buildReleaseDatesText(item)
                        if (relDates.isNotBlank()) {
                            Text(text = relDates, color = TextSubtle, fontSize = 12.sp)
                        }

                        // Seasons & Total episodes
                        val episodesAndSeasons = buildSeasonsAndEpisodesText(item)
                        if (episodesAndSeasons.isNotBlank()) {
                            Text(text = episodesAndSeasons, color = TextSubtle, fontSize = 12.sp)
                        }

                        // Rating
                        if (item.rating != null && item.rating > 0f) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = YellowAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = String.format("%.1f", item.rating),
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Budget & Box office for movies
                        if (item.mediaType == MediaType.MOVIE) {
                            val budget = item.movieMetadata?.budget
                            val boxOffice = item.movieMetadata?.boxOffice
                            if (budget != null && budget > 0) {
                                Text(text = "Budget: $${formatCurrency(budget)}", color = TextGrey, fontSize = 11.sp)
                            }
                            if (boxOffice != null && boxOffice > 0) {
                                Text(text = "Box Office: $${formatCurrency(boxOffice)}", color = TextGrey, fontSize = 11.sp)
                            }
                        }

                        // Link to Simkl
                        Row(
                            modifier = Modifier
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.simklUrl))
                                    context.startActivity(intent)
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = PurpleLight,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Open on Simkl",
                                color = PurpleLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Watch/List Status Dropdown
            item {
                Box {
                    val statusText = if (listStatus != null) {
                        "List: ${listStatus.displayName()}"
                    } else {
                        "Add to watchlist"
                    }

                    FilledTonalButton(
                        onClick = { isStatusMenuExpanded = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (listStatus != null) PurplePrimaryDark else ContainerBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = statusText, fontWeight = FontWeight.Bold)
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = isStatusMenuExpanded,
                        onDismissRequest = { isStatusMenuExpanded = false },
                        modifier = Modifier.background(SurfaceContainer)
                    ) {
                        WatchlistStatus.entries.forEach { status ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(text = status.displayName(), color = TextPrimary)
                                        if (status == listStatus) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = GreenSuccess,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    isStatusMenuExpanded = false
                                    onUpdateWatchlistStatus(status)
                                }
                            )
                        }
                    }
                }
            }

            // Genres & Summary / Description Section
            if (item.genres.isNotEmpty() || !item.description.isNullOrBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                        border = BorderStroke(1.dp, OutlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (item.genres.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    item.genres.forEach { genre ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = SurfaceDark,
                                            border = BorderStroke(1.dp, OutlineVariant)
                                        ) {
                                            Text(
                                                text = genre,
                                                color = PurpleLight,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (!item.description.isNullOrBlank()) {
                                Text(
                                    text = item.description,
                                    color = TextSubtle,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            // Search Links Section
            if (customSearchLinks.isNotEmpty()) {
                item {
                    CustomSearchLinksSection(
                        title = item.title,
                        mediaType = item.mediaType,
                        links = customSearchLinks
                    )
                }
            }

            // Episode List Grouped by Season (TV + Anime)
            if (item.mediaType != MediaType.MOVIE && episodes.isNotEmpty()) {
                val seasonKeys = episodes.groupBy { it.season ?: 1 }.keys.sorted()

                item {
                    Text(
                        text = "Episodes",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                items(seasonKeys) { seasonNumber ->
                    val seasonEpisodes = episodes.filter { (it.season ?: 1) == seasonNumber }
                    val isExpanded = seasonExpandedState[seasonNumber] ?: false

                    SeasonCollapsibleCard(
                        seasonNumber = seasonNumber,
                        episodeCount = seasonEpisodes.size,
                        isExpanded = isExpanded,
                        onToggle = { seasonExpandedState[seasonNumber] = !isExpanded },
                        episodes = seasonEpisodes
                    )
                }
            }

            // Related / Similar Section
            if (item.similarItems.isNotEmpty()) {
                item {
                    Text(
                        text = "Similar Titles",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                items(item.similarItems) { similar ->
                    SimilarItemRow(
                        item = similar,
                        onClick = {
                            onNavigateToSimilar(similar.simklId, similar.mediaType)
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun SeasonCollapsibleCard(
    seasonNumber: Int,
    episodeCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    episodes: List<SimklEpisodeResponse>,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (seasonNumber == 0) "Specials ($episodeCount)" else "Season $seasonNumber ($episodeCount)",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSubtle
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    HorizontalDivider(color = OutlineVariant, thickness = 1.dp)
                    episodes.forEach { ep ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "E${ep.episode ?: "?"}",
                                color = PurpleLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.width(36.dp)
                            )
                            Text(
                                text = ep.title ?: "Episode ${ep.episode ?: ""}",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (!ep.date.isNullOrBlank()) {
                                Text(
                                    text = ep.date.take(10),
                                    color = TextGrey,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimilarItemRow(
    item: CachedSimilarMediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.poster.toPosterUrl(PosterSize.COMPACT))
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(45.dp, 65.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceDark)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val yr = item.year?.toString() ?: ""
                val tp = item.mediaType.displayName
                val subtitle = listOf(tp, yr).filter { it.isNotEmpty() }.joinToString(" • ")
                if (subtitle.isNotEmpty()) {
                    Text(text = subtitle, color = TextSubtle, fontSize = 11.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomSearchLinksSection(
    title: String,
    mediaType: MediaType,
    links: List<CustomSearchLink>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val relevantLinks = remember(links, mediaType) {
        links.filter { it.associatedTypes.contains(mediaType) }
    }

    if (relevantLinks.isEmpty()) return

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Search Links",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                relevantLinks.forEach { link ->
                    OutlinedButton(
                        onClick = {
                            val url = link.buildUrl(
                                title = title,
                                titleRomaji = null,
                                type = mediaType,
                                season = 1,
                                episode = 1
                            )
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PurpleLight),
                        border = BorderStroke(1.dp, OutlineVariant)
                    ) {
                        Text(text = link.name, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun WatchlistStatus.displayName(): String = when (this) {
    WatchlistStatus.WATCHING -> "Watching"
    WatchlistStatus.PLAN_TO_WATCH -> "Plan to Watch"
    WatchlistStatus.COMPLETED -> "Completed"
    WatchlistStatus.HOLD -> "On Hold"
    WatchlistStatus.DROPPED -> "Dropped"
}

private fun buildReleaseDatesText(item: CachedMediaItem): String {
    return when (item.mediaType) {
        MediaType.MOVIE -> {
            val relDate = item.movieMetadata?.releaseDate?.toString()
            val dvdDate = item.movieMetadata?.dvdDate?.toString()
            val parts = mutableListOf<String>()
            if (relDate != null) parts.add("Released: $relDate")
            if (dvdDate != null) parts.add("DVD: $dvdDate")
            parts.joinToString(" • ")
        }
        MediaType.TV -> {
            val startYr = item.year?.toString() ?: ""
            val endYr = item.seriesMetadata?.endYear?.toString() ?: ""
            when {
                startYr.isNotEmpty() && endYr.isNotEmpty() && startYr != endYr -> "$startYr - $endYr"
                startYr.isNotEmpty() -> startYr
                else -> ""
            }
        }
        MediaType.ANIME -> {
            val startYr = item.year?.toString() ?: ""
            val endYr = item.animeMetadata?.endYear?.toString() ?: ""
            val quarter = item.animeMetadata?.seasonQuarter
            val seasonYr = item.animeMetadata?.seasonYear
            val seasonStr = if (quarter != null && seasonYr != null) "$quarter $seasonYr" else ""
            val yrSpan = when {
                startYr.isNotEmpty() && endYr.isNotEmpty() && startYr != endYr -> "$startYr - $endYr"
                startYr.isNotEmpty() -> startYr
                else -> ""
            }
            listOf(yrSpan, seasonStr).filter { it.isNotEmpty() }.joinToString(" • ")
        }
    }
}

private fun buildSeasonsAndEpisodesText(item: CachedMediaItem): String {
    return when (item.mediaType) {
        MediaType.MOVIE -> ""
        MediaType.TV -> {
            val seasons = null
            val eps = item.seriesMetadata?.totalEpisodes?.let { "$it episodes" }
            listOfNotNull(seasons, eps).joinToString(" • ")
        }
        MediaType.ANIME -> {
            val eps = item.animeMetadata?.totalEpisodes?.let { "$it episodes" }
            val animType = item.animeMetadata?.animeType?.key?.uppercase()
            listOfNotNull(animType, eps).joinToString(" • ")
        }
    }
}

private fun formatCurrency(amount: Long): String {
    return when {
        amount >= 1_000_000_000 -> String.format("%.1fB", amount / 1_000_000_000.0)
        amount >= 1_000_000 -> String.format("%.1fM", amount / 1_000_000.0)
        amount >= 1_000 -> String.format("%.1fK", amount / 1_000.0)
        else -> amount.toString()
    }
}
