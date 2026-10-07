package com.felixbrucker.simklcalendar.ui.viewmodel

import android.util.Log
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
        LogViewerViewModel.defaultDispatcher = testDispatcher
        LogRepository.ioDispatcher = testDispatcher
        LogRepository.clearLogs()
    }

    @After
    fun tearDown() {
        LogRepository.clearLogs()
        Dispatchers.resetMain()
    }

    @Test
    fun testAllLogsAndClearLogs() {
        LogRepository.addLog(LogEntry(priority = Log.DEBUG, tag = "TestTag", message = "Test Message"))
        val viewModel = LogViewerViewModel()

        val logsBeforeClear = viewModel.allLogs.value
        val sizeBefore = logsBeforeClear.size
        viewModel.clearLogs()
        val logsAfterClear = viewModel.allLogs.value
        val sizeAfter = logsAfterClear.size

        assertEquals(1, sizeBefore)
        assertEquals(0, sizeAfter)
    }

    @Test
    fun testFilteringBySearchQuery() {
        LogRepository.addLog(LogEntry(priority = Log.INFO, tag = "Network", message = "Fetching data"))
        LogRepository.addLog(LogEntry(priority = Log.ERROR, tag = "Database", message = "Disk read error"))
        val viewModel = LogViewerViewModel()

        viewModel.setSearchQuery("Disk")
        val filtered = viewModel.filteredLogs.value
        val count = filtered.size
        val tag = filtered.firstOrNull()?.tag

        assertEquals(1, count)
        assertEquals("Database", tag)
    }

    @Test
    fun testFilteringByPriority() {
        LogRepository.addLog(LogEntry(priority = Log.INFO, tag = "Tag1", message = "Msg1"))
        LogRepository.addLog(LogEntry(priority = Log.ERROR, tag = "Tag2", message = "Msg2"))
        val viewModel = LogViewerViewModel()

        viewModel.setSelectedPriority(Log.ERROR)
        val filtered = viewModel.filteredLogs.value
        val count = filtered.size
        val priority = filtered.firstOrNull()?.priority

        assertEquals(1, count)
        assertEquals(Log.ERROR, priority)
    }

    @Test
    fun testFilteringBySearchQueryAndPriority() {
        LogRepository.addLog(LogEntry(priority = Log.WARN, tag = "Tag1", message = "Warning event"))
        LogRepository.addLog(LogEntry(priority = Log.ERROR, tag = "Tag2", message = "Error event"))
        LogRepository.addLog(LogEntry(priority = Log.ERROR, tag = "Tag3", message = "Another message"))
        val viewModel = LogViewerViewModel()

        viewModel.setSearchQuery("event")
        viewModel.setSelectedPriority(Log.ERROR)
        val filtered = viewModel.filteredLogs.value
        val count = filtered.size
        val tag = filtered.firstOrNull()?.tag

        assertEquals(1, count)
        assertEquals("Tag2", tag)
    }
}
