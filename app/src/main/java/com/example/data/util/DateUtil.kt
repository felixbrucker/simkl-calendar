package com.example.data.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object DateUtil {

    /**
     * Parses an ISO 8601 date/time string into a native Instant object.
     * Uses standard ISO 8601 parsers without manual string slicing:
     * - Standard ISO 8601 UTC / Offset timestamps (e.g. "2026-08-22T20:30:00Z", "2026-08-22T20:30:00+00:00")
     * - Date-only strings (e.g. movie DVD release date "2026-08-22")
     * - ISO date-time variations with space separator (e.g. "2026-08-22 20:30:00")
     */
    fun parseToInstant(rawDateStr: String?): Instant? {
        if (rawDateStr.isNullOrBlank()) return null
        val trimmed = rawDateStr.trim()
        return try {
            Instant.parse(trimmed)
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(trimmed).toInstant()
            } catch (_: Exception) {
                try {
                    // Date-only ISO format (e.g., movie DVD release date "2026-08-22")
                    LocalDate.parse(trimmed).atStartOfDay(ZoneId.systemDefault()).toInstant()
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
    }

    /**
     * Checks if a given Instant is strictly before today in the user's local timezone.
     */
    fun isEarlierThanToday(date: Instant): Boolean {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val itemLocalDate = date.atZone(zone).toLocalDate()
        return itemLocalDate.isBefore(today)
    }

    /**
     * Formats an Instant into a calendar group header in the user's local date/time
     * (e.g. "TODAY - SUNDAY, AUGUST 23", "TOMORROW - MONDAY, AUGUST 24", or "SUNDAY, AUGUST 23").
     */
    fun formatAiringDateHeader(date: Instant): String {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val localDate = date.atZone(zone).toLocalDate()

        val diffDays = ChronoUnit.DAYS.between(today, localDate)
        val displayFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
        val dateLabel = localDate.format(displayFormatter).uppercase(Locale.getDefault())

        return when (diffDays) {
            0L -> "TODAY - $dateLabel"
            1L -> "TOMORROW - $dateLabel"
            -1L -> "YESTERDAY - $dateLabel"
            else -> dateLabel
        }
    }

    /**
     * Extracts localized time in 24-hour format (e.g. "16:00" or "04:30") in the user's local timezone.
     * Returns null if date-only (e.g. movie release).
     */
    fun formatLocalizedTime(date: Instant, isDateOnly: Boolean = false): String? {
        if (isDateOnly) return null
        val zonedDateTime = date.atZone(ZoneId.systemDefault())
        return zonedDateTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
    }

    /**
     * Formats an Instant for display in release details (e.g. "August 23, 2026") in local time.
     */
    fun formatDisplayDate(date: Instant): String {
        val localDate = date.atZone(ZoneId.systemDefault()).toLocalDate()
        return localDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
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
        val dateStr = zonedDateTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
        val timeStr = zonedDateTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        return "$dateStr at $timeStr"
    }
}
