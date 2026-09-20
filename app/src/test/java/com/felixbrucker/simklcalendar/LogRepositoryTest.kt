package com.felixbrucker.simklcalendar

import android.content.Context
import android.util.Log
import com.felixbrucker.simklcalendar.data.logging.AppLogTree
import com.felixbrucker.simklcalendar.data.logging.LogEntry
import com.felixbrucker.simklcalendar.data.logging.LogRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LogRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.getStackTraceString(any<Throwable>()) } returns "StackTrace"
        every { Log.println(any<Int>(), any<String>(), any<String>()) } returns 0
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        filesDir = tempFolder.newFolder("files")
        context = mockk(relaxed = true)
        every { context.filesDir } returns filesDir
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun testLogEntryPriorityLabelVerbose() {
        val entry = LogEntry(priority = Log.VERBOSE)

        val label = entry.priorityLabel()

        assertEquals("VERBOSE", label)
    }

    @Test
    fun testLogEntryPriorityLabelDebug() {
        val entry = LogEntry(priority = Log.DEBUG)

        val label = entry.priorityLabel()

        assertEquals("DEBUG", label)
    }

    @Test
    fun testLogEntryPriorityLabelInfo() {
        val entry = LogEntry(priority = Log.INFO)

        val label = entry.priorityLabel()

        assertEquals("INFO", label)
    }

    @Test
    fun testLogEntryPriorityLabelWarn() {
        val entry = LogEntry(priority = Log.WARN)

        val label = entry.priorityLabel()

        assertEquals("WARN", label)
    }

    @Test
    fun testLogEntryPriorityLabelError() {
        val entry = LogEntry(priority = Log.ERROR)

        val label = entry.priorityLabel()

        assertEquals("ERROR", label)
    }

    @Test
    fun testLogEntryPriorityLabelDefault() {
        val entry = LogEntry(priority = 99)

        val label = entry.priorityLabel()

        assertEquals("LOG", label)
    }

    @Test
    fun testPruneEntriesByAge() {
        val now = 1000000L
        val oldEntry = LogEntry(timestamp = 100L, message = "Old")
        val newEntry = LogEntry(timestamp = 900000L, message = "New")
        val entries = listOf(oldEntry, newEntry)

        val result = LogRepository.pruneEntries(entries, nowMs = now, maxAgeMs = 500000L, maxEntries = 10)

        val count = result.size
        val firstMessage = result.first().message
        assertEquals(1, count)
        assertEquals("New", firstMessage)
    }

    @Test
    fun testPruneEntriesByMaxCount() {
        val now = 1000000L
        val entry1 = LogEntry(timestamp = 900000L, message = "E1")
        val entry2 = LogEntry(timestamp = 950000L, message = "E2")
        val entry3 = LogEntry(timestamp = 980000L, message = "E3")
        val entries = listOf(entry1, entry2, entry3)

        val result = LogRepository.pruneEntries(entries, nowMs = now, maxAgeMs = 500000L, maxEntries = 2)

        val count = result.size
        val firstMsg = result[0].message
        val secondMsg = result[1].message
        assertEquals(2, count)
        assertEquals("E2", firstMsg)
        assertEquals("E3", secondMsg)
    }

    @Test
    fun testRepositoryInitAddClear() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        LogRepository.ioDispatcher = testDispatcher
        LogRepository.init(context)
        LogRepository.clearLogs()
        val testEntry = LogEntry(timestamp = System.currentTimeMillis(), priority = Log.INFO, tag = "TestTag", message = "Hello Test")

        LogRepository.addLog(testEntry)
        val logsAfterAdd = LogRepository.logsFlow.value
        val addSize = logsAfterAdd.size
        val addedMsg = logsAfterAdd.last().message
        LogRepository.clearLogs()
        val logsAfterClear = LogRepository.logsFlow.value
        val clearSize = logsAfterClear.size

        assertEquals(1, addSize)
        assertEquals("Hello Test", addedMsg)
        assertEquals(0, clearSize)
    }

    @Test
    fun testAppLogTreeIsLoggable() {
        val tree = AppLogTree()

        val isLoggableVerbose = tree.isLoggable("Tag", Log.VERBOSE)
        val isLoggableError = tree.isLoggable("Tag", Log.ERROR)

        assertTrue(isLoggableVerbose)
        assertTrue(isLoggableError)
    }

    @Test
    fun testAppLogTreeLog() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        LogRepository.ioDispatcher = testDispatcher
        LogRepository.init(context)
        LogRepository.clearLogs()
        val tree = AppLogTree()
        val exception = RuntimeException("Test Exception")

        tree.e(exception, "Error occurred")
        val logs = LogRepository.logsFlow.value
        val size = logs.size
        val lastEntry = logs.last()
        val priority = lastEntry.priority
        val message = lastEntry.message
        val stackTrace = lastEntry.throwableStackTrace

        val hasPrefix = message.startsWith("Error occurred")
        assertEquals(1, size)
        assertEquals(Log.ERROR, priority)
        assertTrue(hasPrefix)
        assertEquals("StackTrace", stackTrace)
    }
}
