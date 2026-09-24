package com.felixbrucker.simklcalendar.data.logging

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

object LogRepository {
    private const val MAX_ENTRIES = 3000
    private const val PRUNE_THRESHOLD = 3500
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000 // 7 days

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(LogEntry::class.java)

    private val mutex = Mutex()
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    private var logFile: File? = null
    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    fun init(context: Context) {
        val logDir = File(context.filesDir, "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        val file = File(logDir, "app_logs.jsonl")
        logFile = file

        CoroutineScope(ioDispatcher).launch {
            mutex.withLock {
                val loadedLogs = mutableListOf<LogEntry>()
                if (file.exists()) {
                    try {
                        file.useLines { lines ->
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
        CoroutineScope(ioDispatcher).launch {
            mutex.withLock {
                val current = _logsFlow.value.toMutableList()
                current.add(entry)

                val file = logFile
                if (file != null) {
                    try {
                        val jsonLine = adapter.toJson(entry)
                        file.appendText(jsonLine + "\n")
                    } catch (_: Exception) {
                    }
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
        CoroutineScope(ioDispatcher).launch {
            mutex.withLock {
                _logsFlow.value = emptyList()
                val file = logFile
                if (file != null && file.exists()) {
                    try {
                        file.writeText("")
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
        val file = logFile ?: return
        try {
            file.bufferedWriter().use { writer ->
                for (entry in entries) {
                    writer.write(adapter.toJson(entry))
                    writer.newLine()
                }
            }
        } catch (_: Exception) {
        }
    }
}
