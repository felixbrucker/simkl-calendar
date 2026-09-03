package com.felixbrucker.simklcalendar.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.felixbrucker.simklcalendar.MainActivity
import com.felixbrucker.simklcalendar.R
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.database.CalendarItemWithWatchlist
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.model.MovieReleaseType
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import com.felixbrucker.simklcalendar.data.util.MediaFormatter
import com.felixbrucker.simklcalendar.data.util.PosterSize
import com.felixbrucker.simklcalendar.data.util.toPosterUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import androidx.core.net.toUri

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

        when (action) {
            ACTION_MARK_EPISODE_WATCHED -> {
                handleMarkEpisodeWatched(context, intent)
                return
            }
            ACTION_MARK_SEASON_WATCHED -> {
                handleMarkSeasonWatched(context, intent)
                return
            }
            ACTION_MARK_MOVIE_WATCHED -> {
                handleMarkMovieWatched(context, intent)
                return
            }
            ACTION_DOWNLOAD_COMPLETED -> {
                handleDownloadCompleted(context, intent)
                return
            }
        }

        if (action == ACTION_AIR_DATE_ALERT) {
            handleAirDateAlert(context, intent)
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Upcoming Airing!"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "An episode is ready to stream."
        val id = intent.getIntExtra(EXTRA_ID, 999)
        val itemKey = intent.getStringExtra(EXTRA_ITEM_KEY)
        val simklId = if (intent.hasExtra(EXTRA_SIMKL_ID)) intent.getIntExtra(EXTRA_SIMKL_ID, 0).takeIf { it != 0 } else null
        val season = if (intent.hasExtra(EXTRA_SEASON)) intent.getIntExtra(EXTRA_SEASON, -1).takeIf { it != -1 } else null
        val episodeNumber = if (intent.hasExtra(EXTRA_EPISODE_NUMBER)) intent.getIntExtra(EXTRA_EPISODE_NUMBER, -1).takeIf { it != -1 } else null
        val mediaTypeName = intent.getStringExtra(EXTRA_MEDIA_TYPE)
        val mediaType = mediaTypeName?.let { runCatching { MediaType.valueOf(it) }.getOrNull() } ?: MediaType.TV
        val isFinale = intent.getBooleanExtra(EXTRA_IS_FINALE, false)
        val showTitle = intent.getStringExtra(EXTRA_SHOW_TITLE)
        val poster = intent.getStringExtra(EXTRA_POSTER)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var resolvedPoster = poster
                if (itemKey != null) {
                    val db = AppDatabase.getDatabase(context)
                    db.calendarItemDao().markItemAsNotified(itemKey)
                    if (resolvedPoster == null) {
                        resolvedPoster = db.calendarItemDao().findItem(itemKey)?.poster
                    }
                }
                showNotification(
                    context = context,
                    title = title,
                    message = message,
                    notificationId = id,
                    itemKey = itemKey,
                    simklId = simklId,
                    season = season,
                    episodeNumber = episodeNumber,
                    type = mediaType,
                    isFinale = isFinale,
                    showTitle = showTitle,
                    poster = resolvedPoster
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error handling broadcast notification in receiver", e)
                showNotification(
                    context = context,
                    title = title,
                    message = message,
                    notificationId = id,
                    itemKey = itemKey,
                    simklId = simklId,
                    season = season,
                    episodeNumber = episodeNumber,
                    type = mediaType,
                    isFinale = isFinale,
                    showTitle = showTitle,
                    poster = poster
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleMarkEpisodeWatched(context: Context, intent: Intent) {
        val simklId = intent.getIntExtra(EXTRA_SIMKL_ID, 0)
        val season = if (intent.hasExtra(EXTRA_SEASON)) intent.getIntExtra(EXTRA_SEASON, -1).takeIf { it != -1 } else null
        val episodeNumber = intent.getIntExtra(EXTRA_EPISODE_NUMBER, 1)
        val mediaTypeName = intent.getStringExtra(EXTRA_MEDIA_TYPE)
        val mediaType = mediaTypeName?.let { runCatching { MediaType.valueOf(it) }.getOrNull() } ?: MediaType.TV
        val notificationId = intent.getIntExtra(EXTRA_ID, 0)
        val showTitle = intent.getStringExtra(EXTRA_SHOW_TITLE)
        val itemKey = intent.getStringExtra(EXTRA_ITEM_KEY)
        val originalTitle = intent.getStringExtra(EXTRA_TITLE)
        val originalMessage = intent.getStringExtra(EXTRA_MESSAGE)
        val poster = intent.getStringExtra(EXTRA_POSTER)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var resolvedPoster = poster
                if (resolvedPoster == null && itemKey != null) {
                    val db = AppDatabase.getDatabase(context)
                    resolvedPoster = db.calendarItemDao().findItem(itemKey)?.poster
                }

                val repo = SimklRepository(context)
                val result = repo.markEpisodeWatched(
                    simklId = simklId,
                    season = season,
                    episodeNumber = episodeNumber,
                    mediaType = mediaType
                )

                if (result.isSuccess) {
                    val statusBadge = "Watched"
                    updateNotificationResult(
                        context = context,
                        notificationId = notificationId,
                        title = originalTitle ?: showTitle ?: "Episode",
                        message = originalMessage ?: "Episode marked as watched",
                        badgeStatus = statusBadge,
                        itemKey = itemKey,
                        poster = resolvedPoster
                    )
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Failed to mark as watched"
                    updateNotificationResult(
                        context = context,
                        notificationId = notificationId,
                        title = originalTitle ?: showTitle ?: "Episode",
                        message = originalMessage ?: err,
                        badgeStatus = "Failed to mark as watched",
                        itemKey = itemKey,
                        poster = resolvedPoster
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking episode as watched from notification action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleMarkSeasonWatched(context: Context, intent: Intent) {
        val simklId = intent.getIntExtra(EXTRA_SIMKL_ID, 0)
        val season = intent.getIntExtra(EXTRA_SEASON, 1)
        val mediaTypeName = intent.getStringExtra(EXTRA_MEDIA_TYPE)
        val mediaType = mediaTypeName?.let { runCatching { MediaType.valueOf(it) }.getOrNull() } ?: MediaType.TV
        val notificationId = intent.getIntExtra(EXTRA_ID, 0)
        val showTitle = intent.getStringExtra(EXTRA_SHOW_TITLE)
        val itemKey = intent.getStringExtra(EXTRA_ITEM_KEY)
        val originalTitle = intent.getStringExtra(EXTRA_TITLE)
        val originalMessage = intent.getStringExtra(EXTRA_MESSAGE)
        val poster = intent.getStringExtra(EXTRA_POSTER)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var resolvedPoster = poster
                if (resolvedPoster == null && itemKey != null) {
                    val db = AppDatabase.getDatabase(context)
                    resolvedPoster = db.calendarItemDao().findItem(itemKey)?.poster
                }

                val repo = SimklRepository(context)
                val result = repo.markSeasonWatched(
                    simklId = simklId,
                    season = season,
                    mediaType = mediaType
                )

                if (result.isSuccess) {
                    val isCompleted = result.getOrDefault(false)
                    val seasonLabel = MediaFormatter.formatSeasonLabel(mediaType, season)
                    val statusBadge = if (isCompleted) "Show Completed" else "$seasonLabel Watched"
                    updateNotificationResult(
                        context = context,
                        notificationId = notificationId,
                        title = originalTitle ?: showTitle ?: "Season",
                        message = originalMessage ?: "Season marked as watched",
                        badgeStatus = statusBadge,
                        itemKey = itemKey,
                        poster = resolvedPoster
                    )
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Failed to mark season as watched"
                    updateNotificationResult(
                        context = context,
                        notificationId = notificationId,
                        title = originalTitle ?: showTitle ?: "Season",
                        message = originalMessage ?: err,
                        badgeStatus = "Failed to mark season as watched",
                        itemKey = itemKey,
                        poster = resolvedPoster
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking season as watched from notification action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleMarkMovieWatched(context: Context, intent: Intent) {
        val simklId = intent.getIntExtra(EXTRA_SIMKL_ID, 0)
        val notificationId = intent.getIntExtra(EXTRA_ID, 0)
        val showTitle = intent.getStringExtra(EXTRA_SHOW_TITLE)
        val itemKey = intent.getStringExtra(EXTRA_ITEM_KEY)
        val originalTitle = intent.getStringExtra(EXTRA_TITLE)
        val originalMessage = intent.getStringExtra(EXTRA_MESSAGE)
        val poster = intent.getStringExtra(EXTRA_POSTER)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var resolvedPoster = poster
                if (resolvedPoster == null && itemKey != null) {
                    val db = AppDatabase.getDatabase(context)
                    resolvedPoster = db.calendarItemDao().findItem(itemKey)?.poster
                }

                val repo = SimklRepository(context)
                val result = repo.markMovieWatched(simklId = simklId)

                if (result.isSuccess) {
                    val statusBadge = "Watched"
                    updateNotificationResult(
                        context = context,
                        notificationId = notificationId,
                        title = originalTitle ?: showTitle ?: "Movie",
                        message = originalMessage ?: "Movie marked as watched",
                        badgeStatus = statusBadge,
                        itemKey = itemKey,
                        poster = resolvedPoster
                    )
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Failed to mark movie as watched"
                    updateNotificationResult(
                        context = context,
                        notificationId = notificationId,
                        title = originalTitle ?: showTitle ?: "Movie",
                        message = originalMessage ?: err,
                        badgeStatus = "Failed to mark movie as watched",
                        itemKey = itemKey,
                        poster = resolvedPoster
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking movie as watched from notification action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleDownloadCompleted(context: Context, intent: Intent) {
        val itemKey = intent.getStringExtra(EXTRA_ITEM_KEY) ?: return
        Log.d(TAG, "Download completed for item: $itemKey")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = SimklRepository(context)
                repo.updateDownloadTaskId(itemKey, null, com.felixbrucker.simklcalendar.data.model.MediaStatus.DOWNLOADED)
                Log.d(TAG, "Updated item $itemKey to DOWNLOADED status and cleared taskId")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling download completion", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleAirDateAlert(context: Context, intent: Intent) {
        val itemKey = intent.getStringExtra(EXTRA_ITEM_KEY) ?: return
        val shouldNotify = intent.getBooleanExtra(EXTRA_SHOULD_NOTIFY, false)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Upcoming Airing!"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "An episode is ready to stream."
        val id = intent.getIntExtra(EXTRA_ID, 999)
        val simklId = if (intent.hasExtra(EXTRA_SIMKL_ID)) intent.getIntExtra(EXTRA_SIMKL_ID, 0).takeIf { it != 0 } else null
        val season = if (intent.hasExtra(EXTRA_SEASON)) intent.getIntExtra(EXTRA_SEASON, -1).takeIf { it != -1 } else null
        val episodeNumber = if (intent.hasExtra(EXTRA_EPISODE_NUMBER)) intent.getIntExtra(EXTRA_EPISODE_NUMBER, -1).takeIf { it != -1 } else null
        val mediaTypeName = intent.getStringExtra(EXTRA_MEDIA_TYPE)
        val mediaType = mediaTypeName?.let { runCatching { MediaType.valueOf(it) }.getOrNull() } ?: MediaType.TV
        val isFinale = intent.getBooleanExtra(EXTRA_IS_FINALE, false)
        val showTitle = intent.getStringExtra(EXTRA_SHOW_TITLE)
        val poster = intent.getStringExtra(EXTRA_POSTER)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = SimklRepository(context)

                // FIRST: Ensure status transitions to WANTED/IGNORED
                repo.updateItemAiredStatus(itemKey)

                // SECOND: Check if we should notify
                if (shouldNotify) {
                    val db = AppDatabase.getDatabase(context)
                    db.calendarItemDao().markItemAsNotified(itemKey)

                    var resolvedPoster = poster
                    if (resolvedPoster == null) {
                        resolvedPoster = db.calendarItemDao().findItem(itemKey)?.poster
                    }

                    showNotification(
                        context = context,
                        title = title,
                        message = message,
                        notificationId = id,
                        itemKey = itemKey,
                        simklId = simklId,
                        season = season,
                        episodeNumber = episodeNumber,
                        type = mediaType,
                        isFinale = isFinale,
                        showTitle = showTitle,
                        poster = resolvedPoster
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling air date alert", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NotificationReceiver"
        const val CHANNEL_ID = "simkl_episode_notifications"
        const val ACTION_AIR_DATE_ALERT = "com.felixbrucker.simklcalendar.ACTION_AIR_DATE_ALERT"
        const val ACTION_MARK_EPISODE_WATCHED = "com.felixbrucker.simklcalendar.ACTION_MARK_EPISODE_WATCHED"
        const val ACTION_MARK_SEASON_WATCHED = "com.felixbrucker.simklcalendar.ACTION_MARK_SEASON_WATCHED"
        const val ACTION_MARK_MOVIE_WATCHED = "com.felixbrucker.simklcalendar.ACTION_MARK_MOVIE_WATCHED"
        const val ACTION_DOWNLOAD_COMPLETED = "com.felixbrucker.simklcalendar.ACTION_DOWNLOAD_COMPLETED"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_ID = "extra_id"
        const val EXTRA_ITEM_KEY = "extra_item_key"
        const val EXTRA_SIMKL_ID = "extra_simkl_id"
        const val EXTRA_SEASON = "extra_season"
        const val EXTRA_EPISODE_NUMBER = "extra_episode_number"
        const val EXTRA_MEDIA_TYPE = "extra_media_type"
        const val EXTRA_IS_FINALE = "extra_is_finale"
        const val EXTRA_SHOW_TITLE = "extra_show_title"
        const val EXTRA_MOVIE_RELEASE_TYPE = "extra_movie_release_type"
        const val EXTRA_POSTER = "extra_poster"
        const val EXTRA_SHOULD_NOTIFY = "extra_should_notify"

        /**
         * Formats notification title and message from raw parameters using type-safe enums.
         */
        fun formatNotificationContent(
            showTitle: String,
            type: MediaType,
            episodeTitle: String?,
            season: Int?,
            episodeNumber: Int?,
            isFinale: Boolean,
            totalEpisodes: Int? = null,
            movieReleaseType: MovieReleaseType? = null
        ): Pair<String, String> {
            return MediaFormatter.formatNotificationContent(
                showTitle = showTitle,
                type = type,
                episodeTitle = episodeTitle,
                season = season,
                episodeNumber = episodeNumber,
                isFinale = isFinale,
                totalEpisodes = totalEpisodes,
                movieReleaseType = movieReleaseType
            )
        }

        /**
         * Formats notification title and message for a CalendarItemWithWatchlist relational model.
         */
        fun formatNotificationContent(
            item: CalendarItemWithWatchlist,
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
                totalEpisodes = totalEpisodes,
                movieReleaseType = item.movieReleaseType
            )
        }

        suspend fun loadPosterBitmap(context: Context, poster: String?): Bitmap? = withContext(Dispatchers.IO) {
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

        fun createNotificationChannel(context: Context) {
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

        suspend fun showNotification(
            context: Context,
            title: String,
            message: String,
            notificationId: Int,
            itemKey: String? = null,
            simklId: Int? = null,
            season: Int? = null,
            episodeNumber: Int? = null,
            type: MediaType = MediaType.TV,
            isFinale: Boolean = false,
            showTitle: String? = null,
            poster: String? = null
        ) {
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

            // Open Intent -> opens MainActivity and directly navigates to the episode/movie detail screen
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (!itemKey.isNullOrEmpty()) {
                    putExtra(MainActivity.EXTRA_ITEM_KEY, itemKey)
                    val encodedKey = try {
                        URLEncoder.encode(itemKey, "UTF-8")
                    } catch (_: Exception) {
                        itemKey
                    }
                    data = "simklcalendar://release_detail/$encodedKey".toUri()
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Check database watched status for this item/season/movie
            val db = try { AppDatabase.getDatabase(context) } catch (_: Exception) { null }
            var isAlreadyWatched = false
            var watchedBadge: String? = null

            if (db != null) {
                try {
                    val calItem = itemKey?.let { db.calendarItemDao().findItem(it) }
                    if (type == MediaType.MOVIE) {
                        val isMovieWatched = calItem?.isWatched == true ||
                            (simklId != null && db.calendarItemDao().getItemsForShow(simklId).any { it.isWatched })
                        if (isMovieWatched) {
                            isAlreadyWatched = true
                            watchedBadge = "Watched"
                        }
                    } else if (isFinale) {
                        val targetSeason = season ?: 1
                        val showCalItems = if (simklId != null) db.calendarItemDao().getItemsForShow(simklId) else emptyList()
                        val showWatched = if (simklId != null) db.watchedEpisodeDao().getWatchedEpisodesForShow(simklId) else emptyList()
                        val epInSeason = showCalItems.filter { (it.season ?: 1) == targetSeason }
                        val isSeasonWatched = if (epInSeason.isNotEmpty()) {
                            epInSeason.all { it.isWatched }
                        } else {
                            showWatched.any { it.season == targetSeason } || calItem?.isWatched == true
                        }
                        if (isSeasonWatched) {
                            isAlreadyWatched = true
                            val seasonsSet = mutableSetOf<Int>()
                            showCalItems.forEach { item -> item.season?.let { if (it > 0) seasonsSet.add(it) } }
                            showWatched.forEach { w -> if (w.season > 0) seasonsSet.add(w.season) }
                            seasonsSet.add(targetSeason)
                            val sortedSeasons = seasonsSet.sorted()
                            val isLastSeason = sortedSeasons.isNotEmpty() && targetSeason == sortedSeasons.last()
                            val allSeasonsWatched = sortedSeasons.all { sNum ->
                                val eps = showCalItems.filter { (it.season ?: 1) == sNum }
                                val wEps = showWatched.filter { it.season == sNum }
                                if (eps.isNotEmpty()) eps.all { it.isWatched } else wEps.isNotEmpty()
                            }
                            val isShowCompleted = isLastSeason && allSeasonsWatched
                            val seasonLabel = MediaFormatter.formatSeasonLabel(type, targetSeason)
                            watchedBadge = if (isShowCompleted) "Show Completed" else "$seasonLabel Watched"
                        }
                    } else {
                        val targetSeason = season ?: 1
                        val targetEp = episodeNumber ?: 1
                        val showWatched = if (simklId != null) db.watchedEpisodeDao().getWatchedEpisodesForShow(simklId) else emptyList()
                        val isEpWatched = calItem?.isWatched == true ||
                            showWatched.any { it.season == targetSeason && it.episodeNumber == targetEp } ||
                            (simklId != null && db.calendarItemDao().getItemsForShow(simklId).any { (it.season ?: 1) == targetSeason && (it.episodeNumber ?: 1) == targetEp && it.isWatched })
                        if (isEpWatched) {
                            isAlreadyWatched = true
                            watchedBadge = "Watched"
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking watched status for notification", e)
                }
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            if (isAlreadyWatched && watchedBadge != null) {
                builder.setSubText(watchedBadge)
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(message).setSummaryText(watchedBadge))
            } else {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(message))
            }

            val posterBitmap = loadPosterBitmap(context, poster)
            if (posterBitmap != null) {
                builder.setLargeIcon(posterBitmap)
            }

            // Add notification action buttons directly in the notification if not already watched
            if (!isAlreadyWatched && simklId != null && simklId > 0) {
                if (type == MediaType.MOVIE) {
                    // Movie Notification: "Mark as Watched"
                    val markMovieIntent = Intent(context, NotificationReceiver::class.java).apply {
                        action = ACTION_MARK_MOVIE_WATCHED
                        putExtra(EXTRA_SIMKL_ID, simklId)
                        putExtra(EXTRA_ID, notificationId)
                        putExtra(EXTRA_ITEM_KEY, itemKey)
                        putExtra(EXTRA_SHOW_TITLE, showTitle ?: title)
                        putExtra(EXTRA_TITLE, title)
                        putExtra(EXTRA_MESSAGE, message)
                        if (poster != null) putExtra(EXTRA_POSTER, poster)
                    }
                    val markMoviePendingIntent = PendingIntent.getBroadcast(
                        context,
                        notificationId * 10 + 1,
                        markMovieIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(
                        R.drawable.ic_check,
                        "Mark as Watched",
                        markMoviePendingIntent
                    )
                } else if (isFinale) {
                    // Finale Notification: "Mark Season as Watched"
                    val markSeasonIntent = Intent(context, NotificationReceiver::class.java).apply {
                        action = ACTION_MARK_SEASON_WATCHED
                        putExtra(EXTRA_SIMKL_ID, simklId)
                        putExtra(EXTRA_SEASON, season ?: 1)
                        putExtra(EXTRA_MEDIA_TYPE, type.name)
                        putExtra(EXTRA_ID, notificationId)
                        putExtra(EXTRA_ITEM_KEY, itemKey)
                        putExtra(EXTRA_SHOW_TITLE, showTitle ?: title)
                        putExtra(EXTRA_TITLE, title)
                        putExtra(EXTRA_MESSAGE, message)
                        if (poster != null) putExtra(EXTRA_POSTER, poster)
                    }
                    val markSeasonPendingIntent = PendingIntent.getBroadcast(
                        context,
                        notificationId * 10 + 2,
                        markSeasonIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(
                        R.drawable.ic_done_all,
                        "Mark Season as Watched",
                        markSeasonPendingIntent
                    )
                } else {
                    // Regular Episode Notification: "Mark as Watched"
                    val markEpIntent = Intent(context, NotificationReceiver::class.java).apply {
                        action = ACTION_MARK_EPISODE_WATCHED
                        putExtra(EXTRA_SIMKL_ID, simklId)
                        putExtra(EXTRA_SEASON, season)
                        putExtra(EXTRA_EPISODE_NUMBER, episodeNumber ?: 1)
                        putExtra(EXTRA_MEDIA_TYPE, type.name)
                        putExtra(EXTRA_ID, notificationId)
                        putExtra(EXTRA_ITEM_KEY, itemKey)
                        putExtra(EXTRA_SHOW_TITLE, showTitle ?: title)
                        putExtra(EXTRA_TITLE, title)
                        putExtra(EXTRA_MESSAGE, message)
                        if (poster != null) putExtra(EXTRA_POSTER, poster)
                    }
                    val markEpPendingIntent = PendingIntent.getBroadcast(
                        context,
                        notificationId * 10 + 1,
                        markEpIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(
                        R.drawable.ic_check,
                        "Mark as Watched",
                        markEpPendingIntent
                    )
                }
            }

            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(notificationId, builder.build())
                Log.d(TAG, "Successfully displayed notification id=$notificationId: $title")
            } catch (e: Exception) {
                Log.e(TAG, "Error posting notification", e)
            }
        }

        suspend fun updateNotificationResult(
            context: Context,
            notificationId: Int,
            title: String,
            message: String,
            badgeStatus: String,
            itemKey: String?,
            poster: String? = null
        ) {
            if (notificationId == 0) return

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (!itemKey.isNullOrEmpty()) {
                    putExtra(MainActivity.EXTRA_ITEM_KEY, itemKey)
                    val encodedKey = try {
                        URLEncoder.encode(itemKey, "UTF-8")
                    } catch (_: Exception) {
                        itemKey
                    }
                    data = "simklcalendar://release_detail/$encodedKey".toUri()
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val updatedNotificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setSubText(badgeStatus)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message).setSummaryText(badgeStatus))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)

            val posterBitmap = loadPosterBitmap(context, poster)
            if (posterBitmap != null) {
                updatedNotificationBuilder.setLargeIcon(posterBitmap)
            }

            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(notificationId, updatedNotificationBuilder.build())
                Log.d(TAG, "Successfully updated notification id=$notificationId with badge: $badgeStatus")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating notification with result", e)
            }
        }
    }
}

