package com.example.data.util

import java.text.SimpleDateFormat
import java.util.*

object DateUtil {

    /**
     * Normalizes any Simkl date string (e.g. "2026-08-12T04:00:00Z", "2026-08-12 04:00:00",
     * "2026-08-12T00:00:00-04:00", "2026-08-12") to a standardized "yyyy-MM-dd" date.
     */
    fun normalizeDate(rawDate: String?): String? {
        if (rawDate.isNullOrBlank()) return null
        val trimmed = rawDate.trim()
        // If it starts with standard ISO format YYYY-MM-DD
        if (trimmed.length >= 10 && trimmed[4] == '-' && trimmed[7] == '-') {
            return trimmed.substring(0, 10)
        }
        return try {
            val parsed = parseDate(trimmed)
            if (parsed != null) {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed)
            } else {
                trimmed
            }
        } catch (e: Exception) {
            trimmed
        }
    }

    /**
     * Parses a date string into a Date object using common ISO and standard patterns.
     */
    fun parseDate(dateStr: String?): Date? {
        if (dateStr.isNullOrBlank()) return null
        val trimmed = dateStr.trim()

        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )

        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val result = sdf.parse(trimmed)
                if (result != null) return result
            } catch (_: Exception) { }
        }

        // Fallback: extract first 10 chars if matching yyyy-MM-dd
        if (trimmed.length >= 10 && trimmed[4] == '-' && trimmed[7] == '-') {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                }
                return sdf.parse(trimmed.substring(0, 10))
            } catch (_: Exception) { }
        }

        return null
    }

    /**
     * Formats a date string into a readable Airing Header (e.g. "TODAY - WEDNESDAY, AUGUST 12").
     */
    fun formatAiringDateHeader(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return "SOMEDAY"
        try {
            val normalized = normalizeDate(dateStr) ?: return dateStr.uppercase(Locale.getDefault())
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val parsedDate = sdf.parse(normalized) ?: return dateStr.uppercase(Locale.getDefault())

            val todayCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val targetCal = Calendar.getInstance().apply {
                time = parsedDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val diffMillis = targetCal.timeInMillis - todayCal.timeInMillis
            val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

            val displayFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
            val dateLabel = displayFormat.format(parsedDate).uppercase(Locale.getDefault())

            return when (diffDays) {
                0 -> "TODAY - $dateLabel"
                1 -> "TOMORROW - $dateLabel"
                -1 -> "YESTERDAY - $dateLabel"
                else -> dateLabel
            }
        } catch (e: Exception) {
            return dateStr.uppercase(Locale.getDefault())
        }
    }

    /**
     * Formats a date string for display in details (e.g. "August 12, 2026").
     */
    fun formatDisplayDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return "TBD"
        try {
            val normalized = normalizeDate(dateStr) ?: return dateStr
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val parsed = sdf.parse(normalized) ?: return dateStr
            return SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(parsed)
        } catch (e: Exception) {
            return dateStr
        }
    }
}
