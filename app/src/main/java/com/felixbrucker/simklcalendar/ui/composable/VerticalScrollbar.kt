package com.felixbrucker.simklcalendar.ui.composable

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.felixbrucker.simklcalendar.ui.theme.PurpleLight

@SuppressLint("FrequentlyChangingValue")
@Composable
fun VerticalScrollbar(
    listState: LazyListState,
    considerStickyHeaders: Boolean,
    modifier: Modifier = Modifier,
) {
    val considerStickyHeaders = remember { considerStickyHeaders }
    val progressRaw = if (considerStickyHeaders)
        calculateScrollProgressForListWithStickyHeaders(listState = listState)
    else
        calculateScrollProgress(listState = listState)
    val progress = progressRaw ?: return
    val isScrolling = listState.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if (isScrolling) 1f else 0f,
        animationSpec = tween(durationMillis = if (isScrolling) 100 else 400),
        label = "scrollbar_fade",
    )

    if (alpha > 0f) {
        ScrollbarContainer(progress = progress, alpha = alpha, modifier = modifier)
    }
}

@SuppressLint("FrequentlyChangingValue")
@Composable
private fun calculateScrollProgressForListWithStickyHeaders(listState: LazyListState): Float? {
    val totalItems = listState.layoutInfo.totalItemsCount
    if (totalItems <= 1) return null

    val visibleItemsInfo = listState.layoutInfo.visibleItemsInfo
    if (visibleItemsInfo.isEmpty()) return null

    val canScrollForward = listState.canScrollForward
    val canScrollBackward = listState.canScrollBackward
    if (!canScrollForward && !canScrollBackward) return null

    if (!canScrollForward) return 1f
    if (!canScrollBackward) return 0f

    val measuredHeights = remember { mutableStateMapOf<Int, Int>() }
    visibleItemsInfo.forEach { item ->
        measuredHeights[item.index] = item.size
    }

    val avgHeight = if (measuredHeights.isNotEmpty()) {
        measuredHeights.values.sum().toFloat() / measuredHeights.size
    } else {
        120f
    }

    val firstIndex = listState.firstVisibleItemIndex
    val firstOffset = listState.firstVisibleItemScrollOffset

    var precedingPixels = 0f
    for (i in 0 until firstIndex) {
        precedingPixels += (measuredHeights[i]?.toFloat() ?: avgHeight)
    }

    val currentPixels = precedingPixels + firstOffset.toFloat()

    var totalPixels = 0f
    for (i in 0 until totalItems) {
        totalPixels += (measuredHeights[i]?.toFloat() ?: avgHeight)
    }

    val viewportHeight = (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset)
        .toFloat()
        .coerceAtLeast(1f)

    val targetMaxPixels = (totalPixels - viewportHeight).coerceAtLeast(1f)
    var stableMaxPixels by remember { mutableFloatStateOf(targetMaxPixels) }
    SideEffect {
        stableMaxPixels = (stableMaxPixels * 0.98f) + (targetMaxPixels * 0.02f)
    }

    return (currentPixels / stableMaxPixels.coerceAtLeast(1f)).coerceIn(0f, 1f)
}

@SuppressLint("FrequentlyChangingValue")
private fun calculateScrollProgress(listState: LazyListState): Float? {
    val totalItems = listState.layoutInfo.totalItemsCount
    if (totalItems <= 1) return null

    val visibleItemsInfo = listState.layoutInfo.visibleItemsInfo
    if (visibleItemsInfo.isEmpty()) return null

    val canScrollForward = listState.canScrollForward
    val canScrollBackward = listState.canScrollBackward
    if (!canScrollForward && !canScrollBackward) return null

    if (!canScrollForward) return 1f
    if (!canScrollBackward) return 0f

    val viewportHeight = (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset)
        .toFloat()
        .coerceAtLeast(1f)

    val first = visibleItemsInfo.first()
    val isPinnedHeader = visibleItemsInfo.size >= 2 && first.offset == 0 && visibleItemsInfo[1].offset <= first.size
    val activeFirst = if (isPinnedHeader) visibleItemsInfo[1] else first
    val topHeaderOffset = if (isPinnedHeader) first.size.toFloat() else 0f

    val last = visibleItemsInfo.last()

    val visibleSpanHeight = (last.offset + last.size - activeFirst.offset).toFloat().coerceAtLeast(1f)
    val visibleSpanCount = (last.index - activeFirst.index + 1).toFloat().coerceAtLeast(1f)
    val avgItemHeight = visibleSpanHeight / visibleSpanCount

    val topScrolledOff = (topHeaderOffset - activeFirst.offset.toFloat()).coerceAtLeast(0f)
    val baseIndex = if (isPinnedHeader) (activeFirst.index - 1).toFloat() else activeFirst.index.toFloat()
    val pixelsAbove = (baseIndex * avgItemHeight) + topScrolledOff

    val lastBottom = (last.offset + last.size).toFloat()
    val lastOverflow = (lastBottom - viewportHeight).coerceAtLeast(0f)
    val itemsBelowCount = (totalItems - 1 - last.index).coerceAtLeast(0).toFloat()
    val pixelsBelow = (itemsBelowCount * avgItemHeight) + lastOverflow

    val totalScrollable = pixelsAbove + pixelsBelow
    if (totalScrollable <= 0f) return 0f

    return (pixelsAbove / totalScrollable).coerceIn(0f, 1f)
}

@Composable
private fun ScrollbarContainer(
    progress: Float,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .width(6.dp)
            .fillMaxHeight()
            .alpha(alpha)
            .padding(vertical = 4.dp),
    ) {
        val density = LocalDensity.current
        val maxHeightPx = constraints.maxHeight.toFloat()
        val thumbHeightPx = 40f
        val availablePx = (maxHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val yOffsetDp = with(density) { (availablePx * progress).toDp() }
        val thumbHeightDp = with(density) { thumbHeightPx.toDp() }

        Box(
            modifier = Modifier
                .width(4.dp)
                .height(thumbHeightDp)
                .offset(y = yOffsetDp)
                .background(PurpleLight, RoundedCornerShape(2.dp)),
        )
    }
}
