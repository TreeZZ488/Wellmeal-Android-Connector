package com.wellmeal.connector

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.resume

enum class ProfileSyncStatus {
    UPLOADED,
    SKIPPED,
    FAILED
}

data class SyncResult(
    val date: LocalDate,
    val dailyUploaded: Boolean,
    val profileStatus: ProfileSyncStatus,
    val profileError: String? = null,
    val error: String? = null
)

class SyncCoordinator(
    private val context: Context,
    private val healthConnectRepository: HealthConnectRepository,
    private val healthJsonExporter: HealthJsonExporter,
    private val microsoftAuthManager: MicrosoftAuthManager,
    private val oneDriveUploader: OneDriveUploader
) {

    /**
     * Executes one complete manual sync pipeline off the main thread.
     */
    suspend fun performSync(): SyncResult = withContext(Dispatchers.IO) {
        // 1. Read yesterday's aggregated health data
        val snapshot = try {
            healthConnectRepository.getYesterdaySummary()
        } catch (e: Exception) {
            return@withContext SyncResult(
                date = LocalDate.now().minusDays(1),
                dailyUploaded = false,
                profileStatus = ProfileSyncStatus.SKIPPED,
                error = "Failed to read Health Connect data: ${e.message}"
            )
        }

        // 2. Export daily health snapshot to local JSON
        val dailyFile = try {
            healthJsonExporter.exportDailyHealth(snapshot)
        } catch (e: Exception) {
            return@withContext SyncResult(
                date = snapshot.date,
                dailyUploaded = false,
                profileStatus = ProfileSyncStatus.SKIPPED,
                error = "Failed to export daily JSON locally: ${e.message}"
            )
        }

        // 3. Acquire Microsoft Graph access token silently
        val tokenResult = acquireToken()
        val accessToken = tokenResult.getOrElse {
            return@withContext SyncResult(
                date = snapshot.date,
                dailyUploaded = false,
                profileStatus = ProfileSyncStatus.SKIPPED,
                error = "Token acquisition failed: ${it.message}"
            )
        }

        // 4. Ensure the 'daily' folder exists in OneDrive App Folder
        val folderResult = oneDriveUploader.ensureFolder(accessToken, "daily")
        if (folderResult.isFailure) {
            return@withContext SyncResult(
                date = snapshot.date,
                dailyUploaded = false,
                profileStatus = ProfileSyncStatus.SKIPPED,
                error = "AppFolder setup failed: ${folderResult.exceptionOrNull()?.message}"
            )
        }

        // 5. Upload yesterday's daily JSON to daily/YYYY-MM-DD.json
        val dailyPath = "daily/${snapshot.date}.json"
        val dailyUploadResult = oneDriveUploader.uploadToAppFolderPath(
            accessToken = accessToken,
            file = dailyFile,
            relativePath = dailyPath
        )

        if (dailyUploadResult.isFailure) {
            return@withContext SyncResult(
                date = snapshot.date,
                dailyUploaded = false,
                profileStatus = ProfileSyncStatus.SKIPPED,
                error = "Daily JSON upload failed: ${dailyUploadResult.exceptionOrNull()?.message}"
            )
        }

        // 6. Check if profile.json exists locally and upload it if present
        val profileFile = File(context.filesDir, "exports/profile.json")
        val (profileStatus, profileError) = if (profileFile.exists()) {
            val profileUploadResult = oneDriveUploader.uploadToAppFolderPath(
                accessToken = accessToken,
                file = profileFile,
                relativePath = "profile.json"
            )
            if (profileUploadResult.isSuccess) {
                Pair(ProfileSyncStatus.UPLOADED, null)
            } else {
                Pair(
                    ProfileSyncStatus.FAILED,
                    profileUploadResult.exceptionOrNull()?.message
                )
            }
        } else {
            Pair(ProfileSyncStatus.SKIPPED, null)
        }

        SyncResult(
            date = snapshot.date,
            dailyUploaded = true,
            profileStatus = profileStatus,
            profileError = profileError
        )
    }

    /**
     * Converts MicrosoftAuthManager callback into a suspend function.
     */
    private suspend fun acquireToken(): Result<String> = suspendCancellableCoroutine { continuation ->
        microsoftAuthManager.acquireTokenSilent(
            scopes = listOf("Files.ReadWrite.AppFolder"),
            onSuccess = { authResult ->
                if (continuation.isActive) {
                    continuation.resume(Result.success(authResult.accessToken))
                }
            },
            onError = { exception ->
                if (continuation.isActive) {
                    continuation.resume(Result.failure(exception))
                }
            }
        )
    }
}
