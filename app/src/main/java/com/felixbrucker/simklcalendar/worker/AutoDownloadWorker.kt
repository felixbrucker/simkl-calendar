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
import com.felixbrucker.simklcalendar.data.repository.DownloadRepository
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloadRepository: DownloadRepository,
    private val torrentServiceHelper: TorrentServiceHelper
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.tag(TAG).d("Starting periodic background torrent search for WANTED items")

        if (!torrentServiceHelper.isServiceInstalled()) {
            Timber.tag(TAG).d("Torrent Downloader service not installed. Skipping periodic search.")
            return Result.success()
        }

        try {
            downloadRepository.searchAndDownloadWantedItems()
            return Result.success()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "AutoDownloadWorker encountered an error")
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
            Timber.tag(TAG).d("Enqueued $effectiveInterval-hour periodic background torrent search work")
        }

    }
}
