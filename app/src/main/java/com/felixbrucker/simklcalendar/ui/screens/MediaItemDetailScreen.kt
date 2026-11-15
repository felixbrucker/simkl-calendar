package com.felixbrucker.simklcalendar.ui.screens

import android.R.drawable.ic_menu_gallery
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.felixbrucker.simklcalendar.data.database.MediaItem
import com.felixbrucker.simklcalendar.data.database.WatchlistItem
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.WatchlistStatus
import com.felixbrucker.simklcalendar.data.util.MediaFormatter
import com.felixbrucker.simklcalendar.data.util.PosterSize
import com.felixbrucker.simklcalendar.data.util.displayTitle
import com.felixbrucker.simklcalendar.extensions.toPosterUrl
import com.felixbrucker.simklcalendar.ui.composable.CustomSearchLinksCard
import com.felixbrucker.simklcalendar.ui.composable.DetailHeaderOpenSimklButton
import com.felixbrucker.simklcalendar.ui.composable.sharedBoundsItem
import com.felixbrucker.simklcalendar.ui.composable.sharedElementItem
import com.felixbrucker.simklcalendar.ui.theme.*
import com.felixbrucker.simklcalendar.ui.viewmodel.MediaItemDetailViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaItemDetailScreen(
    simklId: Int,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MediaItemDetailViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val mediaItem by viewModel.mediaItem.collectAsState()
    val watchlistItem by viewModel.watchlistItem.collectAsState()
    val customSearchLinks by viewModel.customSearchLinks.collectAsState()
    val isUpdatingStatus by viewModel.isUpdatingStatus.collectAsState()

    val categoryColor = when (mediaItem?.mediaType ?: MediaType.TV) {
        MediaType.ANIME -> PurpleLight
        MediaType.MOVIE -> ErrorPink
        MediaType.TV -> BlueAccent
    }

    Scaffold(
        modifier = modifier
            .sharedBoundsItem(
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                key = "container_$simklId"
            ),
        topBar = {
            TopAppBar(
                title = { Text(mediaItem?.title ?: "Details", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = SurfaceDark,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (mediaItem == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val item = mediaItem!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Header Layout with adaptive mobile / wide layout
                item {
                    MediaDetailHeaderContent(
                        simklId = simklId,
                        item = item,
                        categoryColor = categoryColor,
                        watchlistItem = watchlistItem,
                        isUpdatingStatus = isUpdatingStatus,
                        onUpdateStatus = { status ->
                            viewModel.updateWatchlistStatus(simklId, item.mediaType, status) { _, msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // Search Links Section
                item {
                    CustomSearchLinksCard(
                        searchLinks = customSearchLinks,
                        title = item.title,
                        titleRomaji = item.titleRomaji,
                        itemType = item.mediaType,
                        season = null,
                        episode = null,
                        snackbarHostState = snackbarHostState,
                        scope = scope,
                        context = context,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MediaDetailHeaderContent(
    simklId: Int,
    item: MediaItem,
    categoryColor: Color,
    watchlistItem: WatchlistItem?,
    isUpdatingStatus: Boolean,
    onUpdateStatus: (WatchlistStatus?) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isNarrow = maxWidth < 600.dp

        if (isNarrow) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MediaDetailPosterColumn(
                    simklId = simklId,
                    item = item,
                    categoryColor = categoryColor,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    modifier = Modifier.fillMaxWidth()
                )
                MediaDetailInfoColumn(
                    simklId = simklId,
                    item = item,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    modifier = Modifier.fillMaxWidth()
                )
                MediaDetailActionSection(
                    simklId = simklId,
                    mediaType = item.mediaType,
                    watchlistItem = watchlistItem,
                    isUpdatingStatus = isUpdatingStatus,
                    onUpdateStatus = onUpdateStatus,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                MediaDetailPosterColumn(
                    simklId = simklId,
                    item = item,
                    categoryColor = categoryColor,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    modifier = Modifier.width(220.dp)
                )
                MediaDetailInfoColumn(
                    simklId = simklId,
                    item = item,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    modifier = Modifier.weight(1f)
                )
                MediaDetailActionSection(
                    simklId = simklId,
                    mediaType = item.mediaType,
                    watchlistItem = watchlistItem,
                    isUpdatingStatus = isUpdatingStatus,
                    onUpdateStatus = onUpdateStatus,
                    modifier = Modifier.width(160.dp)
                )
            }
        }
    }
}

@Composable
fun MediaDetailPosterColumn(
    simklId: Int,
    item: MediaItem,
    categoryColor: Color,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .sharedBoundsItem(
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                key = "poster_$simklId"
            )
            .clip(RoundedCornerShape(12.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val compactPosterUrl = remember(item.poster) { item.poster.toPosterUrl(PosterSize.COMPACT) }
        val mediumPosterUrl = remember(item.poster) { item.poster.toPosterUrl(PosterSize.MEDIUM) }
        val posterRequest = remember(mediumPosterUrl, compactPosterUrl, context) {
            ImageRequest.Builder(context)
                .data(mediumPosterUrl)
                .placeholderMemoryCacheKey(compactPosterUrl)
                .placeholder(ic_menu_gallery)
                .error(ic_menu_gallery)
                .crossfade(enable = true)
                .build()
        }

        AsyncImage(
            model = posterRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceGreyDark),
            error = painterResource(id = ic_menu_gallery)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(categoryColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.mediaType.displayName.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
fun MediaDetailInfoColumn(
    simklId: Int,
    item: MediaItem,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val displayTitle = remember(item) { item.displayTitle }

        Text(
            text = displayTitle,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.sharedElementItem(
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                key = "title_$simklId"
            )
        )

        if (!item.titleRomaji.isNullOrBlank()) {
            Text(
                text = item.titleRomaji,
                fontSize = 13.sp,
                color = TextSubtle
            )
        }

        val subtitle = MediaFormatter.formatMediaSubtitle(item)
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextSubtle
            )
        }

        if (item.genres.isNotEmpty()) {
            Text(
                text = item.genres.joinToString(" • "),
                fontSize = 12.sp,
                color = PurpleLight,
            )
        }

        if (item.rating != null && item.rating > 0f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = YellowAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = String.format(Locale.US, "%.1f", item.rating),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        if (!item.description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.description,
                fontSize = 12.sp,
                color = TextGrey,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MediaDetailActionSection(
    simklId: Int,
    mediaType: MediaType,
    watchlistItem: WatchlistItem?,
    isUpdatingStatus: Boolean,
    onUpdateStatus: (WatchlistStatus?) -> Unit,
    modifier: Modifier = Modifier
) {
    var watchlistStatusDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DetailHeaderOpenSimklButton(
            simklId = simklId,
            type = mediaType,
            modifier = Modifier.fillMaxWidth()
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            val isInWatchlist = watchlistItem != null
            val currentStatus = watchlistItem?.status
            val buttonText = if (isUpdatingStatus) "Updating..." else (currentStatus?.displayName ?: "Add to list")
            Button(
                onClick = { watchlistStatusDropdownExpanded = true },
                enabled = !isUpdatingStatus,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isInWatchlist) SurfacePrimaryVariant else PurpleLight,
                    contentColor = if (isInWatchlist) PurpleContainerSoft else SurfaceDark
                ),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                if (isUpdatingStatus) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = if (isInWatchlist) PurpleContainerSoft else SurfaceDark
                    )
                } else {
                    Text(
                        text = buttonText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            DropdownMenu(
                expanded = watchlistStatusDropdownExpanded,
                onDismissRequest = { watchlistStatusDropdownExpanded = false },
                modifier = Modifier.background(SurfaceContainer)
            ) {
                val allowedStatuses = if (mediaType == MediaType.MOVIE) {
                    listOf(WatchlistStatus.PLAN_TO_WATCH, WatchlistStatus.COMPLETED, WatchlistStatus.DROPPED)
                } else {
                    WatchlistStatus.entries.toList()
                }

                allowedStatuses.forEach { status ->
                    DropdownMenuItem(
                        text = { Text(status.displayName, color = Color.White, fontSize = 14.sp) },
                        onClick = {
                            watchlistStatusDropdownExpanded = false
                            onUpdateStatus(status)
                        }
                    )
                }

                if (isInWatchlist) {
                    HorizontalDivider(color = OutlineVariant)
                    DropdownMenuItem(
                        text = { Text("Remove from list", color = ErrorPink, fontSize = 14.sp) },
                        onClick = {
                            watchlistStatusDropdownExpanded = false
                            onUpdateStatus(null)
                        }
                    )
                }
            }
        }
    }
}
