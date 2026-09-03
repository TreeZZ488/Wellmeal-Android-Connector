package com.wellmeal.connector

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val attemptCount = runAttemptCount
        val isLastRetry = attemptCount >= MAX_RETRIES
        Log.d(TAG, "automatic sync started runAttemptCount=$attemptCount maxRetries=$MAX_RETRIES")

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
                // No cached signed-in account found -> Permanent failure (no UI launched)
                Log.d(TAG, "permanent failure detected (no cached account)")
                notificationManager.showFailureNotification()
                return Result.failure()
            }

            // Execute automatic sync
            val syncResult = syncCoordinator.performSync(
                trigger = SyncTrigger.AUTOMATIC,
                isLastRetry = isLastRetry
            )

            if (syncResult.dailyUploaded && syncResult.latestUploaded) {
                Log.d(TAG, "sync succeeded (attempt $attemptCount)")
                notificationManager.cancelProgressNotification()
                Result.success()
            } else if (syncResult.retryable && !isLastRetry) {
                Log.d(TAG, "transient network failure detected (attempt $attemptCount/$MAX_RETRIES) -> returning Result.retry()")
                // Remove progress notification quietly; WorkManager will retry silently with backoff
                notificationManager.cancelProgressNotification()
                Result.retry()
            } else {
                Log.d(TAG, "permanent failure or max retries reached (attempt $attemptCount/$MAX_RETRIES)")
                notificationManager.showFailureNotification()
                Result.failure()
            }
        } catch (e: Exception) {
            val isTransient = isTransientNetworkError(e)
            if (isTransient && !isLastRetry) {
                Log.d(TAG, "transient network failure detected in catch (attempt $attemptCount/$MAX_RETRIES) -> returning Result.retry()")
                notificationManager.cancelProgressNotification()
                Result.retry()
            } else {
                Log.d(TAG, "permanent failure or max retries reached in catch (attempt $attemptCount/$MAX_RETRIES)")
                notificationManager.showFailureNotification()
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "WellmealSyncWorker"
        private const val MAX_RETRIES = 3
    }
}
