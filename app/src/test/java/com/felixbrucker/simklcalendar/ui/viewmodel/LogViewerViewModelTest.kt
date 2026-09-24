package com.felixbrucker.simklcalendar.ui.viewmodel

import com.felixbrucker.simklcalendar.data.logging.LogEntry
import com.felixbrucker.simklcalendar.data.logging.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        LogRepository.clearLogs()
    }

    @After
    fun tearDown() {
        LogRepository.clearLogs()
        Dispatchers.resetMain()
    }

    @Test
    fun testAllLogsAndClearLogs() {
        LogRepository.addLog(LogEntry(priority = 3, tag = "TestTag", message = "Test Message"))
        val viewModel = LogViewerViewModel()
        val logsBeforeClear = viewModel.allLogs.value

        viewModel.clearLogs()
        val logsAfterClear = viewModel.allLogs.value

        assertEquals(1, logsBeforeClear.size)
        assertEquals(0, logsAfterClear.size)
    }
}
