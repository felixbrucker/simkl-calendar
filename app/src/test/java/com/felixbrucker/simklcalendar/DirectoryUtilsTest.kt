package com.felixbrucker.simklcalendar.data.util

import android.os.Environment
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DirectoryUtilsTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        mockkStatic(Environment::class)
        tempDir = Files.createTempDirectory("test_downloads").toFile()
        every { Environment.getExternalStoragePublicDirectory(any()) } returns tempDir
    }

    @After
    fun tearDown() {
        unmockkStatic(Environment::class)
        tempDir.deleteRecursively()
    }

    @Test
    fun testGetDownloadSubdirectoriesWhenDirNotExists() {
        val nonExistentDir = File(tempDir, "non_existent")
        every { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) } returns nonExistentDir

        val result = DirectoryUtils.getDownloadSubdirectories()

        assertTrue(result.isEmpty())
    }

    @Test
    fun testGetDownloadSubdirectoriesWithValidDirectories() {
        val validSubdir1 = File(tempDir, "AnimeSeries").apply { mkdirs() }
        val validSubdir2 = File(tempDir, "MoviesSub").apply { mkdirs() }
        val nestedSubdir = File(validSubdir1, "Season1").apply { mkdirs() }
        val deepSubdir = File(nestedSubdir, "SubSub").apply { mkdirs() }
        val hiddenDir = File(tempDir, ".hidden").apply { mkdirs() }
        val excludedDir = File(tempDir, "Musicolet").apply { mkdirs() }
        every { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) } returns tempDir

        val result = DirectoryUtils.getDownloadSubdirectories()

        assertTrue(result.contains("AnimeSeries"))
        assertTrue(result.contains("AnimeSeries/Season1"))
        assertTrue(result.contains("MoviesSub"))
        assertFalse(result.contains("AnimeSeries/Season1/SubSub"))
        assertFalse(result.contains(".hidden"))
        assertFalse(result.contains("Musicolet"))
    }
}
