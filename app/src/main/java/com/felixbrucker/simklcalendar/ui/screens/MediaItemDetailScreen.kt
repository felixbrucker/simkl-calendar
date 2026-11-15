package com.felixbrucker.simklcalendar.ui.screens

import android.R.drawable.ic_menu_gallery
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.felixbrucker.simklcalendar.data.database.AnimeType
import com.felixbrucker.simklcalendar.data.database.EpisodeType
import com.felixbrucker.simklcalendar.data.database.MediaEpisode
import com.felixbrucker.simklcalendar.data.database.MediaItem
import com.felixbrucker.simklcalendar.data.database.MediaItemWithDetails
import com.felixbrucker.simklcalendar.data.database.RecommendedMediaItem
import com.felixbrucker.simklcalendar.data.database.RelatedMediaItem
import com.felixbrucker.simklcalendar.data.database.SimilarMediaItem
import com.felixbrucker.simklcalendar.data.database.WatchlistItem
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.WatchlistStatus
import com.felixbrucker.simklcalendar.data.util.MediaFormatter
import com.felixbrucker.simklcalendar.data.util.PosterSize
import com.felixbrucker.simklcalendar.data.util.displayTitle
import com.felixbrucker.simklcalendar.extensions.toPosterUrl
import com.felixbrucker.simklcalendar.ui.composable.CustomSearchLinksCard
import com.felixbrucker.simklcalendar.ui.composable.DetailHeaderOpenSimklButton
import com.felixbrucker.simklcalendar.ui.composable.StandardEpisodeRow
import com.felixbrucker.simklcalendar.ui.composable.sharedBoundsItem
import com.felixbrucker.simklcalendar.ui.composable.sharedElementItem
import com.felixbrucker.simklcalendar.ui.composable.badgeTextColor
import com.felixbrucker.simklcalendar.ui.composable.categoryColor
import com.felixbrucker.simklcalendar.ui.composable.overlayIcon
import com.felixbrucker.simklcalendar.ui.composable.posterAlpha
import com.felixbrucker.simklcalendar.ui.theme.*
import com.felixbrucker.simklcalendar.ui.viewmodel.MediaItemDetailViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaItemDetailScreen(
    simklId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Int, MediaType) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MediaItemDetailViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val mediaItemWithDetails by viewModel.mediaItem.collectAsState()
    val watchlistItem by viewModel.watchlistItem.collectAsState()
    val customSearchLinks by viewModel.customSearchLinks.collectAsState()
    val isUpdatingStatus by viewModel.isUpdatingStatus.collectAsState()
    val watchlistStatusMap by viewModel.watchlistStatusMap.collectAsState()
    val uiPreferences by viewModel.uiPreferences.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val item = mediaItemWithDetails
    val categoryColor = when (item?.mediaType ?: MediaType.TV) {
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
                title = { Text(item?.title ?: "Details", color = Color.White) },
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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                viewModel.refresh { _, msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (item == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
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

                    val isAnimeMovie = item.mediaType == MediaType.ANIME && item.animeMetadata?.animeType == AnimeType.MOVIE
                    val shouldShowEpisodes = item.mediaType != MediaType.MOVIE && !isAnimeMovie
                    if (shouldShowEpisodes) {
                        item {
                            MediaEpisodeListSection(
                                episodes = item.episodes,
                                mediaType = item.mediaType,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    if (item.relations.isNotEmpty()) {
                        item {
                            RelatedMediaCarousel(
                                relations = item.relations,
                                watchlistStatusMap = watchlistStatusMap,
                                onNavigateToDetail = onNavigateToDetail,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    if (item.similarItems.isNotEmpty()) {
                        item {
                            SimilarMediaCarousel(
                                similarItems = item.similarItems,
                                watchlistStatusMap = watchlistStatusMap,
                                hideWatchlist = uiPreferences.hideWatchlistItemsInSimilar,
                                onToggleHide = { viewModel.toggleHideWatchlistItemsInSimilar(it) },
                                onNavigateToDetail = onNavigateToDetail,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    if (item.recommendedItems.isNotEmpty()) {
                        item {
                            RecommendedMediaCarousel(
                                recommendedItems = item.recommendedItems,
                                watchlistStatusMap = watchlistStatusMap,
                                hideWatchlist = uiPreferences.hideWatchlistItemsInRecommended,
                                onToggleHide = { viewModel.toggleHideWatchlistItemsInRecommended(it) },
                                onNavigateToDetail = onNavigateToDetail,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CarouselHeaderWithToggle(
    title: String,
    hideWatchlist: Boolean,
    onToggleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 15.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Hide watchlist",
                fontSize = 11.sp,
                color = TextSubtle
            )
            Switch(
                checked = hideWatchlist,
                onCheckedChange = onToggleChange,
                modifier = Modifier.scale(0.7f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SurfaceDark,
                    checkedTrackColor = PurpleLight,
                    uncheckedThumbColor = TextSubtle,
                    uncheckedTrackColor = SurfaceContainer
                )
            )
        }
    }
}

@Composable
fun MediaEpisodeListSection(
    episodes: List<MediaEpisode>,
    mediaType: MediaType,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val filteredEpisodes = remember(episodes) {
        episodes.filter { it.type != EpisodeType.SPECIAL && it.season > 0 }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (mediaType == MediaType.MOVIE) "Releases (${filteredEpisodes.size})" else "Episodes (${filteredEpisodes.size})",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = Color.White
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                if (filteredEpisodes.isEmpty()) {
                    Text(text = "No episodes available", fontSize = 13.sp, color = TextSubtle)
                } else {
                    val grouped = remember(filteredEpisodes) { filteredEpisodes.groupBy { it.season }.toSortedMap() }
                    grouped.forEach { (season, seasonEpisodes) ->
                        if (season > 0 && grouped.size > 1) {
                            Text(
                                text = "Season $season",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PurpleLight,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                        seasonEpisodes.forEach { ep ->
                            val episodeCode = if (mediaType == MediaType.MOVIE) "MOVIE" else MediaFormatter.formatEpisodeCode(mediaType, ep.season, ep.episodeNumber)
                            StandardEpisodeRow(
                                title = ep.title.takeIf { it.isNotBlank() } ?: "TBA",
                                episodeCode = episodeCode,
                                date = ep.date
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RelatedMediaCarousel(
    relations: List<RelatedMediaItem>,
    watchlistStatusMap: Map<Int, WatchlistStatus>,
    onNavigateToDetail: (Int, MediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Related",
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(relations) { rel ->
                val watchlistStatus = watchlistStatusMap[rel.relatedSimklId]
                MediaItemCard(
                    simklId = rel.relatedSimklId,
                    title = rel.title,
                    poster = rel.poster,
                    year = rel.year,
                    mediaType = rel.mediaType,
                    topStartBadge = rel.relationType.displayName,
                    topEndBadge = if (rel.isDirect == false) "Spin-off" else null,
                    subtitleBadge = null,
                    watchlistStatus = watchlistStatus,
                    onNavigateToDetail = onNavigateToDetail
                )
            }
        }
    }
}

@Composable
fun SimilarMediaCarousel(
    similarItems: List<SimilarMediaItem>,
    watchlistStatusMap: Map<Int, WatchlistStatus>,
    hideWatchlist: Boolean,
    onToggleHide: (Boolean) -> Unit,
    onNavigateToDetail: (Int, MediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayItems = remember(similarItems, watchlistStatusMap, hideWatchlist) {
        if (hideWatchlist) similarItems.filter { watchlistStatusMap[it.similarSimklId] == null } else similarItems
    }

    if (similarItems.isNotEmpty()) {
        Column(modifier = modifier.fillMaxWidth()) {
            CarouselHeaderWithToggle(
                title = "Similar",
                hideWatchlist = hideWatchlist,
                onToggleChange = onToggleHide
            )
            if (displayItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "No items available",
                        fontSize = 12.sp,
                        color = TextSubtle
                    )
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayItems) { sim ->
                        val watchlistStatus = watchlistStatusMap[sim.similarSimklId]
                        MediaItemCard(
                            simklId = sim.similarSimklId,
                            title = sim.title,
                            poster = sim.poster,
                            year = sim.year,
                            mediaType = sim.mediaType,
                            topStartBadge = null,
                            topEndBadge = null,
                            subtitleBadge = null,
                            watchlistStatus = watchlistStatus,
                            onNavigateToDetail = onNavigateToDetail
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecommendedMediaCarousel(
    recommendedItems: List<RecommendedMediaItem>,
    watchlistStatusMap: Map<Int, WatchlistStatus>,
    hideWatchlist: Boolean,
    onToggleHide: (Boolean) -> Unit,
    onNavigateToDetail: (Int, MediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayItems = remember(recommendedItems, watchlistStatusMap, hideWatchlist) {
        if (hideWatchlist) recommendedItems.filter { watchlistStatusMap[it.recommendedSimklId] == null } else recommendedItems
    }

    if (recommendedItems.isNotEmpty()) {
        Column(modifier = modifier.fillMaxWidth()) {
            CarouselHeaderWithToggle(
                title = "Recommended",
                hideWatchlist = hideWatchlist,
                onToggleChange = onToggleHide
            )
            if (displayItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "No items available",
                        fontSize = 12.sp,
                        color = TextSubtle
                    )
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayItems) { rec ->
                        val watchlistStatus = watchlistStatusMap[rec.recommendedSimklId]
                        MediaItemCard(
                            simklId = rec.recommendedSimklId,
                            title = rec.title,
                            poster = rec.poster,
                            year = rec.year,
                            mediaType = rec.mediaType,
                            topStartBadge = null,
                            topEndBadge = null,
                            subtitleBadge = rec.usersPercent,
                            watchlistStatus = watchlistStatus,
                            onNavigateToDetail = onNavigateToDetail
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaItemCard(
    simklId: Int,
    title: String,
    poster: String?,
    year: Int?,
    mediaType: MediaType,
    topStartBadge: String?,
    topEndBadge: String?,
    subtitleBadge: String?,
    watchlistStatus: WatchlistStatus?,
    onNavigateToDetail: (Int, MediaType) -> Unit
) {
    val context = LocalContext.current
    val posterUrl = remember(poster) { poster.toPosterUrl(PosterSize.COMPACT) }
    val isFaded = watchlistStatus != null
    val alphaVal = watchlistStatus.posterAlpha
    val categoryColor = mediaType.categoryColor
    val overlayIcon = watchlistStatus.overlayIcon

    Column(
        modifier = Modifier
            .width(110.dp)
            .alpha(alphaVal)
            .clickable { onNavigateToDetail(simklId, mediaType) },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(110.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            Column(
                modifier = Modifier.width(110.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(posterUrl).crossfade(enable = true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(110.dp)
                        .height(150.dp),
                    error = painterResource(id = ic_menu_gallery)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(categoryColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mediaType.displayName.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = mediaType.badgeTextColor,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            if (isFaded && overlayIcon != null) {
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(150.dp)
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = overlayIcon,
                        contentDescription = watchlistStatus.displayName,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            if (!topStartBadge.isNullOrBlank() || !topEndBadge.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (!topStartBadge.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = topStartBadge,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    if (!topEndBadge.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = topEndBadge,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        val yearStr = year?.toString() ?: ""
        val infoText = buildString {
            if (yearStr.isNotBlank()) append(yearStr)
            if (!subtitleBadge.isNullOrBlank()) {
                if (isNotEmpty()) append(" • ")
                append(subtitleBadge)
            }
        }
        if (infoText.isNotBlank()) {
            Text(
                text = infoText,
                fontSize = 10.sp,
                color = TextSubtle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MediaDetailHeaderContent(
    simklId: Int,
    item: MediaItemWithDetails,
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
                    item = item.mediaItem,
                    categoryColor = categoryColor,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    modifier = Modifier.fillMaxWidth()
                )
                MediaDetailInfoColumn(
                    simklId = simklId,
                    item = item.mediaItem,
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
                    item = item.mediaItem,
                    categoryColor = categoryColor,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    modifier = Modifier.width(220.dp)
                )
                MediaDetailInfoColumn(
                    simklId = simklId,
                    item = item.mediaItem,
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
                color = item.mediaType.badgeTextColor,
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
            val currentIcon = if (isInWatchlist) currentStatus.overlayIcon ?: Icons.Default.Bookmark else Icons.Default.Add
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
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                if (isUpdatingStatus) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = if (isInWatchlist) PurpleContainerSoft else SurfaceDark
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = currentIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = buttonText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                    val icon = status.overlayIcon
                    DropdownMenuItem(
                        text = { Text(status.displayName, color = Color.White, fontSize = 14.sp) },
                        leadingIcon = if (icon != null) {
                            {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null,
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
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = ErrorPink,
                                modifier = Modifier.size(18.dp)
                            )
                        },
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
