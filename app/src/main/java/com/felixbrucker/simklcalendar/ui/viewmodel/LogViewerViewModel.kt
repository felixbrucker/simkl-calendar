package com.felixbrucker.simklcalendar.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.simklcalendar.data.logging.LogEntry
import com.felixbrucker.simklcalendar.data.logging.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LogViewerViewModel @Inject constructor(
    private val logRepository: LogRepository
) : ViewModel() {

    companion object {
        var defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    }

    val allLogs: StateFlow<List<LogEntry>> = logRepository.logsFlow

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedPriorities = MutableStateFlow(
        setOf(Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR)
    )
    val selectedPriorities: StateFlow<Set<Int>> = _selectedPriorities.asStateFlow()

    val filteredLogs: StateFlow<List<LogEntry>> = combine(
        logRepository.logsFlow,
        _searchQuery,
        _selectedPriorities
    ) { logs, query, priorities ->
        logs.filter { entry ->
            val matchesPriority = entry.priority in priorities
            val matchesSearch = query.isBlank() ||
                    (entry.tag?.contains(query, ignoreCase = true) == true) ||
                    entry.message.contains(query, ignoreCase = true) ||
                    (entry.throwableStackTrace?.contains(query, ignoreCase = true) == true)
            matchesPriority && matchesSearch
        }
    }
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun togglePriority(priority: Int) {
        val current = _selectedPriorities.value
        _selectedPriorities.value = if (priority in current) {
            current - priority
        } else {
            current + priority
        }
    }

    fun clearLogs() {
        logRepository.clearLogs()
    }
}
