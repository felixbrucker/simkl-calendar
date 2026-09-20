package com.felixbrucker.simklcalendar

import android.app.Application
import com.felixbrucker.simklcalendar.data.logging.AppLogTree
import com.felixbrucker.simklcalendar.data.logging.LogRepository
import timber.log.Timber

class SimklApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LogRepository.init(this)
        Timber.plant(AppLogTree())
        Timber.i("SimklApplication initialized")
    }
}
