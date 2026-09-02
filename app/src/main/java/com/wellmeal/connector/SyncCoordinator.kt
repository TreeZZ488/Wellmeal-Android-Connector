package com.wellmeal.connector

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
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
    val latestUploaded: Boolean,
    val profileStatus: ProfileSyncStatus,
    val profileError: String? = null,
    val error: String? = null
)

class SyncCoordinator(
    private val context: Context,
    private val healthConnectRepository: HealthConnectRepository,
    private val healthJsonExporter: HealthJsonExporter,
    private val microsoftAuthManager: MicrosoftAuthManager,
    private val oneDriveUploader: OneDriveUploader,
    private val syncHistoryStore: SyncHistoryStore = SyncHistoryStore(context)
) {

    /**
     * Executes one complete sync pipeline off the main thread, persisting exactly one history entry.
     */
    suspend fun performSync(
        trigger: SyncTrigger = SyncTrigger.MANUAL
    ): SyncResult = withContext(Dispatchers.IO) {
        val result = executeSync()
        saveSyncHistory(result, trigger)
        result
    }

    /**
     * Executes actual sync steps.
     */
    private suspend fun executeSync(): SyncResult {
        // 1. Read yesterday's aggregated health data
        val snapshot = try {
            healthConnectRepository.getYesterdaySummary()
        } catch (e: Exception) {
            return SyncResult(
                date = LocalDate.now().minusDays(1),
                dailyUploaded = false,
                latestUploaded = false,
                profileStatus = ProfileSyncStatus.SKIPPED,
                error = "Failed to read Health Connect data: ${e.message}"
            )
        }

        // 2. Export daily health snapshot to local JSON
        val dailyFile = try {
            healthJsonExporter.exportDailyHealth(snapshot)
        } catch (e: Exception) {
            return SyncResult(
                date = snapshot.date,
                dailyUploaded = false,
                latestUploaded = false,
                profileStatus = ProfileSyncStatus.SKIPPED,
                error = "Failed to export daily JSON locally: ${e.message}"
            )
        }

        // 3. Acquire Microsoft Graph access token silently
        val tokenResult = acquireToken()
        val accessToken = tokenResult.getOrElse {
            return SyncResult(
                date = snapshot.date,
                dailyUploaded = false,
                latestUploaded = false,
                profileStatus = ProfileSyncStatus.SKIPPED,
                error = "Token acquisition failed: ${it.message}"
            )
        }

        // 4. Ensure the 'daily' folder exists in OneDrive App Folder
        val folderResult = oneDriveUploader.ensureFolder(accessToken, "daily")
        if (folderResult.isFailure) {
            return SyncResult(
                date = snapshot.date,
                dailyUploaded = false,
                latestUploaded = false,
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
            return SyncResult(
                date = snapshot.date,
                dailyUploaded = false,
                latestUploaded = false,
                profileStatus = ProfileSyncStatus.SKIPPED,
                error = "Daily JSON upload failed: ${dailyUploadResult.exceptionOrNull()?.message}"
            )
        }

        // 6. Upload exact same daily JSON to latest.json at root of App Folder
        val latestUploadResult = oneDriveUploader.uploadToAppFolderPath(
            accessToken = accessToken,
            file = dailyFile,
            relativePath = "latest.json"
        )

        if (latestUploadResult.isFailure) {
            return SyncResult(
                date = snapshot.date,
                dailyUploaded = true,
                latestUploaded = false,
                profileStatus = ProfileSyncStatus.SKIPPED,
                error = "Latest JSON upload failed: ${latestUploadResult.exceptionOrNull()?.message}"
            )
        }

        // 7. Check if profile.json exists locally and upload it if present
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

        return SyncResult(
            date = snapshot.date,
            dailyUploaded = true,
            latestUploaded = true,
            profileStatus = profileStatus,
            profileError = profileError
        )
    }

    /**
     * Converts SyncResult into SyncHistoryEntry and appends it to local storage.
     */
    private fun saveSyncHistory(result: SyncResult, trigger: SyncTrigger) {
        try {
            val outcome = when {
                !result.dailyUploaded || !result.latestUploaded || result.error != null -> SyncOutcome.FAILED
                result.profileStatus == ProfileSyncStatus.FAILED -> SyncOutcome.PARTIAL
                else -> SyncOutcome.SUCCESS
            }

            val entry = SyncHistoryEntry(
                completedAt = Instant.now().toString(),
                date = result.date,
                trigger = trigger,
                outcome = outcome,
                dailyUploaded = result.dailyUploaded,
                latestUploaded = result.latestUploaded,
                profileStatus = result.profileStatus,
                error = result.error,
                profileError = result.profileError
            )

            syncHistoryStore.appendEntry(entry)
        } catch (_: Exception) {
            // Do not make an otherwise successful sync fail merely because writing local history file failed
        }
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
