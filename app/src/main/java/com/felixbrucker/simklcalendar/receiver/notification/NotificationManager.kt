package com.felixbrucker.simklcalendar.receiver.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager as SystemNotificationManager
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
import com.felixbrucker.simklcalendar.data.database.ActiveNotificationDao
import com.felixbrucker.simklcalendar.data.database.CalendarItemDao
import com.felixbrucker.simklcalendar.data.database.ActiveNotification
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.util.MediaFormatter
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import com.felixbrucker.simklcalendar.data.util.PosterSize
import com.felixbrucker.simklcalendar.extensions.toPosterUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calendarItemDao: CalendarItemDao,
    private val activeNotificationDao: ActiveNotificationDao,
    private val torrentServiceHelper: TorrentServiceHelper
) {
    companion object {
        private const val CHANNEL_ID = "simkl_calendar_notifications"
        private const val TAG = "NotificationManager"
    }

    suspend fun showNotification(
        item: CalendarItemWithWatchlist,
        loadingAction: String? = null,
    ) {
        createNotificationChannel()
        val isNotificationPermissionGranted = validateNotificationPermissionsGranted()
        if (!isNotificationPermissionGranted) {
            return
        }
        val notification = buildNotification(item, loadingAction)
        val notificationId = item.notificationId
        try {
            context.getSystemNotificationManager().notify(notificationId, notification)
            addActiveNotification(item.primaryKey)
            Timber.tag(TAG).d("Successfully displayed notification id=$notificationId")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error posting notification")
        }
    }

    suspend fun updateNotification(
        item: CalendarItemWithWatchlist,
        loadingAction: String? = null,
    ) {
        createNotificationChannel()
        val isNotificationPermissionGranted = validateNotificationPermissionsGranted()
        if (!isNotificationPermissionGranted) {
            return
        }

        val notificationId = item.notificationId
        val systemNotificationManager = context.getSystemNotificationManager()

        // Only update if the notification is currently active/visible
        val isActive = systemNotificationManager.activeNotifications.any { it.id == notificationId }
        if (!isActive) {
            return
        }

        val notification = buildNotificationForUpdate(item, loadingAction)
        try {
            systemNotificationManager.notify(notificationId, notification)
            Timber.tag(TAG).d("Successfully updated notification id=$notificationId")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error updating notification")
        }
    }

    suspend fun dismissNotification(itemPrimaryKey: String) {
        val item = calendarItemDao.findItem(itemPrimaryKey) ?: return
        dismissNotification(item = item)
    }

    suspend fun dismissNotification(item: CalendarItemWithWatchlist) {
        try {
            context.getSystemNotificationManager().cancel(item.notificationId)
            removeActiveNotification(item.primaryKey)
            Timber.tag(TAG).d("Successfully dismissed notification id=${item.notificationId} primaryKey=${item.primaryKey}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error dismissing notification id=${item.notificationId}")
        }
    }

    suspend fun addActiveNotification(primaryKey: String) {
        try {
            activeNotificationDao.insertActiveNotification(
                ActiveNotification(primaryKey = primaryKey)
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error inserting active notification primaryKey=$primaryKey")
        }
    }

    suspend fun removeActiveNotification(primaryKey: String) {
        try {
            activeNotificationDao.deleteActiveNotification(primaryKey)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error removing active notification primaryKey=$primaryKey")
        }
    }

    suspend fun getActiveNotifications(): List<String> {
        return try {
            activeNotificationDao.getAllActiveKeys()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error fetching active notification keys")
            emptyList()
        }
    }

    suspend fun restoreActiveNotifications() {
        createNotificationChannel()
        val isNotificationPermissionGranted = validateNotificationPermissionsGranted()
        if (!isNotificationPermissionGranted) {
            return
        }

        val activeKeys = getActiveNotifications()
        if (activeKeys.isEmpty()) {
            return
        }

        val systemNotificationManager = context.getSystemNotificationManager()
        val currentlyPostedIds = systemNotificationManager.activeNotifications.map { it.id }.toSet()

        for (primaryKey in activeKeys) {
            val item = calendarItemDao.findItem(primaryKey)
            if (item == null) {
                removeActiveNotification(primaryKey)
                continue
            }

            if (!currentlyPostedIds.contains(item.notificationId)) {
                val notification = buildNotification(item)
                try {
                    systemNotificationManager.notify(item.notificationId, notification)
                    Timber.tag(TAG).d("Restored missing notification primaryKey=$primaryKey id=${item.notificationId}")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error restoring notification primaryKey=$primaryKey")
                }
            }
        }
    }

    fun createNotificationChannel() {
        val name = "Simkl Calendar Notifications"
        val descriptionText = "Notifications for airing episodes and movies as well as and seasons that finished airing."
        val importance = SystemNotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemNotificationManager().createNotificationChannel(channel)
    }

    private suspend fun buildNotification(
        item: CalendarItemWithWatchlist,
        loadingAction: String? = null,
    ): Notification {
        return makeConfiguredNotificationBuilder(item, loadingAction).build()
    }

    private suspend fun makeConfiguredNotificationBuilder(
        item: CalendarItemWithWatchlist,
        loadingAction: String?,
    ): NotificationCompat.Builder {
        val itemsInSeasonOrRelatedItems = calendarItemDao
            .getItemsInSeasonOrRelatedItems(item.simklId, item.season)
        val totalEpisodesInSeason = itemsInSeasonOrRelatedItems.maxOfOrNull { it.episodeNumber ?: 1 } ?: 1
        val (title, message) = item.formatNotificationContent(totalEpisodesInSeason)
        val openPendingIntent = item.makeOpenReleaseDetailViewIntent(context)
        val openActivityIntent = item.makeOpenReleaseDetailViewActivityIntent(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPendingIntent)
            .setDeleteIntent(item.makeDismissNotificationIntent(context))
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
            when {
                itemsInSeasonOrRelatedItems.isNotEmpty() && itemsInSeasonOrRelatedItems.all { it.mediaStatus == MediaStatus.DOWNLOADED } -> MediaStatus.DOWNLOADED
                itemsInSeasonOrRelatedItems.any { it.mediaStatus == MediaStatus.DOWNLOADING } -> MediaStatus.DOWNLOADING
                itemsInSeasonOrRelatedItems.any { it.mediaStatus == MediaStatus.WANTED } -> MediaStatus.WANTED
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
        }

        val posterBitmap = loadPosterBitmap(item.poster)
        if (posterBitmap != null) {
            builder.setLargeIcon(posterBitmap)
        }

        val actions = mutableListOf<NotificationActionInfo>()

        if (!isWatched) {
            if (item.isSeasonFinale) {
                actions.add(
                    NotificationActionInfo(
                        action = NotificationActionReceiver.ACTION_MARK_SEASON_WATCHED,
                        label = "Mark Season Watched",
                        loadingLabel = "Marking Season Watched ..",
                        iconResId = R.drawable.ic_done_all,
                        intent = item.makeMarkSeasonWatchedBroadcastIntent(context)
                    )
                )
            } else {
                actions.add(
                    NotificationActionInfo(
                        action = NotificationActionReceiver.ACTION_MARK_ITEM_WATCHED,
                        label = "Mark Watched",
                        loadingLabel = "Marking Watched ..",
                        iconResId = R.drawable.ic_check,
                        intent = item.makeMarkWatchedBroadcastIntent(context)
                    )
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
                    actions.add(
                        NotificationActionInfo(
                            action = NotificationActionReceiver.ACTION_DOWNLOAD_ITEM,
                            label = "Download",
                            loadingLabel = "Searching ..",
                            iconResId = R.drawable.ic_download,
                            intent = digitalRelease.makeDownloadItemBroadcastIntent(context)
                        )
                    )
                }
            } else {
                val hasDownloadableEpisodes = itemsInSeasonOrRelatedItems.any {
                    it.mediaStatus == MediaStatus.IGNORED || it.mediaStatus == MediaStatus.WANTED
                }
                if (hasDownloadableEpisodes) {
                    if (item.isSeasonFinale) {
                        actions.add(
                            NotificationActionInfo(
                                action = NotificationActionReceiver.ACTION_DOWNLOAD_SEASON_MISSING_EPISODES,
                                label = "Download episodes",
                                loadingLabel = "Searching episodes ..",
                                iconResId = R.drawable.ic_download,
                                intent = item.makeDownloadSeasonMissingEpisodesBroadcastIntent(context)
                            )
                        )
                    } else if (item.mediaStatus == MediaStatus.IGNORED || item.mediaStatus == MediaStatus.WANTED) {
                        actions.add(
                            NotificationActionInfo(
                                action = NotificationActionReceiver.ACTION_DOWNLOAD_ITEM,
                                label = "Download",
                                loadingLabel = "Searching ..",
                                iconResId = R.drawable.ic_download,
                                intent = item.makeDownloadItemBroadcastIntent(context)
                            )
                        )
                    }
                }
            }
        }

        val collapsedRemoteViews = NotificationGlanceHelper.buildCollapsedRemoteViews(
            context = context,
            title = title,
            message = message,
            openIntent = openActivityIntent
        )
        val expandedRemoteViews = NotificationGlanceHelper.buildExpandedRemoteViews(
            context = context,
            title = title,
            message = message,
            openIntent = openActivityIntent,
            actions = actions,
            loadingAction = loadingAction
        )

        builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
        builder.setCustomContentView(collapsedRemoteViews)
        builder.setCustomBigContentView(expandedRemoteViews)

        return builder
    }

    private suspend fun buildNotificationForUpdate(
        item: CalendarItemWithWatchlist,
        loadingAction: String?,
    ): Notification {
        return makeConfiguredNotificationBuilder(item, loadingAction)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun validateNotificationPermissionsGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Timber.tag(TAG).w("POST_NOTIFICATIONS permission not granted. Cannot display notification.")
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Notification permission required to display notification", Toast.LENGTH_SHORT).show()
                }
                return false
            }
        }

        return true
    }

    private suspend fun loadPosterBitmap(poster: String?): Bitmap? = withContext(Dispatchers.IO) {
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
            Timber.tag(TAG).e(e, "Failed to load poster bitmap for notification")
            null
        }
    }
}

