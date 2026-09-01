package com.wellmeal.connector

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId

class HealthProfileJsonExporter(
    private val context: Context
) {

    fun exportProfile(
        profile: HealthProfile
    ): File {

        val root = JSONObject()

        root.put("schemaVersion", 1)
        root.put("updatedAt", Instant.now().toString())
        root.put("timezone", ZoneId.systemDefault().id)

        root.put(
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

        root.put(
            "dietaryRestrictions",
            JSONArray(
                profile.dietaryRestrictions
            )
        )

        root.put(
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

        val exportDirectory =
            File(context.filesDir, "exports")

        if (!exportDirectory.exists()) {
            exportDirectory.mkdirs()
        }

        val outputFile =
            File(
                exportDirectory,
                "profile.json"
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
