package com.wellmeal.connector

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class OneDriveUploader {

    /**
     * Ensures and accesses the user's OneDrive App Folder (special/approot).
     *
     * @param accessToken Bearer token for Microsoft Graph API
     * @return Result indicating success or error description
     */
    suspend fun ensureAppFolder(
        accessToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://graph.microsoft.com/v1.0/me/drive/special/approot")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Result.success(Unit)
            } else {
                val errorStream = connection.errorStream ?: connection.inputStream
                val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: "No response body"
                val sanitizedError = sanitizeError(errorResponse, accessToken)
                Result.failure(Exception("AppFolder check failed (HTTP $responseCode): $sanitizedError"))
            }
        } catch (e: Exception) {
            val sanitizedMessage = sanitizeError(e.message ?: "AppFolder check error", accessToken)
            Result.failure(Exception(sanitizedMessage))
        }
    }

    /**
     * Uploads or replaces a file in the OneDrive App Folder (special/approot).
     *
     * @param accessToken Bearer token for Microsoft Graph API
     * @param file Local file to upload
     * @param remoteFilename Filename to use in the remote App Folder
     * @return Result indicating success or sanitized error description
     */
    suspend fun uploadToAppFolder(
        accessToken: String,
        file: File,
        remoteFilename: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext Result.failure(IllegalArgumentException("profile.json not found"))
        }

        // First ensure/access the special approot folder
        val folderCheck = ensureAppFolder(accessToken)
        if (folderCheck.isFailure) {
            return@withContext folderCheck
        }

        try {
            val endpointUrl = "https://graph.microsoft.com/v1.0/me/drive/special/approot:/$remoteFilename:/content"
            val url = URL(endpointUrl)
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "PUT"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")

            val fileBytes = file.readBytes()
            connection.setFixedLengthStreamingMode(fileBytes.size)

            connection.outputStream.use { os ->
                os.write(fileBytes)
                os.flush()
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                Result.success(Unit)
            } else {
                val errorStream = connection.errorStream ?: connection.inputStream
                val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: "No response body"
                val sanitizedError = sanitizeError(errorResponse, accessToken)
                Result.failure(Exception("HTTP $responseCode: $sanitizedError"))
            }
        } catch (e: Exception) {
            val sanitizedMessage = sanitizeError(e.message ?: "Unknown upload error", accessToken)
            Result.failure(Exception(sanitizedMessage))
        }
    }

    /**
     * Sanitizes error text to guarantee access token is never leaked in error messages.
     */
    private fun sanitizeError(input: String, accessToken: String): String {
        return if (accessToken.isNotBlank()) {
            input.replace(accessToken, "[REDACTED]")
        } else {
            input
        }
    }
}