fun Context.getSystemNotificationManager(): SystemNotificationManager {
    return getSystemService(Context.NOTIFICATION_SERVICE) as SystemNotificationManager
}

fun CalendarItemWithWatchlist.makeOpenReleaseDetailViewActivityIntent(context: Context): Intent {
    return Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(MainActivity.EXTRA_ITEM_KEY, primaryKey)
        data = "simklcalendar://release_detail/$primaryKey".toUri() // enforces uniqueness
    }
}

fun CalendarItemWithWatchlist.makeOpenReleaseDetailViewIntent(context: Context): PendingIntent {
    val openIntent = makeOpenReleaseDetailViewActivityIntent(context)
    return PendingIntent.getActivity(
        context,
        notificationId,
        openIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

fun CalendarItemWithWatchlist.makeMarkWatchedBroadcastIntent(context: Context): Intent {
    return Intent(context, NotificationActionReceiver::class.java).apply {
        action = NotificationActionReceiver.ACTION_MARK_ITEM_WATCHED
        data = "simklcalendar://item/$primaryKey".toUri() // enforces uniqueness
        putExtra(NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY, primaryKey)
    }
}

fun CalendarItemWithWatchlist.makeDismissNotificationIntent(context: Context): PendingIntent {
    val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
        action = NotificationActionReceiver.ACTION_NOTIFICATION_DISMISSED
        data = "simklcalendar://item/$primaryKey".toUri() // enforces uniqueness
        putExtra(NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY, primaryKey)
    }

    return PendingIntent.getBroadcast(
        context,
        notificationId * 10 + 5,
        dismissIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun CalendarItemWithWatchlist.makeMarkSeasonWatchedBroadcastIntent(context: Context): Intent {
    return Intent(context, NotificationActionReceiver::class.java).apply {
        action = NotificationActionReceiver.ACTION_MARK_SEASON_WATCHED
        data = "simklcalendar://item/$primaryKey".toUri() // enforces uniqueness
        putExtra(NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY, primaryKey)
    }
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

fun CalendarItemWithWatchlist.makeDownloadItemBroadcastIntent(context: Context): Intent {
    return Intent(context, NotificationActionReceiver::class.java).apply {
        action = NotificationActionReceiver.ACTION_DOWNLOAD_ITEM
        data = "simklcalendar://item/$primaryKey".toUri() // enforces uniqueness
        putExtra(NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY, primaryKey)
    }
}

fun CalendarItemWithWatchlist.makeDownloadSeasonMissingEpisodesBroadcastIntent(context: Context): Intent {
    return Intent(context, NotificationActionReceiver::class.java).apply {
        action = NotificationActionReceiver.ACTION_DOWNLOAD_SEASON_MISSING_EPISODES
        data = "simklcalendar://item/$primaryKey".toUri() // enforces uniqueness
        putExtra(NotificationActionReceiver.EXTRA_ITEM_PRIMARY_KEY, primaryKey)
    }
}
