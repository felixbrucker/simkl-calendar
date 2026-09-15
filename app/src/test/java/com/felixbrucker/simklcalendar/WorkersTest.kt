package com.felixbrucker.simklcalendar

import android.content.Context
import androidx.work.Operation
import androidx.work.WorkManager
import com.felixbrucker.simklcalendar.worker.AutoDownloadWorker
import com.felixbrucker.simklcalendar.worker.SyncCalendarWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class WorkersTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = mockk<Context>(relaxed = true)
        workManager = mockk<WorkManager>(relaxed = true)

        val operationMock = mockk<Operation>(relaxed = true)
        every { workManager.enqueueUniquePeriodicWork(any(), any(), any()) } returns operationMock

        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns workManager
    }

    @After
    fun tearDown() {
        unmockkObject(WorkManager.Companion)
    }

    @Test
    fun testAutoDownloadWorkerConstantsAndEnqueue() {
        assertEquals("simkl_periodic_auto_download", AutoDownloadWorker.UNIQUE_WORK_NAME)

        AutoDownloadWorker.enqueuePeriodicSearch(context, 12)
        verify { workManager.enqueueUniquePeriodicWork(AutoDownloadWorker.UNIQUE_WORK_NAME, any(), any()) }
    }

    @Test
    fun testSyncCalendarWorkerConstantsAndEnqueue() {
        assertEquals("simkl_periodic_calendar_sync", SyncCalendarWorker.UNIQUE_WORK_NAME)

        SyncCalendarWorker.enqueuePeriodicSync(context, 12)
        verify { workManager.enqueueUniquePeriodicWork(SyncCalendarWorker.UNIQUE_WORK_NAME, any(), any()) }
    }
}
