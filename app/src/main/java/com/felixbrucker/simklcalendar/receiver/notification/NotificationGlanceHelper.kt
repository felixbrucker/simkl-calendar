package com.felixbrucker.simklcalendar.receiver.notification

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

data class NotificationActionInfo(
    val action: String,
    val label: String,
    val loadingLabel: String,
    val iconResId: Int,
    val intent: Intent,
) {
    fun toActionConfig(loadingAction: String?, context: Context): NotificationActionConfig {
        val isLoading = loadingAction == action
        val effectiveLabel = if (isLoading) loadingLabel else label

        return NotificationActionConfig(
            action = action,
            label = effectiveLabel,
            iconResId = iconResId,
            intent = intent,
            isLoading = isLoading,
            isEnabled = loadingAction == null,
            labelWidth = effectiveLabel.width(context, textSizeSp = 12f, isBold = false)
        )
    }
}

data class NotificationActionConfig(
    val action: String,
    val label: String,
    val labelWidth: Dp,
    val iconResId: Int,
    val intent: Intent,
    val isLoading: Boolean,
    val isEnabled: Boolean,
) {
    val estimatedItemWidthDp: Dp get() = (labelWidth.value + (6f + 6f) + 16f + 4f).dp
}

object NotificationGlanceHelper {

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    suspend fun buildCollapsedRemoteViews(
        context: Context,
        title: String,
        message: String,
        openIntent: Intent
    ): RemoteViews {
        val glanceRemoteViews = GlanceRemoteViews()
        return glanceRemoteViews.compose(context, DpSize.Unspecified) {
            GlanceTheme {
                CollapsedNotificationContent(
                    title = title,
                    message = message,
                    openIntent = openIntent
                )
            }
        }.remoteViews
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    suspend fun buildExpandedRemoteViews(
        context: Context,
        title: String,
        message: String,
        openIntent: Intent,
        actions: List<NotificationActionInfo>,
        loadingAction: String? = null
    ): RemoteViews {
        val notificationContainerWidthDp = getNotificationContainerWidth(context)
        val configs = actions.map { it.toActionConfig(loadingAction, context) }
        val glanceRemoteViews = GlanceRemoteViews()
        return glanceRemoteViews.compose(context, DpSize.Unspecified) {
            GlanceTheme {
                ExpandedNotificationContent(
                    title = title,
                    message = message,
                    openIntent = openIntent,
                    configs = configs,
                    notificationContainerWidthDp = notificationContainerWidthDp,
                )
            }
        }.remoteViews
    }

    @Composable
    private fun CollapsedNotificationContent(
        title: String,
        message: String,
        openIntent: Intent
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity(openIntent))
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 2.dp)
            )
            Text(
                text = message,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 14.sp
                ),
                maxLines = 2,
                modifier = GlanceModifier.fillMaxWidth()
            )
        }
    }

    @Composable
    private fun ExpandedNotificationContent(
        title: String,
        message: String,
        openIntent: Intent,
        configs: List<NotificationActionConfig>,
        notificationContainerWidthDp: Dp,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity(openIntent))
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)
            )
            Text(
                text = message,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 14.sp
                ),
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            if (configs.isNotEmpty()) {
                NotificationActionFlowRow(
                    notificationContainerWidthDp = notificationContainerWidthDp,
                    configs = configs
                )
            }
        }
    }

    @Composable
    private fun NotificationActionButton(
        config: NotificationActionConfig,
        modifier: GlanceModifier = GlanceModifier
    ) {
        val clickModifier = if (config.isEnabled) {
            GlanceModifier.clickable(actionSendBroadcast(config.intent))
        } else {
            GlanceModifier
        }

        val backgroundModifier = if (config.isEnabled) {
            GlanceModifier.background(GlanceTheme.colors.secondaryContainer)
        } else {
            GlanceModifier.background(GlanceTheme.colors.surfaceVariant)
        }

        Row(
            modifier = modifier
                .cornerRadius(8.dp)
                .then(backgroundModifier)
                .then(clickModifier)
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (config.isLoading) {
                CircularProgressIndicator(
                    modifier = GlanceModifier.size(16.dp),
                    color = GlanceTheme.colors.primary
                )
            } else {
                Image(
                    provider = ImageProvider(config.iconResId),
                    contentDescription = config.label,
                    modifier = GlanceModifier.size(16.dp)
                )
            }

            Spacer(GlanceModifier.width(4.dp))

            Text(
                text = config.label,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (config.isEnabled) GlanceTheme.colors.onSecondaryContainer else GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }

    @Composable
    fun NotificationActionFlowRow(
        notificationContainerWidthDp: Dp,
        configs: List<NotificationActionConfig>,
        modifier: GlanceModifier = GlanceModifier,
        horizontalSpacing: Dp = 8.dp,
        verticalSpacing: Dp = 8.dp,
    ) {
        // Partition items into dynamic rows based on calculated item widths
        val rows = rememberDynamicRows(
            configs = configs,
            availableWidthDp = notificationContainerWidthDp,
            spacingDp = horizontalSpacing
        )

        // 3. Render the resulting rows
        Column(
            modifier = modifier.fillMaxWidth()
        ) {
            rows.forEachIndexed { rowIndex, rowItems ->
                if (rowIndex > 0) {
                    Spacer(modifier = GlanceModifier.height(verticalSpacing))
                }

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rowItems.forEachIndexed { itemIndex, config ->
                        if (itemIndex > 0) {
                            Spacer(modifier = GlanceModifier.width(horizontalSpacing))
                        }
                        NotificationActionButton(
                            config = config,
                            modifier = GlanceModifier.width(config.estimatedItemWidthDp)
                        )
                    }
                }
            }
        }
    }

    /**
     * Calculates row distribution based on item string lengths and available width.
     */
    private fun rememberDynamicRows(
        configs: List<NotificationActionConfig>,
        availableWidthDp: Dp,
        spacingDp: Dp,
    ): List<List<NotificationActionConfig>> {
        val resultRows = mutableListOf<MutableList<NotificationActionConfig>>()
        var currentRow = mutableListOf<NotificationActionConfig>()
        var currentRowWidth = 0f

        for (config in configs) {
            // Calculate estimated width for this specific item
            val estimatedItemWidth = config.estimatedItemWidthDp.value

            val spacingNeeded = if (currentRow.isEmpty()) 0f else spacingDp.value

            if (((currentRowWidth + spacingNeeded + estimatedItemWidth) > availableWidthDp.value) && currentRow.isNotEmpty()) {
                // Row is full: commit current row and start a new line
                resultRows.add(currentRow)
                currentRow = mutableListOf(config)
                currentRowWidth = estimatedItemWidth
            } else {
                // Item fits: append to active row
                currentRow.add(config)
                currentRowWidth += spacingNeeded + estimatedItemWidth
            }
        }

        if (currentRow.isNotEmpty()) {
            resultRows.add(currentRow)
        }

        return resultRows
    }

    private fun getNotificationContainerWidth(context: Context): Dp {
        val screenWidthDp = context.resources.configuration.screenWidthDp

        val estimatedWidthDp = if (screenWidthDp >= 600) {
            // Tablets / Large Foldables / Landscape: SystemUI caps panel width around ~480dp
            480
        } else {
            // Screen width minus system margins
            // Outer System Shade Margins
            // Decorated Card Container Padding
            // System Header/App Icon Alignment Gutter
            // Expand Caret & System Action Reserves
            // OEM Card Corner Radius Clip Safety Offsets
            screenWidthDp - (32 + 32 + 24 + 24 + 24)
        }

        return estimatedWidthDp.coerceAtLeast(100).dp
    }
}

private fun String.width(
    context: Context,
    textSizeSp: Float = 12f,
    isBold: Boolean = false,
    // SystemUI Button styles apply ~0.015em to 0.025em letter spacing
    letterSpacingEm: Float = 0.02f,
    // Internal TextView/Button compound padding overhead
    viewOverheadDp: Float = 6f
): Dp {
    val displayMetrics = context.resources.displayMetrics

    // 1. Convert SP to PX using modern TypedValue API (Handles Non-Linear Scaling on Android 14+)
    val textSizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        textSizeSp,
        displayMetrics
    )

    // 2. Measure string width with TextPaint
    val paint = TextPaint().apply {
        isAntiAlias = true
        textSize = textSizePx
        typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        letterSpacing = letterSpacingEm
    }

    val rawWidthPx = paint.measureText(this)
    val rawWidthDp = rawWidthPx / displayMetrics.density

    // Add view-level layout overhead to prevent premature row packing
    return (rawWidthDp + viewOverheadDp).dp
}
