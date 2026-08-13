package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Upcoming Airing!"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "An episode is ready to stream."
        val id = intent.getIntExtra(EXTRA_ID, 999)

        showNotification(context, title, message, id)
    }

    companion object {
        const val CHANNEL_ID = "simkl_episode_notifications"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_ID = "extra_id"

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Simkl Calendar Alerts"
                val descriptionText = "Local notifications for airing episodes and ready-to-binge series finales."
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
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

            // Configure modern look
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Safe fallback drawable icon
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
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
                " (S${season}E${episodeNumber})"
            } else ""

            val message = if (isLastEpisode) {
                "$showTitle$epLabel - Last episode released"
            } else {
                "$showTitle$epLabel - New episode released"
            }

            val id = (showTitle.hashCode() + (episodeNumber ?: 1))
            showNotification(context, title, message, id)
        }
    }
}
