package com.felixbrucker.simklcalendar.data.logging

import android.content.Context
import com.felixbrucker.simklcalendar.di.ApplicationScope
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    companion object {
        private const val MAX_ENTRIES = 3000
        private const val PRUNE_THRESHOLD = 3500
        private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
    }

    private val adapter = moshi.adapter(LogEntry::class.java)

    private val mutex = Mutex()
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    private val logFile: File

    init {
        val logDir = File(context.filesDir, "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        logFile = File(logDir, "app_logs.jsonl")
        loadLogs()
    }

    private fun loadLogs() {
        appScope.launch(Dispatchers.IO) {
            mutex.withLock {
                val loadedLogs = mutableListOf<LogEntry>()
                if (logFile.exists()) {
                    try {
                        logFile.useLines { lines ->
                            for (line in lines) {
                                if (line.isNotBlank()) {
                                    try {
                                        val entry = adapter.fromJson(line)
                                        if (entry != null) {
                                            loadedLogs.add(entry)
                                        }
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                val now = System.currentTimeMillis()
                val pruned = pruneEntries(loadedLogs, now, MAX_AGE_MS, MAX_ENTRIES)
                _logsFlow.value = pruned
                saveAllLocked(pruned)
            }
        }
    }

    fun addLog(entry: LogEntry) {
        appScope.launch(Dispatchers.IO) {
            mutex.withLock {
                val current = _logsFlow.value.toMutableList()
                current.add(entry)

                try {
                    val jsonLine = adapter.toJson(entry)
                    logFile.appendText(jsonLine + "\n")
                } catch (_: Exception) {
                }

                if (current.size > PRUNE_THRESHOLD) {
                    val now = System.currentTimeMillis()
                    val pruned = pruneEntries(current, now, MAX_AGE_MS, MAX_ENTRIES)
                    _logsFlow.value = pruned
                    saveAllLocked(pruned)
                } else {
                    _logsFlow.value = current
                }
            }
        }
    }

    fun clearLogs() {
        appScope.launch(Dispatchers.IO) {
            mutex.withLock {
                _logsFlow.value = emptyList()
                if (logFile.exists()) {
                    try {
                        logFile.writeText("")
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    internal fun pruneEntries(
        entries: List<LogEntry>,
        nowMs: Long,
        maxAgeMs: Long,
        maxEntries: Int
    ): List<LogEntry> {
        val cutoff = nowMs - maxAgeMs
        var startIndex = entries.indexOfFirst { it.timestamp >= cutoff }
        if (startIndex == -1) return emptyList()
        if (entries.size - startIndex > maxEntries) {
            startIndex = entries.size - maxEntries
        }
        return entries.subList(startIndex, entries.size).toList()
    }

    private fun saveAllLocked(entries: List<LogEntry>) {
        try {
            logFile.bufferedWriter().use { writer ->
                for (entry in entries) {
                    writer.write(adapter.toJson(entry))
                    writer.newLine()
                }
            }
        } catch (_: Exception) {
        }
    }
}
