package com.wellmeal.connector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExampleUnitTest {

    @Test
    fun emailBody_formatting_isCorrect() {
        val sender = HealthEmailSender()
        val snapshot = DailyHealthSnapshot(
            date = LocalDate.of(2026, 9, 24),
            steps = 9842,
            heartRateAverage = 76,
            heartRateMinimum = 52,
            heartRateMaximum = 143,
            sleepMinutes = 391,
            exerciseMinutes = 42
        )

        val body = sender.buildEmailBody(
            snapshot = snapshot,
            asOfTime = "20:03",
            timezone = "Asia/Tokyo"
        )

        assertTrue(body.contains("WellMeal Health Data"))
        assertTrue(body.contains("Date: 2026-09-24"))
        assertTrue(body.contains("As of: 20:03"))
        assertTrue(body.contains("Timezone: Asia/Tokyo"))
        assertTrue(body.contains("Steps: 9842"))
        assertTrue(body.contains("Exercise: 42 min"))
        assertTrue(body.contains("Average: 76 bpm"))
        assertTrue(body.contains("Minimum: 52 bpm"))
        assertTrue(body.contains("Maximum: 143 bpm"))
        assertTrue(body.contains("Total: 391 min"))
    }

    @Test
    fun emailBody_nullValues_renderNotAvailable() {
        val sender = HealthEmailSender()
        val snapshot = DailyHealthSnapshot(
            date = LocalDate.of(2026, 9, 24),
            steps = null,
            heartRateAverage = null,
            heartRateMinimum = null,
            heartRateMaximum = null,
            sleepMinutes = null,
            exerciseMinutes = null
        )

        val body = sender.buildEmailBody(
            snapshot = snapshot,
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
