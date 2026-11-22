package com.felixbrucker.simklcalendar.data.logging

import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLogTree @Inject constructor(
    private val logRepository: LogRepository
) : Timber.Tree() {
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
        logRepository.addLog(entry)
    }
}
