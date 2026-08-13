package com.example.data.util

object ImageUtil {

    /**
     * Standardizes Simkl poster URLs into fully qualified image links.
     */
    fun formatPosterUrl(posterRaw: String?): String {
        if (posterRaw.isNullOrBlank()) {
            return "https://simkl.in/poster_no_pic_c.png"
        }
        val trimmed = posterRaw.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }

        val clean = trimmed.removePrefix("/").removePrefix("posters/")
        val fileNameWithExt = when {
            clean.endsWith(".webp") || clean.endsWith(".jpg") || clean.endsWith(".png") || clean.endsWith(".jpeg") -> {
                clean
            }
            clean.matches(Regex(".*_[a-z0-9]+$")) -> {
                "$clean.webp"
            }
            else -> {
                "${clean}_c.webp"
            }
        }

        return "https://simkl.in/posters/$fileNameWithExt"
    }
}
