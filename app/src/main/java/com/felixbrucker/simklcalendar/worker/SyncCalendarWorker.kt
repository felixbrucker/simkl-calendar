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
import com.felixbrucker.simklcalendar.data.repository.SimklRepository
import java.util.concurrent.TimeUnit

class SyncCalendarWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting periodic background calendar synchronization")
        return try {
            val repository = SimklRepository(applicationContext)
            // Perform full calendar synchronization
            repository.syncCalendar()

            Log.d(TAG, "Periodic calendar synchronization succeeded")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Periodic calendar synchronization worker encountered an error", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncCalendarWorker"
        const val UNIQUE_WORK_NAME = "simkl_periodic_calendar_sync"

        /**
         * Enqueues a periodic background sync with configurable interval (in hours, min 1 hour or 15 mins for WorkManager).
         * Runs reliably even when the app is in background or closed.
         */
        fun enqueuePeriodicSync(context: Context, intervalHours: Long = 12) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val effectiveInterval = intervalHours.coerceAtLeast(1)
            val syncRequest = PeriodicWorkRequestBuilder<SyncCalendarWorker>(effectiveInterval, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
            Log.d(TAG, "Enqueued $effectiveInterval-hour periodic background calendar sync work")
        }
    }
}
