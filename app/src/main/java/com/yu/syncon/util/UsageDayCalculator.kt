package com.yu.syncon.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Single source of truth for all usage-day calculations.
 * A usage-day runs strictly from 4:00 AM to 4:00 AM the next day.
 * Example: 1:00 AM Tuesday is attributed to Monday's usage-day.
 */
object UsageDayCalculator {

    private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Converts a given epoch millisecond timestamp into its usage-day date string (YYYY-MM-DD).
     */
    fun getUsageDate(timestampMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val zdt = Instant.ofEpochMilli(timestampMs).atZone(zoneId)
        val date = if (zdt.hour < 4) {
            zdt.toLocalDate().minusDays(1)
        } else {
            zdt.toLocalDate()
        }
        return date.format(DATE_FORMATTER)
    }

    /**
     * Gets the usage-day date string for right now.
     */
    fun getTodayUsageDate(zoneId: ZoneId = ZoneId.systemDefault()): String {
        return getUsageDate(System.currentTimeMillis(), zoneId)
    }

    /**
     * Returns the start (inclusive) and end (exclusive) epoch millisecond timestamps
     * for a given usage-day date string ("YYYY-MM-DD").
     * The range begins at 4:00:00.000 AM on that date and ends at 4:00:00.000 AM the next day.
     */
    fun getUsageDayRange(usageDate: String, zoneId: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val localDate = LocalDate.parse(usageDate, DATE_FORMATTER)
        val startZdt = localDate.atTime(4, 0, 0, 0).atZone(zoneId)
        val endZdt = localDate.plusDays(1).atTime(4, 0, 0, 0).atZone(zoneId)
        return Pair(startZdt.toInstant().toEpochMilli(), endZdt.toInstant().toEpochMilli())
    }

    /**
     * Returns the list of last N usage-day dates up to and including the current usage-day.
     */
    fun getRecentUsageDates(count: Int, zoneId: ZoneId = ZoneId.systemDefault()): List<String> {
        val today = LocalDate.parse(getTodayUsageDate(zoneId), DATE_FORMATTER)
        return (count - 1 downTo 0).map { offset ->
            today.minusDays(offset.toLong()).format(DATE_FORMATTER)
        }
    }
}
