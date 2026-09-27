package com.felixbrucker.simklcalendar.receiver.notification

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
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
)

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
        val glanceRemoteViews = GlanceRemoteViews()
        return glanceRemoteViews.compose(context, DpSize.Unspecified) {
            GlanceTheme {
                ExpandedNotificationContent(
                    title = title,
                    message = message,
                    openIntent = openIntent,
                    actions = actions,
                    loadingAction = loadingAction
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
        actions: List<NotificationActionInfo>,
        loadingAction: String?
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

            if (actions.isNotEmpty()) {
                val shouldStackButtons = (actions.size > 1) || (actions.sumOf { it.label.length } > 18)
                if (shouldStackButtons) {
                    Column(
                        modifier = GlanceModifier.fillMaxWidth()
                    ) {
                        actions.forEachIndexed { index, actionInfo ->
                            if (index > 0) {
                                Spacer(GlanceModifier.height(6.dp))
                            }
                            NotificationActionButton(
                                actionInfo = actionInfo,
                                isLoading = loadingAction == actionInfo.action,
                                isEnabled = loadingAction == null,
                                modifier = GlanceModifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        actions.forEachIndexed { index, actionInfo ->
                            if (index > 0) {
                                Spacer(GlanceModifier.width(8.dp))
                            }
                            NotificationActionButton(
                                actionInfo = actionInfo,
                                isLoading = loadingAction == actionInfo.action,
                                isEnabled = loadingAction == null,
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun NotificationActionButton(
        actionInfo: NotificationActionInfo,
        isLoading: Boolean,
        isEnabled: Boolean,
        modifier: GlanceModifier = GlanceModifier
    ) {
        val clickModifier = if (isEnabled) {
            GlanceModifier.clickable(actionSendBroadcast(actionInfo.intent))
        } else {
            GlanceModifier
        }

        val backgroundModifier = if (isEnabled) {
            GlanceModifier.background(GlanceTheme.colors.secondaryContainer)
        } else {
            GlanceModifier.background(GlanceTheme.colors.surfaceVariant)
        }

        Row(
            modifier = modifier
                .cornerRadius(8.dp)
                .then(backgroundModifier)
                .then(clickModifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = GlanceModifier.size(16.dp),
                    color = GlanceTheme.colors.primary
                )
                Spacer(GlanceModifier.width(6.dp))
            } else {
                Image(
                    provider = ImageProvider(actionInfo.iconResId),
                    contentDescription = actionInfo.label,
                    modifier = GlanceModifier.size(16.dp)
                )
                Spacer(GlanceModifier.width(6.dp))
            }

            Text(
                text = if (isLoading) actionInfo.loadingLabel else actionInfo.label,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isEnabled) GlanceTheme.colors.onSecondaryContainer else GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }
}
