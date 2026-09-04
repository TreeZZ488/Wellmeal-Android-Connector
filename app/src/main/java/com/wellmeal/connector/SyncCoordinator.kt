package com.wellmeal.connector

import android.content.Context
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
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

enum class EmailDeliveryStatus {
    DISABLED,
    SENT,
    SKIPPED,
    FAILED
}

data class SyncResult(
    val date: LocalDate,
    val dailyUploaded: Boolean,
    val latestUploaded: Boolean,
    val profileStatus: ProfileSyncStatus,
    val emailStatus: EmailDeliveryStatus = EmailDeliveryStatus.DISABLED,
    val emailError: String? = null,
    val retryable: Boolean = false,
    val profileError: String? = null,
    val error: String? = null
)

private data class ProfileSyncResult(
    val status: ProfileSyncStatus,
    val error: String?,
    val file: File?,
    val isTransient: Boolean = false
)

class SyncCoordinator(
    private val context: Context,
    private val healthConnectRepository: HealthConnectRepository,
    private val healthJsonExporter: HealthJsonExporter,
    private val microsoftAuthManager: MicrosoftAuthManager,
    private val oneDriveUploader: OneDriveUploader,
    private val syncHistoryStore: SyncHistoryStore = SyncHistoryStore(context),
    private val syncSettingsStore: SyncSettingsStore = SyncSettingsStore(context),
    private val healthEmailSender: HealthEmailSender = HealthEmailSender()
) {

    /**
     * Executes one complete sync pipeline off the main thread, persisting exactly one history entry.
     */
    suspend fun performSync(
        trigger: SyncTrigger = SyncTrigger.MANUAL,
        isLastRetry: Boolean = false
    ): SyncResult = withContext(Dispatchers.IO) {
        val rawResult = executeSyncAttempt(trigger)
        val result = if (isLastRetry && rawResult.retryable) {
            rawResult.copy(retryable = false)
        } else {
            rawResult
        }
        recordSyncHistory(result, trigger)
        result
    }

    /**
     * Executes actual low-level sync steps without writing to SyncHistory.
     */
    @OptIn(ExperimentalPersonalHealthRecordApi::class)
    suspend fun executeSyncAttempt(trigger: SyncTrigger = SyncTrigger.MANUAL): SyncResult {
        // 1. Read yesterday's aggregated health data
        val snapshot = try {
            healthConnectRepository.getYesterdaySummary()
        } catch (e: Exception) {
            val isTransient = isTransientNetworkError(e)
            return SyncResult(
                date = LocalDate.now().minusDays(1),
                dailyUploaded = false,
                latestUploaded = false,
                profileStatus = ProfileSyncStatus.SKIPPED,
                retryable = isTransient,
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
                retryable = false,
                error = "Failed to export daily JSON locally: ${e.message}"
            )
        }

        // 3. Acquire Microsoft Graph access token silently for OneDrive
        val tokenResult = acquireToken()
        val accessToken = tokenResult.getOrElse {
            val isTransient = isTransientNetworkError(it)
            return SyncResult(
                date = snapshot.date,
                dailyUploaded = false,
                latestUploaded = false,
                profileStatus = ProfileSyncStatus.SKIPPED,
                retryable = isTransient,
                error = "Token acquisition failed: ${it.message}"
            )
        }

        // 4. Ensure the 'daily' folder exists in OneDrive App Folder
        val folderResult = oneDriveUploader.ensureFolder(accessToken, "daily")
        if (folderResult.isFailure) {
            val err = folderResult.exceptionOrNull()
            val isTransient = isTransientNetworkError(err)
            return SyncResult(
                date = snapshot.date,
                dailyUploaded = false,
                latestUploaded = false,
                profileStatus = ProfileSyncStatus.SKIPPED,
                retryable = isTransient,
                error = "AppFolder setup failed: ${err?.message}"
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
            val err = dailyUploadResult.exceptionOrNull()
            val isTransient = isTransientNetworkError(err)
            return SyncResult(
                date = snapshot.date,
                dailyUploaded = false,
                latestUploaded = false,
                profileStatus = ProfileSyncStatus.SKIPPED,
                retryable = isTransient,
                error = "Daily JSON upload failed: ${err?.message}"
            )
        }

        // 6. Upload exact same daily JSON to latest.json at root of App Folder
        val latestUploadResult = oneDriveUploader.uploadToAppFolderPath(
            accessToken = accessToken,
            file = dailyFile,
            relativePath = "latest.json"
        )

        if (latestUploadResult.isFailure) {
            val err = latestUploadResult.exceptionOrNull()
            val isTransient = isTransientNetworkError(err)
            return SyncResult(
                date = snapshot.date,
                dailyUploaded = true,
                latestUploaded = false,
                profileStatus = ProfileSyncStatus.SKIPPED,
                retryable = isTransient,
                error = "Latest JSON upload failed: ${err?.message}"
            )
        }

        // 7. Rebuild current Medical Profile, export locally, and upload/overwrite OneDrive profile.json
        val profileResult = syncMedicalProfile(accessToken)

        // 8. Optional Daily Health Email delivery
        val (emailStatus, emailError) = processDailyEmail(snapshot, trigger, dailyFile, profileResult.file)

        return SyncResult(
            date = snapshot.date,
            dailyUploaded = true,
            latestUploaded = true,
            profileStatus = profileResult.status,
            emailStatus = emailStatus,
            emailError = emailError,
            retryable = profileResult.isTransient,
            profileError = profileResult.error
        )
    }

    /**
     * Reads current Medical Profile state. If PHR permissions are missing or read fails,
     * returns ProfileSyncStatus.FAILED without overwriting OneDrive profile.json with empty data.
     */
    @OptIn(ExperimentalPersonalHealthRecordApi::class)
    private suspend fun syncMedicalProfile(accessToken: String): ProfileSyncResult {
        val parser = MedicalProfileParser()
        val medicalRepo = MedicalProfileRepository(context)
        val store = DietaryRestrictionStore(context)
        val exporter = HealthProfileJsonExporter(context)

        // 1. Read Health Connect PHR allergies and medications
        val allergies = try {
            medicalRepo.readAllergies()
        } catch (e: Exception) {
            return ProfileSyncResult(
                status = ProfileSyncStatus.FAILED,
                error = "Health Connect medical permission missing or allergy read failed: ${e.message}",
                file = null,
                isTransient = false
            )
        }

        val medications = try {
            medicalRepo.readMedications()
        } catch (e: Exception) {
            return ProfileSyncResult(
                status = ProfileSyncStatus.FAILED,
                error = "Health Connect medical permission missing or medication read failed: ${e.message}",
                file = null,
                isTransient = false
            )
        }

        // 2. Read locally managed dietary restrictions (always preserved)
        val dietaryRestrictions = try {
            store.load()
        } catch (_: Exception) {
            emptyList()
        }

        // 3. Rebuild HealthProfile (0 records from a successful read is a valid empty profile)
        val healthProfile = parser.parse(
            allergies = allergies,
            medications = medications,
            dietaryRestrictions = dietaryRestrictions
        )

        // 4. Export to local profile.json
        val profileFile = try {
            exporter.exportProfile(healthProfile)
        } catch (e: Exception) {
            return ProfileSyncResult(
                status = ProfileSyncStatus.FAILED,
                error = "Failed to generate profile.json locally: ${e.message}",
                file = null,
                isTransient = false
            )
        }

        // 5. Upload/overwrite OneDrive profile.json
        val uploadResult = oneDriveUploader.uploadToAppFolderPath(
            accessToken = accessToken,
            file = profileFile,
            relativePath = "profile.json"
        )

        return if (uploadResult.isSuccess) {
            ProfileSyncResult(
                status = ProfileSyncStatus.UPLOADED,
                error = null,
                file = profileFile,
                isTransient = false
            )
        } else {
            val uploadError = uploadResult.exceptionOrNull()
            val isTransient = isTransientNetworkError(uploadError)
            ProfileSyncResult(
                status = ProfileSyncStatus.FAILED,
                error = uploadError?.message,
                file = profileFile,
                isTransient = isTransient
            )
        }
    }

    /**
     * Handles optional daily health email delivery during automatic sync.
     */
    @OptIn(ExperimentalPersonalHealthRecordApi::class)
    private suspend fun processDailyEmail(
        snapshot: DailyHealthSnapshot,
        trigger: SyncTrigger,
        dailyFile: File?,
        profileFile: File?
    ): Pair<EmailDeliveryStatus, String?> {
        val settings = syncSettingsStore.load()

        if (!settings.dailyHealthEmailEnabled || settings.emailRecipient.isBlank()) {
            return Pair(EmailDeliveryStatus.DISABLED, null)
        }

        if (trigger != SyncTrigger.AUTOMATIC) {
            // Manual sync does not trigger automatic email
            return Pair(EmailDeliveryStatus.SKIPPED, null)
        }

        // Check idempotency: skip if already emailed for this date
        if (settings.lastEmailedDate == snapshot.date.toString()) {
            return Pair(EmailDeliveryStatus.SKIPPED, null)
        }

        // Acquire Mail.Send token silently
        val mailTokenResult = acquireMailToken()
        val mailAccessToken = mailTokenResult.getOrElse {
            return Pair(
                EmailDeliveryStatus.FAILED,
                "Mail.Send consent required: ${it.message}"
            )
        }

        // Read health profile for email body
        val healthProfile = try {
            val parser = MedicalProfileParser()
            val medicalRepo = MedicalProfileRepository(context)
            val store = DietaryRestrictionStore(context)
            val allergies = try { medicalRepo.readAllergies() } catch (_: Exception) { emptyList() }
            val medications = try { medicalRepo.readMedications() } catch (_: Exception) { emptyList() }
            val dietary = try { store.load() } catch (_: Exception) { emptyList() }
            parser.parse(
                allergies = allergies,
                medications = medications,
                dietaryRestrictions = dietary
            )
        } catch (_: Exception) {
            null
        }

        val bodyText = healthEmailSender.buildEmailBody(snapshot, healthProfile)
        val sendResult = healthEmailSender.sendDailyHealthEmail(
            accessToken = mailAccessToken,
            recipientEmail = settings.emailRecipient,
            date = snapshot.date,
            bodyText = bodyText,
            dailyFile = dailyFile,
            profileFile = profileFile
        )

        return if (sendResult.isSuccess) {
            syncSettingsStore.saveLastEmailedDate(snapshot.date.toString())
            Pair(EmailDeliveryStatus.SENT, null)
        } else {
            Pair(
                EmailDeliveryStatus.FAILED,
                sendResult.exceptionOrNull()?.message
            )
        }
    }

    /**
     * Converts SyncResult into SyncHistoryEntry and appends it to local storage.
     */
    fun recordSyncHistory(result: SyncResult, trigger: SyncTrigger) {
        try {
            val outcome = when {
                !result.dailyUploaded || !result.latestUploaded || result.error != null -> SyncOutcome.FAILED
                result.profileStatus == ProfileSyncStatus.FAILED || result.emailStatus == EmailDeliveryStatus.FAILED -> SyncOutcome.PARTIAL
                else -> SyncOutcome.SUCCESS
            }

            val formattedError = if (result.retryable && !result.error.isNullOrBlank()) {
                if (result.error.startsWith("Temporary network error")) {
                    result.error
                } else {
                    "Temporary network error: ${result.error}"
                }
            } else {
                result.error
            }

            val entry = SyncHistoryEntry(
                completedAt = Instant.now().toString(),
                date = result.date,
                trigger = trigger,
                outcome = outcome,
                dailyUploaded = result.dailyUploaded,
                latestUploaded = result.latestUploaded,
                profileStatus = result.profileStatus,
                emailStatus = result.emailStatus,
                retryScheduled = result.retryable,
                error = formattedError,
                profileError = result.profileError ?: result.emailError
            )

            syncHistoryStore.appendEntry(entry)
        } catch (_: Exception) {
            // Do not make an otherwise successful sync fail merely because writing local history file failed
        }
    }

    /**
     * Converts MicrosoftAuthManager callback into a suspend function for OneDrive token.
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

    /**
     * Converts MicrosoftAuthManager callback into a suspend function for Mail.Send token.
     */
    private suspend fun acquireMailToken(): Result<String> = suspendCancellableCoroutine { continuation ->
        microsoftAuthManager.acquireMailTokenSilent(
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

/**
 * Checks whether an exception is caused by a transient network condition (e.g. DNS resolution error, socket timeout).
 */
fun isTransientNetworkError(throwable: Throwable?): Boolean {
    var curr: Throwable? = throwable
    var depth = 0
    while (curr != null && depth < 10) {
        if (curr is java.net.UnknownHostException ||
            curr is java.net.SocketTimeoutException ||
            curr is java.net.ConnectException ||
            curr is java.net.SocketException
        ) {
            return true
        }

        if (curr is com.microsoft.identity.client.exception.MsalException) {
            val errorCode = curr.errorCode?.lowercase() ?: ""
            if (errorCode == "io_error" ||
                errorCode == "no_network" ||
                errorCode == "network_unavailable" ||
                errorCode == "device_offline"
            ) {
                return true
            }
        }

        val msg = curr.message?.lowercase() ?: ""
        if (msg.contains("unable to resolve host") ||
            msg.contains("no address associated with hostname") ||
            msg.contains("network layer") ||
            msg.contains("socket time out") ||
            msg.contains("sockettimeout") ||
            msg.contains("unknownhost") ||
            msg.contains("connection refused") ||
            msg.contains("connection reset") ||
            msg.contains("failed to connect to") ||
            msg.contains("software caused connection abort")
        ) {
            return true
        }

        if (curr is java.io.IOException && !msg.contains("file not found") && !msg.contains("permission denied")) {
            return true
        }

        curr = curr.cause
        depth++
    }
    return false
}
