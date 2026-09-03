package com.wellmeal.connector

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId

class HealthEmailSender {

    /**
     * Builds human-readable text body for the Daily Health Email report.
     */
    fun buildEmailBody(
        snapshot: DailyHealthSnapshot,
        profile: HealthProfile?,
        timezone: String = ZoneId.systemDefault().id
    ): String {
        val stepsText = snapshot.steps?.let { "$it" } ?: "Not available"
        val exerciseText = snapshot.exerciseMinutes?.let { "$it min" } ?: "Not available"

        val hrAvgText = snapshot.heartRateAverage?.let { "$it bpm" } ?: "Not available"
        val hrMinText = snapshot.heartRateMinimum?.let { "$it bpm" } ?: "Not available"
        val hrMaxText = snapshot.heartRateMaximum?.let { "$it bpm" } ?: "Not available"

        val sleepText = snapshot.sleepMinutes?.let { "$it min" } ?: "Not available"

        val allergiesText = profile?.allergies?.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { it.name } ?: "None"

        val dietaryText = profile?.dietaryRestrictions?.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") ?: "None"

        val medicationsText = profile?.medications?.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { it.name } ?: "None"

        return """
            Wellmeal Daily Health Report

            Date: ${snapshot.date}
            Timezone: $timezone

            Activity
            Steps: $stepsText
            Exercise: $exerciseText

            Heart
            Average: $hrAvgText
            Minimum: $hrMinText
            Maximum: $hrMaxText

            Sleep
            Total: $sleepText

            Medical Profile
            Allergies: $allergiesText
            Dietary restrictions: $dietaryText
            Medications: $medicationsText

            Generated automatically by Wellmeal Connector.
        """.trimIndent()
    }

    /**
     * Sends the Daily Health Email using Microsoft Graph POST /me/sendMail.
     */
    suspend fun sendDailyHealthEmail(
        accessToken: String,
        recipientEmail: String,
        date: LocalDate,
        bodyText: String,
        dailyFile: File?,
        profileFile: File?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (recipientEmail.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Recipient email is empty"))
        }

        try {
            val url = URL("https://graph.microsoft.com/v1.0/me/sendMail")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            val attachmentsArray = JSONArray()

            if (dailyFile != null && dailyFile.exists()) {
                val bytes = dailyFile.readBytes()
                val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                attachmentsArray.put(
                    JSONObject()
                        .put("@odata.type", "#microsoft.graph.fileAttachment")
                        .put("name", "$date.json")
                        .put("contentType", "application/json")
                        .put("contentBytes", base64Data)
                )
            }

            if (profileFile != null && profileFile.exists()) {
                val bytes = profileFile.readBytes()
                val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                attachmentsArray.put(
                    JSONObject()
                        .put("@odata.type", "#microsoft.graph.fileAttachment")
                        .put("name", "profile.json")
                        .put("contentType", "application/json")
                        .put("contentBytes", base64Data)
                )
            }

            val emailAddressJson = JSONObject().put("address", recipientEmail)
            val recipientJson = JSONObject().put("emailAddress", emailAddressJson)
            val toRecipientsArray = JSONArray().put(recipientJson)

            val bodyJson = JSONObject()
                .put("contentType", "Text")
                .put("content", bodyText)

            val messageJson = JSONObject()
                .put("subject", "[Wellmeal Daily] $date")
                .put("body", bodyJson)
                .put("toRecipients", toRecipientsArray)
                .put("attachments", attachmentsArray)

            val rootJson = JSONObject()
                .put("message", messageJson)
                .put("saveToSentItems", "false")

            val jsonString = rootJson.toString()
            val payloadBytes = jsonString.toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(payloadBytes.size)

            connection.outputStream.use { os ->
                os.write(payloadBytes)
                os.flush()
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_ACCEPTED ||
                responseCode == HttpURLConnection.HTTP_OK ||
                responseCode == HttpURLConnection.HTTP_CREATED
            ) {
                Result.success(Unit)
            } else {
                val errorStream = connection.errorStream ?: connection.inputStream
                val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: "No response body"
                val sanitizedError = sanitizeError(errorResponse, accessToken)
                Result.failure(Exception("Mail send failed (HTTP $responseCode): $sanitizedError"))
            }
        } catch (e: Exception) {
            val sanitizedMessage = sanitizeError(e.message ?: "Email send error", accessToken)
            Result.failure(Exception(sanitizedMessage))
        }
    }

    private fun sanitizeError(input: String, accessToken: String): String {
        return if (accessToken.isNotBlank()) {
            input.replace(accessToken, "[REDACTED]")
        } else {
            input
        }
    }
}
