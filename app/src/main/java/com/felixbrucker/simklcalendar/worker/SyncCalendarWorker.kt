package com.felixbrucker.simklcalendar.worker

import android.content.Context
import timber.log.Timber
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.felixbrucker.simklcalendar.data.repository.SyncRepository
import com.felixbrucker.simklcalendar.data.repository.UserRepository
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncCalendarWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userRepository: UserRepository,
    private val syncRepository: SyncRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.tag(TAG).d("Starting periodic background calendar synchronization")
        val token = userRepository.getActiveUserToken()
        if (token == null || token.accessToken.isEmpty()) {
            Timber.tag(TAG).d("User is not authenticated. Skipping periodic calendar synchronization.")
            return Result.success()
        }
        return try {
            // Perform full calendar synchronization
            syncRepository.syncCalendar()

            Timber.tag(TAG).d("Periodic calendar synchronization succeeded")
            Result.success()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Periodic calendar synchronization worker encountered an error")
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
            Timber.tag(TAG).d("Enqueued $effectiveInterval-hour periodic background calendar sync work")
        }
    }
}
