package com.felixbrucker.simklcalendar.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.felixbrucker.simklcalendar.data.model.MediaStatus
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class AutoDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting periodic background torrent search for WANTED items")
        val repository = SimklRepository(applicationContext)

        try {
            val items = repository.calendarItems.first()
            val wantedItems = items.filter { it.mediaStatus == MediaStatus.WANTED }

            if (wantedItems.isEmpty()) {
                Log.d(TAG, "No items in WANTED status found.")
                return Result.success()
            }

            Log.d(TAG, "Found ${wantedItems.size} WANTED items. Starting search...")

            for (item in wantedItems) {
                try {
                    repository.searchAndDownloadEpisode(item)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed search/download for ${item.primaryKey}", e)
                }
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "AutoDownloadWorker encountered an error", e)
            return Result.retry()
        }
    }

    companion object {
        private const val TAG = "AutoDownloadWorker"
        const val UNIQUE_WORK_NAME = "simkl_periodic_auto_download"

        fun enqueuePeriodicSearch(context: Context, intervalHours: Long = 12) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val effectiveInterval = intervalHours.coerceAtLeast(1)
            val searchRequest = PeriodicWorkRequestBuilder<AutoDownloadWorker>(effectiveInterval, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                searchRequest
            )
            Log.d(TAG, "Enqueued $effectiveInterval-hour periodic background torrent search work")
        }

    }
}
