package com.felixbrucker.simklcalendar.extensions

import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlin.collections.any

fun WorkManager.isWorkCurrentlyQueued(uniqueWorkName: String): Boolean {
    return try {
        val workInfos = getWorkInfosForUniqueWork(uniqueWorkName).get()
        workInfos?.any { !it.state.isFinished } == true
    } catch (_: Exception) {
        false
    }
}

val queuedStatesExcludingRunning = setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED)

fun WorkManager.isWorkCurrentlyQueuedByTag(tag: String): Boolean {
    return try {
        val workInfos = getWorkInfosByTag(tag).get()
        workInfos?.any { it.state in queuedStatesExcludingRunning } == true
    } catch (_: Exception) {
        false
    }
}

// Avoid cancelling in progress work
fun WorkManager.cancelAllQueuedWorkByTag(tag: String) {
    val workInfos = getWorkInfosByTag(tag).get()
    workInfos.forEach {
        if (it.state in queuedStatesExcludingRunning) {
            cancelWorkById(it.id)
        }
    }
}
