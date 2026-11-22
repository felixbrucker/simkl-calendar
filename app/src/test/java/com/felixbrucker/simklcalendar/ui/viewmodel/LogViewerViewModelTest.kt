package com.felixbrucker.simklcalendar.ui.viewmodel

import android.content.Context
import android.util.Log
import com.felixbrucker.simklcalendar.data.logging.LogEntry
import com.felixbrucker.simklcalendar.data.logging.LogRepository
import com.squareup.moshi.Moshi
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewerViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var moshi: Moshi

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        LogViewerViewModel.defaultDispatcher = testDispatcher
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns testDispatcher

        context = mockk(relaxed = true)
        val filesDir = tempFolder.newFolder("files")
        every { context.filesDir } returns filesDir
        moshi = Moshi.Builder().build()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Dispatchers::class)
    }

    @Test
    fun testAllLogsAndClearLogs() = runTest {
        val logRepository = LogRepository(context, moshi, backgroundScope)
        advanceUntilIdle()

        logRepository.addLog(LogEntry(priority = Log.DEBUG, tag = "TestTag", message = "Test Message"))
        advanceUntilIdle()

        val viewModel = LogViewerViewModel(logRepository)
        advanceUntilIdle()

        val logsBeforeClear = viewModel.allLogs.value
        val sizeBefore = logsBeforeClear.size
        viewModel.clearLogs()
        advanceUntilIdle()

        val logsAfterClear = viewModel.allLogs.value
        val sizeAfter = logsAfterClear.size

        assertEquals(1, sizeBefore)
        assertEquals(0, sizeAfter)
    }

    @Test
    fun testFilteringBySearchQuery() = runTest {
        val logRepository = LogRepository(context, moshi, backgroundScope)
        advanceUntilIdle()

        logRepository.addLog(LogEntry(priority = Log.INFO, tag = "Network", message = "Fetching data"))
        logRepository.addLog(LogEntry(priority = Log.ERROR, tag = "Database", message = "Disk read error"))
        advanceUntilIdle()

        val viewModel = LogViewerViewModel(logRepository)
        advanceUntilIdle()

        viewModel.setSearchQuery("Disk")
        advanceUntilIdle()

        val filtered = viewModel.filteredLogs.value
        val count = filtered.size
        val tag = filtered.firstOrNull()?.tag

        assertEquals(1, count)
        assertEquals("Database", tag)
    }

    @Test
    fun testFilteringByPriority() = runTest {
        val logRepository = LogRepository(context, moshi, backgroundScope)
        advanceUntilIdle()

        logRepository.addLog(LogEntry(priority = Log.INFO, tag = "Tag1", message = "Msg1"))
        logRepository.addLog(LogEntry(priority = Log.ERROR, tag = "Tag2", message = "Msg2"))
        advanceUntilIdle()

        val viewModel = LogViewerViewModel(logRepository)
        advanceUntilIdle()

        viewModel.setSelectedPriority(Log.ERROR)
        advanceUntilIdle()

        val filtered = viewModel.filteredLogs.value
        val count = filtered.size
        val priority = filtered.firstOrNull()?.priority

        assertEquals(1, count)
        assertEquals(Log.ERROR, priority)
    }

    @Test
    fun testFilteringBySearchQueryAndPriority() = runTest {
        val logRepository = LogRepository(context, moshi, backgroundScope)
        advanceUntilIdle()

        logRepository.addLog(LogEntry(priority = Log.WARN, tag = "Tag1", message = "Warning event"))
        logRepository.addLog(LogEntry(priority = Log.ERROR, tag = "Tag2", message = "Error event"))
        logRepository.addLog(LogEntry(priority = Log.ERROR, tag = "Tag3", message = "Another message"))
        advanceUntilIdle()

        val viewModel = LogViewerViewModel(logRepository)
        advanceUntilIdle()

        viewModel.setSearchQuery("event")
        viewModel.setSelectedPriority(Log.ERROR)
        advanceUntilIdle()

        val filtered = viewModel.filteredLogs.value
        val count = filtered.size
        val tag = filtered.firstOrNull()?.tag

        assertEquals(1, count)
        assertEquals("Tag2", tag)
    }
}
