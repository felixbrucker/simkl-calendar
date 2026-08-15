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
import com.example.MainActivity
import com.example.data.database.AppDatabase
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

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Simkl Calendar Alerts"
                val descriptionText = "Local notifications for airing episodes and ready-to-binge series finales."
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
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationId, builder.build())
        }

        fun triggerEpisodeNotification(context: Context, showTitle: String, episodeName: String?, season: Int?, episodeNumber: Int?, isLastEpisode: Boolean) {
            val title = if (isLastEpisode) {
                "Last episode released"
            } else {
                "New episode released"
            }

            val epLabel = if (season != null && episodeNumber != null) {
                String.format(Locale.US, " (S%02dE%02d)", season, episodeNumber)
            } else if (episodeNumber != null) {
                " (Episode $episodeNumber)"
            } else ""

            val epName = if (!episodeName.isNullOrBlank()) " \"$episodeName\"" else ""

            val message = if (isLastEpisode) {
                "$showTitle$epLabel - Ready to binge!"
            } else {
                "$showTitle$epLabel$epName is now airing."
            }

            val id = (showTitle.hashCode() + (episodeNumber ?: 1))
            showNotification(context, title, message, id)
        }
    }
}
