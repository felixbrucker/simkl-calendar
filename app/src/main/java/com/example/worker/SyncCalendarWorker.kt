package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.repository.SimklRepository
import com.example.receiver.NotificationScheduler
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
            repository.syncCalendar(force = false)

            // Reschedule and dispatch any notifications that have reached their air date
            NotificationScheduler.scheduleAllNotifications(applicationContext)

            Log.d(TAG, "Periodic calendar synchronization and notification scheduling succeeded")
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
         * Enqueues a periodic background sync every 12 hours with network connectivity requirement.
         * Runs reliably even when the app is in background or closed.
         */
        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncCalendarWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
            Log.d(TAG, "Enqueued 12-hour periodic background calendar sync work")
        }
    }
}
