package com.felixbrucker.simklcalendar.data.logging

import android.util.Log
import timber.log.Timber

class AppLogTree : Timber.DebugTree() {

    public override fun isLoggable(tag: String?, priority: Int): Boolean {
        // Do not restrict log levels for production
        return true
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val stackTrace = t?.let { Log.getStackTraceString(it) }
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            priority = priority,
            tag = tag ?: "SimklCalendar",
            message = message,
            throwableStackTrace = stackTrace
        )
        LogRepository.addLog(entry)

        super.log(priority, tag, message, t)
    }
}
