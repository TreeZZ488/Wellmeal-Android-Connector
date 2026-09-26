package com.wellmeal.connector

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val workManagerAttempt = runAttemptCount
        val isLastWorkManagerRetry = workManagerAttempt >= MAX_WORKMANAGER_RETRIES
        Log.d(TAG, "automatic sync started runAttemptCount=$workManagerAttempt maxWorkManagerRetries=$MAX_WORKMANAGER_RETRIES")

        val context = applicationContext
        val notificationManager = SyncNotificationManager(context)
        val diagnosticStore = AutoSyncDiagnosticStore(context)

        // Record worker start in diagnostic store
        diagnosticStore.recordStarted()

        // Show quiet ongoing progress notification
        notificationManager.showProgressNotification()

        return try {
            // Create required backend components
            val healthConnectRepository = HealthConnectRepository(context)
            val healthJsonExporter = HealthJsonExporter(context)
            val authManager = MicrosoftAuthManager(context)
            val oneDriveUploader = OneDriveUploader()
            val syncHistoryStore = SyncHistoryStore(context)

            val syncCoordinator = SyncCoordinator(
                context = context,
                healthConnectRepository = healthConnectRepository,
                healthJsonExporter = healthJsonExporter,
                microsoftAuthManager = authManager,
                oneDriveUploader = oneDriveUploader,
                syncHistoryStore = syncHistoryStore,
                diagnosticStore = diagnosticStore
            )

            // Wait for MSAL initialization and account restoration
            diagnosticStore.recordStage(SyncDiagnosticStage.MSAL_INITIALIZING)
            val readyResult = authManager.awaitReady()
            val account = readyResult.getOrNull()

            if (account == null) {
                // No cached signed-in account found -> Permanent failure
                Log.d(TAG, "permanent failure detected (no cached account)")
                val errorMsg = "No cached Microsoft account signed in"
                val failResult = SyncResult(
                    date = java.time.LocalDate.now().minusDays(1),
                    dailyUploaded = false,
                    latestUploaded = false,
                    profileStatus = ProfileSyncStatus.SKIPPED,
                    retryable = false,
                    error = errorMsg
                )
                syncCoordinator.recordSyncHistory(failResult, SyncTrigger.AUTOMATIC)
                diagnosticStore.recordCompleted(success = false, error = errorMsg)
                notificationManager.showFailureNotification()
                return Result.failure()
            }

            diagnosticStore.recordStage(SyncDiagnosticStage.MSAL_READY)

            // Fast in-Worker retry loop (attempt 1 immediate, then 5s, 10s, 20s delays)
            val maxFastAttempts = 1 + FAST_RETRY_DELAYS_MS.size
            var currentResult: SyncResult? = null

            for (attemptIndex in 0 until maxFastAttempts) {
                val attemptNumber = attemptIndex + 1
                Log.d(TAG, "fast sync attempt=$attemptNumber/$maxFastAttempts")

                val syncResult = syncCoordinator.executeSyncAttempt(trigger = SyncTrigger.AUTOMATIC)
                currentResult = syncResult

                // If core sync succeeded (daily + latest uploaded), stop fast retries immediately
                if (syncResult.dailyUploaded && syncResult.latestUploaded) {
                    Log.d(TAG, "fast sync succeeded attempt=$attemptNumber")
                    syncCoordinator.recordSyncHistory(syncResult, SyncTrigger.AUTOMATIC)
                    diagnosticStore.recordStage(SyncDiagnosticStage.COMPLETED)
                    diagnosticStore.recordCompleted(success = true)
                    notificationManager.cancelProgressNotification()
                    return Result.success()
                }

                // If non-retryable (permanent error, e.g. permission/auth failure), stop fast retries
                if (!syncResult.retryable) {
                    Log.d(TAG, "permanent failure detected on fast attempt=$attemptNumber")
                    syncCoordinator.recordSyncHistory(syncResult, SyncTrigger.AUTOMATIC)
                    diagnosticStore.recordCompleted(success = false, error = syncResult.error)
                    notificationManager.showFailureNotification()
                    return Result.failure()
                }

                // If transient network error and we have remaining fast retries
                if (attemptIndex < FAST_RETRY_DELAYS_MS.size) {
                    val delayMs = FAST_RETRY_DELAYS_MS[attemptIndex]
                    val delaySeconds = delayMs / 1000
                    Log.d(TAG, "transient network failure on fast attempt=$attemptNumber")
                    Log.d(TAG, "waiting $delaySeconds seconds before fast retry")
                    delay(delayMs)
                }
            }

            // All fast retries in this Worker execution failed with transient network errors
            val finalResult = currentResult ?: SyncResult(
                date = java.time.LocalDate.now().minusDays(1),
                dailyUploaded = false,
                latestUploaded = false,
                profileStatus = ProfileSyncStatus.SKIPPED,
                retryable = true,
                error = "All fast retries failed due to transient network errors"
            )

            if (!isLastWorkManagerRetry) {
                Log.d(TAG, "fast retries exhausted -> Result.retry()")
                val retryResult = finalResult.copy(retryable = true)
                syncCoordinator.recordSyncHistory(retryResult, SyncTrigger.AUTOMATIC)
                diagnosticStore.recordCompleted(
                    success = false,
                    error = finalResult.error ?: "Transient network failure - WorkManager retry scheduled"
                )
                // Cancel progress notification quietly; WorkManager will retry later silently with backoff
                notificationManager.cancelProgressNotification()
                Result.retry()
            } else {
                Log.d(TAG, "fast retries exhausted and max WorkManager retries reached -> Result.failure()")
                val finalFailResult = finalResult.copy(retryable = false)
                syncCoordinator.recordSyncHistory(finalFailResult, SyncTrigger.AUTOMATIC)
                diagnosticStore.recordCompleted(
                    success = false,
                    error = finalResult.error ?: "Max WorkManager retries reached"
                )
                notificationManager.showFailureNotification()
                Result.failure()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            val reasonCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                stopReason
            } else {
                WorkInfo.STOP_REASON_UNKNOWN
            }
            val reasonText = formatStopReason(reasonCode)
            diagnosticStore.recordStopped(reasonText)
            diagnosticStore.recordCompleted(success = false, error = "Worker stopped: $reasonText")
            notificationManager.cancelProgressNotification()
            Log.d(TAG, "SyncWorker stopped during execution: $reasonText")
            throw e
        } catch (e: Exception) {
            val isTransient = isTransientNetworkError(e)
            Log.d(TAG, "exception in SyncWorker doWork: isTransient=$isTransient")
            val sanitized = "Exception (${e.javaClass.simpleName}): ${e.message}"
            diagnosticStore.recordCompleted(success = false, error = sanitized)
            notificationManager.showFailureNotification()
            Result.failure()
        }
    }

    private fun formatStopReason(reasonCode: Int): String {
        return when (reasonCode) {
            WorkInfo.STOP_REASON_CANCELLED_BY_APP -> "Cancelled by app"
            WorkInfo.STOP_REASON_PREEMPT -> "Preempted by higher priority job"
            WorkInfo.STOP_REASON_TIMEOUT -> "Execution timed out"
            WorkInfo.STOP_REASON_DEVICE_STATE -> "Device state change (Doze/battery)"
            WorkInfo.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> "Battery low constraint unsatisfied"
            WorkInfo.STOP_REASON_CONSTRAINT_CHARGING -> "Charging constraint unsatisfied"
            WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "Connectivity constraint unsatisfied"
            WorkInfo.STOP_REASON_CONSTRAINT_DEVICE_IDLE -> "Device idle constraint unsatisfied"
            WorkInfo.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW -> "Storage low constraint unsatisfied"
            WorkInfo.STOP_REASON_QUOTA -> "App background execution quota exceeded"
            WorkInfo.STOP_REASON_BACKGROUND_RESTRICTION -> "Background restriction applied by OS"
            WorkInfo.STOP_REASON_APP_STANDBY -> "App standby bucket constraint"
            WorkInfo.STOP_REASON_USER -> "User stopped job"
            WorkInfo.STOP_REASON_SYSTEM_PROCESSING -> "System processing requires stop"
            WorkInfo.STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED -> "Estimated launch time changed"
            WorkInfo.STOP_REASON_UNKNOWN -> "Unknown stop reason"
            WorkInfo.STOP_REASON_NOT_STOPPED -> "Not stopped"
            else -> "Stop reason code: $reasonCode"
        }
    }

    companion object {
        private const val TAG = "WellmealSyncWorker"
        private const val MAX_WORKMANAGER_RETRIES = 3

        private val FAST_RETRY_DELAYS_MS = listOf(
            5_000L,
            10_000L,
            20_000L
        )
    }
}
