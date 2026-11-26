package com.felixbrucker.simklcalendar

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.felixbrucker.simklcalendar.data.logging.AppLogTree
import com.felixbrucker.simklcalendar.data.preferences.AppSettingsRepository
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadRepository
import com.felixbrucker.simklcalendar.data.repository.CalendarRepository
import com.felixbrucker.simklcalendar.data.sentry.SentryManager
import com.felixbrucker.simklcalendar.data.util.TorrentServiceHelper
import com.felixbrucker.simklcalendar.extensions.isWorkCurrentlyQueuedByTag
import com.felixbrucker.simklcalendar.worker.AutoDownloadWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class SimklApplication : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "SimklApplication"
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appSettingsRepo: AppSettingsRepository

    @Inject
    lateinit var autoDownloadRepo: AutoDownloadRepository

    @Inject
    lateinit var torrentServiceHelper: TorrentServiceHelper

    @Inject
    lateinit var calendarRepository: CalendarRepository

    @Inject
    lateinit var sentryManager: SentryManager

    @Inject
    lateinit var appLogTree: AppLogTree

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        Timber.plant(appLogTree)
        Timber.tag(TAG).i("SimklApplication initialized")

        applicationScope.launch {
            appSettingsRepo.preferencesFlow
                .map { it.isSentryEnabled }
                .distinctUntilChanged()
                .collect { sentryManager.updateSentryState(this@SimklApplication, it) }
        }

        applicationScope.launch(Dispatchers.IO) {
            combine(
                autoDownloadRepo.preferencesFlow.map { it.searchIntervalHours.toLong() },
                torrentServiceHelper.isInstalled,
                calendarRepository.wantedCalendarItems.map { it.isNotEmpty() },
            ) { searchIntervalHours, isInstalled, hasWantedItems ->
                Triple(searchIntervalHours, isInstalled, hasWantedItems)
            }
                .distinctUntilChanged()
                .collect { (searchIntervalHours, isInstalled, hasWantedItems) ->
                    val isQueued = WorkManager.getInstance(this@SimklApplication)
                        .isWorkCurrentlyQueuedByTag(AutoDownloadWorker.WORK_TAG)
                    if (isInstalled && hasWantedItems) {
                        if (!isQueued) {
                            Timber.tag(TAG).i("Detected wanted items, enqueueing background torrent search with $searchIntervalHours-hour delay")
                            AutoDownloadWorker.enqueueAutoDownload(
                                this@SimklApplication,
                                initialDelayHours = searchIntervalHours
                            )
                        }
                    } else if (isQueued) {
                        Timber.tag(TAG).i("No more wanted items available, cancelling background torrent search")
                        AutoDownloadWorker.cancelQueuedAutoDownload(this@SimklApplication)
                    }
                }
        }
    }
}
