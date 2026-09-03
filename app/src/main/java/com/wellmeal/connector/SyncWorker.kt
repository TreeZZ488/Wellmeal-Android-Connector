package com.wellmeal.connector

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
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
                syncHistoryStore = syncHistoryStore
            )

            // Wait for MSAL initialization and account restoration
            val readyResult = authManager.awaitReady()
            val account = readyResult.getOrNull()

            if (account == null) {
                // No cached signed-in account found -> Permanent failure (no UI launched, no retry)
                Log.d(TAG, "permanent failure detected (no cached account)")
                val failResult = SyncResult(
                    date = java.time.LocalDate.now().minusDays(1),
                    dailyUploaded = false,
                    latestUploaded = false,
                    profileStatus = ProfileSyncStatus.SKIPPED,
                    retryable = false,
                    error = "No cached Microsoft account signed in"
                )
                syncCoordinator.recordSyncHistory(failResult, SyncTrigger.AUTOMATIC)
                notificationManager.showFailureNotification()
                return Result.failure()
            }

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
                    notificationManager.cancelProgressNotification()
                    return Result.success()
                }

                // If non-retryable (permanent error, e.g. permission/auth failure), stop fast retries
                if (!syncResult.retryable) {
                    Log.d(TAG, "permanent failure detected on fast attempt=$attemptNumber")
                    syncCoordinator.recordSyncHistory(syncResult, SyncTrigger.AUTOMATIC)
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
                // Cancel progress notification quietly; WorkManager will retry later silently with backoff
                notificationManager.cancelProgressNotification()
                Result.retry()
            } else {
                Log.d(TAG, "fast retries exhausted and max WorkManager retries reached -> Result.failure()")
                val finalFailResult = finalResult.copy(retryable = false)
                syncCoordinator.recordSyncHistory(finalFailResult, SyncTrigger.AUTOMATIC)
                notificationManager.showFailureNotification()
                Result.failure()
            }
        } catch (e: Exception) {
            val isTransient = isTransientNetworkError(e)
            Log.d(TAG, "exception in SyncWorker doWork: isTransient=$isTransient")
            notificationManager.showFailureNotification()
            Result.failure()
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
