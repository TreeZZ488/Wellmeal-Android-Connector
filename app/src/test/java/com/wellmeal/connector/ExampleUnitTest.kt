package com.wellmeal.connector

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExampleUnitTest {

    @Test
    fun emailBody_twoSections_formattingIsCorrect() {
        val sender = HealthEmailSender()
        val todaySnapshot = DailyHealthSnapshot(
            date = LocalDate.of(2026, 9, 26),
            steps = 9842,
            heartRateAverage = 76,
            heartRateMinimum = 52,
            heartRateMaximum = 143,
            sleepMinutes = 391,
            exerciseMinutes = 42
        )
        val yesterdaySnapshot = DailyHealthSnapshot(
            date = LocalDate.of(2026, 9, 25),
            steps = 12341,
            heartRateAverage = 73,
            heartRateMinimum = 49,
            heartRateMaximum = 151,
            sleepMinutes = 418,
            exerciseMinutes = 67
        )

        val body = sender.buildEmailBody(
            todaySnapshot = todaySnapshot,
            yesterdaySnapshot = yesterdaySnapshot,
            asOfTime = "20:03",
            timezone = "Asia/Tokyo"
        )

        // Verify title & headings
        assertTrue(body.contains("WellMeal Health Data"))
        assertTrue(body.contains("Timezone: Asia/Tokyo"))
        assertTrue(body.contains("Today's Data"))
        assertTrue(body.contains("Yesterday's Data"))

        // Verify Today's section details
        val todayIndex = body.indexOf("Today's Data")
        val yesterdayIndex = body.indexOf("Yesterday's Data")
        assertTrue(todayIndex < yesterdayIndex)

        val todayPart = body.substring(todayIndex, yesterdayIndex)
        assertTrue(todayPart.contains("Date: 2026-09-26"))
        assertTrue(todayPart.contains("As of: 20:03"))
        assertTrue(todayPart.contains("Steps: 9842"))
        assertTrue(todayPart.contains("Exercise: 42 min"))
        assertTrue(todayPart.contains("Average: 76 bpm"))
        assertTrue(todayPart.contains("Minimum: 52 bpm"))
        assertTrue(todayPart.contains("Maximum: 143 bpm"))
        assertTrue(todayPart.contains("Total: 391 min"))

        // Verify Yesterday's section details (NO "As of")
        val yesterdayPart = body.substring(yesterdayIndex)
        assertTrue(yesterdayPart.contains("Date: 2026-09-25"))
        assertFalse(yesterdayPart.contains("As of:"))
        assertTrue(yesterdayPart.contains("Steps: 12341"))
        assertTrue(yesterdayPart.contains("Exercise: 67 min"))
        assertTrue(yesterdayPart.contains("Average: 73 bpm"))
        assertTrue(yesterdayPart.contains("Minimum: 49 bpm"))
        assertTrue(yesterdayPart.contains("Maximum: 151 bpm"))
        assertTrue(yesterdayPart.contains("Total: 418 min"))

        // Verify Medical Profile content is absent
        assertFalse(body.contains("Medical Profile"))
        assertFalse(body.contains("Allergies"))
        assertFalse(body.contains("Dietary restrictions"))
        assertFalse(body.contains("Medications"))
    }

    @Test
    fun emailBody_nullValues_renderNotAvailable() {
        val sender = HealthEmailSender()
        val todaySnapshot = DailyHealthSnapshot(
            date = LocalDate.of(2026, 9, 26),
            steps = null,
            heartRateAverage = null,
            heartRateMinimum = null,
            heartRateMaximum = null,
            sleepMinutes = null,
            exerciseMinutes = null
        )
        val yesterdaySnapshot = DailyHealthSnapshot(
            date = LocalDate.of(2026, 9, 25),
            steps = null,
            heartRateAverage = null,
            heartRateMinimum = null,
            heartRateMaximum = null,
            sleepMinutes = null,
            exerciseMinutes = null
        )

        val body = sender.buildEmailBody(
            todaySnapshot = todaySnapshot,
            yesterdaySnapshot = yesterdaySnapshot,
            asOfTime = "08:00",
            timezone = "Asia/Tokyo"
        )

        assertTrue(body.contains("Steps: Not available"))
        assertTrue(body.contains("Exercise: Not available"))
        assertTrue(body.contains("Average: Not available"))
        assertTrue(body.contains("Minimum: Not available"))
        assertTrue(body.contains("Maximum: Not available"))
        assertTrue(body.contains("Total: Not available"))
    }
}
