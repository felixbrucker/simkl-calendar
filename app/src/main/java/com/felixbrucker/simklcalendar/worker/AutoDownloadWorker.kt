package com.felixbrucker.simklcalendar.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import com.felixbrucker.simklcalendar.data.repository.CalendarRepository
import com.felixbrucker.simklcalendar.data.repository.DownloadRepository
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import com.felixbrucker.simklcalendar.extensions.cancelAllQueuedWorkByTag
import com.felixbrucker.simklcalendar.extensions.isWorkCurrentlyQueuedByTag
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoDownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloadRepository: DownloadRepository,
    private val torrentServiceHelper: TorrentServiceHelper,
    private val calendarRepository: CalendarRepository,
    private val autoDownloadRepository: AutoDownloadRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.tag(TAG).i("Starting background torrent search for WANTED items")

        if (!torrentServiceHelper.isServiceInstalled()) {
            Timber.tag(TAG).w("Torrent Downloader service not installed. Skipping search.")
            return Result.success()
        }

        val workResult: Result = try {
            downloadRepository.searchAndDownloadWantedItems()
            Result.success()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "AutoDownloadWorker encountered an error")
            Result.retry()
        } finally {
            torrentServiceHelper.unbind()
        }

        if ((workResult == Result.success()) && torrentServiceHelper.isServiceInstalled()) {
            try {
                val wantedItems = calendarRepository.wantedCalendarItems.first()
                val isQueued = WorkManager
                    .getInstance(appContext)
                    .isWorkCurrentlyQueuedByTag(WORK_TAG)
                if (wantedItems.isNotEmpty() && !isQueued) {
                    val searchIntervalHours = autoDownloadRepository.preferencesFlow.first().searchIntervalHours.toLong().coerceAtLeast(1)
                    Timber.tag(TAG).i("${wantedItems.size} wanted items still present. Next search will run in $searchIntervalHours hours.")
                    enqueueAutoDownload(appContext, initialDelayHours = searchIntervalHours)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error checking wanted items for self-enqueueing")
            }
        }

        return workResult
    }

    companion object {
        private const val TAG = "AutoDownloadWorker"
        const val WORK_TAG = "simkl_auto_download_work"

        fun enqueueImmediateSearch(context: Context) {
            enqueueAutoDownload(context, initialDelayHours = 0)
            Timber.tag(TAG).i("Enqueued background torrent search")
        }

        fun cancelQueuedAutoDownload(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelAllQueuedWorkByTag(WORK_TAG)
        }

        fun enqueueAutoDownload(context: Context, initialDelayHours: Long = 0) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val builder = OneTimeWorkRequestBuilder<AutoDownloadWorker>()
                .setConstraints(constraints)
                .addTag(WORK_TAG)

            if (initialDelayHours > 0) {
                builder.setInitialDelay(initialDelayHours, TimeUnit.HOURS)
            }

            val searchRequest = builder.build()
            val workManager = WorkManager.getInstance(context)
            workManager.enqueue(searchRequest)
        }
    }
}
