package com.felixbrucker.simklcalendar.ui.composable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedMenuButton(
    menuExpanded: Boolean,
    onClick: () -> Unit,
    animationDurationMillis: Int = 300
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedMenuToXIcon(
            isOpen = menuExpanded,
            size = 24.dp,
            color = Color.White,
            strokeWidth = 2.dp,
            animationDurationMillis = animationDurationMillis
        )
    }
}


@Composable
private fun AnimatedMenuToXIcon(
    isOpen: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = Color.Black,
    strokeWidth: Dp = 2.5.dp,
    animationDurationMillis: Int = 300
) {
    val progress by animateFloatAsState(
        targetValue = if (isOpen) 1f else 0f,
        animationSpec = tween(durationMillis = animationDurationMillis),
        label = "menu_to_x_animation"
    )

    Canvas(modifier = modifier.size(size)) {
        val width = size.toPx()
        val height = size.toPx()
        val stroke = strokeWidth.toPx()

        val centerY = height / 2f
        val spacing = height * 0.28f // Distance of top/bottom bars from center

        // 1. Top Bar: Translates to center (centerY) and rotates +45°
        val currentTopY = centerY - spacing + (spacing * progress)
        rotate(degrees = progress * 45f, pivot = Offset(width / 2f, currentTopY)) {
            drawLine(
                color = color,
                start = Offset(0f, currentTopY),
                end = Offset(width, currentTopY),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }

        // 2. Middle Bar: Scales down horizontally and fades out
        val middleAlpha = 1f - progress
        if (middleAlpha > 0f) {
            val horizontalInset = (width / 2f) * progress
            drawLine(
                color = color.copy(alpha = middleAlpha),
                start = Offset(horizontalInset, centerY),
                end = Offset(width - horizontalInset, centerY),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }

        // 3. Bottom Bar: Translates to center (centerY) and rotates -45°
        val currentBottomY = centerY + spacing - (spacing * progress)
        rotate(degrees = progress * -45f, pivot = Offset(width / 2f, currentBottomY)) {
            drawLine(
                color = color,
                start = Offset(0f, currentBottomY),
                end = Offset(width, currentBottomY),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}
