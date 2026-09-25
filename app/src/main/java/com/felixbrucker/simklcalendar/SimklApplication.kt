package com.felixbrucker.simklcalendar

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.felixbrucker.simklcalendar.data.logging.AppLogTree
import com.felixbrucker.simklcalendar.data.logging.LogRepository
import com.felixbrucker.simklcalendar.data.preferences.AppSettingsRepository
import com.felixbrucker.simklcalendar.data.sentry.SentryManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class SimklApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appSettingsRepo: AppSettingsRepository

    @Inject
    lateinit var sentryManager: SentryManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        LogRepository.init(this)
        Timber.plant(AppLogTree())
        Timber.i("SimklApplication initialized")

        applicationScope.launch {
            appSettingsRepo.preferencesFlow
                .map { it.isSentryEnabled }
                .distinctUntilChanged()
                .collect { sentryManager.updateSentryState(this@SimklApplication, it) }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        Timber.i("SimklApplication destroyed")
    }
}
