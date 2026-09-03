package com.felixbrucker.simklcalendar.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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

        if (!repository.torrentServiceHelper.isServiceInstalled()) {
            Log.d(TAG, "Torrent Downloader service not installed. Skipping periodic search.")
            return Result.success()
        }

        try {
            repository.searchAndDownloadWantedItems()
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "AutoDownloadWorker encountered an error", e)
            return Result.retry()
        }
    }

    companion object {
        private const val TAG = "AutoDownloadWorker"
        const val UNIQUE_WORK_NAME = "simkl_periodic_auto_download"
        const val MANUAL_WORK_NAME = "simkl_manual_auto_download"

        fun runOnce(context: Context) {
            val searchRequest = OneTimeWorkRequestBuilder<AutoDownloadWorker>()
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                MANUAL_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                searchRequest
            )
            Log.d(TAG, "Enqueued manual one-time background torrent search work")
        }

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
