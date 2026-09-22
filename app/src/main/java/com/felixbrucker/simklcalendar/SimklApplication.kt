package com.felixbrucker.simklcalendar

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.felixbrucker.simklcalendar.data.logging.AppLogTree
import com.felixbrucker.simklcalendar.data.logging.LogRepository
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class SimklApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        LogRepository.init(this)
        Timber.plant(AppLogTree())
        Timber.i("SimklApplication initialized")
    }
}
