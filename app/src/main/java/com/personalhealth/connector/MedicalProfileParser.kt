package com.personalhealth.connector

import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import androidx.health.connect.client.records.MedicalResource
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalPersonalHealthRecordApi::class)
class MedicalProfileParser {

    fun parse(
        allergies: List<MedicalResource>,
        medications: List<MedicalResource>,
        dietaryRestrictions: List<String> = emptyList()
    ): HealthProfile {

        return HealthProfile(
            allergies = allergies.mapNotNull {
                parseAllergy(it)
            },
            dietaryRestrictions = dietaryRestrictions,
            medications = medications.mapNotNull {
                parseMedication(it)
            }
        )
    }

    private fun parseAllergy(
        resource: MedicalResource
    ): HealthAllergy? {

        val json = parseJson(resource) ?: return null

        if (json.optString("resourceType") != "AllergyIntolerance") {
            return null
        }

        val name =
            readCodeableConceptText(
                json.optJSONObject("code")
            ) ?: return null

        val category =
            json.optJSONArray("category")
                ?.optStringOrNull(0)

        val severity =
            json.optStringOrNull("criticality")
                ?: readReactionSeverity(json)

        return HealthAllergy(
            name = name,
            category = category,
            severity = severity
        )
    }

    private fun parseMedication(
        resource: MedicalResource
    ): Medication? {

        val json = parseJson(resource) ?: return null

        val resourceType =
            json.optString("resourceType")

        val name =
            when (resourceType) {

                "Medication" ->
                    readCodeableConceptText(
                        json.optJSONObject("code")
                    )

                "MedicationRequest",
                "MedicationStatement" ->
                    readMedicationName(json)

                else ->
                    null
            } ?: return null

        val status =
            json.optStringOrNull("status")

        return Medication(
            name = name,
            status = status
        )
    }

    private fun readMedicationName(
        json: JSONObject
    ): String? {

        val codedMedication =
            readCodeableConceptText(
                json.optJSONObject(
                    "medicationCodeableConcept"
                )
            )

        if (codedMedication != null) {
            return codedMedication
        }

        val medicationReference =
            json.optJSONObject(
                "medicationReference"
            )

        return medicationReference
            ?.optStringOrNull("display")
            ?: medicationReference
                ?.optStringOrNull("reference")
    }

    private fun readCodeableConceptText(
        concept: JSONObject?
    ): String? {

        if (concept == null) {
            return null
        }

        concept.optStringOrNull("text")
            ?.let {
                return it
            }

        val coding =
            concept.optJSONArray("coding")

        if (coding != null) {

            for (index in 0 until coding.length()) {

                val entry =
                    coding.optJSONObject(index)
                        ?: continue

                entry.optStringOrNull("display")
                    ?.let {
                        return it
                    }

                entry.optStringOrNull("code")
                    ?.let {
                        return it
                    }
            }
        }

        return null
    }

    private fun readReactionSeverity(
        json: JSONObject
    ): String? {

        val reactions =
            json.optJSONArray("reaction")
                ?: return null

        for (index in 0 until reactions.length()) {

            val reaction =
                reactions.optJSONObject(index)
                    ?: continue

            reaction.optStringOrNull("severity")
                ?.let {
                    return it
                }
        }

        return null
    }

    private fun parseJson(
        resource: MedicalResource
    ): JSONObject? {

        return try {

            JSONObject(
                resource.fhirResource.data
            )

        } catch (_: Exception) {

            null
        }
    }

    private fun JSONObject.optStringOrNull(
        key: String
    ): String? {

        if (!has(key) || isNull(key)) {
            return null
        }

        return optString(key)
            .takeIf {
                it.isNotBlank()
            }
    }

    private fun JSONArray.optStringOrNull(
        index: Int
    ): String? {

        if (index !in 0 until length()) {
            return null
        }

        return optString(index)
            .takeIf {
                it.isNotBlank()
            }
    }
}
