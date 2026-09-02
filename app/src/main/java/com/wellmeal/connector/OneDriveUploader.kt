package com.wellmeal.connector

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class OneDriveUploader {

    /**
     * Ensures and accesses the user's OneDrive App Folder (special/approot), returning its DriveItem ID.
     *
     * @param accessToken Bearer token for Microsoft Graph API
     * @return Result containing the App Folder item ID or error description
     */
    suspend fun ensureAppFolder(
        accessToken: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://graph.microsoft.com/v1.0/me/drive/special/approot")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val itemId = json.optString("id")
                if (itemId.isNotBlank()) {
                    Result.success(itemId)
                } else {
                    Result.failure(Exception("AppFolder item ID missing in response"))
                }
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
     * Ensures a subfolder (e.g. "daily") exists under the OneDrive App Folder using its DriveItem ID.
     *
     * @param accessToken Bearer token for Microsoft Graph API
     * @param folderName Subfolder name to ensure (e.g., "daily")
     * @return Result indicating success or sanitized error description
     */
    suspend fun ensureFolder(
        accessToken: String,
        folderName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val rootCheck = ensureAppFolder(accessToken)
        if (rootCheck.isFailure) {
            return@withContext Result.failure(rootCheck.exceptionOrNull()!!)
        }

        val appFolderItemId = rootCheck.getOrThrow()

        try {
            val url = URL("https://graph.microsoft.com/v1.0/me/drive/items/$appFolderItemId/children")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            val jsonPayload = JSONObject()
                .put("name", folderName)
                .put("folder", JSONObject())
                .put("@microsoft.graph.conflictBehavior", "fail")
                .toString()

            val bytes = jsonPayload.toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { os ->
                os.write(bytes)
                os.flush()
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_CREATED ||
                responseCode == HttpURLConnection.HTTP_OK ||
                responseCode == HttpURLConnection.HTTP_CONFLICT
            ) {
                Result.success(Unit)
            } else {
                val errorStream = connection.errorStream ?: connection.inputStream
                val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: "No response body"
                val sanitizedError = sanitizeError(errorResponse, accessToken)
                Result.failure(Exception("Folder creation failed (HTTP $responseCode): $sanitizedError"))
            }
        } catch (e: Exception) {
            val sanitizedMessage = sanitizeError(e.message ?: "Folder creation error", accessToken)
            Result.failure(Exception(sanitizedMessage))
        }
    }

    /**
     * Uploads or replaces a file in a relative path inside the OneDrive App Folder (e.g., "daily/2026-09-02.json").
     *
     * @param accessToken Bearer token for Microsoft Graph API
     * @param file Local file to upload
     * @param relativePath Relative path in App Folder
     * @return Result indicating success or sanitized error description
     */
    suspend fun uploadToAppFolderPath(
        accessToken: String,
        file: File,
        relativePath: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext Result.failure(IllegalArgumentException("${file.name} not found"))
        }

        try {
            val endpointUrl = "https://graph.microsoft.com/v1.0/me/drive/special/approot:/$relativePath:/content"
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
     * Uploads or replaces a file in the root of OneDrive App Folder (special/approot).
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
        val folderCheck = ensureAppFolder(accessToken)
        if (folderCheck.isFailure) {
            return@withContext Result.failure(folderCheck.exceptionOrNull()!!)
        }
        return@withContext uploadToAppFolderPath(accessToken, file, remoteFilename)
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
