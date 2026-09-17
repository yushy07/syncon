package com.yu.syncon.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class UsageDayCalculatorTest {

    private val testZoneId = ZoneId.of("UTC")

    @Test
    fun `timestamp at 3_59 AM belongs to previous calendar day`() {
        val dt = ZonedDateTime.of(2026, 9, 17, 3, 59, 59, 0, testZoneId)
        val usageDate = UsageDayCalculator.getUsageDate(dt.toInstant().toEpochMilli(), testZoneId)
        assertEquals("2026-09-16", usageDate)
    }

    @Test
    fun `timestamp at 4_00 AM belongs to current calendar day`() {
        val dt = ZonedDateTime.of(2026, 9, 17, 4, 0, 0, 0, testZoneId)
        val usageDate = UsageDayCalculator.getUsageDate(dt.toInstant().toEpochMilli(), testZoneId)
        assertEquals("2026-09-17", usageDate)
    }

    @Test
    fun `timestamp at 11_30 PM belongs to current calendar day`() {
        val dt = ZonedDateTime.of(2026, 9, 17, 23, 30, 0, 0, testZoneId)
        val usageDate = UsageDayCalculator.getUsageDate(dt.toInstant().toEpochMilli(), testZoneId)
        assertEquals("2026-09-17", usageDate)
    }

    @Test
    fun `timestamp at 1_00 AM on Jan 1 belongs to Dec 31 of previous year`() {
        val dt = ZonedDateTime.of(2026, 1, 1, 1, 0, 0, 0, testZoneId)
        val usageDate = UsageDayCalculator.getUsageDate(dt.toInstant().toEpochMilli(), testZoneId)
        assertEquals("2025-12-31", usageDate)
    }

    @Test
    fun `usage day range starts at 4_00 AM and ends at 4_00 AM next day`() {
        val (startMs, endMs) = UsageDayCalculator.getUsageDayRange("2026-09-17", testZoneId)
        val startZdt = ZonedDateTime.of(2026, 9, 17, 4, 0, 0, 0, testZoneId)
        val endZdt = ZonedDateTime.of(2026, 9, 18, 4, 0, 0, 0, testZoneId)

        assertEquals(startZdt.toInstant().toEpochMilli(), startMs)
        assertEquals(endZdt.toInstant().toEpochMilli(), endMs)
    }
}
