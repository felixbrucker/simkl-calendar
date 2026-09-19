package com.felixbrucker.simklcalendar.data.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.TimeZone
import java.text.SimpleDateFormat

object DateUtil {

    // Thread-safe cached formatters to avoid pattern compilation during UI rendering
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val headerSameYearFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
    private val headerDiffYearFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
    private val slashDateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
    private val displayDateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)

    /**
     * Parses an HTTP date string (e.g. from Last-Modified header in RFC 1123 format) into an Instant.
     */
    fun parseHttpDateToInstant(httpDateStr: String?): Instant? {
        if (httpDateStr.isNullOrBlank()) return null
        val trimmed = httpDateStr.trim()
        return try {
            Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(trimmed))
        } catch (_: Exception) {
            try {
                val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("GMT")
                }
                sdf.parse(trimmed)?.toInstant()
            } catch (_: Exception) {
                null
            }
        }
    }

    fun parseHttpDateToMillis(httpDateStr: String?): Long? {
        return parseHttpDateToInstant(httpDateStr)?.toEpochMilli()
    }

    /**
     * Parses an ISO 8601 date/time string into a native Instant object.
     * Uses standard ISO 8601 parsers without manual string slicing:
     * - Standard ISO 8601 UTC / Offset timestamps (e.g. "2026-08-22T20:30:00Z", "2026-08-22T20:30:00+00:00")
     * - Date-only strings (e.g. movie DVD release date "2026-08-22", "08/22/2026")
     * - ISO date-time variations with space separator (e.g. "2026-08-22 20:30:00")
     */
    fun parseToInstant(rawDateStr: String?): Instant? {
        if (rawDateStr.isNullOrBlank()) return null
        val trimmed = rawDateStr.trim()
        return try {
            // Try parsing from ISO 8601 UTC / Offset timestamps
            Instant.parse(trimmed)
        } catch (_: Exception) {
            try {
                // Date-only (ISO 8601: "2026-08-22") or non-standard ("08/22/2026")
                val localDate = if (trimmed.contains("/")) {
                    LocalDate.parse(trimmed, slashDateFormatter)
                } else {
                    LocalDate.parse(trimmed)
                }
                localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
            } catch (_: Exception) {
                try {
                    val isoFormatted = trimmed.replace(" ", "T")
                    if (isoFormatted.contains("T")) {
                        if (!isoFormatted.endsWith("Z") && !isoFormatted.contains("+") && !isoFormatted.substringAfter("T").contains("-")) {
                            LocalDateTime.parse(isoFormatted).atZone(ZoneOffset.UTC).toInstant()
                        } else {
                            Instant.parse(isoFormatted)
                        }
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Checks if a given Instant is strictly before today in the user's local timezone.
     * Accepts optional pre-computed today/zone to avoid repeated system calls in loops.
     */
    fun isEarlierThanToday(
        date: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone)
    ): Boolean {
        val itemLocalDate = date.atZone(zone).toLocalDate()
        return itemLocalDate.isBefore(today)
    }

    /**
     * Formats a LocalDate into a calendar group header in the user's local date/time
     * (e.g. "TODAY - SUNDAY, AUGUST 23", "TOMORROW - MONDAY, AUGUST 24", or "SUNDAY, AUGUST 23").
     * Accepts optional pre-computed today/zone to avoid repeated system calls in loops.
     */
    fun formatAiringDateHeader(
        localDate: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone)
    ): String {
        val diffDays = ChronoUnit.DAYS.between(today, localDate)
        val displayFormatter = if (localDate.year != today.year) {
            headerDiffYearFormatter
        } else {
            headerSameYearFormatter
        }
        val dateLabel = localDate.format(displayFormatter).uppercase(Locale.getDefault())

        return when (diffDays) {
            0L -> "TODAY - $dateLabel"
            1L -> "TOMORROW - $dateLabel"
            -1L -> "YESTERDAY - $dateLabel"
            else -> dateLabel
        }
    }

    /**
     * Formats an Instant into a calendar group header in the user's local date/time.
     */
    fun formatAiringDateHeader(
        date: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone)
    ): String {
        val localDate = date.atZone(zone).toLocalDate()
        return formatAiringDateHeader(localDate, zone, today)
    }

    /**
     * Extracts localized time in 24-hour format (e.g. "16:00" or "04:30") in the user's local timezone.
     * Returns null if date-only (e.g. movie release).
     */
    fun formatLocalizedTime(date: Instant, isDateOnly: Boolean = false): String? {
        if (isDateOnly) return null
        val zonedDateTime = date.atZone(ZoneId.systemDefault())
        return zonedDateTime.format(timeFormatter)
    }

    /**
     * Formats an Instant for display in release details (e.g. "August 23, 2026") in local time.
     */
    fun formatDisplayDate(date: Instant): String {
        val localDate = date.atZone(ZoneId.systemDefault()).toLocalDate()
        return localDate.format(displayDateFormatter)
    }

    /**
     * Formats localized date and 24-hour time into a single unified display string
     * (e.g. "August 23, 2026 at 16:00") in local time.
     */
    fun formatDisplayDateTime(date: Instant, isDateOnly: Boolean = false): String {
        if (isDateOnly) {
            return formatDisplayDate(date)
        }
        val zonedDateTime = date.atZone(ZoneId.systemDefault())
        val dateStr = zonedDateTime.format(displayDateFormatter)
        val timeStr = zonedDateTime.format(timeFormatter)
        return "$dateStr at $timeStr"
    }

    /**
     * Formats a duration in seconds into a compact string like "1d 12h 34m 25s".
     * Only non-zero units are shown.
     */
    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "0s"
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        val parts = mutableListOf<String>()
        if (days > 0) parts.add("${days}d")
        if (hours > 0) parts.add("${hours}h")
        if (minutes > 0) parts.add("${minutes}m")
        if (secs > 0 || parts.isEmpty()) parts.add("${secs}s")

        return parts.joinToString(" ")
    }
}
