package com.felixbrucker.simklcalendar.data.util

import android.os.Environment
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class DirectoryUtilsTest {

    @Before
    fun setUp() {
        mockkStatic(Environment::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(Environment::class)
    }

    @Test
    fun testGetDownloadSubdirectoriesWhenDirNotExists() {
        val nonExistentDir = File("/non/existent/path/downloads")
        every { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) } returns nonExistentDir

        val result = DirectoryUtils.getDownloadSubdirectories()
        assertTrue(result.isEmpty())
    }
}
