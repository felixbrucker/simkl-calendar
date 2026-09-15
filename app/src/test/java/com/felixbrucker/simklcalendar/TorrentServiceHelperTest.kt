package com.felixbrucker.simklcalendar.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentServiceHelperTest {

    @Test
    fun testDownloadProgressDataClass() {
        val progress = DownloadProgress(
            taskId = "task_123",
            uri = "magnet:?xt=urn:btih:abc",
            bytesDownloaded = 1000L,
            totalBytes = 2000L,
            downloadSpeed = 50.0,
            isCompleted = false,
            error = null
        )

        assertEquals("task_123", progress.taskId)
        assertEquals("magnet:?xt=urn:btih:abc", progress.uri)
        assertEquals(1000L, progress.bytesDownloaded)
        assertEquals(2000L, progress.totalBytes)
        assertEquals(50.0, progress.downloadSpeed, 0.001)
        assertFalse(progress.isCompleted)
        assertNull(progress.error)
    }

    @Test
    fun testDownloadProgressCompleted() {
        val completed = DownloadProgress(
            taskId = "task_456",
            uri = "magnet:?xt=urn:btih:def",
            bytesDownloaded = 2000L,
            totalBytes = 2000L,
            downloadSpeed = 0.0,
            isCompleted = true,
            error = null
        )

        assertTrue(completed.isCompleted)
    }
}
