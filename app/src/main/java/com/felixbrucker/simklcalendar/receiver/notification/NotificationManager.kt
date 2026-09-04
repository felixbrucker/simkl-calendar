package com.felixbrucker.simklcalendar.receiver.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.felixbrucker.simklcalendar.MainActivity
import com.felixbrucker.simklcalendar.R
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.util.MediaFormatter
import com.felixbrucker.simklcalendar.data.util.PosterSize
import com.felixbrucker.simklcalendar.data.util.toPosterUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class NotificationManager {
    companion object {
        private const val CHANNEL_ID = "simkl_calendar_notifications"
        private const val TAG = "NotificationManager"
        suspend fun showNotification(item: CalendarItemWithWatchlist, context: Context) {
            createNotificationChannel(context)
            val isNotificationPermissionGranted = validateNotificationPermissionsGranted(context)
            if (!isNotificationPermissionGranted) {
                return
            }
            val notification = buildNotification(item, context)
            val notificationId = item.notificationId
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(notificationId, notification)
                Log.d(TAG, "Successfully displayed notification id=$notificationId")
            } catch (e: Exception) {
                Log.e(TAG, "Error posting notification", e)
            }
        }

        suspend fun updateNotification(
            item: CalendarItemWithWatchlist,
            context: Context,
        ) {
            createNotificationChannel(context)
            val isNotificationPermissionGranted = validateNotificationPermissionsGranted(context)
            if (!isNotificationPermissionGranted) {
                return
            }
            val notification = buildNotificationForUpdate(item, context)
            val notificationId = item.notificationId
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(notificationId, notification)
                Log.d(TAG, "Successfully updated notification id=$notificationId")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating notification", e)
            }
        }

        fun createNotificationChannel(context: Context) {
            val name = "Simkl Calendar Notifications"
            val descriptionText = "Notifications for airing episodes and movies as well as and seasons that finished airing."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        private suspend fun buildNotification(item: CalendarItemWithWatchlist, context: Context): Notification {
            return makeConfiguredNotificationBuilder(item, context).build()
        }

        private suspend fun makeConfiguredNotificationBuilder(item: CalendarItemWithWatchlist, context: Context): NotificationCompat.Builder {
            val db = AppDatabase.getDatabase(context)
            val itemsInSeason = db.calendarItemDao().getItemsInSeason(item.simklId, item.season)
            val totalEpisodesInSeason = itemsInSeason.maxOfOrNull { it.episodeNumber ?: 1 } ?: 1
            val (title, message) = item.formatNotificationContent(totalEpisodesInSeason)
            val openIntent = item.makeOpenReleaseDetailViewIntent(context)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(openIntent)
                .setAutoCancel(true)

            val isWatched = if (item.type == MediaType.MOVIE) {
                itemsInSeason.any { it.isWatched }
            } else if (item.isSeasonFinale) {
                itemsInSeason.all { it.isWatched }
            } else {
                item.isWatched
            }

            if (isWatched) {
                builder.setSubText("Watched")
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(message).setSummaryText("Watched"))
            } else {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(message))
            }

            val posterBitmap = loadPosterBitmap(context, item.poster)
            if (posterBitmap != null) {
                builder.setLargeIcon(posterBitmap)
            }

            // Add notification action buttons directly in the notification if not already watched
            if (!isWatched) {
                if (item.isSeasonFinale) {
                    builder.addAction(
                        R.drawable.ic_done_all,
                        "Mark Season as Watched",
                        item.makeMarkSeasonWatchedIntent(context)
                    )
                } else {
                    builder.addAction(
                        R.drawable.ic_check,
                        "Mark as Watched",
                        item.makeMarkWatchedIntent(context)
                    )
                }
            }

            return builder
        }

        private suspend fun buildNotificationForUpdate(item: CalendarItemWithWatchlist, context: Context): Notification {
            return makeConfiguredNotificationBuilder(item, context)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOnlyAlertOnce(true)
                .build()
        }

        private fun validateNotificationPermissionsGranted(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Cannot display notification.")
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Notification permission required to display notification", Toast.LENGTH_SHORT).show()
                    }
                    return false
                }
            }

            return true
        }

        private suspend fun loadPosterBitmap(context: Context, poster: String?): Bitmap? = withContext(Dispatchers.IO) {
            if (poster.isNullOrBlank()) return@withContext null
            try {
                val posterUrl = poster.toPosterUrl(PosterSize.COMPACT)
                val imageLoader = ImageLoader.Builder(context).build()
                val request = ImageRequest.Builder(context)
                    .data(posterUrl)
                    .allowHardware(false)
                    .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    result.drawable.toBitmap()
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load poster bitmap for notification", e)
                null
            }
        }
    }
}


fun CalendarItemWithWatchlist.makeOpenReleaseDetailViewIntent(context: Context): PendingIntent {
    val openIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(MainActivity.EXTRA_ITEM_KEY, primaryKey)
        val encodedKey = try {
            URLEncoder.encode(primaryKey, "UTF-8")
        } catch (_: Exception) {
            primaryKey
        }
        data = "simklcalendar://release_detail/$encodedKey".toUri()
    }

    return PendingIntent.getActivity(
        context,
        notificationId,
        openIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

fun CalendarItemWithWatchlist.makeMarkWatchedIntent(context: Context): PendingIntent {
    val markWatchedIntent = Intent(context, NotificationActionReceiver::class.java).apply {
        action = NotificationActionReceiver.ACTION_MARK_ITEM_WATCHED
        putExtra(NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY, primaryKey)
    }

    return PendingIntent.getBroadcast(
        context,
        notificationId * 10 + 1,
        markWatchedIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun CalendarItemWithWatchlist.makeMarkSeasonWatchedIntent(context: Context): PendingIntent {
    val markSeasonWatchedIntent = Intent(context, NotificationActionReceiver::class.java).apply {
        action = NotificationActionReceiver.ACTION_MARK_SEASON_WATCHED
        putExtra(NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY, primaryKey)
    }

    return PendingIntent.getBroadcast(
        context,
        notificationId * 10 + 2,
        markSeasonWatchedIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun CalendarItemWithWatchlist.formatNotificationContent(totalEpisodesInSeason: Int): Pair<String, String> {
    return MediaFormatter.formatNotificationContent(
        showTitle = title,
        type = type,
        episodeTitle = episodeTitle,
        season = season,
        episodeNumber = episodeNumber,
        isFinale = isSeasonFinale,
        totalEpisodes = totalEpisodesInSeason,
        movieReleaseType = movieReleaseType
    )
}

