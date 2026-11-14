package com.felixbrucker.simklcalendar.data.logging

import kotlinx.coroutines.CancellationException
import timber.log.Timber

class AppLogTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (t is CancellationException) return
        val stackTrace = t?.stackTraceToString()
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            priority = priority,
            tag = tag ?: "Unknown",
            message = message,
            throwableStackTrace = stackTrace
        )
        LogRepository.addLog(entry)
    }
}
