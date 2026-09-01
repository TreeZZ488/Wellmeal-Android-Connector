package com.personalhealth.connector

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

class HealthJsonExporter(
    private val context: Context
) {

    fun exportDailyHealth(
        snapshot: DailyHealthSnapshot
    ): File {

        val root = JSONObject()

        root.put("schemaVersion", 1)
        root.put("generatedAt", ZonedDateTime.now().toString())
        root.put("timezone", ZoneId.systemDefault().id)
        root.put("date", snapshot.date.toString())

        // Activity data
        root.put(
            "activity",
            JSONObject()
                .putNullable("steps", snapshot.steps)
                .putNullable("exerciseMinutes", snapshot.exerciseMinutes)
        )

        // Heart rate data
        root.put(
            "heart",
            JSONObject()
                .putNullable(
                    "averageBpm",
                    snapshot.heartRateAverage
                )
                .putNullable(
                    "minimumBpm",
                    snapshot.heartRateMinimum
                )
                .putNullable(
                    "maximumBpm",
                    snapshot.heartRateMaximum
                )
        )

        // Sleep data
        root.put(
            "sleep",
            JSONObject()
                .putNullable(
                    "totalMinutes",
                    snapshot.sleepMinutes
                )
        )

        val exportDirectory =
            File(context.filesDir, "exports")

        if (!exportDirectory.exists()) {
            exportDirectory.mkdirs()
        }

        val outputFile =
            File(
                exportDirectory,
                "health-${snapshot.date}.json"
            )

        outputFile.writeText(
            root.toString(2)
        )

        return outputFile
    }

    private fun JSONObject.putNullable(
        key: String,
        value: Any?
    ): JSONObject {

        if (value == null) {
            put(key, JSONObject.NULL)
        } else {
            put(key, value)
        }

        return this
    }
}
