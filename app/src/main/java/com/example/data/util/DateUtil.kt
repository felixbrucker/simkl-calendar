package com.example.data.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
     * Parses an ISO date/time string into a Date object.
     */
    fun parseDate(isoDateStr: String?): Date? {
        if (isoDateStr.isNullOrBlank()) return null
        val trimmed = isoDateStr.trim()

        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )

        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                if (pattern.endsWith("'Z'") || pattern == "yyyy-MM-dd HH:mm:ss" || pattern.contains("'T'")) {
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(trimmed)
                if (date != null) return date
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Extracts localized time (e.g. "4:00 AM" or "16:00" in local timezone).
     * Returns null if only a date without time was provided.
     */
    fun formatLocalizedTime(isoDateStr: String?): String? {
        if (isoDateStr.isNullOrBlank() || isoDateStr.trim().length <= 10) return null
        val date = parseDate(isoDateStr) ?: return null
        val timeFormat = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT, Locale.getDefault())
        return timeFormat.format(date)
    }

    /**
     * Checks if a given ISO date is strictly earlier than today (00:00:00).
     */
    fun isEarlierThanToday(isoDateStr: String?): Boolean {
        val ymd = normalizeDate(isoDateStr) ?: return false
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val parsedDate = sdf.parse(ymd) ?: return false

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

            targetCal.before(todayCal)
        } catch (_: Exception) {
            false
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

    /**
     * Formats localized date and time into a single unified display string (e.g. "August 12, 2026 at 4:00 PM" or "August 12, 2026").
     */
    fun formatDisplayDateTime(isoDateStr: String?): String {
        if (isoDateStr.isNullOrBlank()) return "TBD"
        val parsedDate = parseDate(isoDateStr)
        if (parsedDate != null && isoDateStr.trim().length > 10) {
            val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT, Locale.getDefault())
            return "${dateFormat.format(parsedDate)} at ${timeFormat.format(parsedDate)}"
        }
        return formatDisplayDate(isoDateStr)
    }
}

