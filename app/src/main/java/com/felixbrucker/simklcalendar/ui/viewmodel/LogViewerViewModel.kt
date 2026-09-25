package com.felixbrucker.simklcalendar.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.felixbrucker.simklcalendar.data.logging.LogEntry
import com.felixbrucker.simklcalendar.data.logging.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LogViewerViewModel @Inject constructor() : ViewModel() {

    val allLogs: StateFlow<List<LogEntry>> = LogRepository.logsFlow

    fun clearLogs() {
        LogRepository.clearLogs()
    }
}
