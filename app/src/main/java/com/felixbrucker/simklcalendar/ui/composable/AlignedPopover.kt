package com.felixbrucker.simklcalendar.ui.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.felixbrucker.simklcalendar.ui.theme.SurfaceContainer
import com.felixbrucker.simklcalendar.ui.theme.SurfaceVariantGrey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

class CenteredAnchorPositionProvider(
    private val buttonBounds: Rect?,
    private val knownSize: IntSize,
    private val onClampedXCalculated: (Int) -> Unit = {}
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val sizeToUse = if (popupContentSize.width > 0) popupContentSize else knownSize
        if (buttonBounds == null || buttonBounds.isEmpty) {
            val defaultX = (windowSize.width - sizeToUse.width) / 2
            onClampedXCalculated(defaultX)
            return IntOffset(defaultX.coerceAtLeast(16), 140)
        }
        val buttonCenterX = buttonBounds.center.x.roundToInt()
        val buttonBottomY = buttonBounds.bottom.roundToInt()

        val popupX = buttonCenterX - (sizeToUse.width / 2)
        val minBound = 16
        val maxBound = maxOf(minBound, windowSize.width - sizeToUse.width - 16)
        val clampedX = popupX.coerceIn(minBound, maxBound)
        onClampedXCalculated(clampedX)
        return IntOffset(clampedX, buttonBottomY)
    }
}

class PopoverShape(
    private val peakX: Float,
    private val peakWidth: Float = 36f,
    private val peakHeight: Float = 16f,
    private val cornerRadius: Float = 20f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val width = size.width
            val height = size.height
            val radius = cornerRadius

            val peakLeft = (peakX - peakWidth / 2f).coerceIn(radius, width - radius - peakWidth)
            val peakRight = peakLeft + peakWidth
            val peakTipX = (peakLeft + peakRight) / 2f

            moveTo(radius, peakHeight)
            lineTo(peakLeft, peakHeight)
            lineTo(peakTipX, 0f)
            lineTo(peakRight, peakHeight)
            lineTo(width - radius, peakHeight)
            quadraticTo(width, peakHeight, width, peakHeight + radius)
            lineTo(width, height - radius)
            quadraticTo(width, height, width - radius, height)
            lineTo(radius, height)
            quadraticTo(0f, height, 0f, height - radius)
            lineTo(0f, peakHeight + radius)
            quadraticTo(0f, peakHeight, radius, peakHeight)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun AlignedPopover(
    isVisible: Boolean,
    anchorBounds: Rect?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!isVisible || anchorBounds == null || anchorBounds.isEmpty) return

    var popoverSize by remember(anchorBounds) { mutableStateOf<IntSize?>(null) }

    Box(modifier = modifier) {
        if (popoverSize == null) {
            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .alpha(0f)
                    .onGloballyPositioned {
                        if (it.size.width > 0 && it.size.height > 0) {
                            popoverSize = it.size
                        }
                    }
            ) {
                AlignedPopoverSurfaceContainer(
                    anchorBounds = anchorBounds,
                    clampedX = 0,
                    content = content
                )
            }
        } else {
            AlignedPopoverPopup(
                anchorBounds = anchorBounds,
                contentSize = popoverSize!!,
                onDismissRequest = onDismissRequest,
                content = content
            )
        }
    }
}

@Composable
fun AlignedPopoverPopup(
    anchorBounds: Rect?,
    contentSize: IntSize,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    var clampedX by remember { mutableIntStateOf(0) }
    val positionProvider = remember(anchorBounds, contentSize) {
        CenteredAnchorPositionProvider(buttonBounds = anchorBounds, knownSize = contentSize) { clampedX = it }
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scope = rememberCoroutineScope()

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = {
            scope.launch {
                visible = false
                delay(100.milliseconds)
                onDismissRequest()
            }
        },
        properties = PopupProperties(focusable = true)
    ) {
        AlignedPopoverAnimationContainer(
            visible = visible,
            anchorBounds = anchorBounds,
            clampedX = clampedX,
            content = content
        )
    }
}

@Composable
fun AlignedPopoverAnimationContainer(
    visible: Boolean,
    anchorBounds: Rect?,
    clampedX: Int,
    content: @Composable () -> Unit
) {
    var popoverWidth by remember { mutableFloatStateOf(0f) }
    val anchorCenterX = anchorBounds?.center?.x ?: 0f
    val peakX = if (anchorBounds != null && !anchorBounds.isEmpty) anchorCenterX - clampedX.toFloat() else 140f
    val peakRatio = if (popoverWidth > 0f) (peakX / popoverWidth).coerceIn(0f, 1f) else 0.5f

    AlignedPopoverAnimatedContent(
        visible = visible,
        peakRatio = peakRatio,
        anchorBounds = anchorBounds,
        clampedX = clampedX,
        onWidthMeasured = { popoverWidth = it },
        content = content
    )
}

@Composable
fun AlignedPopoverAnimatedContent(
    visible: Boolean,
    peakRatio: Float,
    anchorBounds: Rect?,
    clampedX: Int,
    onWidthMeasured: (Float) -> Unit,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            initialScale = 0.7f,
            transformOrigin = TransformOrigin(peakRatio, 0f),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(animationSpec = tween(100)),
        exit = scaleOut(
            targetScale = 0.7f,
            transformOrigin = TransformOrigin(peakRatio, 0f),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeOut(animationSpec = tween(100))
    ) {
        AlignedPopoverSurfaceContainer(
            anchorBounds = anchorBounds,
            clampedX = clampedX,
            modifier = Modifier.onGloballyPositioned { if (it.size.width > 0) onWidthMeasured(it.size.width.toFloat()) },
            content = content
        )
    }
}

@Composable
private fun AlignedPopoverSurfaceContainer(
    anchorBounds: Rect?,
    clampedX: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val anchorCenterX = anchorBounds?.center?.x ?: 0f
    val peakX = if (anchorBounds != null && !anchorBounds.isEmpty) {
        anchorCenterX - clampedX.toFloat()
    } else {
        140f
    }

    Surface(
        shape = PopoverShape(peakX = peakX),
        color = SurfaceContainer,
        border = BorderStroke(1.dp, SurfaceVariantGrey),
        shadowElevation = 8.dp,
        modifier = modifier.wrapContentWidth()
    ) {
        Box(modifier = Modifier.padding(top = 16.dp, bottom = 10.dp, start = 14.dp, end = 14.dp)) {
            content()
        }
    }
}
