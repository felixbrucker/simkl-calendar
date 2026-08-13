package com.example.data.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DateUtil {

    /**
     * Extracts the standard YYYY-MM-DD date from ISO-8601 timestamps (e.g. "2026-08-12T04:00:00Z", "2026-08-12").
     */
    fun normalizeDate(isoDateStr: String?): String? {
        if (isoDateStr.isNullOrBlank()) return null
        val trimmed = isoDateStr.trim()
        return if (trimmed.length >= 10 && trimmed[4] == '-' && trimmed[7] == '-') {
            trimmed.substring(0, 10)
        } else {
            trimmed
        }
    }

    /**
     * Formats an ISO-8601 date string into a calendar group header (e.g. "TODAY - WEDNESDAY, AUGUST 12").
     */
    fun formatAiringDateHeader(isoDateStr: String?): String {
        val ymd = normalizeDate(isoDateStr) ?: return "SOMEDAY"
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val parsedDate = sdf.parse(ymd) ?: return ymd

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

            when (diffDays) {
                0 -> "TODAY - $dateLabel"
                1 -> "TOMORROW - $dateLabel"
                -1 -> "YESTERDAY - $dateLabel"
                else -> dateLabel
            }
        } catch (_: Exception) {
            ymd.uppercase(Locale.getDefault())
        }
    }

    /**
     * Formats an ISO-8601 date string for display in details (e.g. "August 12, 2026").
     */
    fun formatDisplayDate(isoDateStr: String?): String {
        val ymd = normalizeDate(isoDateStr) ?: return "TBD"
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val parsed = sdf.parse(ymd) ?: return ymd
            SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(parsed)
        } catch (_: Exception) {
            ymd
        }
    }
}

