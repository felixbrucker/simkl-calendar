package com.felixbrucker.simklcalendar.ui.composable

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.felixbrucker.simklcalendar.ui.theme.OutlineVariant

enum class EpisodeListSliceType {
    HEADER,
    MIDDLE,
    FOOTER
}

fun Modifier.episodeListSliceBorder(sliceType: EpisodeListSliceType): Modifier = this.drawWithContent {
    drawContent()

    val strokeWidth = 1.dp.toPx()
    val radius = 12.dp.toPx()
    val color = OutlineVariant
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = strokeWidth)

    when (sliceType) {
        EpisodeListSliceType.HEADER -> {
            val effectiveRadius = minOf(radius, w / 2, h / 2)
            val arcSize = Size(maxOf(0f, effectiveRadius * 2 - strokeWidth), maxOf(0f, effectiveRadius * 2 - strokeWidth))
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = arcSize,
                style = stroke
            )
            drawArc(
                color = color,
                startAngle = 270f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(w - effectiveRadius * 2 + strokeWidth / 2, strokeWidth / 2),
                size = arcSize,
                style = stroke
            )
            drawLine(
                color = color,
                start = Offset(effectiveRadius, strokeWidth / 2),
                end = Offset(w - effectiveRadius, strokeWidth / 2),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = color,
                start = Offset(strokeWidth / 2, effectiveRadius),
                end = Offset(strokeWidth / 2, h),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = color,
                start = Offset(w - strokeWidth / 2, effectiveRadius),
                end = Offset(w - strokeWidth / 2, h),
                strokeWidth = strokeWidth
            )
        }
        EpisodeListSliceType.MIDDLE -> {
            drawLine(
                color = color,
                start = Offset(strokeWidth / 2, 0f),
                end = Offset(strokeWidth / 2, h),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = color,
                start = Offset(w - strokeWidth / 2, 0f),
                end = Offset(w - strokeWidth / 2, h),
                strokeWidth = strokeWidth
            )
        }
        EpisodeListSliceType.FOOTER -> {
            val effectiveRadius = minOf(radius, w / 2)
            val arcSize = Size(maxOf(0f, effectiveRadius * 2 - strokeWidth), maxOf(0f, effectiveRadius * 2 - strokeWidth))
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = -90f,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, -effectiveRadius + strokeWidth / 2),
                size = arcSize,
                style = stroke
            )
            drawArc(
                color = color,
                startAngle = 90f,
                sweepAngle = -90f,
                useCenter = false,
                topLeft = Offset(w - effectiveRadius * 2 + strokeWidth / 2, -effectiveRadius + strokeWidth / 2),
                size = arcSize,
                style = stroke
            )
            drawLine(
                color = color,
                start = Offset(effectiveRadius, h - strokeWidth / 2),
                end = Offset(w - effectiveRadius, h - strokeWidth / 2),
                strokeWidth = strokeWidth
            )
        }
    }
}
