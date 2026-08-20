package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.widget.Toast
import com.example.MainActivity
import com.example.R
import com.example.data.database.AppDatabase
import com.example.data.database.CalendarItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        Log.d(TAG, "NotificationReceiver received action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Reschedule all alarms upon system boot or app update
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    NotificationScheduler.scheduleAllNotifications(context)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Upcoming Airing!"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "An episode is ready to stream."
        val id = intent.getIntExtra(EXTRA_ID, 999)
        val itemKey = intent.getStringExtra(EXTRA_ITEM_KEY)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (itemKey != null) {
                    val db = AppDatabase.getDatabase(context)
                    db.calendarItemDao().markItemAsNotified(itemKey)
                }
                showNotification(context, title, message, id)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling broadcast notification in receiver", e)
                showNotification(context, title, message, id)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NotificationReceiver"
        const val CHANNEL_ID = "simkl_episode_notifications"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_ID = "extra_id"
        const val EXTRA_ITEM_KEY = "extra_item_key"

        /**
         * Formats notification title and message from raw parameters.
         * Finale notifications display season and total episode count without the individual episode number.
         */
        fun formatNotificationContent(
            showTitle: String,
            type: String,
            episodeTitle: String?,
            season: Int?,
            episodeNumber: Int?,
            isFinale: Boolean,
            totalEpisodes: Int? = null
        ): Pair<String, String> {
            return if (type == "movie") {
                val isTheater = episodeTitle?.contains("theater", ignoreCase = true) == true
                if (isTheater) {
                    val title = "Movie In Theaters Today"
                    val message = "$showTitle is now playing in theaters!"
                    title to message
                } else {
                    val title = "Movie Released Today"
                    val message = "$showTitle is now available on Digital / DVD!"
                    title to message
                }
            } else if (isFinale) {
                val title = "Season finished airing"
                val total = totalEpisodes ?: episodeNumber
                val episodeCountStr = if (total != null) {
                    "$total ${if (total == 1) "Episode" else "Episodes"}"
                } else null

                val finaleTag = if (type == "anime") {
                    if (episodeCountStr != null) " ($episodeCountStr)" else ""
                } else {
                    if (season != null && episodeCountStr != null) {
                        " (Season $season, $episodeCountStr)"
                    } else if (season != null) {
                        " (Season $season)"
                    } else if (episodeCountStr != null) {
                        " ($episodeCountStr)"
                    } else ""
                }
                val message = "$showTitle$finaleTag"
                title to message
            } else {
                val title = "New Episode Released"
                val epLabel = if (type == "anime") {
                    if (episodeNumber != null) " (Episode $episodeNumber)" else ""
                } else if (season != null && episodeNumber != null) {
                    String.format(Locale.US, " (S%02dE%02d)", season, episodeNumber)
                } else if (episodeNumber != null) {
                    " (Episode $episodeNumber)"
                } else ""
                val epName = if (!episodeTitle.isNullOrBlank()) " \"$episodeTitle\"" else ""
                val message = "$showTitle$epLabel$epName is now airing."
                title to message
            }
        }

        /**
         * Formats notification title and message for a CalendarItem entity.
         */
        fun formatNotificationContent(
            item: CalendarItem,
            isFinale: Boolean,
            totalEpisodes: Int? = null
        ): Pair<String, String> {
            return formatNotificationContent(
                showTitle = item.title,
                type = item.type,
                episodeTitle = item.episodeTitle,
                season = item.season,
                episodeNumber = item.episodeNumber,
                isFinale = isFinale,
                totalEpisodes = totalEpisodes
            )
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Simkl Calendar Alerts"
                val descriptionText = "Local notifications for airing episodes and seasons that finished airing."
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableVibration(true)
                    enableLights(true)
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun showNotification(context: Context, title: String, message: String, notificationId: Int) {
            createNotificationChannel(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Cannot display notification.")
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Notification permission required to display alert", Toast.LENGTH_SHORT).show()
                    }
                    return
                }
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(notificationId, builder.build())
                Log.d(TAG, "Successfully displayed notification id=$notificationId: $title")
            } catch (e: Exception) {
                Log.e(TAG, "Error posting notification", e)
            }
        }

        fun triggerEpisodeNotification(
            context: Context,
            showTitle: String,
            episodeName: String?,
            season: Int?,
            episodeNumber: Int?,
            isLastEpisode: Boolean,
            type: String = "tv",
            totalEpisodes: Int? = null
        ) {
            val (title, message) = formatNotificationContent(
                showTitle = showTitle,
                type = type,
                episodeTitle = episodeName,
                season = season,
                episodeNumber = episodeNumber,
                isFinale = isLastEpisode,
                totalEpisodes = totalEpisodes
            )

            val id = Math.abs(showTitle.hashCode() + (episodeNumber ?: 1))
            showNotification(context, title, message, id)
        }
    }
}
