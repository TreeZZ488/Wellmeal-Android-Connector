package com.wellmeal.connector

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
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
                // No cached signed-in account found
                notificationManager.showFailureNotification()
                return Result.failure()
            }

            // Execute automatic sync
            val syncResult = syncCoordinator.performSync(
                trigger = SyncTrigger.AUTOMATIC
            )

            // Interpret WorkManager result based on daily upload success
            if (syncResult.dailyUploaded) {
                notificationManager.cancelProgressNotification()
                Result.success()
            } else {
                notificationManager.showFailureNotification()
                Result.failure()
            }
        } catch (_: Exception) {
            notificationManager.showFailureNotification()
            Result.failure()
        }
    }
}
