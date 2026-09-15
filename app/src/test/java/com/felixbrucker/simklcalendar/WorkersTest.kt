package com.felixbrucker.simklcalendar

import com.felixbrucker.simklcalendar.worker.AutoDownloadWorker
import com.felixbrucker.simklcalendar.worker.SyncCalendarWorker
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkersTest {

    @Test
    fun testAutoDownloadWorkerConstants() {
        assertEquals("simkl_periodic_auto_download", AutoDownloadWorker.UNIQUE_WORK_NAME)
    }

    @Test
    fun testSyncCalendarWorkerConstants() {
        assertEquals("simkl_periodic_calendar_sync", SyncCalendarWorker.UNIQUE_WORK_NAME)
    }
}
