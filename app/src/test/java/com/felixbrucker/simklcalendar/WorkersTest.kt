package com.felixbrucker.simklcalendar

import android.content.Context
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.felixbrucker.simklcalendar.extensions.isWorkCurrentlyQueuedByTag
import com.felixbrucker.simklcalendar.worker.AutoDownloadWorker
import com.felixbrucker.simklcalendar.worker.SyncCalendarWorker
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class WorkersTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = mockk<Context>(relaxed = true)
        workManager = mockk<WorkManager>(relaxed = true)

        val operationMock = mockk<Operation>(relaxed = true)
        every { workManager.enqueue(any<WorkRequest>()) } returns operationMock
        every { workManager.enqueueUniquePeriodicWork(any(), any(), any()) } returns operationMock
        every { workManager.cancelUniqueWork(any()) } returns operationMock
        every { workManager.cancelAllWorkByTag(any()) } returns operationMock

        val emptyFuture = mockk<ListenableFuture<List<WorkInfo>>>()
        every { emptyFuture.get() } returns emptyList()
        every { workManager.getWorkInfosByTag(any()) } returns emptyFuture

        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns workManager
    }

    @After
    fun tearDown() {
        unmockkObject(WorkManager.Companion)
    }

    @Test
    fun testAutoDownloadWorkerCancel() {
        val workId = UUID.randomUUID()
        val workInfoQueued = mockk<WorkInfo> {
            every { id } returns workId
            every { state } returns WorkInfo.State.ENQUEUED
        }
        val future = mockk<ListenableFuture<List<WorkInfo>>>()
        every { future.get() } returns listOf(workInfoQueued)
        every { workManager.getWorkInfosByTag(AutoDownloadWorker.WORK_TAG) } returns future

        AutoDownloadWorker.cancelQueuedAutoDownload(context)

        verify { workManager.cancelWorkById(workId) }
    }

    @Test
    fun testAutoDownloadWorkerEnqueueSearchOnce() {
        AutoDownloadWorker.enqueueImmediateSearch(context)

        verify { workManager.enqueue(any<WorkRequest>()) }
    }

    @Test
    fun testIsWorkCurrentlyQueuedByTag() {
        val workInfoQueued = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.ENQUEUED
        }
        val future = mockk<ListenableFuture<List<WorkInfo>>>()
        every { future.get() } returns listOf(workInfoQueued)
        every { workManager.getWorkInfosByTag("test_tag") } returns future

        assertTrue(workManager.isWorkCurrentlyQueuedByTag("test_tag"))
    }

    @Test
    fun testIsWorkCurrentlyQueuedByTagFinished() {
        val workInfoFinished = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.SUCCEEDED
        }
        val future = mockk<ListenableFuture<List<WorkInfo>>>()
        every { future.get() } returns listOf(workInfoFinished)
        every { workManager.getWorkInfosByTag("test_tag") } returns future

        assertFalse(workManager.isWorkCurrentlyQueuedByTag("test_tag"))
    }

    @Test
    fun testSyncCalendarWorkerConstantsAndEnqueue() {
        val uniqueName = SyncCalendarWorker.UNIQUE_WORK_NAME

        SyncCalendarWorker.enqueuePeriodicSync(context, 12)

        assertEquals("simkl_periodic_calendar_sync", uniqueName)
        verify { workManager.enqueueUniquePeriodicWork(SyncCalendarWorker.UNIQUE_WORK_NAME, any(), any()) }
    }

    @Test
    fun testSyncCalendarWorkerEnqueueZeroCoercesInterval() {
        SyncCalendarWorker.enqueuePeriodicSync(context, 0)

        verify { workManager.enqueueUniquePeriodicWork(SyncCalendarWorker.UNIQUE_WORK_NAME, any(), any()) }
    }
}
