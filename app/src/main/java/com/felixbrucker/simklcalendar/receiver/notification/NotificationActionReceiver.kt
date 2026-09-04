package com.felixbrucker.simklcalendar.receiver.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationActionReceiver: BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    companion object {
        private const val TAG = "NotificationActionReceiver"
        const val ACTION_MARK_ITEM_WATCHED = "com.felixbrucker.simklcalendar.ACTION_MARK_ITEM_WATCHED"
        const val ACTION_MARK_SEASON_WATCHED = "com.felixbrucker.simklcalendar.ACTION_MARK_SEASON_WATCHED"
        const val EXTRA_ITEM_PRIMARY_KEY = "extra_item_primary_key"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            ACTION_MARK_ITEM_WATCHED -> {
                handleMarkItemWatched(context, intent)
                return
            }
            ACTION_MARK_SEASON_WATCHED -> {
                handleMarkSeasonWatched(context, intent)
                return
            }
        }
    }

    private fun handleMarkItemWatched(context: Context, intent: Intent) {
        val itemPrimaryKey = intent.getStringExtra(EXTRA_ITEM_PRIMARY_KEY) ?: return
        val repo = SimklRepository(context)
        val db = AppDatabase.getDatabase(context)

        val pendingResult = goAsync()
        scope.launch {
            try {
                val item = db.calendarItemDao().findItem(itemPrimaryKey) ?: return@launch

                val result = if (item.type == MediaType.MOVIE) {
                    repo.markMovieWatched(simklId = item.simklId)
                } else {
                    repo.markEpisodeWatched(
                        simklId = item.simklId,
                        season = item.season,
                        episodeNumber = item.episodeNumber ?: 1,
                        mediaType = item.type,
                    )
                }

                val err = result.exceptionOrNull()
                if (err != null) {
                    Log.e(TAG, "Error marking item as watched from notification action", err)
                }

                val updatedItem = db.calendarItemDao().findItem(itemPrimaryKey) ?: return@launch
                NotificationManager.updateNotification(
                    item = updatedItem,
                    context = context
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error marking episode as watched from notification action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleMarkSeasonWatched(context: Context, intent: Intent) {
        val itemPrimaryKey = intent.getStringExtra(EXTRA_ITEM_PRIMARY_KEY) ?: return
        val repo = SimklRepository(context)
        val db = AppDatabase.getDatabase(context)

        val pendingResult = goAsync()
        scope.launch {
            try {
                val item = db.calendarItemDao().findItem(itemPrimaryKey) ?: return@launch
                val result = repo.markSeasonWatched(
                    simklId = item.simklId,
                    season = item.season ?: 1,
                    mediaType = item.type,
                )

                val err = result.exceptionOrNull()
                if (err != null) {
                    Log.e(TAG, "Error marking season as watched from notification action", err)
                }

                val updatedItem = db.calendarItemDao().findItem(itemPrimaryKey) ?: return@launch
                NotificationManager.updateNotification(
                    item = updatedItem,
                    context = context
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error marking season as watched from notification action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
