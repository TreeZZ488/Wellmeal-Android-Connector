package com.personalhealth.connector

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

class HealthJsonExporter(
    private val context: Context
) {

    fun exportDailyHealth(
        snapshot: DailyHealthSnapshot,
        profile: HealthProfile = HealthProfile()
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

        // Profile data
        root.put(
            "profile",
            JSONObject()
                .put(
                    "allergies",
                    JSONArray().apply {
                        profile.allergies.forEach { allergy ->
                            put(
                                JSONObject()
                                    .put("name", allergy.name)
                                    .putNullable(
                                        "category",
                                        allergy.category
                                    )
                                    .putNullable(
                                        "severity",
                                        allergy.severity
                                    )
                            )
                        }
                    }
                )
                .put(
                    "dietaryRestrictions",
                    JSONArray(
                        profile.dietaryRestrictions
                    )
                )
                .put(
                    "medications",
                    JSONArray().apply {
                        profile.medications.forEach { medication ->
                            put(
                                JSONObject()
                                    .put("name", medication.name)
                                    .putNullable(
                                        "status",
                                        medication.status
                                    )
                            )
                        }
                    }
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