package com.wellmeal.connector

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectRepository(context: Context) {

    private val client =
        HealthConnectClient.getOrCreate(context)

    suspend fun getYesterdaySummary(): DailyHealthSnapshot {

        val zone = ZoneId.systemDefault()

        val yesterday =
            LocalDate.now(zone).minusDays(1)

        val start =
            yesterday
                .atStartOfDay(zone)
                .toInstant()

        val end =
            yesterday
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()

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

                timeRangeFilter =
                    TimeRangeFilter.between(
                        start,
                        end
                    )
            )
        )

        return DailyHealthSnapshot(

            date = yesterday,

            steps =
                result[StepsRecord.COUNT_TOTAL],

            heartRateAverage =
                result[HeartRateRecord.BPM_AVG],

            heartRateMinimum =
                result[HeartRateRecord.BPM_MIN],

            heartRateMaximum =
                result[HeartRateRecord.BPM_MAX],

            sleepMinutes =
                result[
                    SleepSessionRecord.SLEEP_DURATION_TOTAL
                ]?.toMinutes(),

            exerciseMinutes =
                result[
                    ExerciseSessionRecord.EXERCISE_DURATION_TOTAL
                ]?.toMinutes()
        )
    }
}
