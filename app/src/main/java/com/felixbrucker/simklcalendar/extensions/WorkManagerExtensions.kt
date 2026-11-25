package com.felixbrucker.simklcalendar.extensions

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
