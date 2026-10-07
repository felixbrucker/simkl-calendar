package com.felixbrucker.simklcalendar.ui.viewmodel

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
class LogViewerViewModel @Inject constructor() : ViewModel() {

    companion object {
        var defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    }

    val allLogs: StateFlow<List<LogEntry>> = LogRepository.logsFlow

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedPriority = MutableStateFlow(-1)
    val selectedPriority: StateFlow<Int> = _selectedPriority.asStateFlow()

    val filteredLogs: StateFlow<List<LogEntry>> = combine(
        LogRepository.logsFlow,
        _searchQuery,
        _selectedPriority
    ) { logs, query, priority ->
        logs.filter { entry ->
            val matchesPriority = priority == -1 || entry.priority == priority
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

    fun setSelectedPriority(priority: Int) {
        _selectedPriority.value = priority
    }

    fun clearLogs() {
        LogRepository.clearLogs()
    }
}
