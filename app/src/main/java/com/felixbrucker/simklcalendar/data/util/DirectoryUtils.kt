package com.felixbrucker.simklcalendar.data.util

import android.os.Environment
import java.io.File

object DirectoryUtils {

    private val EXCLUDED_NAMES = setOf(
        "Adobe Acrobat",
        "Musicolet",
        "tmp",
        "update",
        "Quick Share"
    )

    fun getDownloadSubdirectories(): List<String> {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) ?: return emptyList()
        if (!downloadDir.exists() || !downloadDir.isDirectory) return emptyList()

        val subdirectories = mutableListOf<String>()
        scanDir(downloadDir, downloadDir, 0, subdirectories)
        return subdirectories.sorted()
    }

    private fun scanDir(baseDir: File, currentDir: File, depth: Int, result: MutableList<String>) {
        if (depth >= 2) return

        val directories = currentDir.listFiles { file ->
            file.isDirectory &&
            !file.name.startsWith(".") &&
            !EXCLUDED_NAMES.contains(file.name)
        } ?: return

        for (directory in directories) {
            val relativePath = directory.toRelativeString(baseDir)
            result.add(relativePath)
            scanDir(baseDir, directory, depth + 1, result)
        }
    }
}
