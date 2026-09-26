package com.wellmeal.connector

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectRepository(context: Context) {

    private val client = HealthConnectClient.getOrCreate(context)

    /**
     * Aggregates cumulative health metrics for TODAY from 00:00 local time up to current sync time.
     */
    suspend fun getTodaySummary(): DailyHealthSnapshot {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant()
        val end = Instant.now()
        return getSummaryForRange(today, start, end)
    }

    /**
     * Aggregates complete health metrics for YESTERDAY from 00:00 local time to today's 00:00 local time.
     */
    suspend fun getYesterdaySummary(): DailyHealthSnapshot {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        val start = yesterday.atStartOfDay(zone).toInstant()
        val end = today.atStartOfDay(zone).toInstant()
        return getSummaryForRange(yesterday, start, end)
    }

    private suspend fun getSummaryForRange(
        date: LocalDate,
        start: Instant,
        end: Instant
    ): DailyHealthSnapshot {
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(
                    StepsRecord.COUNT_TOTAL,
                    HeartRateRecord.BPM_AVG,
                    HeartRateRecord.BPM_MIN,
                    HeartRateRecord.BPM_MAX,
                    SleepSessionRecord.SLEEP_DURATION_TOTAL,
                    ExerciseSessionRecord.EXERCISE_DURATION_TOTAL
                ),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )

        return DailyHealthSnapshot(
            date = date,
            steps = result[StepsRecord.COUNT_TOTAL],
            heartRateAverage = result[HeartRateRecord.BPM_AVG],
            heartRateMinimum = result[HeartRateRecord.BPM_MIN],
            heartRateMaximum = result[HeartRateRecord.BPM_MAX],
            sleepMinutes = result[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes(),
            exerciseMinutes = result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes()
        )
    }
}
