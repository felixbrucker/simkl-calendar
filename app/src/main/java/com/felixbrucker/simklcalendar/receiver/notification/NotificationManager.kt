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
import timber.log.Timber
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
import com.felixbrucker.simklcalendar.data.database.ActiveNotification
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import com.felixbrucker.simklcalendar.data.util.MediaFormatter
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import com.felixbrucker.simklcalendar.data.util.PosterSize
import com.felixbrucker.simklcalendar.extensions.toPosterUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val torrentServiceHelper: TorrentServiceHelper
) {
    companion object {
        private const val CHANNEL_ID = "simkl_calendar_notifications"
        private const val TAG = "NotificationManager"
    }

    suspend fun showNotification(item: CalendarItemWithWatchlist, customContext: Context = context) {
        createNotificationChannel(customContext)
        val isNotificationPermissionGranted = validateNotificationPermissionsGranted(customContext)
        if (!isNotificationPermissionGranted) {
            return
        }
        val notification = buildNotification(item, customContext)
        val notificationId = item.notificationId
        try {
            val systemNotificationManager = customContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            systemNotificationManager.notify(notificationId, notification)
            addActiveNotification(customContext, item.primaryKey)
            Timber.tag(TAG).d("Successfully displayed notification id=$notificationId")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error posting notification")
        }
    }

    suspend fun updateNotification(
        item: CalendarItemWithWatchlist,
        customContext: Context = context,
    ) {
        createNotificationChannel(customContext)
        val isNotificationPermissionGranted = validateNotificationPermissionsGranted(customContext)
        if (!isNotificationPermissionGranted) {
            return
        }

        val notificationId = item.notificationId
        val systemNotificationManager =
            customContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        // Only update if the notification is currently active/visible
        val isActive = systemNotificationManager.activeNotifications.any { it.id == notificationId }
        if (!isActive) {
            Timber.tag(TAG).d("Notification id=$notificationId is not active, skipping update.")
            return
        }

        val notification = buildNotificationForUpdate(item, customContext)
        try {
            systemNotificationManager.notify(notificationId, notification)
            Timber.tag(TAG).d("Successfully updated notification id=$notificationId")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error updating notification")
        }
    }

    suspend fun dismissNotification(item: CalendarItemWithWatchlist, customContext: Context = context) {
        try {
            val systemNotificationManager =
                customContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            systemNotificationManager.cancel(item.notificationId)
            removeActiveNotification(customContext, item.primaryKey)
            Timber.tag(TAG).d("Successfully dismissed notification id=${item.notificationId} primaryKey=${item.primaryKey}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error dismissing notification id=${item.notificationId}")
        }
    }

    suspend fun addActiveNotification(customContext: Context = context, primaryKey: String) {
        try {
            db.activeNotificationDao().insertActiveNotification(
                ActiveNotification(primaryKey = primaryKey)
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error inserting active notification primaryKey=$primaryKey")
        }
    }

    suspend fun removeActiveNotification(customContext: Context = context, primaryKey: String) {
        try {
            db.activeNotificationDao().deleteActiveNotification(primaryKey)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error removing active notification primaryKey=$primaryKey")
        }
    }

    suspend fun getActiveNotifications(customContext: Context = context): List<String> {
        return try {
            db.activeNotificationDao().getAllActiveKeys()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error fetching active notification keys")
            emptyList()
        }
    }

    suspend fun restoreActiveNotifications(customContext: Context = context) {
        createNotificationChannel(customContext)
        val isNotificationPermissionGranted = validateNotificationPermissionsGranted(customContext)
        if (!isNotificationPermissionGranted) {
            return
        }

        val activeKeys = getActiveNotifications(customContext)
        if (activeKeys.isEmpty()) {
            return
        }

        val systemNotificationManager =
            customContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val currentlyPostedIds = systemNotificationManager.activeNotifications.map { it.id }.toSet()

        for (primaryKey in activeKeys) {
            val item = db.calendarItemDao().findItem(primaryKey)
            if (item == null) {
                removeActiveNotification(customContext, primaryKey)
                continue
            }

            if (!currentlyPostedIds.contains(item.notificationId)) {
                val notification = buildNotification(item, customContext)
                try {
                    systemNotificationManager.notify(item.notificationId, notification)
                    Timber.tag(TAG).d("Restored missing notification primaryKey=$primaryKey id=${item.notificationId}")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error restoring notification primaryKey=$primaryKey")
                }
            }
        }
    }

    fun createNotificationChannel(customContext: Context = context) {
        val name = "Simkl Calendar Notifications"
        val descriptionText = "Notifications for airing episodes and movies as well as and seasons that finished airing."
        val importance = android.app.NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val systemNotificationManager = customContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        systemNotificationManager.createNotificationChannel(channel)
    }

    private suspend fun buildNotification(item: CalendarItemWithWatchlist, customContext: Context): Notification {
        return makeConfiguredNotificationBuilder(item, customContext).build()
    }

    private suspend fun makeConfiguredNotificationBuilder(item: CalendarItemWithWatchlist, customContext: Context): NotificationCompat.Builder {
        val itemsInSeasonOrRelatedItems = db
            .calendarItemDao()
            .getItemsInSeasonOrRelatedItems(item.simklId, item.season)
        val totalEpisodesInSeason = itemsInSeasonOrRelatedItems.maxOfOrNull { it.episodeNumber ?: 1 } ?: 1
        val (title, message) = item.formatNotificationContent(totalEpisodesInSeason)
        val openIntent = item.makeOpenReleaseDetailViewIntent(customContext)

        val builder = NotificationCompat.Builder(customContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openIntent)
            .setDeleteIntent(item.makeDismissNotificationIntent(customContext))
            .setAutoCancel(true)

        val isWatched = if (item.type == MediaType.MOVIE) {
            itemsInSeasonOrRelatedItems.any { it.isWatched }
        } else if (item.isSeasonFinale) {
            itemsInSeasonOrRelatedItems.all { it.isWatched }
        } else {
            item.isWatched
        }

        // Calculate aggregate media status
        val aggregateMediaStatus = if (item.type == MediaType.MOVIE) {
            itemsInSeasonOrRelatedItems.firstOrNull { it.movieReleaseType == MovieReleaseType.DIGITAL }?.mediaStatus
                ?: item.mediaStatus
        } else if (item.isSeasonFinale) {
            val statuses = itemsInSeasonOrRelatedItems.map { it.mediaStatus }
            when {
                statuses.all { it == MediaStatus.DOWNLOADED } -> MediaStatus.DOWNLOADED
                statuses.any { it == MediaStatus.DOWNLOADING } -> MediaStatus.DOWNLOADING
                statuses.any { it == MediaStatus.WANTED } -> MediaStatus.WANTED
                else -> MediaStatus.IGNORED
            }
        } else {
            item.mediaStatus
        }

        val statusText = when (aggregateMediaStatus) {
            MediaStatus.DOWNLOADED -> "Downloaded"
            MediaStatus.DOWNLOADING -> "Downloading"
            MediaStatus.WANTED -> "Wanted"
            else -> null
        }

        val subText = listOfNotNull(
            if (isWatched) "Watched" else null,
            statusText
        ).joinToString(" · ")

        if (subText.isNotEmpty()) {
            builder.setSubText(subText)
            builder.setStyle(
                NotificationCompat.BigTextStyle().bigText(message).setSummaryText(subText)
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(message))
        }

        val posterBitmap = loadPosterBitmap(customContext, item.poster)
        if (posterBitmap != null) {
            builder.setLargeIcon(posterBitmap)
        }

        // Add notification action buttons
        if (!isWatched) {
            if (item.isSeasonFinale) {
                builder.addAction(
                    R.drawable.ic_done_all,
                    "Mark Season as Watched",
                    item.makeMarkSeasonWatchedIntent(customContext)
                )
            } else {
                builder.addAction(
                    R.drawable.ic_check,
                    "Mark as Watched",
                    item.makeMarkWatchedIntent(customContext)
                )
            }
        }

        val isTorrentServiceInstalled = torrentServiceHelper.isInstalled.value
        if (isTorrentServiceInstalled) {
            // Download actions
            if (item.type == MediaType.MOVIE) {
                val digitalRelease =
                    itemsInSeasonOrRelatedItems.find { it.movieReleaseType == MovieReleaseType.DIGITAL }
                if (digitalRelease != null && (digitalRelease.mediaStatus == MediaStatus.IGNORED || digitalRelease.mediaStatus == MediaStatus.WANTED)) {
                    builder.addAction(
                        R.drawable.ic_download,
                        "Download",
                        digitalRelease.makeDownloadItemIntent(customContext)
                    )
                }
            } else {
                val hasDownloadableEpisodes =
                    itemsInSeasonOrRelatedItems.any { it.mediaStatus == MediaStatus.IGNORED || it.mediaStatus == MediaStatus.WANTED }
                if (hasDownloadableEpisodes) {
                    if (item.isSeasonFinale) {
                        builder.addAction(
                            R.drawable.ic_download,
                            "Download missing episodes",
                            item.makeDownloadSeasonMissingEpisodesIntent(customContext)
                        )
                    } else if (item.mediaStatus == MediaStatus.IGNORED || item.mediaStatus == MediaStatus.WANTED) {
                        builder.addAction(
                            R.drawable.ic_download,
                            "Download",
                            item.makeDownloadItemIntent(customContext)
                        )
                    }
                }
            }
        }

        return builder
    }

    private suspend fun buildNotificationForUpdate(item: CalendarItemWithWatchlist, customContext: Context): Notification {
        return makeConfiguredNotificationBuilder(item, customContext)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun validateNotificationPermissionsGranted(customContext: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    customContext,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Timber.tag(TAG).w("POST_NOTIFICATIONS permission not granted. Cannot display notification.")
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(customContext, "Notification permission required to display notification", Toast.LENGTH_SHORT).show()
                }
                return false
            }
        }

        return true
    }

    private suspend fun loadPosterBitmap(customContext: Context, poster: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (poster.isNullOrBlank()) return@withContext null
        try {
            val posterUrl = poster.toPosterUrl(PosterSize.COMPACT)
            val imageLoader = ImageLoader.Builder(customContext).build()
            val request = ImageRequest.Builder(customContext)
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
            Timber.tag(TAG).e(e, "Failed to load poster bitmap for notification")
            null
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

fun CalendarItemWithWatchlist.makeDismissNotificationIntent(context: Context): PendingIntent {
    val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
        action = NotificationActionReceiver.ACTION_NOTIFICATION_DISMISSED
        putExtra(NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY, primaryKey)
    }

    return PendingIntent.getBroadcast(
        context,
        notificationId * 10 + 5,
        dismissIntent,
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

fun CalendarItemWithWatchlist.makeDownloadItemIntent(context: Context): PendingIntent {
    val downloadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
        action = NotificationActionReceiver.ACTION_DOWNLOAD_ITEM
        putExtra(NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY, primaryKey)
    }

    return PendingIntent.getBroadcast(
        context,
        notificationId * 10 + 3,
        downloadIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun CalendarItemWithWatchlist.makeDownloadSeasonMissingEpisodesIntent(context: Context): PendingIntent {
    val downloadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
        action = NotificationActionReceiver.ACTION_DOWNLOAD_SEASON_MISSING_EPISODES
        putExtra(NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY, primaryKey)
    }

    return PendingIntent.getBroadcast(
        context,
        notificationId * 10 + 4,
        downloadIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

